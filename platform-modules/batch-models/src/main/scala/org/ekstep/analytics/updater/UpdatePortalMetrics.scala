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
import org.ekstep.analytics.framework.util.{CommonUtil, JSONUtils, RestUtil}
import org.ekstep.analytics.util.{Constants, WorkFlowUsageSummaryFact}

import scala.util.Try


case class WorkflowSummaryEvents(deviceId: String, mode: String, dType: String, totalSession: Long, totalTs: Double) extends AlgoInput with Input


case class Publisher_agg(buckets: List[Buckets])

case class Buckets(key: String, doc_count: Double, publisher_agg: Language_agg)

case class Language_agg(buckets: List[Buckets])

case class Aggregations(language_agg: Language_agg)

case class ESResponse(aggregations: Aggregations)

case class Publisher(id: String, count: Double)

case class languagePublisher(language: String, publishers: List[Publisher])

case class contentPublished(count: Int, languages: List[languagePublisher])

case class Metrics(noOfUniqueDevices: Long, totalContentPlaySessions: Long, totalTimeSpent: Double, totalContentPublished: contentPublished) extends AlgoOutput with Output

case class PortalMetrics(eid: String, ets: Long, syncts: Long, metrics_summary: Option[Metrics]) extends AlgoOutput with Output

object UpdatePortalMetrics extends IBatchModelTemplate[DerivedEvent, WorkflowSummaryEvents, Metrics, PortalMetrics] with Serializable {

  val className = "org.ekstep.analytics.updater.UpdatePortalMetrics"

  private val EVENT_ID: String = "ME_PORTAL_CUMULATIVE_METRICS"

  override def name: String = "UpdatePortalMetrics"

  /**
    * preProcess which will fetch the `WorkFlowUsageSummaryFact` Event data from the Cassandra Database.
    *
    * @param data   - RDD Event Data(Empty RDD event)
    * @param config - Configurations to run preProcess
    * @param sc     - SparkContext
    * @return - workflowSummaryEvents
    */
  override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[WorkflowSummaryEvents] = {
    sc.cassandraTable[WorkFlowUsageSummaryFact](Constants.PLATFORM_KEY_SPACE_NAME, Constants.WORKFLOW_USAGE_SUMMARY_FACT).filter { x => x.d_period == 0 && x.d_tag.equals("all") && x.d_content_id.equals("all") && x.d_user_id.equals("all") }.map(event => {
      WorkflowSummaryEvents(event.d_device_id, event.d_mode, event.d_type, event.m_total_sessions, event.m_total_ts)
    })
  }

  /**
    *
    * @param data   - RDD Workflow summary event data
    * @param config - Configurations to algorithm
    * @param sc     - Spark context
    * @return - DashBoardSummary ->(uniqueDevices, totalContentPlayTime, totalTimeSpent,)
    */
  override def algorithm(data: RDD[WorkflowSummaryEvents], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[Metrics] = {
    object _constant extends Enumeration {
      val APP = "app"
      val PLAY = "play"
      val CONTENT = "content"
      val SESSION = "session"
      val ALL = "all"
    }

    val uniqueDevicesCount = data.filter(x => x.deviceId != _constant.ALL).map(_.deviceId).distinct().count()
    val totalContentPlaySessions = data.filter(x => x.mode.equalsIgnoreCase(_constant.PLAY) && x.dType.equalsIgnoreCase(_constant.CONTENT) && x.deviceId.equals("all")).map(_.totalSession).sum().toLong
    val totalTimeSpent = data.filter(x => (x.dType.equalsIgnoreCase(_constant.APP) || x.dType.equalsIgnoreCase(_constant.SESSION)) && x.deviceId.equals("all")).map(_.totalTs).sum()
    val publishedContentCount: Int = Try(ContentAdapter.getPublishedContentList().count).getOrElse(0)
    sc.parallelize(Array(Metrics(uniqueDevicesCount, totalContentPlaySessions, CommonUtil.roundDouble(totalTimeSpent / 3600, 2), contentPublished(publishedContentCount, getLanguageAndPublisherList()))))
  }

  /**
    *
    * @param data   - RDD DashboardSummary Event
    * @param config - Configurations to run postprocess method
    * @param sc     - Spark context
    * @return - ME_PORTAL_CUMULATIVE_METRICS MeasuredEvents
    */
  override def postProcess(data: RDD[Metrics], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[PortalMetrics] = {
    val record = data.first()
    val measures = Metrics(record.noOfUniqueDevices, record.totalContentPlaySessions, record.totalTimeSpent, record.totalContentPublished)
    val metrics = PortalMetrics(EVENT_ID, System.currentTimeMillis(), System.currentTimeMillis(), Some(measures))
    if (config.getOrElse("dispatch", false).asInstanceOf[Boolean]) {
      AzureDispatcher.dispatch(Array(JSONUtils.serialize(metrics)), config)
    }
    sc.parallelize(Array(metrics))
  }

  /**
    * This method is used to get the list of list and count of publisher by language
    *
    * @return List of languagePublisher
    */
  private def getLanguageAndPublisherList(): List[languagePublisher] = {
    val endPoint = Constants.ELASTIC_SEARCH_SERVICE_ENDPOINT
    val index = Constants.ELASTIC_SEARCH_INDEX_COMPOSITESEARCH_NAME
    val apiURL = s"$endPoint/$index/_search"
    val request =
      s"""
         |{"_source":false,"query":{"bool":{"must":[{"match":{"status":{"query":"Live","operator":"AND","lenient":false,"zero_terms_query":"NONE"}}}],"should":[{"match":{"objectType":{"query":"Content","operator":"OR","lenient":false,"zero_terms_query":"NONE"}}},{"match":{"objectType":{"query":"ContentImage","operator":"OR","lenient":false,"zero_terms_query":"NONE"}}},{"match":{"contentType":{"query":"Resource","operator":"OR","lenient":false,"zero_terms_query":"NONE"}}},{"match":{"contentType":{"query":"Collection","operator":"OR","lenient":false,"zero_terms_query":"NONE"}}}]}},"aggs":{"language_agg":{"terms":{"field":"language.raw","size":300},"aggs":{"publisher_agg":{"terms":{"field":"lastPublishedBy.raw","size":1000}}}}}}
                    """.stripMargin
    val response = RestUtil.post[ESResponse](apiURL, request)
    println("ES_RESPONSE", response);
    response.aggregations.language_agg.buckets.map(languageBucket => {
      val publishers = languageBucket.publisher_agg.buckets.map(publisherBucket => {
        (Publisher(publisherBucket.key, publisherBucket.doc_count))
      })
      languagePublisher(languageBucket.key, publishers)
    })
  }
}