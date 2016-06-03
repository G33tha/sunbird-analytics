package org.ekstep.analytics.job

import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.model.DeviceSpecification
import org.ekstep.analytics.framework.Event
import org.apache.spark.SparkContext
import optional.Application
import org.ekstep.analytics.framework.util.JobLogger

object DeviceSpecificationUpdater extends Application {
  
    val className = "org.ekstep.analytics.job.DeviceSpecificationUpdater"
  
    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        JobLogger.debug("Started executin Job", className)
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        JobDriver.run[Event]("batch", config, DeviceSpecification);
        JobLogger.debug("Job Completed.", className)
    }
}