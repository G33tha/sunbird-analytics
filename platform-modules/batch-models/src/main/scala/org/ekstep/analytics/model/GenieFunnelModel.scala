package org.ekstep.analytics.model

import org.ekstep.analytics.framework.SessionBatchModel
import org.ekstep.analytics.framework.MeasuredEvent
import org.ekstep.analytics.framework.IBatchModelTemplate
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import scala.collection.mutable.Buffer
import org.ekstep.analytics.framework.AlgoInput
import org.ekstep.analytics.framework.Filter
import org.ekstep.analytics.framework.DataFilter
import org.ekstep.analytics.framework.Event
import org.ekstep.analytics.framework.AlgoOutput
import org.ekstep.analytics.framework.util.JSONUtils
import scala.collection.mutable.ListBuffer
import org.ekstep.analytics.framework.util.CommonUtil
import scala.collection.mutable.HashMap
import org.ekstep.analytics.framework.DtRange
import org.ekstep.analytics.framework.DtRange
import org.ekstep.analytics.framework.PData
import org.ekstep.analytics.framework.Context
import org.ekstep.analytics.framework.MEEdata
import org.ekstep.analytics.framework.Dimensions

case class GenieFunnelSession(did: String, cid: String, dspec: Map[String, AnyRef], funnel: String, events: Buffer[Event], onbFlag: Boolean) extends AlgoInput
case class GenieFunnel(funnel: String, cid: String, did: String, sid: String, dspec: Map[String, AnyRef], genieVer: String, summary: HashMap[String, FunnelStageSummary], timeSpent: Double, onboarding: Boolean, syncts: Long, dateRange: DtRange, tags: Option[AnyRef]) extends AlgoOutput

case class FunnelStageSummary(timeSpent: Option[Double] = Option(0.0), count: Option[Int] = Option(0), stageInvoked: Option[Int] = Option(0))

object GenieFunnelModel extends SessionBatchModel[Event, MeasuredEvent] with IBatchModelTemplate[Event, GenieFunnelSession, GenieFunnel, MeasuredEvent] with Serializable {

    def computeFunnelSummary(event: GenieFunnelSession): GenieFunnel = {

        var stageMap = HashMap[String, FunnelStageSummary]();
        val funnel = event.funnel
        val events = event.events
        val did = event.did

        if (events.length > 0) {
            var stageList = ListBuffer[(String, Double)]();
            var prevEvent = events(0);

            if ("GenieOnboarding".equals(funnel)) {
                stageMap.put("loadOnboardPage", FunnelStageSummary());
                stageMap.put("welcomeContentSkipped", FunnelStageSummary());
                stageMap.put("addChildSkipped", FunnelStageSummary());
                stageMap.put("firstLessonSkipped", FunnelStageSummary());
                stageMap.put("gotoLibrarySkipped", FunnelStageSummary());
                stageMap.put("searchLessonSkipped", FunnelStageSummary());
                stageMap.put("gotoLibrarySkipped", FunnelStageSummary());
                stageMap.put("contentPlayed", FunnelStageSummary());

                events.foreach { x =>
                    x.eid match {
                        case "GE_INTERACT" =>
                            val subType = x.edata.eks.subtype
                            val stage = subType match {
                                case "WelcomeContent-Skipped" => "welcomeContentSkipped";
                                case "AddChild-Skipped"       => "addChildSkipped";
                                case "FirstLesson-Skipped"    => "firstLessonSkipped";
                                case "GoToLibrary-Skipped"    => "gotoLibrarySkipped";
                                case "SearchLesson-Skipped"   => "searchLessonSkipped";
                                case ""                       => "loadOnboardPage";
                            }

                            stageList += Tuple2(stage, CommonUtil.roundDouble(CommonUtil.getTimeDiff(prevEvent, x).get, 2));
                        case "GE_LAUNCH_GAME" =>
                            stageList += Tuple2("contentPlayed", CommonUtil.roundDouble(CommonUtil.getTimeDiff(prevEvent, x).get, 2));
                    }
                    prevEvent = x;
                }
            } else {

                stageMap.put("listContent", FunnelStageSummary());
                stageMap.put("selectContent", FunnelStageSummary());
                stageMap.put("downloadInitiated", FunnelStageSummary());
                stageMap.put("downloadComplete", FunnelStageSummary());
                stageMap.put("contentPlayed", FunnelStageSummary());

                events.foreach { x =>
                    x.eid match {
                        case "GE_INTERACT" =>

                            val stageId = x.edata.eks.stageid
                            val subType = x.edata.eks.subtype
                            val stage = (stageId, subType) match {
                                case ("ContentSearch", "SearchPhrase")             => "listContent";
                                case ("ContentList", "SearchPhrase")               => "listContent";
                                case ("ContentList", "ContentClicked")             => "selectContent";
                                case ("ContentDetail", "ContentDownload-Initiate") => "downloadInitiated";
                                case ("ContentDetail", "ContentDownload-Success")  => "downloadComplete";
                                case ("ExploreContent", "ContentClicked")          => "selectContent";
                                case ("ExploreContent", "")                        => "listContent";
                            }

                            stageList += Tuple2(stage, CommonUtil.roundDouble(CommonUtil.getTimeDiff(prevEvent, x).get, 2));
                        case "GE_LAUNCH_GAME" =>
                            stageList += Tuple2("contentPlayed", CommonUtil.roundDouble(CommonUtil.getTimeDiff(prevEvent, x).get, 2));
                    }
                    prevEvent = x;
                }
            }

            var currStage: String = null;
            var prevStage: String = null;
            stageList.foreach { x =>
                if (currStage == null) {
                    currStage = x._1;
                }
                stageMap.put(currStage, FunnelStageSummary(Option(CommonUtil.roundDouble((stageMap.get(currStage).get.timeSpent.get + x._2), 2)), stageMap.get(currStage).get.count, stageMap.get(currStage).get.stageInvoked));
                if (currStage.equals(x._1)) {
                    if (prevStage != currStage)
                        stageMap.put(currStage, FunnelStageSummary(Option(CommonUtil.roundDouble(stageMap.get(currStage).get.timeSpent.get, 2)), Option(stageMap.get(currStage).get.count.get + 1), Option(1)));
                    currStage = null;
                }
                prevStage = x._1;
            }
        }

        val firstEvent = events.head
        val endEvent = events.last
        val sid = firstEvent.sid
        val cid = event.cid
        val dspec = event.dspec
        val genieVer = firstEvent.gdata.ver
        val dateRange = DtRange(CommonUtil.getEventTS(firstEvent), CommonUtil.getEventTS(endEvent))
        val syncts = CommonUtil.getEventSyncTS(endEvent)
        val tags = endEvent.tags
        val totalTimeSpent = CommonUtil.roundDouble(stageMap.map { x => x._2.timeSpent.get }.sum, 2)

        GenieFunnel(funnel, cid, did, sid, dspec, genieVer, stageMap, totalTimeSpent, event.onbFlag, syncts, dateRange, Option(tags));

    }

    override def preProcess(data: RDD[Event], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[GenieFunnelSession] = {
        val idleTime = config.getOrElse("idleTime", 30).asInstanceOf[Int]
        val genieLaunchSessions = getGenieLaunchSessions(data, idleTime);

        genieLaunchSessions.mapValues { x =>
            val geStartEvents = DataFilter.filter(x, Filter("eid", "EQ", Option("GE_GENIE_START")))
            val dspec = if (geStartEvents.length > 0) geStartEvents.last.edata.eks.dspec; else null;

            val filteredData = DataFilter.filter(x, Filter("eid", "IN", Option(List("GE_LAUNCH_GAME", "GE_INTERACT")))).filter { x => x.cdata != null && x.cdata.nonEmpty }
            val onb = filteredData.filter { x => "ONBRDNG".equals(x.cdata.last.`type`.get) }
            val onbflag = if (onb.length > 0) true; else false;
            filteredData.map { x => (x.cdata.last.id, x) }.groupBy { x => x._1 }.map { x => (x._1, dspec, x._2.map(y => y._2), onbflag) };
        }.map { x =>
            val did = x._1
            x._2.map { x =>
                val events = x._3.sortBy { x => x.ts }
                val firstEvent = events.head
                val cdataType = firstEvent.cdata.last.`type`.get
                val stageId = firstEvent.edata.eks.stageid
                val subType = firstEvent.edata.eks.subtype
                val funnel = if ("ONBRDNG".equals(cdataType)) "GenieOnboarding"; else if ("org.ekstep.recommendation".equals(cdataType)) "ContentRecommendation"; else if ("ExploreContent".equals(stageId) && "".equals(subType)) "ExploreContent"; else if ("ContentSearch".equals(stageId) && "SearchPhrase".equals(subType)) "ContentSearch"; else "ExploreContent";
                GenieFunnelSession(did, x._1, x._2, funnel, events, x._4)
            };
        }.flatMap { x => x };

    }

    override def algorithm(data: RDD[GenieFunnelSession], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[GenieFunnel] = {
        data.map { x =>
            computeFunnelSummary(x)
        }
    }

    override def postProcess(data: RDD[GenieFunnel], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[MeasuredEvent] = {
        data.map { summary =>
            val mid = CommonUtil.getMessageId("ME_GENIE_FUNNEL", summary.funnel + summary.cid, config.getOrElse("granularity", "FUNNEL").asInstanceOf[String], summary.dateRange, summary.did);
            val measures = summary.summary.toMap ++ Map("timeSpent" -> summary.timeSpent)
            MeasuredEvent("ME_GENIE_FUNNEL", System.currentTimeMillis(), summary.syncts, "1.0", mid, "", None, None,
                Context(PData(config.getOrElse("producerId", "AnalyticsDataPipeline").asInstanceOf[String], config.getOrElse("modelId", "GenieFunnel").asInstanceOf[String], config.getOrElse("modelVersion", "1.0").asInstanceOf[String]), None, config.getOrElse("granularity", "FUNNEL").asInstanceOf[String], summary.dateRange),
                Dimensions(None, Option(summary.did), None, None, None, None, None, None, None, None, None, None, None, None, Option(summary.sid), None, Option(summary.funnel), Option(summary.dspec), Option(summary.onboarding), Option(summary.genieVer)),
                MEEdata(measures), summary.tags);
        }
    }
}