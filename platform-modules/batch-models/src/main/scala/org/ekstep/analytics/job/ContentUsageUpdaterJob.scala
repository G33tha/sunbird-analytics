package org.ekstep.analytics.job

import org.ekstep.analytics.framework.IJob
import optional.Application
import org.ekstep.analytics.framework.MeasuredEvent
import org.ekstep.analytics.updater.ContentUsageUpdater
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.framework.util.JobLogger

object ContentUsageUpdaterJob extends Application with IJob {
    
    val className = "org.ekstep.analytics.job.ContentUsageUpdaterJob"
  
    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        JobLogger.debug("Started executing Job", className)
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        JobDriver.run[MeasuredEvent]("batch", config, ContentUsageUpdater);
        JobLogger.debug("Job Completed.", className)
    }
}