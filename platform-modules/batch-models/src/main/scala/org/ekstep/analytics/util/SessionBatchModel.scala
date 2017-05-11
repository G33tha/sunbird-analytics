package org.ekstep.analytics.util

import org.apache.spark.rdd.RDD
import scala.collection.mutable.Buffer
import org.apache.spark.HashPartitioner
import org.ekstep.analytics.framework.util.CommonUtil
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.ekstep.analytics.framework.Event
import org.ekstep.analytics.framework.IBatchModel
import org.ekstep.analytics.framework.JobContext

/**
 * @author Santhosh
 */
trait SessionBatchModel[T, R] extends IBatchModel[T, R] {

    def getGameSessions(data: RDD[Event]): RDD[(String, Buffer[Event])] = {
        data.filter { x => x.uid != null && x.gdata.id != null }
            .map(event => (event.uid, Buffer(event)))
            .partitionBy(new HashPartitioner(JobContext.parallelization))
            .reduceByKey((a, b) => a ++ b).mapValues { events =>
                events.sortBy { x => CommonUtil.getEventTS(x) }.groupBy { e => (e.sid, e.ver) }.mapValues { x =>
                    var sessions = Buffer[Buffer[Event]]();
                    var tmpArr = Buffer[Event]();
                    var lastContentId: String = x(0).gdata.id;
                    x.foreach { y =>
                        y.eid match {
                            case "OE_START" =>
                                if (tmpArr.length > 0) {
                                    sessions += tmpArr;
                                    tmpArr = Buffer[Event]();
                                }
                                tmpArr += y;
                                lastContentId = y.gdata.id;
                            case "OE_END" =>
                                tmpArr += y;
                                sessions += tmpArr;
                                tmpArr = Buffer[Event]();
                            case _ =>
                                if (!lastContentId.equals(y.gdata.id)) {
                                    sessions += tmpArr;
                                    tmpArr = Buffer[Event]();
                                }
                                tmpArr += y;
                                lastContentId = y.gdata.id;
                        }
                    }
                    sessions += tmpArr;
                    sessions;
                }.map(f => f._2).reduce((a, b) => a ++ b);
            }.flatMap(f => f._2.map { x => (f._1, x) }).filter(f => f._2.nonEmpty);
    }

    def getGenieLaunchSessions(data: RDD[Event], idleTime: Int): RDD[(String, Buffer[Event])] = {
        data.filter { x => x.did != null }
            .map(event => (event.did, Buffer(event)))
            .partitionBy(new HashPartitioner(JobContext.parallelization))
            .reduceByKey((a, b) => a ++ b).mapValues { events =>
                val sortedEvents = events.sortBy { x => CommonUtil.getEventTS(x) }
                var sessions = Buffer[Buffer[Event]]();
                var tmpArr = Buffer[Event]();
                sortedEvents.foreach { y =>
                    y.eid match {
                        case "GE_GENIE_START" =>
                            if (tmpArr.length > 0) {
                                sessions += tmpArr;
                                tmpArr = Buffer[Event]();
                            }
                            tmpArr += y;

                        case "GE_GENIE_END" =>
                            if (!tmpArr.isEmpty) {
                                val event = tmpArr.last
                                val timeSpent = CommonUtil.getTimeDiff(CommonUtil.getEventTS(event), CommonUtil.getEventTS(y)).get
                                if (timeSpent > (idleTime * 60)) {
                                    sessions += tmpArr;
                                    tmpArr = Buffer[Event]();
                                }
                            }
                            tmpArr += y;
                            sessions += tmpArr;
                            tmpArr = Buffer[Event]();
                        case _ =>
                            if (!tmpArr.isEmpty) {
                                val event = tmpArr.last
                                val timeSpent = CommonUtil.getTimeDiff(CommonUtil.getEventTS(event), CommonUtil.getEventTS(y)).get
                                if (timeSpent < (idleTime * 60)) {
                                    tmpArr += y;
                                } else {
                                    sessions += tmpArr;
                                    tmpArr = Buffer[Event]();
                                    tmpArr += y;
                                }
                            }else {
                                tmpArr += y;
                            }
                            
                    }
                }
                sessions += tmpArr;
                sessions;
            }.flatMap(f => f._2.map { x => (f._1, x) }).filter(f => f._2.nonEmpty);
    }

    def getGenieSessions(data: RDD[Event], idleTime: Int): RDD[(String, Buffer[Event])] = {
        data.map(event => (event.sid, Buffer(event)))
            .partitionBy(new HashPartitioner(JobContext.parallelization))
            .reduceByKey((a, b) => a ++ b).mapValues { events =>
                val sortedEvents = events.sortBy { x => CommonUtil.getEventTS(x) }
                var sessions = Buffer[Buffer[Event]]();
                var tmpArr = Buffer[Event]();
                sortedEvents.foreach { y =>
                    y.eid match {
                        case "GE_SESSION_START" =>
                            tmpArr += y;

                        case "GE_SESSION_END" =>
                            if (!tmpArr.isEmpty) {
                                val event = tmpArr.last
                                val timeSpent = CommonUtil.getTimeDiff(CommonUtil.getEventTS(event), CommonUtil.getEventTS(y)).get
                                if (timeSpent > (idleTime * 60)) {
                                    sessions += tmpArr;
                                    tmpArr = Buffer[Event]();
                                }
                            }
                            tmpArr += y;
                            sessions += tmpArr;
                            tmpArr = Buffer[Event]();
                        case _ =>
                            if (!tmpArr.isEmpty) {
                                val event = tmpArr.last
                                val timeSpent = CommonUtil.getTimeDiff(CommonUtil.getEventTS(event), CommonUtil.getEventTS(y)).get
                                if (timeSpent < (idleTime * 60)) {
                                    tmpArr += y;
                                } else {
                                    sessions += tmpArr;
                                    tmpArr = Buffer[Event]();
                                    tmpArr += y;
                                }
                            }else {
                                tmpArr += y;
                            }
                    }
                }
                sessions += tmpArr;
                sessions;
            }.flatMap(f => f._2.map { x => (f._1, x) }).filter(f => f._2.nonEmpty);
    }
}