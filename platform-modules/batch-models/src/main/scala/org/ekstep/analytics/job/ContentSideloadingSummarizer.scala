package org.ekstep.analytics.job

import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.framework.Event
import org.ekstep.analytics.model.ContentSideloadingSummary
import org.ekstep.analytics.framework.IJob
import org.ekstep.analytics.framework.util.JobLogger

object ContentSideloadingSummarizer extends optional.Application with IJob {
  
    val className = "org.ekstep.analytics.job.ContentSideloadingSummarizer"
  
    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        JobLogger.log("Started executing Job", className, None, None, None)
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        JobDriver.run("batch", config, ContentSideloadingSummary);
        JobLogger.log("Job Completed", className, None, None, None)
    }
}