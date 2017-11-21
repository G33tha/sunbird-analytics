package org.ekstep.analytics.model

import scala.collection.mutable.Buffer
import org.ekstep.analytics.framework.IBatchModelTemplate
import org.ekstep.analytics.framework._
import org.ekstep.analytics.framework.GData
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.util.CommonUtil
import org.apache.spark.HashPartitioner
import org.ekstep.analytics.util.Constants
import com.datastax.spark.connector._
import org.apache.commons.lang3.StringUtils
import scala.collection.mutable.ListBuffer
import org.joda.time.DateTime
import org.ekstep.analytics.framework.ContentKey
import org.joda.time.DateTimeZone
import org.ekstep.analytics.framework.conf.AppConf

case class ContentPopularitySummary(ck: ContentKey, m_comments: List[Map[String, AnyRef]], m_ratings: List[Map[String, AnyRef]], m_avg_rating: Double, m_downloads: Int, m_side_loads: Int, dt_range: DtRange, syncts: Long, pdata: PData, gdata: Option[GData] = None) extends AlgoOutput;
case class InputEventsContentPopularity(ck: ContentKey, events: Buffer[ContentPopularitySummary]) extends Input with AlgoInput

object ContentPopularitySummaryModel extends IBatchModelTemplate[V3Event, InputEventsContentPopularity, ContentPopularitySummary, MeasuredEvent] with Serializable {

    val className = "org.ekstep.analytics.model.ContentPopularitySummaryModel"
    override def name: String = "ContentPopularitySummaryModel"

    private def _computeMetrics(events: Buffer[ContentPopularitySummary], ck: ContentKey): ContentPopularitySummary = {
        val sortedEvents = events.sortBy { x => x.dt_range.from };
        val firstEvent = sortedEvents.head;
        val lastEvent = sortedEvents.last;
        val ck = firstEvent.ck;

        val gdata = if (StringUtils.equals(ck.content_id, "all")) None else Option(new GData(ck.content_id, firstEvent.gdata.get.ver));
        val dt_range = DtRange(firstEvent.dt_range.from, lastEvent.dt_range.to);
        val downloads = events.map { x => x.m_downloads }.sum;
        val side_loads = events.map { x => x.m_side_loads }.sum;
        val comments = events.map { x => x.m_comments }.flatMap { x => x }.filter { x => !StringUtils.isEmpty(x.getOrElse("comment", "").asInstanceOf[String]) }.toList;
        val ratings = events.map { x => x.m_ratings }.flatMap { x => x }.filter { x => x.getOrElse("rating", 0.0).asInstanceOf[Double] > 0.0 }.toList;
        val avg_rating = if (ratings.length > 0) {
            val total_rating = ratings.map(f => f.getOrElse("rating", 0.0).asInstanceOf[Double]).sum;
            CommonUtil.roundDouble(total_rating / ratings.length, 2);
        } else 0.0;
        ContentPopularitySummary(ck, comments, ratings, avg_rating, downloads, side_loads, dt_range, lastEvent.syncts, firstEvent.pdata, gdata);
    }

    private def getContentPopularitySummary(event: V3Event, period: Int, contentId: String, tagId: String): Array[ContentPopularitySummary] = {
        val dt_range = DtRange(event.ets, event.ets);
        if ("FEEDBACK".equals(event.eid)) {
            val cId = getFeedbackContentId(event, contentId);

            val pdata = CommonUtil.getAppDetails(event)
            val channel = CommonUtil.getChannelId(event)

            val ck = ContentKey(period, pdata.id, channel, cId, tagId);
            val gdata = Option(new GData(event.`object`.get.id, event.`object`.get.ver.get))
            val comments = List(Map("comment" -> event.edata.comments, "time" -> event.ets.asInstanceOf[AnyRef]));
            val ratings = List(Map("rating" -> event.edata.rating.asInstanceOf[AnyRef], "time" -> event.ets.asInstanceOf[AnyRef]));
            val avg_rating = event.edata.rating;
            Array(ContentPopularitySummary(ck, comments, ratings, avg_rating, 0, 0, dt_range, CommonUtil.getEventSyncTS(event), pdata, gdata));
        } else if ("SHARE".equals(event.eid)) {
            val contents = event.edata.items;

            val pdata = CommonUtil.getAppDetails(event)
            val channel = CommonUtil.getChannelId(event)

            contents.map { content =>
                val tsContentId = if ("all".equals(contentId)) contentId else content.obj.id

                val ck = ContentKey(period, pdata.id, channel, tsContentId, tagId);
                val gdata = if ("all".equals(contentId)) None else Option(new GData(tsContentId, content.obj.ver.getOrElse("")));
                val transferCount = content.params.asInstanceOf[List[Map[String, AnyRef]]].head.get("transfers").get.asInstanceOf[Number].doubleValue();
                val downloads = if (transferCount == 0.0) 1 else 0;
                val side_loads = if (transferCount >= 1.0) 1 else 0;
                ContentPopularitySummary(ck, List(), List(), 0.0, downloads, side_loads, dt_range, CommonUtil.getEventSyncTS(event), pdata, gdata);
            }.toArray
        } else {
            Array();
        }
    }

    private def getFeedbackContentId(event: V3Event, default: String): String = {
        if ("all".equals(default)) default
        else {
            if (null == event.`object`) {
                default
            } else {
                val id = event.`object`.get.id //.asInstanceOf[String]
                if(id.isEmpty()) default else id
            }
        }
    }

    override def preProcess(data: RDD[V3Event], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[InputEventsContentPopularity] = {
        val tags = sc.cassandraTable[RegisteredTag](Constants.CONTENT_KEY_SPACE_NAME, Constants.REGISTERED_TAGS).filter { x => true == x.active }.map { x => x.tag_id }.collect
        val registeredTags = if (tags.nonEmpty) tags; else Array[String]();

        val transferEvents = DataFilter.filter(data, Array(Filter("actor.id", "ISNOTEMPTY", None), Filter("eid", "EQ", Option("SHARE"))));
        val importEvents = DataFilter.filter(transferEvents, Filter("edata.dir", "EQ", Option("in"))).filter { x => x.edata.items.map(f => f.obj.`type`).contains("Content") };
        val feedbackEvents = DataFilter.filter(data, Array(Filter("actor.id", "ISNOTEMPTY", None), Filter("eid", "EQ", Option("FEEDBACK"))));
        val normalizeEvents = importEvents.union(feedbackEvents).map { event =>
            var list: ListBuffer[ContentPopularitySummary] = ListBuffer[ContentPopularitySummary]();
            val period = CommonUtil.getPeriod(event.ets, Period.DAY);

            list ++= getContentPopularitySummary(event, period, "all", "all");
            list ++= getContentPopularitySummary(event, period, event.`object`.get.id, "all");
            val tags = CommonUtil.getValidTags(event, registeredTags);
            for (tag <- tags) {
                list ++= getContentPopularitySummary(event, period, "all", tag); // for tag
                list ++= getContentPopularitySummary(event, period, event.`object`.get.id, tag); // for tag and content
            }
            list.toArray
        }.flatMap { x => x };

        normalizeEvents.map { x => (x.ck, Buffer(x)) }
            .partitionBy(new HashPartitioner(JobContext.parallelization))
            .reduceByKey((a, b) => a ++ b).map { x => InputEventsContentPopularity(x._1, x._2) };
    }

    override def algorithm(data: RDD[InputEventsContentPopularity], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ContentPopularitySummary] = {
        data.map { x =>
            _computeMetrics(x.events, x.ck);
        }
    }

    override def postProcess(data: RDD[ContentPopularitySummary], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[MeasuredEvent] = {
        val meEventVersion = AppConf.getConfig("telemetry.version");
        data.map { cpMetrics =>
            val mid = CommonUtil.getMessageId("ME_CONTENT_POPULARITY_SUMMARY", cpMetrics.ck.content_id + cpMetrics.ck.tag + cpMetrics.ck.period, "DAY", cpMetrics.syncts, Option(cpMetrics.ck.app_id), Option(cpMetrics.ck.channel));
            val measures = Map(
                "m_downloads" -> cpMetrics.m_downloads,
                "m_side_loads" -> cpMetrics.m_side_loads,
                "m_avg_rating" -> cpMetrics.m_avg_rating,
                "m_ratings" -> cpMetrics.m_ratings,
                "m_comments" -> cpMetrics.m_comments,
                "m_avg_rating" -> cpMetrics.m_avg_rating)

            MeasuredEvent("ME_CONTENT_POPULARITY_SUMMARY", System.currentTimeMillis(), cpMetrics.syncts, meEventVersion, mid, "", cpMetrics.ck.channel, None, None,
                Context(PData(config.getOrElse("producerId", "AnalyticsDataPipeline").asInstanceOf[String], config.getOrElse("modelVersion", "1.0").asInstanceOf[String], Option(config.getOrElse("modelId", "ContentPopularitySummary").asInstanceOf[String])), None, config.getOrElse("granularity", "DAY").asInstanceOf[String], cpMetrics.dt_range),
                Dimensions(None, None, cpMetrics.gdata, None, None, None, Option(cpMetrics.pdata), None, None, None, Option(cpMetrics.ck.tag), Option(cpMetrics.ck.period), Option(cpMetrics.ck.content_id)),
                MEEdata(measures));
        }
    }
}