package org.ekstep.analytics.util

import org.ekstep.analytics.framework.conf.AppConf
import java.net.URLEncoder

object Constants {
    
    val KEY_SPACE_NAME = "learner_db";
    val LEARNER_SNAPSHOT_TABLE = "learnersnapshot";
    val LEARNER_PROFICIENCY_TABLE = "learnerproficiency";
    val LEARNER_CONTENT_SUMMARY_TABLE = "learnercontentsummary";
    val LEARNER_CONCEPT_RELEVANCE_TABLE = "learnerconceptrelevance";
    val CONCEPT_SIMILARITY_TABLE = "conceptsimilaritymatrix";
    val DEVICE_SPECIFICATION_TABLE = "devicespecification";
    val DEVICE_USAGE_SUMMARY_TABLE = "device_usage_summary"
    val CONTENT_KEY_SPACE_NAME = "content_db";
    val CONTENT_CUMULATIVE_SUMMARY_TABLE = "content_cumulative_summary";
    val CONTENT_CUMULATIVE_METRICS_TABLE = "content_usage_metrics";
    val LEARNER_PROFILE_TABLE = "learnerprofile";
    val CONTENT_USAGE_SUMMARY_FACT = "content_usage_summary_fact";
    val CONTENT_SIDELOADING_SUMMARY = "content_sideloading_summary";
    val LP_URL = AppConf.getConfig("lp.url");

    def getContentAPIUrl(contentId: String): String = {
        s"$LP_URL/learning-service/v2/content/$contentId";
    }

    def getGameList(): String = {
        s"$LP_URL/learning-service/v1/game/list";
    }
    
    def getContentList(): String = {
        s"$LP_URL/learning-service/v2/analytics/content/list";
    }
    
    def getDomainMap(): String = {
        s"$LP_URL/learning-service/v2/analytics/domain/map";
    }
    
    def getContentItems(apiVersion: String, contentId: String): String = {
        s"$LP_URL/learning-service/$apiVersion/analytics/items/" + URLEncoder.encode(contentId, "UTF-8");
    }

    def getItemConcept(version: String, contentId: String, itemId: String): String = {
        s"$LP_URL/learning-service/$version/analytics/item/$contentId/$itemId";
    }
    
    def getQuestionnaireAPIUrl(questionnaireId: String, subject: String): String = {
        s"$LP_URL/learning-service/v1/questionnaire/$questionnaireId?taxonomyId=$subject";
    }

    def getItemAPIUrl(itemId: String, subject: String): String = {
        s"$LP_URL/learning-service/v1/assessmentitem/$itemId?taxonomyId=$subject";
    }

    def getSearchItemAPIUrl(subject: String): String = {
        s"$LP_URL/learning-service/v1/assessmentitem/search?taxonomyId=$subject";
    }

    def getItemSetAPIUrl(itemSetId: String, subject: String): String = {
        s"$LP_URL/learning-service/v1/assessmentitemset/$itemSetId?taxonomyId=$subject";
    }
    
    def getContentUpdateAPIUrl(contentId: String): String = {
        s"$LP_URL/learning-service/v2/content/$contentId";
    }
}