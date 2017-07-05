package org.ekstep.analytics.updater

import org.ekstep.analytics.framework.IBatchModel
import org.ekstep.analytics.framework._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.util.CommonUtil._
import org.ekstep.analytics.framework.DataFilter
import org.ekstep.analytics.framework.Filter
import org.ekstep.analytics.framework.Period._
import com.datastax.spark.connector._
import org.ekstep.analytics.util.Constants
import org.ekstep.analytics.framework.util.JSONUtils
import org.joda.time.DateTime
import scala.concurrent.duration._
import com.pygmalios.reactiveinflux._
import com.datastax.spark.connector._
import org.ekstep.analytics.framework.conf.AppConf
import org.ekstep.analytics.framework.dispatcher.InfluxDBDispatcher.InfluxRecord
import org.ekstep.analytics.framework.dispatcher.InfluxDBDispatcher
import org.ekstep.analytics.connector.InfluxDB._

case class ConceptSnapshotSummary(d_period: Int, d_concept_id: String, d_app_id: String, d_channel: String, total_content_count: Long, total_content_count_start: Long, live_content_count: Long, live_content_count_start: Long, review_content_count: Long, review_content_count_start: Long, updated_date: Option[DateTime] = Option(DateTime.now())) extends AlgoOutput with Output with CassandraTable
case class ConceptSnapshotKey(d_period: Int, d_concept_id: String, d_app_id: String, d_channel: String)

object UpdateConceptSnapshotDB extends IBatchModelTemplate[DerivedEvent, DerivedEvent, ConceptSnapshotSummary, ConceptSnapshotSummary] with IInfluxDBUpdater with Serializable {

    val className = "org.ekstep.analytics.updater.UpdateConceptSnapshotDB"
    override def name: String = "UpdateConceptSnapshotDB"
    val CONCEPT_SNAPSHOT_METRICS = "concept_snapshot_metrics";

    override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DerivedEvent] = {
        DataFilter.filter(data, Filter("eid", "EQ", Option("ME_CONCEPT_SNAPSHOT_SUMMARY")));
    }

    override def algorithm(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ConceptSnapshotSummary] = {

        val periodsList = List(DAY, WEEK, MONTH)
        val currentData = data.map { x =>
            for (p <- periodsList) yield {
                val d_period = CommonUtil.getPeriod(x.syncts, p);
                val appId = CommonUtil.getAppDetails(x).id
                val channel = CommonUtil.getChannelId(x)
                (ConceptSnapshotKey(d_period, x.dimensions.concept_id.get, appId, channel), x);
            }
        }.flatMap(f => f)
        val prvData = currentData.map { x => x._1 }.joinWithCassandraTable[ConceptSnapshotSummary](Constants.CONTENT_KEY_SPACE_NAME, Constants.CONCEPT_SNAPSHOT_SUMMARY).on(SomeColumns("d_period", "d_concept_id"));
        val joinedData = currentData.leftOuterJoin(prvData)
        joinedData.map { f =>
            val prevSumm = f._2._2.getOrElse(null)
            val eksMap = f._2._1.edata.eks.asInstanceOf[Map[String, AnyRef]]

            val total_content_count = eksMap.get("total_content_count").get.asInstanceOf[Number].longValue()
            val live_content_count = eksMap.get("live_content_count").get.asInstanceOf[Number].longValue()
            val review_content_count = eksMap.get("review_content_count").get.asInstanceOf[Number].longValue()

            if (null == prevSumm)
                ConceptSnapshotSummary(f._1.d_period, f._1.d_concept_id, f._1.d_app_id, f._1.d_channel, total_content_count, total_content_count, live_content_count, live_content_count, review_content_count, review_content_count)
            else
                ConceptSnapshotSummary(f._1.d_period, f._1.d_concept_id, f._1.d_app_id, f._1.d_channel, total_content_count, prevSumm.total_content_count_start, live_content_count, prevSumm.live_content_count_start, review_content_count, prevSumm.review_content_count_start)
        }
    }

    override def postProcess(data: RDD[ConceptSnapshotSummary], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ConceptSnapshotSummary] = {
        data.saveToCassandra(Constants.CONTENT_KEY_SPACE_NAME, Constants.CONCEPT_SNAPSHOT_SUMMARY)
        saveToInfluxDB(data);
        data;
    }

    private def saveToInfluxDB(data: RDD[ConceptSnapshotSummary])(implicit sc: SparkContext){
        val metrics = data.map { x =>
            val fields = (CommonUtil.caseClassToMap(x) - ("d_period", "d_concept_id", "d_app_id", "d_channel", "updated_date")).map(f => (f._1, f._2.asInstanceOf[Number].doubleValue().asInstanceOf[AnyRef]));
            val time = getDateTime(x.d_period);
            InfluxRecord(Map("period" -> time._2, "concept_id" -> x.d_concept_id, "app_id" -> x.d_app_id, "channel" -> x.d_channel), fields, time._1);
        };
        val concepts = getDenormalizedData("Concept", data.map { x => x.d_concept_id })
        metrics.denormalize("concept_id", "concept_name", concepts).saveToInflux(CONCEPT_SNAPSHOT_METRICS);
    }

}