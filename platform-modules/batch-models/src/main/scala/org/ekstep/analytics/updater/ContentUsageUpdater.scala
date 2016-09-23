package org.ekstep.analytics.updater

import org.ekstep.analytics.framework.IBatchModel
import org.ekstep.analytics.framework._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.util.CommonUtil
import org.joda.time.DateTime
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import org.ekstep.analytics.framework.DataFilter
import org.ekstep.analytics.framework.Filter
import org.ekstep.analytics.framework.Period._
import com.datastax.spark.connector._
import org.ekstep.analytics.util.Constants
import org.joda.time.LocalDate
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.util.JobLogger

case class ContentUsageSummaryFact(d_period: Int, d_content_id: String, d_tag: String, m_publish_date: DateTime, m_last_sync_date: DateTime, m_last_gen_date: DateTime,
                                      m_total_ts: Double, m_total_sessions: Long, m_avg_ts_session: Double, m_total_interactions: Long, m_avg_interactions_min: Double) extends AlgoOutput
case class ContentUsageSummaryIndex(d_period: Int, d_content_id: String, d_tag: String) extends Output

object ContentUsageUpdater extends IBatchModelTemplate[DerivedEvent, DerivedEvent, ContentUsageSummaryFact, ContentUsageSummaryIndex] with Serializable {

    val className = "org.ekstep.analytics.updater.ContentUsageUpdater"
    override def name: String = "ContentUsageUpdater"
    
    override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DerivedEvent] = {
        DataFilter.filter(data, Filter("eid", "EQ", Option("ME_CONTENT_USAGE_SUMMARY")));
    }

    override def algorithm(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ContentUsageSummaryFact] = {

        val contentSummary = data.map { x =>

            val period = x.dimensions.period.get;
            val contentId = x.dimensions.content_id.get;
            val tag = x.dimensions.tag.get;

            val eksMap = x.edata.eks.asInstanceOf[Map[String, AnyRef]]
            val publish_date = new DateTime(x.context.date_range.from)
            val total_ts = eksMap.get("total_ts").get.asInstanceOf[Double]
            val total_sessions = eksMap.get("total_sessions").get.asInstanceOf[Int]
            val avg_ts_session = eksMap.get("avg_ts_session").get.asInstanceOf[Double]
            val total_interactions = eksMap.get("total_interactions").get.asInstanceOf[Int]
            val avg_interactions_min = eksMap.get("avg_interactions_min").get.asInstanceOf[Double]
            ContentUsageSummaryFact(period, contentId, tag, publish_date, new DateTime(x.syncts), new DateTime(x.context.date_range.to), total_ts, total_sessions, avg_ts_session, total_interactions, avg_interactions_min);
        }.cache();

        // Roll up summaries
        rollup(contentSummary, DAY).union(rollup(contentSummary, WEEK)).union(rollup(contentSummary, MONTH)).union(rollup(contentSummary, CUMULATIVE)).cache();
    }

    override def postProcess(data: RDD[ContentUsageSummaryFact], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ContentUsageSummaryIndex] = {
        // Update the database
        data.saveToCassandra(Constants.CONTENT_KEY_SPACE_NAME, Constants.CONTENT_USAGE_SUMMARY_FACT)
        data.map { x => ContentUsageSummaryIndex(x.d_period, x.d_content_id, x.d_tag) };
    }

    /**
     * Rollup daily summaries by period. The period summaries are joined with the previous entries in the database and then reduced to produce new summaries.
     */
    private def rollup(data: RDD[ContentUsageSummaryFact], period: Period): RDD[ContentUsageSummaryFact] = {

        val currentData = data.map { x =>
            val d_period = CommonUtil.getPeriod(x.m_last_gen_date.getMillis, period);
            (ContentUsageSummaryIndex(d_period, x.d_content_id, x.d_tag), ContentUsageSummaryFact(d_period, x.d_content_id, x.d_tag, x.m_publish_date, x.m_last_sync_date, x.m_last_gen_date, x.m_total_ts, x.m_total_sessions, x.m_avg_ts_session, x.m_total_interactions, x.m_avg_interactions_min));
        }.reduceByKey(reduce);
        val prvData = currentData.map { x => x._1 }.joinWithCassandraTable[ContentUsageSummaryFact](Constants.CONTENT_KEY_SPACE_NAME, Constants.CONTENT_USAGE_SUMMARY_FACT).on(SomeColumns("d_period", "d_content_id", "d_tag"));
        val joinedData = currentData.leftOuterJoin(prvData)
        val rollupSummaries = joinedData.map { x =>
            val index = x._1
            val newSumm = x._2._1
            val prvSumm = x._2._2.getOrElse(ContentUsageSummaryFact(index.d_period, index.d_content_id, index.d_tag, newSumm.m_publish_date, newSumm.m_last_sync_date, newSumm.m_last_gen_date, 0.0, 0, 0.0, 0, 0.0))
            reduce(prvSumm, newSumm);
        }
        rollupSummaries;
    }

    /**
     * Reducer to rollup two summaries
     */
    private def reduce(fact1: ContentUsageSummaryFact, fact2: ContentUsageSummaryFact): ContentUsageSummaryFact = {
        val total_ts = CommonUtil.roundDouble(fact2.m_total_ts + fact1.m_total_ts, 2);
        val total_sessions = fact2.m_total_sessions + fact1.m_total_sessions
        val avg_ts_session = CommonUtil.roundDouble((total_ts / total_sessions), 2);
        val total_interactions = fact2.m_total_interactions + fact1.m_total_interactions
        val avg_interactions_min = if (total_interactions == 0 || total_ts == 0) 0d else CommonUtil.roundDouble(BigDecimal(total_interactions / (total_ts / 60)).toDouble, 2);
        val publish_date = if (fact2.m_publish_date.isBefore(fact1.m_publish_date)) fact2.m_publish_date else fact1.m_publish_date;
        val sync_date = if (fact2.m_last_sync_date.isAfter(fact1.m_last_sync_date)) fact2.m_last_sync_date else fact1.m_last_sync_date;

        ContentUsageSummaryFact(fact1.d_period, fact1.d_content_id, fact1.d_tag, publish_date, sync_date, fact2.m_last_gen_date, total_ts, total_sessions, avg_ts_session, total_interactions, avg_interactions_min);
    }
    
}