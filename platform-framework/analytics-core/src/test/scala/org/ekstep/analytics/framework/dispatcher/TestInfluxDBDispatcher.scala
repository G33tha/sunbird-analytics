package org.ekstep.analytics.framework.dispatcher

import org.ekstep.analytics.framework.dispatcher.InfluxDBDispatcher.InfluxRecord
import org.ekstep.analytics.framework.SparkSpec

class TestInfluxDBDispatcher extends SparkSpec {
  
  ignore should "send output to InfluxDB" in {
        
        val records = sc.parallelize(Seq(InfluxRecord(Map("id" -> "tag1"), Map("name" -> "value1"))))
        val events = InfluxDBDispatcher.dispatch("test_influx_dispatcher", records);
  }
}