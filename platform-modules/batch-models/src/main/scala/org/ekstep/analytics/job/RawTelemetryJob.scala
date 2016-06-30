package org.ekstep.analytics.job

import optional.Application
import org.apache.spark.SparkContext
import org.ekstep.analytics.model._
import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.framework.IBatchModel

object RawTelemetryJob extends Application {

    val className = "org.ekstep.analytics.job.RawTelemetryJob"

    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        val models = List(LearnerSessionSummary, AserScreenSummary, GenieLaunchSummary, GenieUsageSessionSummary, ContentSideloadingSummary)
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        JobDriver.run("batch", config, models, className);
    }
}