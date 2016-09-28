package org.ekstep.analytics.api.metrics

import org.ekstep.analytics.api.IMetricsModel
import org.apache.spark.SparkContext
import com.typesafe.config.Config
import org.ekstep.analytics.api.ContentUsageListMetrics
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.api.util.CommonUtil
import org.ekstep.analytics.api.service.RecommendationAPIService
import org.ekstep.analytics.framework.util.JSONUtils

object ContentUsageListMetricsModel  extends IMetricsModel[ContentUsageListMetrics]  with Serializable {
	
	override def metric : String = "gls"; // Because content list is part of GLS.
	
	override def getMetrics(records: RDD[ContentUsageListMetrics], period: String)(implicit sc: SparkContext, config: Config): Array[ContentUsageListMetrics] = {
		val periodEnum = periodMap.get(period).get._1;
		val periods = _getPeriods(period);
		val recordsRDD = records.map { x => (x.d_period, x) };
		var periodsRDD = sc.parallelize(periods.map { period => (period, ContentUsageListMetrics(period)) });
		periodsRDD.leftOuterJoin(recordsRDD).sortBy(-_._1).map { f =>
			if(f._2._2.isDefined) f._2._2.get else f._2._1 
		}.map { x => 
			x.label = Option(CommonUtil.getPeriodLabel(periodEnum, x.d_period));
			val contents = for(id <- x.m_contents.getOrElse(List())) yield {
				RecommendationAPIService.contentBroadcastMap.value.getOrElse(id.toString, Map())
			}
			x.m_contents = Option(contents);
		 x }.collect();
	}
	
	override def getSummary(metrics: Array[ContentUsageListMetrics]): Map[String, AnyRef] = {
		Map();
	} 
}