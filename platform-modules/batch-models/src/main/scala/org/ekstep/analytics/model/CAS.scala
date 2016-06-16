package org.ekstep.analytics.model

import org.ekstep.analytics.framework._
import org.ekstep.analytics.framework.util.CommonUtil
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.util.Constants
import com.datastax.spark.connector._
import org.ekstep.analytics.framework.util.JSONUtils
import org.joda.time.DateTime
import org.apache.spark.HashPartitioner
import scala.collection.mutable.Buffer

case class ContentSumm(content_id: String, start_date: DateTime, total_num_sessions: Long, total_ts: Double, average_ts_session: Double,
                          total_interactions: Long, average_interactions_min: Double, num_sessions_week: Double, ts_week: Double, content_type: String, mime_type: String)

object CAS extends IBatchModelTemplate[MEEvent,(String, (Buffer[MEEvent], Option[ContentSumm])),(String, (ContentSumm, DtRange, String)),MEEvent] with Serializable {
  
    @Override
    def preProcess(data: RDD[MEEvent], jobParams: Option[Map[String, AnyRef]])(implicit sc: SparkContext): RDD[(String, (Buffer[MEEvent], Option[ContentSumm]))] = {
        
        val filteredData = DataFilter.filter(data, Filter("eventId", "EQ", Option("ME_SESSION_SUMMARY")));
        val newEvents = filteredData.map(event => (event.dimensions.gdata.get.id, Buffer(event)))
            .partitionBy(new HashPartitioner(JobContext.parallelization))
            .reduceByKey((a, b) => a ++ b);
        val prevContentState = newEvents.map(f => ContentId(f._1)).joinWithCassandraTable[ContentSumm](Constants.CONTENT_KEY_SPACE_NAME, Constants.CONTENT_CUMULATIVE_SUMMARY_TABLE).map(f => (f._1.content_id, f._2))
        newEvents.leftOuterJoin(prevContentState);
    }
    
    @Override
    def algorithm(data: RDD[(String, (Buffer[MEEvent], Option[ContentSumm]))], jobParams: Option[Map[String, AnyRef]])(implicit sc: SparkContext): RDD[(String, (ContentSumm, DtRange, String))] = {
        
        val contentSummary = data.mapValues { events =>

            val sortedEvents = events._1.sortBy { x => x.edata.eks.asInstanceOf[Map[String, AnyRef]].get("syncDate").get.asInstanceOf[Long] };
            val firstEvent = sortedEvents.head;
            val lastEvent = sortedEvents.last;
            val eventSyncts = lastEvent.edata.eks.asInstanceOf[Map[String, AnyRef]].get("syncDate").get.asInstanceOf[Long];
            val gameId = firstEvent.dimensions.gdata.get.id;
            val gameVersion = firstEvent.dimensions.gdata.get.ver;

            val firstGE = events._1.sortBy { x => x.context.date_range.from }.head;
            val lastGE = events._1.sortBy { x => x.context.date_range.to }.last;
            val eventStartTimestamp = firstGE.context.date_range.from;
            val eventEndTimestamp = lastGE.context.date_range.to;
            val eventStartDate = new DateTime(eventStartTimestamp);
            val date_range = DtRange(firstEvent.edata.eks.asInstanceOf[Map[String, AnyRef]].get("syncDate").get.asInstanceOf[Long], eventSyncts);

            val prevContentSummary = events._2.getOrElse(ContentSumm(gameId, DateTime.now(), 0L, 0.0, 0.0, 0L, 0.0, 0L, 0.0, "", ""));
            val startDate = if (eventStartDate.isBefore(prevContentSummary.start_date)) eventStartDate else prevContentSummary.start_date;
            val numSessions = sortedEvents.size + prevContentSummary.total_num_sessions;
            val timeSpent = sortedEvents.map { x =>
                (x.edata.eks.asInstanceOf[Map[String, AnyRef]].get("timeSpent").get.asInstanceOf[Double])
            }.sum + (prevContentSummary.total_ts * 3600);
            val totalInteractions = sortedEvents.map { x =>
                (x.edata.eks.asInstanceOf[Map[String, AnyRef]].get("noOfInteractEvents").get.asInstanceOf[Int])
            }.sum + prevContentSummary.total_interactions;
            val numWeeks = CommonUtil.getWeeksBetween(startDate.getMillis, eventEndTimestamp)

            val averageTsSession = (timeSpent / numSessions);
            val averageInteractionsMin = if (totalInteractions == 0 || timeSpent == 0) 0d else CommonUtil.roundDouble(BigDecimal(totalInteractions / (timeSpent / 60)).toDouble, 2);
            val numSessionsWeek: Double = if (numWeeks == 0) numSessions else numSessions / numWeeks
            val tsWeek = if (numWeeks == 0) timeSpent else timeSpent / numWeeks
            val contentType = if (prevContentSummary.content_type.isEmpty()) firstEvent.edata.eks.asInstanceOf[Map[String, AnyRef]].getOrElse("contentType", "").asInstanceOf[String] else prevContentSummary.content_type
            val mimeType = if (prevContentSummary.mime_type.isEmpty()) firstEvent.edata.eks.asInstanceOf[Map[String, AnyRef]].getOrElse("mimeType", "").asInstanceOf[String] else prevContentSummary.mime_type

            (ContentSumm(gameId, startDate, numSessions, CommonUtil.roundDouble(timeSpent / 3600, 2), CommonUtil.roundDouble(averageTsSession, 2), totalInteractions, CommonUtil.roundDouble(averageInteractionsMin, 2), numSessionsWeek, tsWeek, contentType, mimeType), date_range, gameVersion)
        }.cache();
        contentSummary
    }
     
    @Override
    def postProcess(data: RDD[(String, (ContentSumm, DtRange, String))], jobParams: Option[Map[String, AnyRef]])(implicit sc: SparkContext): RDD[MEEvent] = {
        
        val config = jobParams.getOrElse(Map[String, AnyRef]())
        data.map(f => f._2._1).saveToCassandra(Constants.CONTENT_KEY_SPACE_NAME, Constants.CONTENT_CUMULATIVE_SUMMARY_TABLE);

        val summaries = sc.cassandraTable[ContentSumm](Constants.CONTENT_KEY_SPACE_NAME, Constants.CONTENT_CUMULATIVE_SUMMARY_TABLE).filter { x => !"Collection".equals(x.content_type) };
        val count = summaries.count().intValue();
        val defaultVal = if (5 > count) count else 5;
        val topContentByTime = summaries.sortBy(f => f.total_ts, false, 1).take(config.getOrElse("topK", defaultVal).asInstanceOf[Int]);
        val topContentBySessions = summaries.sortBy(f => f.total_num_sessions, false, 1).take(config.getOrElse("topK", defaultVal).asInstanceOf[Int]);

        val rdd = sc.parallelize(Array(ContentMetrics("content", topContentByTime.map { x => (x.content_id, x.total_ts) }.toMap, topContentBySessions.map { x => (x.content_id, x.total_num_sessions) }.toMap)), 1);
        rdd.saveToCassandra(Constants.CONTENT_KEY_SPACE_NAME, Constants.CONTENT_CUMULATIVE_METRICS_TABLE);
        
        data.map(f => {
            getMEEvent(f._2._1, config, f._2._2, f._2._3);
        });
       
    }
    
     private def getMEEvent(contentSumm: ContentSumm, config: Map[String, AnyRef], dtRange: DtRange, game_version: String): MEEvent = {

        val eid = "ME_CONTENT_SUMMARY"
        val context = Context(PData(config.getOrElse("producerId", "AnalyticsDataPipeline").asInstanceOf[String], config.getOrElse("modelId", "ContentSummary").asInstanceOf[String], config.getOrElse("modelVersion", "1.0").asInstanceOf[String]), None, config.getOrElse("granularity", "DAY").asInstanceOf[String], dtRange)
        val dimension = Dimensions(None, None, Option(new GData(contentSumm.content_id, game_version)), None, None, None, None)
        val measures = Map(
            "timeSpent" -> contentSumm.total_ts,
            "totalSessions" -> contentSumm.total_num_sessions,
            "averageTimeSpent" -> contentSumm.average_ts_session,
            "totalInteractionEvents" -> contentSumm.total_interactions,
            "averageInteractionsPerMin" -> contentSumm.average_interactions_min,
            "sessionsPerWeek" -> contentSumm.num_sessions_week,
            "tsPerWeek" -> contentSumm.ts_week,
            "contentType" -> contentSumm.content_type,
            "mimeType" -> contentSumm.mime_type);
        new MEEvent(eid,context,dimension,MEEdata(measures))
        
    }
}