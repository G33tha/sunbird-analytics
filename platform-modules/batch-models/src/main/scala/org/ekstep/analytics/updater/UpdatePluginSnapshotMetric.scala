package org.ekstep.analytics.updater

import scala.collection.JavaConversions.asScalaBuffer

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.framework.AlgoOutput
import org.ekstep.analytics.framework.Empty
import org.ekstep.analytics.framework.IBatchModelTemplate
import org.ekstep.analytics.framework.Output
import org.ekstep.analytics.framework.Period.DAY
import org.ekstep.analytics.framework.Period.MONTH
import org.ekstep.analytics.framework.Period.WEEK
import org.ekstep.analytics.framework.dispatcher.GraphQueryDispatcher
import org.ekstep.analytics.util.Constants
import org.ekstep.analytics.util.CypherQueries
import com.datastax.spark.connector._
import org.ekstep.analytics.framework.dispatcher.InfluxDBDispatcher.InfluxRecord
import com.datastax.spark.connector.toRDDFunctions
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.util.JSONUtils
import org.joda.time.DateTime
import org.ekstep.analytics.framework.dispatcher.InfluxDBDispatcher
import org.ekstep.analytics.connector.InfluxDB._

case class PluginMetrics(d_plugin_id: String, plugin_name: String, domain: String, author: String, content_count: Int)
case class PluginSnapshotMetrics(d_period: Int, d_plugin_id: String, plugin_name: String, domain: String, author: String, content_count: Int, content_count_start: Int, updated_date: Option[DateTime] = Option(DateTime.now())) extends AlgoOutput with Output
case class PluginSnapshotIndex(d_period: Int, d_plugin_id: String)

object UpdatePluginSnapshotMetrics extends IBatchModelTemplate[Empty, Empty, PluginSnapshotMetrics, PluginSnapshotMetrics] with IInfluxDBUpdater with Serializable {

    val className = "org.ekstep.analytics.updater.UpdatePluginSnapshotMetrics";
    override def name: String = "UpdatePluginSnapshotMetrics";

    val PLUGIN_SNAPSHOT_METRICS = "plugin_snapshot_metrics";
    val periodsList = List(DAY, WEEK, MONTH);
    val noValue = "None"

    private def getPluginMetrics(query: String)(implicit sc: SparkContext): List[PluginMetrics] = {
        GraphQueryDispatcher.dispatch(query).list().map { x =>
            PluginMetrics(x.get("plugin_id").asString(), x.get("name").asString(), x.get("domain").asList().last.asInstanceOf[String], x.get("author").asString(), x.get("contentCount").asInt)
        }.toList
    }
    override def preProcess(data: RDD[Empty], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[Empty] = {
        data
    }
    override def algorithm(data: RDD[Empty], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[PluginSnapshotMetrics] = {

        val metrics = getPluginMetrics(CypherQueries.PLUGIN_SNAPSHOT_METIRCS)
        val currentData = sc.parallelize(metrics.map { x =>
            for (p <- periodsList) yield {
                val d_period = CommonUtil.getPeriod(System.currentTimeMillis(), p);
                (PluginSnapshotIndex(d_period, x.d_plugin_id), x);
            }
        }.flatMap(f => f))

        val prvData = currentData.map { x => x._1 }.joinWithCassandraTable[PluginSnapshotMetrics](Constants.CREATION_METRICS_KEY_SPACE_NAME, Constants.PLUGIN_SNAPSHOT_METRICS_TABLE).on(SomeColumns("d_period", "d_plugin_id"));
        val joinedData = currentData.leftOuterJoin(prvData)

        joinedData.map { x =>
            val prevSumm = x._2._2.getOrElse(null)
            val period = x._1.d_period
            val pulgin_id = x._1.d_plugin_id
            val plugin_name = x._2._1.plugin_name
            val author = x._2._1.author
            val domain = x._2._1.domain
            val content_count = x._2._1.content_count
            if (prevSumm == null) {
                PluginSnapshotMetrics(period, pulgin_id, plugin_name, domain, author, content_count, content_count)
            } else {
                PluginSnapshotMetrics(period, pulgin_id, plugin_name, domain, author, content_count, prevSumm.content_count_start)
            }
        }
    }

    override def postProcess(data: RDD[PluginSnapshotMetrics], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[PluginSnapshotMetrics] = {
        data.saveToCassandra(Constants.CREATION_METRICS_KEY_SPACE_NAME, Constants.PLUGIN_SNAPSHOT_METRICS_TABLE);
        saveToInfluxDB(data);
        data;
    }

    private def saveToInfluxDB(data: RDD[PluginSnapshotMetrics])(implicit sc: SparkContext) {
        data.map { x =>
            val time = getDateTime(x.d_period);
            InfluxRecord(Map("period" -> time._2, "plugin_id" -> x.d_plugin_id, "author" -> x.author, "domain" -> x.domain, "plugin_name" -> x.plugin_name), Map("content_count" -> x.content_count.asInstanceOf[AnyRef], "content_count_start" -> x.content_count_start.asInstanceOf[AnyRef]), time._1);

        }.saveToInflux(PLUGIN_SNAPSHOT_METRICS);

    }
}