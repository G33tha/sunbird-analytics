package org.ekstep.analytics.job

import optional.Application
import org.apache.spark.SparkContext
import org.ekstep.analytics.model._
import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.framework.IBatchModel
import org.ekstep.analytics.updater.LearnerContentActivitySummary

object SSJobs extends Application {

    val className = "org.ekstep.analytics.job.SSJobs"
    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        val models = List(LearnerActivitySummary, ContentUsageSummary, LearnerProficiencySummary)
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        JobDriver.run("batch", config, models, className);
    }
}