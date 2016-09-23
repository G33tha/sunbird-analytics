package org.ekstep.analytics.model

import org.ekstep.analytics.framework.MeasuredEvent
import org.ekstep.analytics.framework.OutputDispatcher
import org.ekstep.analytics.framework.Dispatcher
import com.datastax.spark.connector._
import org.ekstep.analytics.framework.util.CommonUtil
import com.datastax.spark.connector.cql.CassandraConnector
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.DerivedEvent

@deprecated
class TestRecommendationEngine extends SparkSpec(null) {

    "RecommendationEngine" should "run the recommendation for a push data to learner db" in {

        val rdd = loadFile[DerivedEvent]("src/test/resources/reco-engine/reco_engine_test.log");
        val rdd2 = RecommendationEngine.execute(rdd, None);
        val result = rdd2.collect();
    }

    it should "compute the recommendation for a learner" in {

        val learner_id = "8f111d6c-b618-4cf4-bb4b-bbf06cf4363a";
        CassandraConnector(sc.getConf).withSessionDo { session =>
            session.execute("DELETE FROM learner_db.learnerconceptrelevance where learner_id = '" + learner_id + "'");

            // deleting from learnercontentactivity table
            session.execute("DELETE FROM learner_db.learnercontentsummary where learner_id = '" + learner_id + "'");
        }

        val rdd = loadFile[DerivedEvent]("src/test/resources/reco-engine/reco_test_data_1.log");
        val rdd2 = RecommendationEngine.execute(rdd, None);
        val result = rdd2.collect();
        val event = result(0);
        event.mid should be ("D5BB7D44D8EC0C3131EF25A677ED40A2")
        event.syncts should be (1454897876605L)

        val lcr = sc.cassandraTable[LearnerConceptRelevance]("learner_db", "learnerconceptrelevance").where("learner_id = ?", learner_id).first();
        val r = lcr.relevance;
        r.size should be > 100;
    }
}