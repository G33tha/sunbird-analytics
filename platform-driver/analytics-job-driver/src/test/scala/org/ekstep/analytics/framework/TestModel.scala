package org.ekstep.analytics.framework

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.framework.util.JSONUtils

/**
 * @author Santhosh
 */
class TestModel extends IBatchModel with Serializable {
    
    def execute(sc: SparkContext, events: RDD[Event], jobParams: Option[Map[String, AnyRef]]): RDD[String] = {
        events.map { x => JSONUtils.serialize(x) };
    }
  
}