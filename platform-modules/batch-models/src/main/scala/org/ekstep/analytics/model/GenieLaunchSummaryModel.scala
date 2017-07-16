package org.ekstep.analytics.model

import scala.collection.mutable.Buffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.framework.AlgoInput
import org.ekstep.analytics.framework.AlgoOutput
import org.ekstep.analytics.framework.Context
import org.ekstep.analytics.framework.DataFilter
import org.ekstep.analytics.framework.Dimensions
import org.ekstep.analytics.framework.DtRange
import org.ekstep.analytics.framework.ETags
import org.ekstep.analytics.framework.Event
import org.ekstep.analytics.framework.Filter
import org.ekstep.analytics.framework.IBatchModelTemplate
import org.ekstep.analytics.framework.MEEdata
import org.ekstep.analytics.framework.MeasuredEvent
import org.ekstep.analytics.framework.PData
import org.ekstep.analytics.framework.exception.DataFilterException
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.util.SessionBatchModel
import org.ekstep.analytics.framework.conf.AppConf

case class GenieSummary(did: String, timeSpent: Double, time_stamp: Long, content: Buffer[String], contentCount: Int, syncts: Long,
                        etags: Option[ETags] = Option(ETags(None, None, None)), dateRange: DtRange, stageSummary: Iterable[GenieStageSummary], pdata: PData, channel: String) extends AlgoOutput
case class LaunchSessions(channel: String, did: String, events: Buffer[Event]) extends AlgoInput
case class GenieStageSummary(stageId: String, sid: String, timeSpent: Double, visitCount: Int, interactEventsCount: Int, interactEvents: List[Map[String, String]])
case class StageDetails(timeSpent: Double, interactEvents: Buffer[Event], visitCount: Int, sid: String)

object GenieLaunchSummaryModel extends SessionBatchModel[Event, MeasuredEvent] with IBatchModelTemplate[Event, LaunchSessions, GenieSummary, MeasuredEvent] with Serializable {

    val className = "org.ekstep.analytics.model.GenieLaunchSummaryModel"
    override def name: String = "GenieLaunchSummaryModel"

    def computeGenieScreenSummary(events: Buffer[Event]): Iterable[GenieStageSummary] = {

        val screenInteractEvents = DataFilter.filter(events, Filter("eid", "IN", Option(List("GE_GENIE_START", "GE_INTERACT", "GE_GENIE_END"))))

        var stageMap = HashMap[String, StageDetails]();
        var screenSummaryList = Buffer[HashMap[String, Double]]();
        val screenInteractCount = DataFilter.filter(screenInteractEvents, Filter("eid", "EQ", Option("GE_INTERACT"))).length;
        if (screenInteractCount > 0) {
            var stageList = ListBuffer[(String, Double, Buffer[Event], String)]();
            var prevEvent = events(0);
            screenInteractEvents.foreach { x =>
                x.eid match {
                    case "GE_GENIE_START" =>
                        stageList += Tuple4("splash", CommonUtil.getTimeDiff(prevEvent, x).get, Buffer[Event](), x.sid);
                    case "GE_INTERACT" =>
                        stageList += Tuple4(x.edata.eks.stageid, CommonUtil.getTimeDiff(prevEvent, x).get, Buffer(x), x.sid);
                    case "GE_GENIE_END" =>
                        stageList += Tuple4("endStage", CommonUtil.getTimeDiff(prevEvent, x).get, Buffer[Event](), x.sid);
                }
                prevEvent = x;
            }

            var currStage: String = null;
            var prevStage: String = null;
            stageList.foreach { x =>
                if (currStage == null) {
                    currStage = x._1;
                }
                if (stageMap.getOrElse(currStage, null) == null) {
                    stageMap.put(currStage, StageDetails(x._2, x._3, 0, x._4));
                } else {
                    stageMap.put(currStage, StageDetails(stageMap.get(currStage).get.timeSpent + x._2, stageMap.get(currStage).get.interactEvents ++ x._3, stageMap.get(currStage).get.visitCount, stageMap.get(currStage).get.sid));
                }
                if (currStage.equals(x._1)) {
                    if (prevStage != currStage)
                        stageMap.put(currStage, StageDetails(stageMap.get(currStage).get.timeSpent, stageMap.get(currStage).get.interactEvents, stageMap.get(currStage).get.visitCount + 1, stageMap.get(currStage).get.sid));
                    currStage = null;
                }
                prevStage = x._1;
            }
        }
        stageMap.map { x =>

            val interactEventsDetails = x._2.interactEvents.toList.map { f =>
                Map("ID" -> f.edata.eks.id, "type" -> f.edata.eks.`type`, "subtype" -> f.edata.eks.subtype)
            }
            GenieStageSummary(x._1, x._2.sid, x._2.timeSpent, x._2.visitCount, x._2.interactEvents.length, interactEventsDetails)
        }
    }

    override def preProcess(data: RDD[Event], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[LaunchSessions] = {
        val eventList = List("GE_GENIE_START","GE_GENIE_END","GE_SESSION_START","GE_SESSION_END","GE_LAUNCH_GAME","GE_GAME_END","GE_PROFILE_SET","GE_VIEW_PROGRESS","GE_GENIE_UPDATE","GE_GAME_UPDATE","GE_API_CALL","GE_GENIE_RESUME","GE_INTERACT","GE_INTERRUPT","GE_ERROR","GE_TRANSFER","GE_SERVICE_API_CALL","GE_CREATE_USER","GE_CREATE_PROFILE","GE_FEEDBACK","GE_DELETE_PROFILE","GE_IMPORT","GE_MISC","OE_START","OE_END","OE_NAVIGATE","OE_LEARN","OE_ASSESS","OE_ITEM_RESPONSE","OE_EARN","OE_LEVEL_SET","OE_INTERACT","OE_INTERRUPT","OE_FEEDBACK","OE_ERROR","OE_SUMMARY","OE_MISC")
        val eids = sc.broadcast(eventList);
        
        val events = DataFilter.filter(data, Filter("eid", "IN", Option(eids.value)))
        val idleTime = config.getOrElse("idleTime", 30).asInstanceOf[Int]
        val jobConfig = sc.broadcast(config);
        val filteredData = data.filter { x => !"AutoSync-Initiated".equals(x.edata.eks.subtype) }
        val genieLaunchSessions = getGenieLaunchSessions(filteredData, idleTime);
        genieLaunchSessions.map { x => LaunchSessions(x._1._1, x._1._2, x._2) }
    }

    override def algorithm(data: RDD[LaunchSessions], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[GenieSummary] = {

        data.map { x =>
            val geStart = x.events.head
            val geEnd = x.events.last

            val pdata = CommonUtil.getAppDetails(geStart)
            val channel = x.channel

            val syncts = CommonUtil.getEventSyncTS(geEnd)
            val startTimestamp = CommonUtil.getEventTS(geStart)
            val endTimestamp = CommonUtil.getEventTS(geEnd)
            val dtRange = DtRange(startTimestamp, endTimestamp);
            val timeSpent = CommonUtil.getTimeDiff(startTimestamp, endTimestamp)
            val content = x.events.filter { x => "OE_START".equals(x.eid) }.map { x => x.gdata.id }.filter { x => x != null }.distinct
            val stageSummary = computeGenieScreenSummary(x.events)
            GenieSummary(x.did, timeSpent.getOrElse(0d), endTimestamp, content, content.size, syncts, Option(CommonUtil.getETags(geEnd)), dtRange, stageSummary, pdata, channel);
        }.filter { x => (x.timeSpent >= 0) }
    }

    override def postProcess(data: RDD[GenieSummary], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[MeasuredEvent] = {
        val meEventVersion = AppConf.getConfig("telemetry.version");
        data.map { summary =>
            val mid = CommonUtil.getMessageId("ME_GENIE_LAUNCH_SUMMARY", null, config.getOrElse("granularity", "DAY").asInstanceOf[String], summary.dateRange, summary.did, Option(summary.pdata.id), Option(summary.channel));
            val measures = Map(
                "timeSpent" -> summary.timeSpent,
                "time_stamp" -> summary.time_stamp,
                "content" -> summary.content,
                "contentCount" -> summary.contentCount,
                "screenSummary" -> summary.stageSummary);
            MeasuredEvent("ME_GENIE_LAUNCH_SUMMARY", System.currentTimeMillis(), summary.syncts, meEventVersion, mid, "", summary.channel, None, None,
                Context(PData(config.getOrElse("producerId", "AnalyticsDataPipeline").asInstanceOf[String], config.getOrElse("modelVersion", "1.0").asInstanceOf[String], Option(config.getOrElse("modelId", "GenieUsageSummarizer").asInstanceOf[String])), None, config.getOrElse("granularity", "DAY").asInstanceOf[String], summary.dateRange),
                Dimensions(None, Option(summary.did), None, None, None, None, Option(summary.pdata)),
                MEEdata(measures), summary.etags);
        }
    }
}