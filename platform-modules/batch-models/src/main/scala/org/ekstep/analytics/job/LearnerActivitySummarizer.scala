package org.ekstep.analytics.job

import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.model.LearnerActivitySummary
import org.ekstep.analytics.framework.MeasuredEvent
import org.apache.spark.SparkContext
import org.apache.log4j.Logger
import org.ekstep.analytics.framework.util.JobLogger
import org.ekstep.analytics.framework.IJob

/**
 * @author Santhosh
 */
object LearnerActivitySummarizer extends optional.Application with IJob {

    val className = "org.ekstep.analytics.job.LearnerActivitySummarizer"
  
    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        JobLogger.debug("Started executing Job", className)
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        //JobDriver.run[MeasuredEvent]("batch", config, LearnerActivitySummary);
        JobLogger.debug("Job Completed.", className)
    }

}