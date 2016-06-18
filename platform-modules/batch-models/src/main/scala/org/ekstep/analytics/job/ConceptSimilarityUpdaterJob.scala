package org.ekstep.analytics.job

import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.updater.ConceptSimilarityUpdater
import org.ekstep.analytics.updater.ConceptSimilarityEntity
import org.apache.spark.SparkContext
import org.apache.log4j.Logger
import org.ekstep.analytics.framework.util.JobLogger

object ConceptSimilarityUpdaterJob extends optional.Application {

    val className = "org.ekstep.analytics.job.ConceptSimilarityUpdaterJob"
    
    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        JobLogger.debug("Started executing Job", className)
        JobDriver.run("batch", config, ConceptSimilarityUpdater);
        JobLogger.debug("Job completed.", className)
    }
}