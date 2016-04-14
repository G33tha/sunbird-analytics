package org.ekstep.analytics.job

import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.model.LearnerActivitySummary
import org.ekstep.analytics.framework.MeasuredEvent
import org.apache.spark.SparkContext
import org.apache.log4j.Logger
import org.ekstep.analytics.framework.util.JobLogger

/**
 * @author Santhosh
 */
object LearnerActivitySummarizer extends optional.Application {
    val logger = Logger.getLogger(JobLogger.jobName)
    logger.setLevel(JobLogger.level)
    val className = this.getClass.getName

    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        JobLogger.info(logger, "Started executing LearnerActivitySummarizer Job", className)
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        JobDriver.run[MeasuredEvent]("batch", config, LearnerActivitySummary);
        JobLogger.info(logger, "LearnerActivitySummarizer Job completed....", className)
    }

}