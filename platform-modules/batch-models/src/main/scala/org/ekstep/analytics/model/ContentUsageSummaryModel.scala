package org.ekstep.analytics.model

import org.ekstep.analytics.framework.IBatchModel
import org.ekstep.analytics.framework._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import scala.collection.mutable.Buffer
import org.apache.spark.HashPartitioner
import org.ekstep.analytics.framework.JobContext
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.DtRange
import org.ekstep.analytics.framework.GData
import org.ekstep.analytics.framework.PData
import org.ekstep.analytics.framework.Dimensions
import org.ekstep.analytics.framework.MEEdata
import org.ekstep.analytics.framework.Context
import org.ekstep.analytics.framework.Filter
import org.ekstep.analytics.framework.DataFilter
import org.ekstep.analytics.framework.util.JobLogger
import com.datastax.spark.connector._
import org.ekstep.analytics.util.Constants
import org.joda.time.DateTime
import org.apache.commons.lang3.StringUtils
import scala.collection.mutable.ListBuffer

case class content_key(period: Int, content_id: String, tag: String);
case class content_usage_summary(ck: content_key, total_ts: Double, total_sessions: Long, avg_ts_session: Double, total_interactions: Long, avg_interactions_min: Double, dt_range: DtRange, syncts: Long, gdata: Option[GData] = None) extends AlgoOutput;
case class registered_tag(tag_id: String)
case class input_events(ck: content_key, events: Buffer[content_usage_summary]) extends Input with AlgoInput

object ContentUsageSummaryModel extends IBatchModelTemplate[DerivedEvent, input_events, content_usage_summary, MeasuredEvent] with Serializable {

    val className = "org.ekstep.analytics.model.ContentUsageSummary"
    override def name: String = "ContentUsageSummarizer"

    private def _computeMetrics(events: Buffer[content_usage_summary], ck: content_key): content_usage_summary = {

        val firstEvent = events.sortBy { x => x.dt_range.from }.head;
        val lastEvent = events.sortBy { x => x.dt_range.to }.last;
        val ck = firstEvent.ck;

        val gdata = if (StringUtils.equals(ck.content_id, "all")) None else Option(new GData(ck.content_id, firstEvent.gdata.get.ver));

        val date_range = DtRange(firstEvent.dt_range.from, lastEvent.dt_range.to);
        val total_ts = CommonUtil.roundDouble(events.map { x => x.total_ts }.sum, 2);
        val total_sessions = events.size
        val avg_ts_session = CommonUtil.roundDouble((total_ts / total_sessions), 2)
        val total_interactions = events.map { x => x.total_interactions }.sum;
        val avg_interactions_min = if (total_interactions == 0 || total_ts == 0) 0d else CommonUtil.roundDouble(BigDecimal(total_interactions / (total_ts / 60)).toDouble, 2);
        content_usage_summary(ck, total_ts, total_sessions, avg_ts_session, total_interactions, avg_interactions_min, date_range, lastEvent.syncts, gdata);
    }

    private def getContentUsageSummary(event: DerivedEvent, period: Int, contentId: String, tagId: String): content_usage_summary = {

        val ck = content_key(period, contentId, tagId);
        val gdata = event.dimensions.gdata;
        val total_ts = event.edata.eks.asInstanceOf[Map[String, AnyRef]].get("timeSpent").get.asInstanceOf[Double];
        val total_sessions = 1;
        val avg_ts_session = total_ts;
        val total_interactions = event.edata.eks.asInstanceOf[Map[String, AnyRef]].get("noOfInteractEvents").get.asInstanceOf[Int];
        val avg_interactions_min = if (total_interactions == 0 || total_ts == 0) 0d else CommonUtil.roundDouble(BigDecimal(total_interactions / (total_ts / 60)).toDouble, 2);
        content_usage_summary(ck, total_ts, total_sessions, avg_ts_session, total_interactions, avg_interactions_min, event.context.date_range, event.syncts, gdata);
    }

    private def _getValidTags(event: DerivedEvent, registeredTags: Array[String]): Array[String] = {

        val tagList = event.tags.getOrElse(List()).asInstanceOf[List[Map[String, List[String]]]]
        val genieTagFilter = if (tagList.nonEmpty) tagList.filter(f => f.contains("genie")) else List()
        val tempList = if (genieTagFilter.nonEmpty) genieTagFilter.filter(f => f.contains("genie")).last.get("genie").get; else List();
        tempList.filter { x => registeredTags.contains(x) }.toArray;
    }

    override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[input_events] = {

        val configMapping = sc.broadcast(config);
        val tags = sc.cassandraTable[registered_tag](Constants.CONTENT_KEY_SPACE_NAME, Constants.REGISTERED_TAGS).select("tag_id").where("active = ?", true).map { x => x.tag_id }.collect
        val registeredTags = if (tags.nonEmpty) tags; else Array[String]();

        val sessionEvents = DataFilter.filter(data, Filter("eid", "EQ", Option("ME_SESSION_SUMMARY")));

        val normalizeEvents = sessionEvents.map { event =>

            var list: ListBuffer[content_usage_summary] = ListBuffer[content_usage_summary]();
            val period = CommonUtil.getPeriod(event.context.date_range.to, Period.DAY);
            // For all
            list += getContentUsageSummary(event, period, "all", "all");
            list += getContentUsageSummary(event, period, event.dimensions.gdata.get.id, "all");
            val tags = _getValidTags(event, registeredTags);
            for (tag <- tags) {
                list += getContentUsageSummary(event, period, event.dimensions.gdata.get.id, tag);
            }
            list.toArray;
        }.flatMap { x => x.map { x => x } };

        normalizeEvents.map { x => (x.ck, Buffer(x)) }
            .partitionBy(new HashPartitioner(JobContext.parallelization))
            .reduceByKey((a, b) => a ++ b).map { x => input_events(x._1, x._2) };
    }

    override def algorithm(data: RDD[input_events], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[content_usage_summary] = {

        data.map { x =>
            _computeMetrics(x.events, x.ck);
        }
    }

    override def postProcess(data: RDD[content_usage_summary], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[MeasuredEvent] = {
        data.map { cuMetrics =>
            val mid = CommonUtil.getMessageId("ME_CONTENT_USAGE_SUMMARY", cuMetrics.ck.content_id + cuMetrics.ck.tag + cuMetrics.ck.period, "DAY", cuMetrics.syncts);
            val measures = Map(
                "total_ts" -> cuMetrics.total_ts,
                "total_sessions" -> cuMetrics.total_sessions,
                "avg_ts_session" -> cuMetrics.avg_ts_session,
                "total_interactions" -> cuMetrics.total_interactions,
                "avg_interactions_min" -> cuMetrics.avg_interactions_min)

            MeasuredEvent("ME_CONTENT_USAGE_SUMMARY", System.currentTimeMillis(), cuMetrics.dt_range.to, "1.0", mid, "", None, None,
                Context(PData(config.getOrElse("producerId", "AnalyticsDataPipeline").asInstanceOf[String], config.getOrElse("modelId", "ContentUsageSummary").asInstanceOf[String], config.getOrElse("modelVersion", "1.0").asInstanceOf[String]), None, config.getOrElse("granularity", "DAY").asInstanceOf[String], cuMetrics.dt_range),
                Dimensions(None, None, cuMetrics.gdata, None, None, None, None, None, None, Option(cuMetrics.ck.tag), Option(cuMetrics.ck.period), Option(cuMetrics.ck.content_id)),
                MEEdata(measures));
        }

    }

}