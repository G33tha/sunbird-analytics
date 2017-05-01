package org.ekstep.analytics.updater

import org.ekstep.analytics.framework.IBatchModel
import org.ekstep.analytics.framework._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.util.CommonUtil._
import java.util.Calendar
import java.text.SimpleDateFormat
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

case class ContentSnapshotSummary(d_period: Int, d_author_id: String, d_partner_id: String, total_author_count: Long, total_author_count_start: Long, active_author_count: Long, active_author_count_start: Long, total_content_count: Long, total_content_count_start: Long, live_content_count: Long, live_content_count_start: Long, review_content_count: Long, review_content_count_start: Long) extends AlgoOutput with Output
case class ContentSnapshotIndex(d_period: Int, d_author_id: String, d_partner_id: String)

object UpdateContentSnapshotDB extends IBatchModelTemplate[DerivedEvent, DerivedEvent, ContentSnapshotSummary, ContentSnapshotSummary] with Serializable {

    val className = "org.ekstep.analytics.updater.UpdateContentSnapshotDB"
    override def name: String = "UpdateContentSnapshotDB"
    val CONTENT_SNAPSHOT_METRICS = "content_snapshot_metrics";

    override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DerivedEvent] = {
        DataFilter.filter(data, Filter("eid", "EQ", Option("ME_CONTENT_SNAPSHOT_SUMMARY")));
    }

    override def algorithm(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ContentSnapshotSummary] = {

        val periodsList = List(DAY, WEEK, MONTH)
        val currentData = data.map { x =>
            for (p <- periodsList) yield {
                val d_period = CommonUtil.getPeriod(x.syncts, p);
                (ContentSnapshotIndex(d_period, x.dimensions.author_id.get, x.dimensions.partner_id.get), x);
            }
        }.flatMap(f => f)
        val prvData = currentData.map { x => x._1 }.joinWithCassandraTable[ContentSnapshotSummary](Constants.CONTENT_KEY_SPACE_NAME, Constants.CONTENT_SNAPSHOT_SUMMARY).on(SomeColumns("d_period", "d_author_id", "d_partner_id"));
        val joinedData = currentData.leftOuterJoin(prvData)
        joinedData.map { f =>
            val prevSumm = f._2._2.getOrElse(null)
            val eksMap = f._2._1.edata.eks.asInstanceOf[Map[String, AnyRef]]

            val total_author_count = eksMap.get("total_user_count").get.asInstanceOf[Number].longValue()
            val active_author_count = eksMap.get("active_user_count").get.asInstanceOf[Number].longValue()
            val total_content_count = eksMap.get("total_content_count").get.asInstanceOf[Number].longValue()
            val live_content_count = eksMap.get("live_content_count").get.asInstanceOf[Number].longValue()
            val review_content_count = eksMap.get("review_content_count").get.asInstanceOf[Number].longValue()

            if (null == prevSumm)
                ContentSnapshotSummary(f._1.d_period, f._1.d_author_id, f._1.d_partner_id, total_author_count, total_author_count, active_author_count, active_author_count, total_content_count, total_content_count, live_content_count, live_content_count, review_content_count, review_content_count)
            else
                ContentSnapshotSummary(f._1.d_period, f._1.d_author_id, f._1.d_partner_id, total_author_count, prevSumm.total_author_count_start, active_author_count, prevSumm.active_author_count_start, total_content_count, prevSumm.total_content_count_start, live_content_count, prevSumm.live_content_count_start, review_content_count, prevSumm.review_content_count_start)
        }
    }

    override def postProcess(data: RDD[ContentSnapshotSummary], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ContentSnapshotSummary] = {
        data.saveToCassandra(Constants.CONTENT_KEY_SPACE_NAME, Constants.CONTENT_SNAPSHOT_SUMMARY)
        saveToInfluxDB(data);
        data;
    }
    
    private def saveToInfluxDB(data: RDD[ContentSnapshotSummary]) {
    	val metrics = data.map { x =>
			val fields: Map[com.pygmalios.reactiveinflux.Point.FieldKey, com.pygmalios.reactiveinflux.FieldValue] = Map(
					"total_author_count" -> x.total_author_count.toDouble,
					"total_author_count_start" -> x.total_author_count_start.toDouble,
					"active_author_count" -> x.active_author_count.toDouble,
					"active_author_count_start" -> x.active_author_count_start.toDouble,
					"total_content_count" -> x.total_content_count.toDouble,
					"total_content_count_start" -> x.total_content_count_start.toDouble,
					"live_content_count" -> x.live_content_count.toDouble,
					"live_content_count_start" -> x.live_content_count_start.toDouble,
					"review_content_count" -> x.review_content_count.toDouble,
					"review_content_count_start" -> x.review_content_count_start.toDouble)
			val time = getDateTime(x.d_period);
        	Point(time = time._1, 
        			measurement = CONTENT_SNAPSHOT_METRICS, 
        			tags = Map("env" -> AppConf.getConfig("application.env"), "period" -> time._2, "partner_id" -> x.d_partner_id, "author_id" -> x.d_author_id), 
        			fields = fields);
        };
        import com.pygmalios.reactiveinflux.spark._
        implicit val params = ReactiveInfluxDbName(AppConf.getConfig("reactiveinflux.database"))
        implicit val awaitAtMost = Integer.parseInt(AppConf.getConfig("reactiveinflux.awaitatmost")).second
        metrics.saveToInflux();
    }
    
    private def getDateTime(periodVal: Int): (DateTime, String) = {
		val period = periodVal.toString();
		period.size match {
			case 8 => (dayPeriod.parseDateTime(period).withTimeAtStartOfDay(), "day");
			case 7 =>
				val week = period.substring(0, 4) + "-" + period.substring(5, period.length);
				val firstDay = weekPeriodLabel.parseDateTime(week)
				val lastDay = firstDay.plusDays(6);
				(lastDay.withTimeAtStartOfDay(), "week");
			case 6 => (monthPeriod.parseDateTime(period).withTimeAtStartOfDay(), "month");
		}
	}
}