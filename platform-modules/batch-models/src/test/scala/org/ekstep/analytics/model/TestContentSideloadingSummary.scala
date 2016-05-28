package org.ekstep.analytics.model

import org.ekstep.analytics.framework.Event
import org.ekstep.analytics.framework.util.JSONUtils
import com.datastax.spark.connector.cql.CassandraConnector
import org.ekstep.analytics.framework.MeasuredEvent

class TestContentSideloadingSummary extends SparkSpec(null) {
    
    "ContentSideloadingSummary" should "generate content sideloading summary events" in {
      
        CassandraConnector(sc.getConf).withSessionDo { session =>
            session.execute("truncate content_db.content_sideloading_summary");
        }  
      
        val rdd = loadFile[Event]("src/test/resources/content-sideloading-summary/test_data_1.log");
        val rdd2 = ContentSideloadingSummary.execute(rdd, None);
        val events = rdd2.collect
        events.length should be (2)
        
        val event1 = JSONUtils.deserialize[MeasuredEvent](events(0));
        
        event1.eid should be("ME_CONTENT_SIDELOADING_SUMMARY");
        event1.mid should be("56C7D87F4E861BD50E97076168607FBD");
        event1.context.pdata.model should be("ContentSideloadingSummary");
        event1.context.pdata.ver should be("1.0");
        event1.context.granularity should be("CUMULATIVE");
        event1.context.date_range should not be null;
        event1.content_id.get should be("org.ekstep.story.en.family")

        val eks = event1.edata.eks.asInstanceOf[Map[String, AnyRef]]
        eks.get("num_downloads").get should be(2)
        eks.get("num_devices").get should be(5)
    }
}