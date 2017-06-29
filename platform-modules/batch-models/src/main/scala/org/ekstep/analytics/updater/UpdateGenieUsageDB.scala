package org.ekstep.analytics.updater

import org.ekstep.analytics.framework.IBatchModel
import org.ekstep.analytics.framework._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.util.CommonUtil
import java.util.Calendar
import java.text.SimpleDateFormat
import org.ekstep.analytics.framework.DataFilter
import org.ekstep.analytics.framework.Filter
import org.ekstep.analytics.framework.Period._
import com.datastax.spark.connector._
import org.ekstep.analytics.util.Constants
import org.ekstep.analytics.util.BloomFilterUtil
import org.joda.time.DateTime
import org.ekstep.analytics.framework.conf.AppConf

case class GenieUsageSummaryFact_T(d_period: Int, d_tag: String, d_app_id: String, d_channel_id: String, m_total_sessions: Long, m_total_ts: Double, m_avg_ts_session: Double, m_last_gen_date: Long, m_contents: Array[String], m_device_ids: List[String]) extends AlgoOutput
case class GenieUsageSummaryFact(d_period: Int, d_tag: String, d_app_id: String, d_channel_id: String, m_total_sessions: Long, m_total_ts: Double, m_avg_ts_session: Double, m_contents: List[String], m_total_devices: Long, m_avg_sess_device: Double, m_device_ids: Array[Byte], updated_date: Option[DateTime] = Option(DateTime.now())) extends AlgoOutput with CassandraTable
case class GenieUsageSummaryIndex(d_period: Int, d_tag: String, d_app_id: String, d_channel_id: String) extends Output

object UpdateGenieUsageDB extends IBatchModelTemplate[DerivedEvent, DerivedEvent, GenieUsageSummaryFact, GenieUsageSummaryIndex] with Serializable {

    val className = "org.ekstep.analytics.updater.UpdateGenieUsageDB"
    override def name: String = "UpdateGenieUsageDB"

    override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DerivedEvent] = {
        DataFilter.filter(data, Filter("eid", "EQ", Option("ME_GENIE_USAGE_SUMMARY")));
    }

    override def algorithm(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[GenieUsageSummaryFact] = {

        val genieSummary = data.map { x =>

            val period = x.dimensions.period.get;
            val tag = x.dimensions.tag.get;
            val appId = x.dimensions.app_id.getOrElse(AppConf.getConfig("default.app.id"));
            val channelId = x.dimensions.channel_id.getOrElse(AppConf.getConfig("default.channel.id"))

            val eksMap = x.edata.eks.asInstanceOf[Map[String, AnyRef]]

            val total_ts = eksMap.get("total_ts").get.asInstanceOf[Double]
            val total_sessions = eksMap.get("total_sessions").get.asInstanceOf[Int]
            val avg_ts_session = eksMap.get("avg_ts_session").get.asInstanceOf[Double]
            val contents = eksMap.get("contents").get.asInstanceOf[List[String]]
            val device_ids = eksMap.get("device_ids").get.asInstanceOf[List[String]]

            GenieUsageSummaryFact_T(period, tag, appId, channelId, total_sessions, total_ts, avg_ts_session, x.context.date_range.to, contents.toArray, device_ids);
        }.cache();

        // Roll up summaries
        rollup(genieSummary, DAY).union(rollup(genieSummary, WEEK)).union(rollup(genieSummary, MONTH)).union(rollup(genieSummary, CUMULATIVE)).cache();
    }
    
    override def postProcess(data: RDD[GenieUsageSummaryFact], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[GenieUsageSummaryIndex] = {
        // Update the database
        data.saveToCassandra(Constants.CONTENT_KEY_SPACE_NAME, Constants.GENIE_LAUNCH_SUMMARY_FACT)
        data.map { x => GenieUsageSummaryIndex(x.d_period, x.d_tag, x.d_app_id, x.d_channel_id) };
    }
    
    private def rollup(data: RDD[GenieUsageSummaryFact_T], period: Period): RDD[GenieUsageSummaryFact] = {

        val currentData = data.map { x =>
            val d_period = CommonUtil.getPeriod(x.m_last_gen_date, period);
            (GenieUsageSummaryIndex(d_period, x.d_tag, x.d_app_id, x.d_channel_id), GenieUsageSummaryFact_T(d_period, x.d_tag, x.d_app_id, x.d_channel_id, x.m_total_sessions, x.m_total_ts, x.m_avg_ts_session, x.m_last_gen_date, x.m_contents, x.m_device_ids));
        }.reduceByKey(reduceGUS);
        val prvData = currentData.map { x => x._1 }.joinWithCassandraTable[GenieUsageSummaryFact](Constants.CONTENT_KEY_SPACE_NAME, Constants.GENIE_LAUNCH_SUMMARY_FACT).on(SomeColumns("d_period", "d_tag", "d_app_id", "d_channel_id"));
        val joinedData = currentData.leftOuterJoin(prvData)
        val rollupSummaries = joinedData.map { x =>
            val index = x._1
            val newSumm = x._2._1
            val prvSumm = x._2._2.getOrElse(GenieUsageSummaryFact(index.d_period, index.d_tag, index.d_app_id, index.d_channel_id, 0L, 0.0, 0.0, List(), 0L, 0.0, BloomFilterUtil.getDefaultBytes(period)));
            reduce(prvSumm, newSumm, period);
        }
        rollupSummaries;
    }
    
     private def reduceGUS(fact1: GenieUsageSummaryFact_T, fact2: GenieUsageSummaryFact_T): GenieUsageSummaryFact_T = {
        val total_ts = CommonUtil.roundDouble(fact2.m_total_ts + fact1.m_total_ts, 2);
        val total_sessions = fact2.m_total_sessions + fact1.m_total_sessions
        val avg_ts_session = CommonUtil.roundDouble((total_ts / total_sessions), 2);
        val contents = (fact2.m_contents ++ fact1.m_contents).distinct
        val device_ids = (fact2.m_device_ids ++ fact1.m_device_ids).distinct

        GenieUsageSummaryFact_T(fact1.d_period, fact1.d_tag, fact1.d_app_id, fact1.d_channel_id, total_sessions, total_ts, avg_ts_session, fact2.m_last_gen_date, contents, device_ids);
    }
     
     private def reduce(fact1: GenieUsageSummaryFact, fact2: GenieUsageSummaryFact_T, period: Period): GenieUsageSummaryFact = {
        val total_ts = CommonUtil.roundDouble(fact2.m_total_ts + fact1.m_total_ts, 2);
        val total_sessions = fact2.m_total_sessions + fact1.m_total_sessions
        val avg_ts_session = CommonUtil.roundDouble((total_ts / total_sessions), 2);
        val contents = (fact2.m_contents ++ fact1.m_contents).distinct
        val bf = BloomFilterUtil.deserialize(period, fact1.m_device_ids);
        val didCount = BloomFilterUtil.countMissingValues(bf, fact2.m_device_ids);
        val total_devices = didCount + fact1.m_total_devices;
        val avg_sess_device = CommonUtil.roundDouble(total_sessions.toDouble / total_devices, 2);
        val device_ids = BloomFilterUtil.serialize(bf);
        GenieUsageSummaryFact(fact1.d_period, fact1.d_tag, fact1.d_app_id, fact1.d_channel_id, total_sessions, total_ts,avg_ts_session, contents.toList, total_devices, avg_sess_device, device_ids);
    }

}