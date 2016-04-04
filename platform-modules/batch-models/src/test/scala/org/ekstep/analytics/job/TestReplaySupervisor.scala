package org.ekstep.analytics.job

import org.ekstep.analytics.framework.JobConfig
import org.ekstep.analytics.framework.Fetcher
import org.ekstep.analytics.framework.Query
import org.ekstep.analytics.framework.Filter
import org.ekstep.analytics.framework.Dispatcher
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.model.BaseSpec

class TestReplaySupervisor extends BaseSpec {

    it should " Run LP from S3 and dispatching to kafka " in {
        val config = JobConfig(Fetcher("s3", None, Option(Array(Query(Option("prod-data-store"), Option("ss/"), None, Option("__endDate__"), Option(0))))), Option(Array(Filter("eid", "EQ", Option("ME_SESSION_SUMMARY")))), None, "org.ekstep.analytics.model.ProficiencyUpdater", Option(Map("alpha" -> 1.0d.asInstanceOf[AnyRef], "beta" -> 1.0d.asInstanceOf[AnyRef])), Option(Array(Dispatcher("console", Map("printEvent" -> Option(false))), Dispatcher("kafka", Map("brokerList" -> "localhost:9092", "topic" -> "replay")))), Option(10), Option("TestReplaySupervisor"), Option(false))
        ReplaySupervisor.main("lp", "2016-02-15", "2016-02-16", JSONUtils.serialize(config));
    }

    it should " Run LAS from s3 data and dispatching to kafka " in {
        val config = JobConfig(Fetcher("s3", None, Option(Array(Query(Option("prod-data-store"), Option("ss/"), None, Option("__endDate__"), Option(6))))), Option(Array(Filter("eid", "EQ", Option("ME_SESSION_SUMMARY")))), None, "org.ekstep.analytics.model.LearnerActivitySummary", None, Option(Array(Dispatcher("console", Map("printEvent" -> Option(false))), Dispatcher("kafka", Map("brokerList" -> "localhost:9092", "topic" -> "replay")))), Option(10), Option("TestReplaySupervisor"), Option(false))
        ReplaySupervisor.main("las", "2016-02-15", "2016-02-22", JSONUtils.serialize(config));
    }

    it should " Run LCAS from s3 data and dispatching to kafka " in {
        val config = JobConfig(Fetcher("s3", None, Option(Array(Query(Option("prod-data-store"), Option("ss/"), None, Option("__endDate__"), Option(6))))), Option(Array(Filter("eid", "EQ", Option("ME_SESSION_SUMMARY")))), None, "org.ekstep.analytics.model.LearnerContentActivitySummary", None, Option(Array(Dispatcher("console", Map("printEvent" -> Option(false))), Dispatcher("kafka", Map("brokerList" -> "localhost:9092", "topic" -> "replay")))), Option(10), Option("TestReplaySupervisor"), Option(false))
        ReplaySupervisor.main("lcas", "2016-02-01", "2016-02-10", JSONUtils.serialize(config));
    }

    it should " Run RE from S3 and dispatching to kafka " in {
        val config = JobConfig(Fetcher("s3", None, Option(Array(Query(Option("prod-data-store"), Option("ss/"), None, Option("__endDate__"), Option(0))))), Option(Array(Filter("eid", "EQ", Option("ME_SESSION_SUMMARY")))), None, "org.ekstep.analytics.model.RecommendationEngine", None, Option(Array(Dispatcher("console", Map("printEvent" -> Option(false))), Dispatcher("kafka", Map("brokerList" -> "localhost:9092", "topic" -> "replay")))), Option(10), Option("TestReplaySupervisor"), Option(false))
        ReplaySupervisor.main("re", "2016-02-15", "2016-02-16", JSONUtils.serialize(config));
    }

    ignore should " Run LP from local file" in {
        val config = JobConfig(Fetcher("local", None, Option(Array(Query(None, None, None, None, None, None, None, None, None, Option("src/test/resources/learner-proficiency/"))))), None, None, "org.ekstep.analytics.model.ProficiencyUpdater", None, Option(Array(Dispatcher("console", Map("printEvent" -> false.asInstanceOf[AnyRef])))), Option(10), Option("TestReplaySupervisor"), Option(false))
        println(JSONUtils.serialize(config))
        ReplaySupervisor.main("lp", "2016-02-22", "2016-02-23", JSONUtils.serialize(config));
    }
    ignore should " Run RE from local file " in {
        val config = JobConfig(Fetcher("local", None, Option(Array(Query(None, None, None, None, None, None, None, None, None, Option("src/test/resources/reco-engine/reco_engine_test.log"))))), None, None, "org.ekstep.analytics.model.RecommendationEngine", None, Option(Array(Dispatcher("console", Map("printEvent" -> false.asInstanceOf[AnyRef])))), Option(10), Option("TestReplaySupervisor"), Option(false))
        ReplaySupervisor.main("re", "2016-02-22", "2016-02-23", JSONUtils.serialize(config))
    }

    ignore should " Run LAS from local file " in {
        val config = JobConfig(Fetcher("local", None, Option(Array(Query(None, None, None, None, None, None, None, None, None, Option("src/test/resources/learner-activity-summary/learner_activity_summary_sample1.log"))))), None, None, "org.ekstep.analytics.model.LearnerActivitySummary", None, Option(Array(Dispatcher("console", Map("printEvent" -> false.asInstanceOf[AnyRef])))), Option(10), Option("TestReplaySupervisor"), Option(false))
        ReplaySupervisor.main("las", "2016-02-22", "2016-02-23", JSONUtils.serialize(config))
    }
}