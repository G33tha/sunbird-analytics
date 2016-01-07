package org.ekstep.analytics.framework

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.framework.util.CommonUtil
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods
import org.json4s.jvalue2extractable
import org.json4s.string2JsonInput
import org.scalatest.BeforeAndAfterAll
import com.fasterxml.jackson.core.JsonParseException
import org.ekstep.analytics.framework.util.JSONUtils

/**
 * @author Santhosh
 */
class SparkSpec(val file: String =  "src/test/resources/sample_telemetry.log") extends BaseSpec with BeforeAndAfterAll {
    
    var events: RDD[Event] = null;
    var sc: SparkContext = null;
    
    override def beforeAll() {
        sc = CommonUtil.getSparkContext(1, "TestAnalyticsCore");
        events = loadFile(file)
    }
    
    override def afterAll() {
        CommonUtil.closeSparkContext(sc);
    }
    
    def loadFile(file: String): RDD[Event] = {
        if(file == null) {
            return null;
        }
        sc.textFile(file, 1).map { line => JSONUtils.deserialize[Event](line)}.filter { x => x != null }.cache();
    }
  
}