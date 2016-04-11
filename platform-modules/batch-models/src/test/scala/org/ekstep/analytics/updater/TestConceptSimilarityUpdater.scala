package org.ekstep.analytics.updater

import org.ekstep.analytics.model.SparkSpec
import com.datastax.spark.connector._

class TestConceptSimilarityUpdater extends SparkSpec(null) {

    "ConceptSimilarityUpdater" should "write concept similarity data to db" in {
        val rdd = loadFile[ConceptSimilarityEntity]("src/test/resources/concept-similarity/ConceptSimilarity.json");
        ConceptSimilarityUpdater.execute(rdd, Option(Map("modelVersion" -> "1.0", "modelId" -> "ConceptSimilarityUpdater")));
        val rowRDD = sc.cassandraTable[ConceptSimilarity]("learner_db", "conceptsimilaritymatrix");
        rowRDD.count() should not be (0);
    }
}