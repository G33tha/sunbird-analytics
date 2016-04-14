package org.ekstep.analytics.job

import org.ekstep.analytics.framework.MeasuredEvent
import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.model.LearnerProficiencySummary
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.util.JobLogger
import org.apache.log4j.Logger

object ProficiencyUpdater extends optional.Application {
    
    val logger = Logger.getLogger(JobLogger.jobName)
    logger.setLevel(JobLogger.level)
    val className= this.getClass.getName
    
    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        JobLogger.info(logger, "Started executing ProficiencyUpdater Job",className)
        JobDriver.run[MeasuredEvent]("batch", config, LearnerProficiencySummary);
        JobLogger.info(logger, "ProficiencyUpdater Job completed",className)
    }
}