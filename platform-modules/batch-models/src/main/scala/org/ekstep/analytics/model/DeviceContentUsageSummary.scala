package org.ekstep.analytics.model

import org.ekstep.analytics.framework._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import scala.collection.mutable.Buffer
import org.apache.spark.HashPartitioner
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.util.Constants
import com.datastax.spark.connector._

case class DeviceSummaryInput(device_id: String, data: Buffer[DerivedEvent], prevData: Option[UsageSummary]) extends AlgoInput
case class DeviceContentUsageSummaryInput(device_id: String, contentId: String, data: Buffer[DerivedEvent], prevData: Option[DeviceContentSummary])
case class DeviceContentSummary(device_id: String, content_id: String, gamever: String, num_sessions: Long, total_interactions: Long, avg_interactions_min: Double,
                                total_timespent: Double, last_played_on: Long, start_time: Long,
                                mean_play_time_interval: Double, downloaded: Boolean, download_date: Long, group_user: Boolean) extends AlgoOutput
case class DeviceContent(device_id: String, contentId: String)

object DeviceContentUsageSummary extends IBatchModelTemplate[DerivedEvent, DeviceSummaryInput, DeviceContentSummary, MeasuredEvent] with Serializable {

    val className = "org.ekstep.analytics.model.DeviceContentUsageSummary"
    override def name: String = "DeviceContentUsageSummarizer"

    override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DeviceSummaryInput] = {

        val filteredEvents = DataFilter.filter(data, Filter("eid", "EQ", Option("ME_SESSION_SUMMARY")));
        val deviceSessions = filteredEvents.map { event =>
            val eksMap = event.edata.eks.asInstanceOf[Map[String, AnyRef]]
            val did = event.dimensions.did.get
            (did, Buffer(event));
        }.partitionBy(new HashPartitioner(JobContext.parallelization)).reduceByKey((a, b) => a ++ b);
        val prevDeviceSummary = deviceSessions.map(f => DeviceId(f._1)).joinWithCassandraTable[UsageSummary](Constants.KEY_SPACE_NAME, Constants.DEVICE_USAGE_SUMMARY_TABLE).map(f => (f._1.device_id, f._2))
        val joinedData = deviceSessions.leftOuterJoin(prevDeviceSummary)
        joinedData.map(f => DeviceSummaryInput(f._1, f._2._1, f._2._2));
    }

    override def algorithm(data: RDD[DeviceSummaryInput], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DeviceContentSummary] = {

        val events = data.flatMap { x => x.data }
        val dcuSummaries = events.map { event =>
            val did = event.dimensions.did.get
            val content_id = event.dimensions.gdata.get.id
            ((did, content_id), Buffer(event));
        }.partitionBy(new HashPartitioner(JobContext.parallelization)).reduceByKey((a, b) => a ++ b);
        val prevDeviceContentSummary = dcuSummaries.map(f => DeviceContent(f._1._1, f._1._2)).joinWithCassandraTable[DeviceContentSummary](Constants.KEY_SPACE_NAME, Constants.DEVICE_CONTENT_SUMMARY).map(f => ((f._1.device_id, f._1.contentId), f._2))
        val joinedData = dcuSummaries.leftOuterJoin(prevDeviceContentSummary)
        val dcusEvents = joinedData.map(f => DeviceContentUsageSummaryInput(f._1._1, f._1._2, f._2._1, f._2._2));

        dcusEvents.map { dcusEvent =>
            val firstEvent = dcusEvent.data.sortBy { x => x.context.date_range.from }.head;
            val lastEvent = dcusEvent.data.sortBy { x => x.context.date_range.to }.last;
            val gameVer = firstEvent.dimensions.gdata.get.ver
            val prevDeviceContentSummary = dcusEvent.prevData.getOrElse(DeviceContentSummary(dcusEvent.device_id, dcusEvent.contentId, gameVer, 0l, 0l, 0.0, 0.0, 0l, 0l, 0.0, false, 0l, false))
            val num_sessions = dcusEvent.data.size + prevDeviceContentSummary.num_sessions
            val total_timespent = CommonUtil.roundDouble(dcusEvent.data.map { x => (x.edata.eks.asInstanceOf[Map[String, AnyRef]].get("timeSpent").get.asInstanceOf[Double]) }.sum, 2) + prevDeviceContentSummary.total_timespent;
            val total_interactions = dcusEvent.data.map { x => x.edata.eks.asInstanceOf[Map[String, AnyRef]].get("noOfInteractEvents").get.asInstanceOf[Int] }.sum + prevDeviceContentSummary.total_interactions
            val avg_interactions_min = if (total_interactions == 0 || total_timespent == 0) 0d else CommonUtil.roundDouble(BigDecimal(total_interactions / (total_timespent / 60)).toDouble, 2);
            val last_played_on = lastEvent.context.date_range.to
            val eventStartTime = firstEvent.context.date_range.from
            val start_time = if (prevDeviceContentSummary.start_time == 0) eventStartTime else if (eventStartTime > prevDeviceContentSummary.start_time) eventStartTime else prevDeviceContentSummary.start_time
            val timeDiff = CommonUtil.getTimeDiff(start_time, last_played_on).get
            val play_time_interval = timeDiff - total_timespent
            val mean_play_time_interval = if (num_sessions < 2) 0d else CommonUtil.roundDouble(BigDecimal(play_time_interval / (num_sessions - 1)).toDouble, 2)
            val downloaded = prevDeviceContentSummary.downloaded
            val download_date = prevDeviceContentSummary.download_date
            val group_user = firstEvent.dimensions.group_user.get
            DeviceContentSummary(dcusEvent.device_id, dcusEvent.contentId, gameVer, num_sessions, total_interactions, avg_interactions_min, total_timespent, last_played_on, start_time, mean_play_time_interval, downloaded, download_date, group_user)
        }.cache()
    }

    override def postProcess(data: RDD[DeviceContentSummary], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[MeasuredEvent] = {

        data.saveToCassandra(Constants.KEY_SPACE_NAME, Constants.DEVICE_CONTENT_SUMMARY)
        data.map { dcuSummary =>
            val mid = CommonUtil.getMessageId("ME_DEVICE_CONTENT_USAGE_SUMMARY", null, null, DtRange(0l, 0l), dcuSummary.content_id + dcuSummary.device_id);
            val measures = Map(
                "num_sessions" -> dcuSummary.num_sessions,
                "total_timespent" -> dcuSummary.total_timespent,
                "avg_interactions_min" -> dcuSummary.avg_interactions_min,
                "total_interactions" -> dcuSummary.total_interactions,
                "last_played_on" -> dcuSummary.last_played_on,
                "start_time" -> dcuSummary.start_time,
                "mean_play_time_interval" -> dcuSummary.mean_play_time_interval,
                "downloaded" -> dcuSummary.downloaded,
                "download_date" -> dcuSummary.download_date,
                "group_user" -> dcuSummary.group_user);
            MeasuredEvent("ME_DEVICE_CONTENT_USAGE_SUMMARY", System.currentTimeMillis(), dcuSummary.last_played_on, "1.0", mid, null, None, None,
                Context(PData(config.getOrElse("producerId", "AnalyticsDataPipeline").asInstanceOf[String], config.getOrElse("modelId", "DeviceContentUsageSummary").asInstanceOf[String], config.getOrElse("modelVersion", "1.0").asInstanceOf[String]), None, "CUMULATIVE", DtRange(dcuSummary.start_time, dcuSummary.last_played_on)),
                Dimensions(None, Option(dcuSummary.device_id), Option(new GData(dcuSummary.content_id, dcuSummary.gamever)), None, None, None, None, None),
                MEEdata(measures));
        }
    }
}