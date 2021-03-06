package org.ekstep.analytics.updater

/**
  * Ref:Design wiki link: https://project-sunbird.atlassian.net/wiki/spaces/SBDES/pages/794198025/Design+Brainstorm+Data+structure+for+capturing+dashboard+portal+metrics
  * Ref:Implementation wiki link: https://project-sunbird.atlassian.net/wiki/spaces/SBDES/pages/794099772/Data+Product+Dashboard+summariser+-+Cumulative
  *
  * @author Manjunath Davanam <manjunathd@ilimi.in>
  */

import com.datastax.spark.connector._
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.adapter.ContentAdapter
import org.ekstep.analytics.framework._
import org.ekstep.analytics.framework.dispatcher.AzureDispatcher
import org.ekstep.analytics.framework.util.{CommonUtil, JSONUtils}
import org.ekstep.analytics.util.Constants
import scala.util.Try


case class WorkFlowUsageMetrics(noOfUniqueDevices: Long, totalContentPlaySessions: Long, totalTimeSpent: Double, totalContentPublished: Long) extends AlgoOutput with Output with AlgoInput

case class PortalMetrics(eid: String, ets: Long, syncts: Long, metrics_summary: Option[WorkFlowUsageMetrics]) extends AlgoOutput with Output

case class DeviceProfile(device_id: String)

object UpdatePortalMetrics extends IBatchModelTemplate[DerivedEvent, DerivedEvent, WorkFlowUsageMetrics, PortalMetrics] with Serializable {

  val className = "org.ekstep.analytics.updater.UpdatePortalMetrics"

  private val EVENT_ID: String = "ME_PORTAL_CUMULATIVE_METRICS"

  override def name: String = "UpdatePortalMetrics"

  /**
    * preProcess which will fetch the `workflow_usage_summary` Event data from the Cassandra Database.
    *
    * @param data   - RDD Event Data(Empty RDD event)
    * @param config - Configurations to run preProcess
    * @param sc     - SparkContext
    * @return -     DerivedEvent
    */
  override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DerivedEvent] = {
    data
  }

  /**
    *
    * @param data   - RDD Workflow summary event data
    * @param config - Configurations to algorithm
    * @param sc     - Spark context
    * @return - DashBoardSummary ->(uniqueDevices, totalContentPlayTime, totalTimeSpent,)
    */
  override def algorithm(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[WorkFlowUsageMetrics] = {
    val totalContentPublished: Int = Try(ContentAdapter.getPublishedContentList().count).getOrElse(0)
    val noOfUniqueDevices = sc.cassandraTable[DeviceProfile](Constants.DEVICE_KEY_SPACE_NAME, Constants.DEVICE_PROFILE_TABLE).map(_.device_id).distinct().count()
    val metrics =  sc.cassandraTable[WorkFlowUsageMetricsAlgoOutput](Constants.PLATFORM_KEY_SPACE_NAME, Constants.WORKFLOW_USAGE_SUMMARY).map(event => {
      (event.total_timespent,event.total_content_play_sessions)
    })
    val totalTimeSpent = metrics.map(_._1).sum()
    val totalContentPlaySessions = metrics.map(_._2).sum().toLong
    sc.parallelize(Array(WorkFlowUsageMetrics(noOfUniqueDevices, totalContentPlaySessions, CommonUtil.roundDouble(totalTimeSpent / 3600, 2), totalContentPublished)))
  }

  /**
    *
    * @param data   - RDD DashboardSummary Event
    * @param config - Configurations to run postprocess method
    * @param sc     - Spark context
    * @return - ME_PORTAL_CUMULATIVE_METRICS MeasuredEvents
    */
  override def postProcess(data: RDD[WorkFlowUsageMetrics], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[PortalMetrics] = {
    val record = data.first()
    val measures = WorkFlowUsageMetrics(record.noOfUniqueDevices, record.totalContentPlaySessions, record.totalTimeSpent, record.totalContentPublished)
    val metrics = PortalMetrics(EVENT_ID, System.currentTimeMillis(), System.currentTimeMillis(), Some(measures))
    if (config.getOrElse("dispatch", false).asInstanceOf[Boolean]) {
      AzureDispatcher.dispatch(Array(JSONUtils.serialize(metrics)), config)
    }
    sc.parallelize(Array(metrics))
  }
}