/**
 * @author Jitendra Singh Sankhwar
 */
package org.ekstep.analytics.updater

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.framework._
import org.ekstep.analytics.framework.IBatchModelTemplate
import org.ekstep.analytics.framework.Period._
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.util.Constants
import com.datastax.spark.connector._
import org.ekstep.analytics.framework.dispatcher.InfluxDBDispatcher.InfluxRecord
import org.ekstep.analytics.framework.dispatcher.InfluxDBDispatcher

/**
 * Case class for Cassandra Models
 */
case class CEUsageSummaryFact(d_period: Int, d_content_id: String, users_count: Long, total_sessions: Long, total_ts: Double, avg_ts_session: Double, updated_date: Long) extends AlgoOutput
case class CEUsageSummaryIndex(d_period: Int, d_content_id: String) extends Output

case class CEUsageSummaryFact_T(d_period: Int, d_content_id: String, users_count: Long, total_sessions: Long, total_ts: Double, avg_ts_session: Double, last_gen_date: Long) extends AlgoOutput

/**
 * @dataproduct
 * @Updater
 *
 * UpdateContentEditorUsageDB
 *
 * Functionality
 * 1. Update content editor usage summary per day, week, month & cumulative metrics in Cassandra DB.
 * Event used - ME_CE_USAGE_SUMMARY
 */
object UpdateContentEditorUsageDB extends IBatchModelTemplate[DerivedEvent, DerivedEvent, CEUsageSummaryFact, CEUsageSummaryIndex] with Serializable with IInfluxDBUpdater {

    val className = "org.ekstep.analytics.updater.UpdateContentEditorMetricsDB"
    override def name: String = "UpdateContentEditorUsageDB"
    val CE_USAGE_METRICS = "ce_usage_metrics"

    override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DerivedEvent] = {
        DataFilter.filter(data, Filter("eid", "EQ", Option("ME_CE_USAGE_SUMMARY")));
    }

    override def algorithm(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[CEUsageSummaryFact] = {

        val CESummary = data.map { x =>

            val period = x.dimensions.period.get;

            val eksMap = x.edata.eks.asInstanceOf[Map[String, AnyRef]]
            val users_count = eksMap.get("users_count").get.asInstanceOf[Number].longValue()
            val total_sessions = eksMap.get("total_sessions").get.asInstanceOf[Number].longValue()
            val total_ts = eksMap.get("total_ts").get.asInstanceOf[Double]
            val avg_ts_session = eksMap.get("avg_ts_session").get.asInstanceOf[Double]

            CEUsageSummaryFact_T(period, x.dimensions.content_id.get, users_count, total_sessions, total_ts, avg_ts_session, x.context.date_range.to);
        }.cache();

        // Roll up summaries
        rollup(CESummary, DAY).union(rollup(CESummary, WEEK)).union(rollup(CESummary, MONTH)).union(rollup(CESummary, CUMULATIVE)).cache();
    }

    override def postProcess(data: RDD[CEUsageSummaryFact], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[CEUsageSummaryIndex] = {
        // Update the database
        data.saveToCassandra(Constants.CREATION_METRICS_KEY_SPACE_NAME, Constants.CE_USAGE_SUMMARY)
        saveToInfluxDB(data)
        data.map { x => CEUsageSummaryIndex(x.d_period, x.d_content_id) };
    }

    private def rollup(data: RDD[CEUsageSummaryFact_T], period: Period): RDD[CEUsageSummaryFact] = {

        val currentData = data.map { x =>
            val d_period = CommonUtil.getPeriod(x.last_gen_date, period);
            (CEUsageSummaryIndex(d_period, x.d_content_id), CEUsageSummaryFact_T(d_period, x.d_content_id, x.users_count, x.total_sessions, x.total_ts, x.avg_ts_session, x.last_gen_date));
        }.reduceByKey(reduceCEUS);
        val prvData = currentData.map { x => x._1 }.joinWithCassandraTable[CEUsageSummaryFact](Constants.CREATION_METRICS_KEY_SPACE_NAME, Constants.CE_USAGE_SUMMARY).on(SomeColumns("d_period", "d_content_id"));
        val joinedData = currentData.leftOuterJoin(prvData)
        val rollupSummaries = joinedData.map { x =>
            val index = x._1
            val newSumm = x._2._1
            val prvSumm = x._2._2.getOrElse(CEUsageSummaryFact(index.d_period, index.d_content_id, 0L, 0L, 0.0, 0.0, 0L));
            reduce(prvSumm, newSumm, period);
        }
        rollupSummaries;
    }

    private def reduceCEUS(fact1: CEUsageSummaryFact_T, fact2: CEUsageSummaryFact_T): CEUsageSummaryFact_T = {
        val users_count = fact2.users_count + fact1.users_count;
        val total_ts = CommonUtil.roundDouble(fact2.total_ts + fact1.total_ts, 2);
        val total_sessions = fact2.total_sessions + fact1.total_sessions
        val avg_ts_session = CommonUtil.roundDouble((total_ts / total_sessions), 2);

        CEUsageSummaryFact_T(fact1.d_period, fact1.d_content_id, users_count, total_sessions, total_ts, avg_ts_session, fact2.last_gen_date);
    }

    private def reduce(fact1: CEUsageSummaryFact, fact2: CEUsageSummaryFact_T, period: Period): CEUsageSummaryFact = {
        val users_count = fact2.users_count + fact1.users_count
        val total_ts = CommonUtil.roundDouble(fact2.total_ts + fact1.total_ts, 2);
        val total_sessions = fact2.total_sessions + fact1.total_sessions
        val avg_ts_session = CommonUtil.roundDouble((total_ts / total_sessions), 2);

        CEUsageSummaryFact(fact1.d_period, fact1.d_content_id, users_count, total_sessions, total_ts, avg_ts_session, System.currentTimeMillis());
    }

    private def saveToInfluxDB(data: RDD[CEUsageSummaryFact]) {
        val metrics = data.filter { x => x.d_period != 0 } map { x =>
            val fields = (CommonUtil.caseClassToMap(x) - ("d_period", "d_content_id", "updated_date")).map(f => (f._1, f._2.asInstanceOf[Number].doubleValue().asInstanceOf[AnyRef]));
            val time = getDateTime(x.d_period);
            InfluxRecord(Map("period" -> time._2, "content_id" -> x.d_content_id), fields, time._1);
        };
        InfluxDBDispatcher.dispatch(CE_USAGE_METRICS, metrics);
    }
}