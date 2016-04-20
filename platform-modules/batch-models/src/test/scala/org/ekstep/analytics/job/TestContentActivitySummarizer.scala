package org.ekstep.analytics.job

import org.ekstep.analytics.model.SparkSpec
import org.ekstep.analytics.framework.JobConfig
import org.ekstep.analytics.framework.Fetcher
import org.ekstep.analytics.framework.Query
import org.ekstep.analytics.framework.Dispatcher
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.Filter

class TestContentActivitySummarizer extends SparkSpec(null) {
  
  "ContentActivitySummarizer" should "execute the job and shouldn't throw any exception" in {
        val config = JobConfig(Fetcher("local", None, Option(Array(Query(None, None, None, None, None, None, None, None, None, Option("src/test/resources/content-summary/test_data_1.log"))))), None, None, "org.ekstep.analytics.model.ContentActivitySummary", None, Option(Array(Dispatcher("console", Map("printEvent" -> false.asInstanceOf[AnyRef])))), Option(10), Option("TestContentActivitySummarizer"), Option(false))
        ContentActivitySummarizer.main(JSONUtils.serialize(config))(Option(sc));
    }
    
    ignore should "execute the job from s3 data" in {
        val config = JobConfig(Fetcher("s3", None, Option(Array(Query(Option("sandbox-data-store"), Option("ss/"), Option("2016-01-01"), Option("2016-04-14"))))), Option(Array(Filter("eventId","EQ",Option("ME_SESSION_SUMMARY")))), None, "org.ekstep.analytics.model.ContentActivitySummary", None, Option(Array(Dispatcher("file", Map("file" -> "content-data.log/")))), Option(10), Option("TestContentActivitySummarizer"), Option(false))
        ContentActivitySummarizer.main(JSONUtils.serialize(config))(Option(sc));
    }
}