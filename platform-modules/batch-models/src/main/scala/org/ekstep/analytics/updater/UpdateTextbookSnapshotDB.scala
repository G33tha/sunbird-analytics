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
import org.ekstep.analytics.framework.util.CommonUtil
import org.joda.time.DateTime
import com.datastax.spark.connector._
import org.ekstep.analytics.util.Constants

case class TextbookSnapshotSummary(d_period: Int, d_textbook_id: String, status: String, author_id: String, content_count: Long, textbookunit_count: Long, avg_content: Double, content_types: List[String], total_ts: Double, creators_count: Int, board: String, medium: String, domain: String) extends AlgoOutput with Output

/**
 * @author mahesh
 */

object UpdateTextbookSnapshotDB extends IBatchModelTemplate[DerivedEvent, DerivedEvent, TextbookSnapshotSummary, TextbookSnapshotSummary] with Serializable {

	val className = "org.ekstep.analytics.updater.UpdateTextbookSnapshotDB";
	override def name: String = "UpdateTextbookSnapshotDB";

	val periodsList = List(DAY, WEEK, MONTH);
	val ALL_PERIOD_TYPES = List("MONTH", "WEEK", "DAY");
	val noValue = "None"

	override def preProcess(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DerivedEvent] = {
		data
	}

	private def getSnapshotSummary(ts: Int)(implicit sc: SparkContext): RDD[TextbookSnapshotSummary] = {
		val bookUnitResult = getResultMap(GraphQueryDispatcher.dispatch(CypherQueries.TEXTBOOK_SNAPSHOT_UNIT_COUNT).list()
			.toArray().map(x => x.asInstanceOf[org.neo4j.driver.v1.Record]).toList);
		val bookUnitRDD = sc.parallelize(bookUnitResult, JobContext.parallelization);
		val contentResult = getResultMap(GraphQueryDispatcher.dispatch(CypherQueries.TEXTBOOK_SNAPSHOT_CONTENT_COUNT).list()
			.toArray().map(x => x.asInstanceOf[org.neo4j.driver.v1.Record]).toList)
		val contentRDD = sc.parallelize(contentResult, JobContext.parallelization);
		val resultRDD = bookUnitRDD.map(f => (f.get("identifier").get.toString(), f))
			.union(contentRDD.map(f => (f.get("identifier").get.toString(), f))).groupByKey().map(f => (f._1, f._2.reduce((a,b) => a ++ b)))
		resultRDD.map { f => 
			val status = f._2.getOrElse("status", noValue).toString();
			val authorId = f._2.getOrElse("author_id", noValue).toString();
			val board = f._2.getOrElse("board", noValue).toString();
			val medium = f._2.getOrElse("medium", noValue).toString();
			val textbookunitCount = f._2.getOrElse("textbookunit_count", 0L).asInstanceOf[Number].longValue();
			val contentCount = f._2.getOrElse("content_count", 0L).asInstanceOf[Number].longValue();
			val contentTypes = List(); //f._2.getOrElse("content_types", List()).asInstanceOf[Collections].asInstanceOf[List[String]];
			val avgContent = if (textbookunitCount == 0) 0 else contentCount/textbookunitCount;
			TextbookSnapshotSummary(ts, f._1, status, authorId, contentCount, textbookunitCount, avgContent, contentTypes, 0, 0, board, medium, noValue);
		}
	}

	override def algorithm(data: RDD[DerivedEvent], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[TextbookSnapshotSummary] = {
		val periodName = config.getOrElse("periodType", WEEK.toString()).asInstanceOf[String];
		val metrics = if ("ALL".equals(periodName)) {
			val snapshotRDD = getSnapshotSummary(0);
			snapshotRDD.map { x => 
				periodsList.map { period => 
					val ts = CommonUtil.getPeriod(DateTime.now(), period);
					TextbookSnapshotSummary(ts, x.d_textbook_id, x.status, x.author_id, x.content_count, x.textbookunit_count, x.avg_content, x.content_types, x.total_ts, x.creators_count, x.board, x.medium, x.domain);
				};
			}.flatMap { x => x };
		} else {
			val period = withName(periodName);
			getSnapshotSummary(CommonUtil.getPeriod(DateTime.now(), period));
		}
		metrics
	}

	private def getResultMap(result: List[org.neo4j.driver.v1.Record]): List[Map[String, AnyRef]] = {
		result.map { x =>
			var metadata = Map[String, AnyRef]();
			for ((k, v) <- x.asMap().asScala) {
				metadata = metadata ++ Map(k -> v.asInstanceOf[AnyRef])
			}
			metadata;
		}
	}

	override def postProcess(data: RDD[TextbookSnapshotSummary], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[TextbookSnapshotSummary] = {
		data.saveToCassandra(Constants.CONTENT_KEY_SPACE_NAME, Constants.TEXTBOOK_SNAPSHOT_SUMMARY);
		data;
	}

}