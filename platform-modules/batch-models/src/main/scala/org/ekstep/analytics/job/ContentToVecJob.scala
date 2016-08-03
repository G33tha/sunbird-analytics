package org.ekstep.analytics.job

import org.ekstep.analytics.framework.IJob
import optional.Application
import org.apache.spark.SparkContext
import org.ekstep.analytics.model.ContentToVec
import org.ekstep.analytics.framework.JobDriver

object ContentToVecJob extends Application with IJob {

    implicit val className = "org.ekstep.analytics.job.ContentToVecJob"

    def main(config: String)(implicit sc: Option[SparkContext] = None) {
        implicit val sparkContext: SparkContext = sc.getOrElse(null);
        JobDriver.run("batch", config, ContentToVec);
    }

}