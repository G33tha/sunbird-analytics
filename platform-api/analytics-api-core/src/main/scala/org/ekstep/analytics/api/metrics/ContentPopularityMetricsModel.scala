package org.ekstep.analytics.api.metrics

import org.ekstep.analytics.api.IMetricsModel
import org.ekstep.analytics.api.ContentPopularityMetrics
import org.apache.spark.SparkContext
import com.typesafe.config.Config
import org.ekstep.analytics.api.util.CommonUtil
import org.ekstep.analytics.api.ContentPopularityViews
import org.ekstep.analytics.api.Rating

object ContentPopularityMetricsModel extends IMetricsModel[ContentPopularityViews, ContentPopularityMetrics]  with Serializable {
	override def metric : String = "cps";
	
	override def getMetrics(records: Array[ContentPopularityViews], period: String, fields: Array[String] = Array())(implicit config: Config): Array[ContentPopularityMetrics] = {
	    val periodEnum = periodMap.get(period).get._1;
		val periods = _getPeriods(period);
		val addComments = returnComments(fields);
		val recordsArray = records.map { x => (x.d_period.get, x) };
		val periodsArray = periods.map { period => 
			if(addComments) 
				(period, ContentPopularityMetrics(Option(period), Option(CommonUtil.getPeriodLabel(periodEnum, period)), Option(List())))
			else
				(period, ContentPopularityMetrics(Option(period), Option(CommonUtil.getPeriodLabel(periodEnum, period))))
			};
		periodsArray.map { tup1 =>
            val tmp = recordsArray.filter(tup2 => tup1._1 == tup2._1)
            if (tmp.isEmpty) (tup1._1, (tup1._2, None)) else (tup1._1, (tup1._2, tmp.apply(0)._2))
        }.sortBy(-_._1).map { f => if (None != f._2._2) _merge(f._2._2.asInstanceOf[ContentPopularityViews], f._2._1, addComments) else f._2._1 }
	}

	private def _merge(obj: ContentPopularityViews, dummy: ContentPopularityMetrics, addComments: Boolean): ContentPopularityMetrics = {
        
	    val m_ratings = obj.m_ratings.getOrElse(List()).map{x=> Rating(x._1, x._2)}
	    if (addComments)
        	ContentPopularityMetrics(dummy.d_period, dummy.label, obj.m_comments, obj.m_downloads, obj.m_side_loads, Option(m_ratings), obj.m_avg_rating)
        else
        	ContentPopularityMetrics(dummy.d_period, dummy.label, None, obj.m_downloads, obj.m_side_loads, Option(m_ratings), obj.m_avg_rating)
    }
	
	private def returnComments(fields: Array[String] = Array()) : Boolean = {
		fields.contains("m_comments");
	}
	
	override def reduce(fact1: ContentPopularityMetrics, fact2: ContentPopularityMetrics, fields: Array[String] = Array()): ContentPopularityMetrics = {
		val m_downloads = fact2.m_downloads.getOrElse(0l).asInstanceOf[Number].longValue() + fact1.m_downloads.getOrElse(0l).asInstanceOf[Number].longValue();
		val m_side_loads = fact2.m_side_loads.getOrElse(0l).asInstanceOf[Number].longValue() + fact1.m_side_loads.getOrElse(0l).asInstanceOf[Number].longValue();
		val m_ratings = (fact2.m_ratings.getOrElse(List()) ++ fact1.m_ratings.getOrElse(List())).distinct;
		val m_avg_rating = if (m_ratings.length > 0) {
			val total_rating = m_ratings.map(_.rating).sum;
			if (total_rating > 0) CommonUtil.roundDouble(total_rating/m_ratings.length, 2) else 0.0;
		} else 0.0;
		if(returnComments(fields)) {
			val m_comments = (fact2.m_comments.getOrElse(List()) ++ fact1.m_comments.getOrElse(List())).distinct;
			ContentPopularityMetrics(fact1.d_period, None, Option(m_comments), Option(m_downloads), Option(m_side_loads), Option(m_ratings), Option(m_avg_rating));
		} else {
			ContentPopularityMetrics(fact1.d_period, None, None, Option(m_downloads), Option(m_side_loads), Option(m_ratings), Option(m_avg_rating));
		}
	}
	
	override def getSummary(summary: ContentPopularityMetrics) : ContentPopularityMetrics = {
		ContentPopularityMetrics(None, None, summary.m_comments, summary.m_downloads, summary.m_side_loads, summary.m_ratings, summary.m_avg_rating);
	}
}