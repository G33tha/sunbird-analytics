package org.ekstep.analytics.updater

import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.DerivedEvent
import org.ekstep.analytics.framework.AlgoOutput
import org.ekstep.analytics.framework.Empty
import org.ekstep.analytics.framework.IBatchModelTemplate
import org.ekstep.analytics.framework.Period._
import org.ekstep.analytics.util.Constants
import com.datastax.spark.connector._
import org.ekstep.analytics.framework.Output
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.AlgoInput
import org.ekstep.analytics.connector.InfluxDB._
import org.ekstep.analytics.framework.dispatcher.InfluxDBDispatcher.InfluxRecord
import org.ekstep.analytics.framework.CassandraTable
import org.ekstep.analytics.framework.conf.AppConf

case class AuthorMetricsFact_T(d_period: Int, d_author_id: String, d_app_id: String, d_channel: String, total_sessions: Long, total_ts: Double, total_ce_ts: Double, total_ce_visit: Long, ce_visits_count: Long, percent_ce_sessions: Double, avg_ts_session: Double, percent_ce_ts: Double, updated_date: Long, last_gen_date: Long)
case class AuthorMetricsFact(d_period: Int, d_author_id: String, d_app_id: String, d_channel: String, total_sessions: Long, total_ts: Double, total_ce_ts: Double, total_ce_visit: Long, ce_visits_count: Long, percent_ce_sessions: Double, avg_ts_session: Double, percent_ce_ts: Double, updated_date: Long) extends AlgoOutput with Output with CassandraTable
case class AuthorMetricsIndex(d_period: Int, d_author_id: String, d_app_id: String, d_channel: String)

object UpdateAuthorSummaryDB extends IBatchModelTemplate[DerivedEvent, DerivedEvent, AuthorMetricsFact, AuthorMetricsFact] with IInfluxDBUpdater with Serializable {

    override def name(): String = "UpdateAuthorSummaryDB";
    implicit val className = "org.ekstep.analytics.updater.UpdateAuthorSummaryDB";
    val AUTHOR_USAGE_METRICS = "author_usage_metrics";

    override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DerivedEvent] = {
        data;
    }
    override def algorithm(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[AuthorMetricsFact] = {
        val authorMetrics = data.map { x =>
            val period = x.dimensions.period.get
            val author = x.uid
            val appId = CommonUtil.getAppDetails(x).id
            val channel = CommonUtil.getChannelId(x)
            val eksMap = x.edata.eks.asInstanceOf[Map[String, AnyRef]]
            val totalSessions = eksMap.getOrElse("total_sessions", 0L).asInstanceOf[Number].longValue()
            val totalTS = CommonUtil.roundDouble(eksMap.getOrElse("total_ts", 0.0).asInstanceOf[Double], 2)
            val totalCETS = CommonUtil.roundDouble(eksMap.getOrElse("ce_total_ts", 0.0).asInstanceOf[Double], 2)
            val totalCEVisits = eksMap.getOrElse("ce_total_visits", 0l).asInstanceOf[Number].longValue()
            val ce_visits_count = eksMap.getOrElse("ce_visits_count", 0l).asInstanceOf[Number].longValue()
            val percentCEsessions = (if (0 != totalSessions) (ce_visits_count * 1.0 / totalSessions) else 0.0) * 100
            val avgSessionTS = CommonUtil.roundDouble(if (0 != totalSessions) (totalTS / totalSessions) else 0.0, 2)
            val percentCEts = CommonUtil.roundDouble((if (0 != totalTS) (totalCETS / totalTS) else 0.0) * 100, 2)
            AuthorMetricsFact_T(period, author, appId, channel, totalSessions, totalTS, totalCETS, totalCEVisits, ce_visits_count, percentCEsessions, avgSessionTS, percentCEts, System.currentTimeMillis(), x.syncts)
        }.cache
        rollup(authorMetrics, DAY).union(rollup(authorMetrics, WEEK)).union(rollup(authorMetrics, MONTH)).union(rollup(authorMetrics, CUMULATIVE)).cache();
    }

    override def postProcess(data: RDD[AuthorMetricsFact], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[AuthorMetricsFact] = {
        data.saveToCassandra(Constants.CREATION_METRICS_KEY_SPACE_NAME, Constants.AUTHOR_USAGE_METRICS_FACT);
        val saveInfluxFlag = config.getOrElse("saveToInflux", false).asInstanceOf[Boolean];
        if(saveInfluxFlag) saveToInfluxDB(data);
        data;
    }

    private def saveToInfluxDB(data: RDD[AuthorMetricsFact])(implicit sc: SparkContext) {
        val metrics = data.filter { x => x.d_period != 0 }.map { x =>
            val fields = (CommonUtil.caseClassToMap(x) - ("d_period", "d_author_id", "d_app_id", "d_channel")).map(f => (f._1, f._2.asInstanceOf[Number].doubleValue().asInstanceOf[AnyRef]));
            val time = getDateTime(x.d_period);
            InfluxRecord(Map("period" -> time._2, "author_id" -> x.d_author_id, "app_id" -> x.d_app_id, "channel" -> x.d_channel), fields, time._1);
        };
        val authors = getDenormalizedData("User", data.map { x => x.d_author_id })
        metrics.denormalize("author_id", "author_name", authors).saveToInflux(AUTHOR_USAGE_METRICS);
    }

    private def rollup(data: RDD[AuthorMetricsFact_T], period: Period): RDD[AuthorMetricsFact] = {

        val currentData = data.map { x =>
            val d_period = CommonUtil.getPeriod(x.last_gen_date, period);
            (AuthorMetricsIndex(d_period, x.d_author_id, x.d_app_id, x.d_channel), x)
        }.reduceByKey(reduceAUS)
        val prvData = currentData.map { x => x._1 }.joinWithCassandraTable[AuthorMetricsFact](Constants.CREATION_METRICS_KEY_SPACE_NAME, Constants.AUTHOR_USAGE_METRICS_FACT).on(SomeColumns("d_period", "d_author_id", "d_app_id", "d_channel"));
        val joinedData = currentData.leftOuterJoin(prvData)
        val rollupSummaries = joinedData.map { x =>
            val index = x._1
            val newSumm = x._2._1
            val prvSumm = x._2._2.getOrElse(AuthorMetricsFact(index.d_period, index.d_author_id, index.d_app_id, index.d_channel, 0l, 0.0, 0.0, 0l, 0l, 0.0, 0.0, 0.0, System.currentTimeMillis()))
            reduce(prvSumm, newSumm, period);
        }
        rollupSummaries;
    }

    private def reduceAUS(fact1: AuthorMetricsFact_T, fact2: AuthorMetricsFact_T): AuthorMetricsFact_T = {
        val totalSessions = fact1.total_sessions + fact2.total_sessions
        val totalTS = CommonUtil.roundDouble(fact1.total_ts + fact2.total_ts, 2)
        val totalCETS = CommonUtil.roundDouble(fact1.total_ce_ts + fact2.total_ce_ts, 2)
        val totalCEVisits = fact1.total_ce_visit + fact2.total_ce_visit
        val ceVisitCount = fact1.ce_visits_count + fact2.ce_visits_count
        val percentCEsessions = CommonUtil.roundDouble((if (0 != totalSessions) (ceVisitCount * 1.0 / totalSessions) else 0.0) * 100, 2)
        val avgSessionTS = CommonUtil.roundDouble(if (0 != totalSessions) (totalTS / totalSessions) else 0.0, 2)
        val percentCEts = CommonUtil.roundDouble((if (0 != totalTS) (totalCETS / totalTS) else 0.0) * 100, 2)
        AuthorMetricsFact_T(fact1.d_period, fact1.d_author_id, fact1.d_app_id, fact1.d_channel, totalSessions, totalTS, totalCETS, totalCEVisits, ceVisitCount, percentCEsessions, avgSessionTS, percentCEts, System.currentTimeMillis(), fact1.last_gen_date)
    }
    private def reduce(fact1: AuthorMetricsFact, fact2: AuthorMetricsFact_T, period: Period): AuthorMetricsFact = {
        val totalSessions = fact1.total_sessions + fact2.total_sessions
        val totalTS = CommonUtil.roundDouble(fact1.total_ts + fact2.total_ts, 2)
        val totalCETS = CommonUtil.roundDouble(fact1.total_ce_ts + fact2.total_ce_ts, 2)
        val totalCEVisits = fact1.total_ce_visit + fact2.total_ce_visit
        val ceVisitCount = fact1.ce_visits_count + fact2.ce_visits_count
        val percentCEsessions = CommonUtil.roundDouble((if (0 != totalSessions) (ceVisitCount * 1.0 / totalSessions) else 0.0) * 100, 2)
        val avgSessionTS = CommonUtil.roundDouble(if (0 != totalSessions) (totalTS / totalSessions) else 0.0, 2)
        val percentCEts = CommonUtil.roundDouble((if (0 != totalTS) (totalCETS / totalTS) else 0.0) * 100, 2)
        AuthorMetricsFact(fact1.d_period, fact1.d_author_id, fact1.d_app_id, fact1.d_channel, totalSessions, totalTS, totalCETS, totalCEVisits, ceVisitCount, percentCEsessions, avgSessionTS, percentCEts, System.currentTimeMillis())
    }
}