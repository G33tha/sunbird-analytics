package org.ekstep.analytics.updater

import scala.collection.JavaConverters._

import org.ekstep.analytics.framework.IBatchModelTemplate
import org.ekstep.analytics.framework.AlgoOutput
import org.ekstep.analytics.framework.Output
import org.ekstep.analytics.framework.DerivedEvent
import org.ekstep.analytics.framework.Period._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.dispatcher.GraphQueryDispatcher
import org.ekstep.analytics.util.CypherQueries
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.JobContext
import java.util.Collections
import org.ekstep.analytics.framework.Period._
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.util.CommonUtil._
import org.joda.time.DateTime
import com.datastax.spark.connector._
import org.ekstep.analytics.util.Constants
import org.ekstep.analytics.framework.dispatcher.InfluxDBDispatcher
import org.ekstep.analytics.framework.dispatcher.InfluxDBDispatcher.InfluxRecord
import org.ekstep.analytics.connector.InfluxDB._
import org.ekstep.analytics.framework.CassandraTable

case class TextbookSnapshotSummary(d_period: Int, d_textbook_id: String, d_app_id: String, d_channel: String, status: String, author_id: String, content_count: Long, textbookunit_count: Long, avg_content: Double, content_types: List[String], total_ts: Double, creators_count: Long, board: String, medium: String, updated_date: DateTime) extends AlgoOutput with Output with CassandraTable
case class TextbookSnapshotIndex(d_textbook_id: String, d_app_id: String, d_channel: String)

/**
 * @author Mahesh Kumar Gangula
 * @dataproduct
 * @Updater
 * 
 */

object UpdateTextbookSnapshotDB extends IBatchModelTemplate[DerivedEvent, DerivedEvent, TextbookSnapshotSummary, TextbookSnapshotSummary] with IInfluxDBUpdater with Serializable {

	val className = "org.ekstep.analytics.updater.UpdateTextbookSnapshotDB";
	override def name: String = "UpdateTextbookSnapshotDB";

	val TEXTBOOK_SNAPSHOT_METRICS = "textbook_snapshot_metrics";
	val periodsList = List(DAY, WEEK, MONTH);
	val noValue = "None"

	override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DerivedEvent] = {
		data
	}

	private def getSnapshotSummary(ts: Int)(implicit sc: SparkContext): RDD[TextbookSnapshotSummary] = {
		val bookUnitRDD = getQueryResultRDD(CypherQueries.TEXTBOOK_SNAPSHOT_UNIT_COUNT)
		val contentRDD = getQueryResultRDD(CypherQueries.TEXTBOOK_SNAPSHOT_CONTENT_COUNT);
		val resultRDD = bookUnitRDD.map(f => (TextbookSnapshotIndex(f.get("identifier").get.toString(), f.get("appId").get.toString(), f.get("channel").get.toString()), f))
			.union(contentRDD.map(f => (TextbookSnapshotIndex(f.get("identifier").get.toString(), f.get("appId").get.toString(), f.get("channel").get.toString()), f))).groupByKey().map(f => (f._1, f._2.reduce((a,b) => a ++ b)))
		val updatedAt = DateTime.now();
		resultRDD.map { f => 
			val status = f._2.getOrElse("status", noValue).toString();
			val authorId = f._2.getOrElse("author_id", noValue).toString();
			val board = f._2.getOrElse("board", noValue).toString();
			val medium = f._2.getOrElse("medium", noValue).toString();
			val textbookunitCount = f._2.getOrElse("textbookunit_count", 0L).asInstanceOf[Number].longValue();
			val creatorsCount = f._2.getOrElse("creators_count", 0).asInstanceOf[Number].longValue();
			val contentCount = f._2.getOrElse("content_count", 0L).asInstanceOf[Number].longValue();
			val contentTypes = f._2.getOrElse("content_types", List()).asInstanceOf[java.util.List[String]].asScala.toList;
			val avgContent = if (textbookunitCount == 0) 0 else contentCount/textbookunitCount;
			// TODO: We need to add ts.
			TextbookSnapshotSummary(ts, f._1.d_textbook_id, f._1.d_app_id, f._1.d_channel, status, authorId, contentCount, textbookunitCount, avgContent, contentTypes, 0, creatorsCount, board, medium, updatedAt);
		}
	}

	override def algorithm(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[TextbookSnapshotSummary] = {
		val periodName = config.getOrElse("periodType", WEEK.toString()).asInstanceOf[String];
		val metrics = if ("ALL".equals(periodName)) {
			val snapshotRDD = getSnapshotSummary(0);
			snapshotRDD.map { x => 
				periodsList.map { period => 
					val ts = CommonUtil.getPeriod(DateTime.now(), period);
					TextbookSnapshotSummary(ts, x.d_textbook_id, x.d_app_id, x.d_channel, x.status, x.author_id, x.content_count, x.textbookunit_count, x.avg_content, x.content_types, x.total_ts, x.creators_count, x.board, x.medium, x.updated_date);
				};
			}.flatMap { x => x };
		} else {
			val period = withName(periodName);
			getSnapshotSummary(CommonUtil.getPeriod(DateTime.now(), period));
		}
		metrics
	}

	private def getQueryResultRDD(query: String)(implicit sc: SparkContext): RDD[Map[String, AnyRef]] = {
		val queryResult = GraphQueryDispatcher.dispatch(query).list()
			.toArray().map(x => x.asInstanceOf[org.neo4j.driver.v1.Record]).toList;
		val result = queryResult.map { x =>
			var metadata = Map[String, AnyRef]();
			for ((k, v) <- x.asMap().asScala) {
				metadata = metadata ++ Map(k -> v.asInstanceOf[AnyRef])
			}
			metadata;
		}
		sc.parallelize(result, JobContext.parallelization);
	}

	override def postProcess(data: RDD[TextbookSnapshotSummary], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[TextbookSnapshotSummary] = {
		data.saveToCassandra(Constants.CREATION_METRICS_KEY_SPACE_NAME, Constants.TEXTBOOK_SNAPSHOT_METRICS_TABLE);
		saveToInfluxDB(data);
		data;
	}
	
	private def saveToInfluxDB(data: RDD[TextbookSnapshotSummary])(implicit sc: SparkContext) {
		val influxRDD = data.map{ f => 
			val time = getDateTime(f.d_period);
			val tags = Map("textbook_id" -> f.d_textbook_id, "period" -> time._2, "app_id" -> f.d_app_id, "channel" -> f.d_channel);
			val map = CommonUtil.caseClassToMap(f)
			val fields = map - ("d_textbook_id", "d_period", "d_app_id", "d_channel", "content_types", "updated_date") ++ Map("content_types" -> f.content_types.mkString(","));
			InfluxRecord(tags, fields, time._1);
		}
		val denormData = getDenormalizedData("Content", data.map { x => x.d_textbook_id });
		influxRDD.denormalize("textbook_id", "textbook_name", denormData).saveToInflux(TEXTBOOK_SNAPSHOT_METRICS);
	}

}