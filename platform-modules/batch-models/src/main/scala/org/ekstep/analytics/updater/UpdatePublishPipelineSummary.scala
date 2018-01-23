package org.ekstep.analytics.updater

import org.ekstep.analytics.framework.IBatchModelTemplate
import org.ekstep.analytics.framework.DerivedEvent
import org.ekstep.analytics.framework.DataFilter
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.Filter
import org.ekstep.analytics.framework.AlgoOutput
import org.ekstep.analytics.framework.Output
import com.datastax.spark.connector._
import org.ekstep.analytics.util.Constants
import org.ekstep.analytics.model.PipelineSummaryOutput
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.Period._
import org.ekstep.analytics.framework.Period
import org.joda.time.format.DateTimeParser
import org.joda.time.format.DateTimeFormat
import scala.collection.mutable.Buffer
import org.apache.spark.HashPartitioner
import org.ekstep.analytics.framework.JobContext
import org.joda.time.DateTime
import org.ekstep.analytics.framework.dispatcher.InfluxDBDispatcher.InfluxRecord
import org.ekstep.analytics.connector.InfluxDB._
import org.ekstep.analytics.framework.conf.AppConf

case class PublishPipelineSummaryFact(d_period: Int, d_app_id: String, d_channel: String, `type`: String, state: String, subtype: String, count: Int, updated_at: Long) extends AlgoOutput
case class ContentPublishFactIndex(d_period: Int, d_app_id: String, d_channel: String, `type`: String, state: String, subtype: String) extends Output

object UpdatePublishPipelineSummary extends IBatchModelTemplate[DerivedEvent, DerivedEvent, PublishPipelineSummaryFact, ContentPublishFactIndex] with IInfluxDBUpdater with Serializable {
  val className = "org.ekstep.analytics.updater.UpdatePublishPipelineSummary"
  override def name: String = "UpdatePublishPipelineSummary"

  override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DerivedEvent] = {
    DataFilter.filter(data, Filter("eid", "EQ", Option("ME_PUBLISH_PIPELINE_SUMMARY")));
  }

  override def algorithm(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[PublishPipelineSummaryFact] = {
    data.cache()
    computeForPeriod(Period.DAY, data).union(computeForPeriod(Period.WEEK, data)).union(computeForPeriod(Period.MONTH, data)).union(computeForPeriod(Period.CUMULATIVE, data))
  }

  private def computeForPeriod(p: Period, data: RDD[DerivedEvent])(implicit sc: SparkContext): RDD[PublishPipelineSummaryFact] = {
    val newData = createAndFlattenFacts(p, data)
    val deDuplicatedFacts = sc.makeRDD(newData).reduceByKey(combineFacts)
    val existingData = deDuplicatedFacts.map { x => x._1 }.joinWithCassandraTable[PublishPipelineSummaryFact](Constants.CREATION_METRICS_KEY_SPACE_NAME, Constants.PUBLISH_PIPELINE_SUMMARY_FACT).on(SomeColumns("d_period", "d_app_id", "d_channel", "type", "state", "subtype"))
    val joined = deDuplicatedFacts.leftOuterJoin(existingData)
    joined.map { d =>
      val newFact = d._2._1
      val existingFact = d._2._2.getOrElse(PublishPipelineSummaryFact(newFact.d_period, newFact.d_app_id, newFact.d_channel, newFact.`type`, newFact.state, newFact.subtype, 0, DateTime.now().getMillis))
      combineFacts(newFact, existingFact)
    }
  }

  private def createAndFlattenFacts(p: Period, data: RDD[DerivedEvent]): List[(ContentPublishFactIndex, PublishPipelineSummaryFact)] = {
    implicit val period = p
    data.aggregate(List[(ContentPublishFactIndex, PublishPipelineSummaryFact)]())(createFactsFromEvent, combineFacts)
  }

  private def createFactsFromEvent(acc: List[(ContentPublishFactIndex, PublishPipelineSummaryFact)], d: DerivedEvent)(implicit period: Period): List[(ContentPublishFactIndex, PublishPipelineSummaryFact)] = {
    val d_period = CommonUtil.getPeriod(DateTimeFormat.forPattern("yyyyMMdd").parseDateTime(d.dimensions.period.get.toString()), period)
    val eks = d.edata.eks.asInstanceOf[Map[String, AnyRef]]
    val pps = eks("publish_pipeline_summary").asInstanceOf[List[Map[String, AnyRef]]]
    val appId = CommonUtil.getAppDetails(d).id
    val channel = CommonUtil.getChannelId(d)

    val facts = pps.map { s =>
      val `type` = s.getOrElse("type", "").toString()
      val state = s("state").toString()
      val subtype = s("subtype").toString()
      val count = s("count").asInstanceOf[Int]
      (ContentPublishFactIndex(d_period, appId, channel, `type`, state, subtype), PublishPipelineSummaryFact(d_period, appId, channel, `type`, state, subtype, count, DateTime.now().getMillis))
    }
    List(acc, facts).flatMap(f => f)
  }

  private def combineFacts(left: List[(ContentPublishFactIndex, PublishPipelineSummaryFact)], right: List[(ContentPublishFactIndex, PublishPipelineSummaryFact)]): List[(ContentPublishFactIndex, PublishPipelineSummaryFact)] = {
    List(left, right).flatMap(f => f)
  }

  private def combineFacts(f1: PublishPipelineSummaryFact, f2: PublishPipelineSummaryFact): PublishPipelineSummaryFact = {
    PublishPipelineSummaryFact(f1.d_period, f1.d_app_id, f1.d_channel, f1.`type`, f1.state, f1.subtype, f1.count + f2.count, DateTime.now().getMillis)
  }

  override def postProcess(data: RDD[PublishPipelineSummaryFact], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ContentPublishFactIndex] = {
    val d = data.collect()
    data.saveToCassandra(Constants.CREATION_METRICS_KEY_SPACE_NAME, Constants.PUBLISH_PIPELINE_SUMMARY_FACT)
    val saveInfluxFlag = config.getOrElse("saveToInflux", false).asInstanceOf[Boolean];
    if(saveInfluxFlag) saveToInfluxDB(data);
    data.map { d => ContentPublishFactIndex(d.d_period, d.d_app_id, d.d_channel, d.`type`, d.state, d.subtype) }
  }

  private def saveToInfluxDB(data: RDD[PublishPipelineSummaryFact])(implicit sc: SparkContext) {
		val influxRDD = data.filter(f => f.d_period != 0).map{ f =>
			val time = getDateTime(f.d_period)
			var tags = Map("type" -> f.`type`, "state" -> f.state, "period" -> time._2, "app_id" -> f.d_app_id, "channel" -> f.d_channel)
			if (f.subtype != "") {
			  tags += "subtype" -> f.subtype
			}
			val map = CommonUtil.caseClassToMap(f)
			val fields = Map[String, AnyRef]("count" -> f.count.asInstanceOf[AnyRef])
			InfluxRecord(tags, fields, time._1);
		}

		influxRDD.saveToInflux("publish_pipeline_metrics")
	}

}
