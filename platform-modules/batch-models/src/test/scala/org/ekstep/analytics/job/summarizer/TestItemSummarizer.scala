package org.ekstep.analytics.job.summarizer

import org.ekstep.analytics.framework.JobConfig
import org.ekstep.analytics.framework.Fetcher
import org.ekstep.analytics.framework.Query
import org.ekstep.analytics.framework.Dispatcher
import org.ekstep.analytics.model.SparkSpec
import org.ekstep.analytics.framework.util.JSONUtils

class TestItemSummarizer extends SparkSpec(null) {
    
      "ItemSummarizer" should "execute ItemSummary job and won't throw any Exception" in {
        val config = JobConfig(Fetcher("local", None, Option(Array(Query(None, None, None, None, None, None, None, None, None, Option("src/test/resources/item-summary/test-data.log"))))), null, null, "org.ekstep.analytics.model.ItemSummary", None, Option(Array(Dispatcher("console", Map("printEvent" -> false.asInstanceOf[AnyRef])))), Option(10), Option("TestItemSummarizer"), Option(true))
        ItemSummarizer.main(JSONUtils.serialize(config))(Option(sc));
    }
}