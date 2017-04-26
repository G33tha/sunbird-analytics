package org.ekstep.analytics.job.updater

import org.ekstep.analytics.framework.IJob
import optional.Application
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.updater.UpdateConceptSnapshotDB

object ConceptSnapshotUpdater extends Application with IJob {
  
    implicit val className = "org.ekstep.analytics.job.updater.ConceptSnapshotUpdater"
  
    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        JobDriver.run("batch", config, UpdateConceptSnapshotDB);
     }
}