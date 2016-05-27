package org.ekstep.analytics.framework

import org.ekstep.analytics.framework.exception.JobNotFoundException
import org.ekstep.analytics.job._

object JobFactory {
    @throws(classOf[JobNotFoundException])
    def getJob(jobType: String): IJob = {
        jobType.toLowerCase() match {
            case "as" =>
                AserScreenSummarizer;
            case "ss" =>
                LearnerSessionSummarizer;
            case "las" =>
                LearnerActivitySummarizer;
            case "lp" =>
                ProficiencyUpdater;
            case "ls" =>
                LearnerSnapshotUpdater;
            case "lcas" =>
                LearnerContentActivityUpdater;
            case "lcr" =>
                RecommendationEngineJob;
            case "cus" =>
                ContentUsageSummarizer
            case "cuu" =>
                ContentUsageUpdaterJob
            case "gus" =>
                GenieUsageSummarizer
            case "dus" =>
                DeviceUsageSummarizer
            case _ =>
                throw new JobNotFoundException("Unknown job type found");
        }
    }
}