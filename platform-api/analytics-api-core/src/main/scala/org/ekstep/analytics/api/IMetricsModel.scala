package org.ekstep.analytics.api

import org.ekstep.analytics.framework.Fetcher
import org.ekstep.analytics.framework.exception.DataFetcherException
import org.ekstep.analytics.framework.Query
import org.apache.spark.SparkContext
import com.typesafe.config.Config
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.api.util.DataFetcher
import org.jets3t.service.S3ServiceException
import scala.reflect.ClassTag
import org.ekstep.analytics.api.util.CommonUtil

trait Metrics extends AnyRef with Serializable
trait IMetricsModel[T <: Metrics] {
	val periodMap = Map[String, (String, Int)]("LAST_7_DAYS" -> ("DAY", 7), "LAST_5_WEEKS" -> ("WEEK", 5), "LAST_12_MONTHS" -> ("MONTH", 12), "CUMULATIVE" -> ("CUMULATIVE",0));
	
	def metric() : String = "metricName";
	
	def fetch(contentId: String, tag: String, period: String)(implicit sc: SparkContext, config: Config, mf : Manifest[T]): Map[String, AnyRef] = {
		val records = getData[T](contentId, tag, period.replace("LAST_", "").replace("_",	""));
		val metrics = getMetrics(records, period);
		val summary = getSummary(metrics);
		Map[String, AnyRef](
			"ttl" -> CommonUtil.getRemainingHours.asInstanceOf[AnyRef],
            "metrics" -> metrics,
            "summary" -> summary);
	}
	
	def getMetrics(records: RDD[T], period: String)(implicit sc: SparkContext, config: Config): Array[T]
	
	def getSummary(metrics: Array[T]): Map[String, AnyRef]
	
	private def getData[T](contentId: String, tag: String, period: String)(implicit mf: Manifest[T], sc: SparkContext, config: Config): RDD[T] = {
		try {
			val basePath = config.getString("metrics.search.params.path");
		  	val filePath = s"$basePath$metric-$tag-$contentId-$period.json";
		  	println("filePath:", filePath);
			val search = config.getString("metrics.search.type") match {
				case "local" => Fetcher("local", None, Option(Array(Query(None, None, None, None, None, None, None, None, None, Option(filePath)))));
				case "s3" => Fetcher("s3", None, Option(Array(Query(Option(config.getString("metrics.search.params.bucket")), Option(filePath)))));
				case _ => throw new DataFetcherException("Unknown fetcher type found");
			}
			DataFetcher.fetchBatchData[T](search);
		} catch {
		  case ex @ (_: DataFetcherException | _: S3ServiceException) =>
		  		println("fetchData Error:", ex.getMessage);
		 	  	sc.emptyRDD[T];
		}
	}
	
	protected def _getPeriods(period: String): Array[Int] = {
		val key = periodMap.get(period).get;
		org.ekstep.analytics.framework.util.CommonUtil.getPeriods(key._1, key._2);
	}
}