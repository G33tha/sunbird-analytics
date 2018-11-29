package org.ekstep.analytics.model

import com.datastax.spark.connector._
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.adapter.ContentAdapter
import org.ekstep.analytics.framework._
import org.ekstep.analytics.framework.conf.AppConf
import org.ekstep.analytics.framework.util.{CommonUtil, JSONUtils, JobLogger}
import org.ekstep.analytics.util.Constants
import org.joda.time.DateTime

import scala.util.{Failure, Try}


case class ContentFlatList(contentId: String, metric: List[Map[String, Any]])
case class ETBCoverageOutput(contentId: String, totalDialcodeAttached: Int, totalDialcode: Map[String, Int], totalDialcodeLinkedToContent: Int, level: Int) extends AlgoOutput
case class ContentHierarchyModel(mimeType: String, contentType: String, dialcodes: Option[List[String]], identifier: String, channel: String, status: String, resourceType: String, name: String, content: Map[String, AnyRef], children: Option[List[ContentHierarchyModel]]) extends AlgoInput

object ETBCoverageSummaryModel extends IBatchModelTemplate[Empty, ContentHierarchyModel, ETBCoverageOutput, MeasuredEvent] with Serializable {

  implicit val className = "org.ekstep.analytics.model.ETBCoverageSummaryModel"
  override def name: String = "ETBCoverageSummaryModel"

  override def preProcess(data: RDD[Empty], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ContentHierarchyModel] = {
    // format date to ISO format
    val fromDate = config.getOrElse("fromDate", new DateTime().toString(CommonUtil.dateFormat)).asInstanceOf[String]
    val startDate = CommonUtil.df7.parseDateTime(fromDate).toDateTimeISO.toString

    val toDate = config.getOrElse("toDate", new DateTime().toString(CommonUtil.dateFormat)).asInstanceOf[String]
    val endDate  = CommonUtil.df7.parseDateTime(toDate).plusHours(23).plusMinutes(59).plusSeconds(59).toDateTimeISO.toString

    JobLogger.log("Started executing Job ETBCoverageSummaryModel")

    // Get all last updated "Live" Textbooks within given period
    val response = ContentAdapter.getTextbookContents(startDate.toString, endDate.toString)
    val contentIds = response.map(_.getOrElse("identifier", "").asInstanceOf[String])
    JobLogger.log("contents count: => " + contentIds.length)

    // Get complete Textbook Hierarchy for all the last published contents
    val hierarchyData  = sc.cassandraTable[ContentHierarchyTable](Constants.HIERARCHY_STORE_KEY_SPACE_NAME, Constants.CONTENT_HIERARCHY_TABLE).where("identifier IN ?", contentIds.toList)
    val data = hierarchyData.map(data => data.hierarchy)

    var model: RDD[ContentHierarchyModel] = sc.emptyRDD[ContentHierarchyModel]
    Try {
      val unescapedJsons: RDD[String] = data.map(x => JSONUtils.unescapeJSON(x))
      model = unescapedJsons.map(jsonString => JSONUtils.deserialize[ContentHierarchyModel](jsonString))
    }.recoverWith {
      case exception => JobLogger.log("unable to parse JSON hierarchy content"); Failure(exception)
    }

    model.filter(x => x.contentType == "TextBook") // filter only Textbooks
  }

  def computeMetrics(data: RDD[ContentHierarchyModel]): RDD[Map[String, Any]] = {
    var mapper: List[Map[String, Any]] = List()
    val collectionMimetype = "application/vnd.ekstep.content-collection"

    def getDialcodesByLevel(content: ContentHierarchyModel): List[String] = {
      content.children.getOrElse(List()).flatMap(x => x.dialcodes.getOrElse(List()))
    }

    def getDialcodeLinkedToContent(content: ContentHierarchyModel): Int = {
      content.children.getOrElse(List()).map(x => x.dialcodes.getOrElse(List()).size).count(_ > 0)
    }

    def _flatten(parent: ContentHierarchyModel, level: Int = 2): Unit = {
        parent.children.getOrElse(List()).foreach(content => {
          if (content.mimeType.toLowerCase.equals(collectionMimetype)) {
            mapper = mapper ::: List(Map(
              "id" -> content.identifier,
              "parent" -> parent.identifier,
              "childCount" -> content.children.getOrElse(List()).length,
              "dialcodes" -> content.dialcodes.getOrElse(List()),
              "childrenIds" -> content.children.getOrElse(List()).map(x => x.identifier),
              "totalDialcode" -> getDialcodesByLevel(parent).groupBy(identity).map(t => (t._1, t._2.size)),
              "totalDialcodeAttached" -> getDialcodesByLevel(parent).distinct.size,
              "totalDialcodeLinkedToContent" -> getDialcodeLinkedToContent(parent),
              "mimeType" -> content.mimeType,
              "level" -> level
            ))
          }
          if (content.children.getOrElse(List()).nonEmpty) _flatten(content, level + 1)
        })
    }

    val result = data.flatMap(content => {
      _flatten(content)
      mapper ::: List(Map(
        "id" -> content.identifier,
        "parent" -> content.identifier,
        "childCount" -> content.children.getOrElse(List()).length,
        "dialcodes" -> content.dialcodes.getOrElse(List()),
        "childrenIds" -> content.children.getOrElse(List()).map(x => x.identifier),
        "totalDialcode" -> content.dialcodes.getOrElse(List()).groupBy(identity).map(t => (t._1, t._2.size)),
        "totalDialcodeAttached" -> content.dialcodes.getOrElse(List()).distinct.size,
        "totalDialcodeLinkedToContent" -> List(content.dialcodes.getOrElse(List()).size).count(_ > 0),
        "mimeType" -> content.mimeType,
        "level" -> 1
      ))
    })
    result
  }

  override def algorithm(input: RDD[ContentHierarchyModel], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ETBCoverageOutput] = {
    computeMetrics(input)
      .map(x => ETBCoverageOutput(
        x.getOrElse("id", "").asInstanceOf[String],
        x.getOrElse("totalDialcodeAttached", 0).asInstanceOf[Int],
        x.getOrElse("totalDialcode", Map()).asInstanceOf[Map[String, Int]],
        x.getOrElse("totalDialcodeLinkedToContent", 0).asInstanceOf[Int],
        x.getOrElse("level", 0).asInstanceOf[Int]
      ))
  }

  override def postProcess(data: RDD[ETBCoverageOutput], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[MeasuredEvent] = {
    val ME_ID = "ME_ETB_COVERAGE_SUMMARY"
    val meEventVersion = AppConf.getConfig("telemetry.version")

    val output = data.map { metric =>
        val mid = CommonUtil.getMessageId(ME_ID, metric.contentId, "DAY",  0, None, None)
        val measures = Map(
          "totalDialcode" -> metric.totalDialcode,
          "totalDialcodeLinkedToContent" -> metric.totalDialcodeLinkedToContent,
          "totalDialcodeAttached" -> metric.totalDialcodeAttached,
          "hierarchyLevel" -> metric.level
        )

        MeasuredEvent(ME_ID, System.currentTimeMillis(), System.currentTimeMillis(), meEventVersion, mid, "", "", None, None,
          Context(PData(config.getOrElse("producerId", "AnalyticsDataPipeline").asInstanceOf[String], config.getOrElse("modelVersion", "1.0").asInstanceOf[String], Option(config.getOrElse("modelId", "DialcodeUsageSummarizer").asInstanceOf[String])), None, "DAY", DtRange(0, 0)),
          Dimensions(None, None, None, None, None, None, None, None, None, None, None, None, Option(metric.contentId), None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None),
          MEEdata(measures), None);
    }
      output
  }
}