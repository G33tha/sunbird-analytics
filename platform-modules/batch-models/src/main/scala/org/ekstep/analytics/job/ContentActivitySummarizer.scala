package org.ekstep.analytics.job

import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.framework.MeasuredEvent
import org.ekstep.analytics.model.ContentActivitySummary
import org.ekstep.analytics.framework.util.JobLogger

object ContentActivitySummarizer extends optional.Application {

    val className = "org.ekstep.analytics.job.ContentActivitySummarizer"
  
    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        JobLogger.debug("Started executing Job", className)
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        JobDriver.run[MeasuredEvent]("batch", config, ContentActivitySummary);
        JobLogger.debug("Job Completed.", className)
    }
}