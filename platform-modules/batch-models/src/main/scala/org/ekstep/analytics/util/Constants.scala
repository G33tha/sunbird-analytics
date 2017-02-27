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
    val LEARNER_PROFILE_TABLE = "learnerprofile";
    val DEVICE_KEY_SPACE_NAME = "device_db";
    val DEVICE_SPECIFICATION_TABLE = "device_specification";
    val DEVICE_USAGE_SUMMARY_TABLE = "device_usage_summary";
    val DEVICE_CONTENT_SUMMARY_FACT = "device_content_summary_fact";
    val DEVICE_RECOS = "device_recos";
    val CONTENT_KEY_SPACE_NAME = "content_db";
    val PLATFORM_KEY_SPACE_NAME = "platform_db";
    val CONTENT_CUMULATIVE_SUMMARY_TABLE = "content_cumulative_summary";
    val CONTENT_CUMULATIVE_METRICS_TABLE = "content_usage_metrics";
    val CONTENT_USAGE_SUMMARY_FACT = "content_usage_summary_fact";
    val CONTENT_POPULARITY_SUMMARY_FACT = "content_popularity_summary_fact";
    val GENIE_LAUNCH_SUMMARY_FACT = "genie_launch_summary_fact";
    val ITEM_USAGE_SUMMARY_FACT = "item_usage_summary_fact";
    val CONTENT_SIDELOADING_SUMMARY = "content_sideloading_summary";
    val CONTENT_TO_VEC = "content_to_vector";
    val RECOMMENDATION_CONFIG = "recommendation_config";
    val JOB_REQUEST = "job_request";
    val CONTENT_RECOS = "content_recos";
    val INFLUX_DB_NAME = AppConf.getConfig("reactiveinflux.db.name")
    val LOCAL_HOST = AppConf.getConfig("reactiveinflux.host")
    val INFLUX_DB_PORT = AppConf.getConfig("reactiveinflux.port")
    val WEEK_PERIODS = AppConf.getConfig("weekperiods")
    val RETENTION_POLICY_NAME = AppConf.getConfig("retention_policy_name")
    val RETENTION_POLICY_DURATION = AppConf.getConfig("retention_policy_duration")
    val RETENTION_POLICY_REPLICATION = AppConf.getConfig("retention_policy_replication")
    val RETENTION_POLICY_BOOLEAN = AppConf.getConfig("retention_policy_boolean")
    val ALTER_RETENTION_POLICY = AppConf.getConfig("alter_retention_policy")
    val REGISTERED_TAGS = "registered_tags";
    
    val LP_URL = AppConf.getConfig("lp.url");
    val SEARCH_SERVICE_URL = AppConf.getConfig("service.search.url");

    def getContentList(): String = {
        s"$LP_URL/v2/analytics/content/list";
    }
    
    def getContent(contentId: String): String = {
        s"$LP_URL/v2/content/" + URLEncoder.encode(contentId, "UTF-8");
    }
    
    def getDomainMap(): String = {
        s"$LP_URL/v2/analytics/domain/map";
    }
    
    def getContentSearch(): String = {
        s"$SEARCH_SERVICE_URL/v2/search";
    }
    
    def getContentItems(apiVersion: String, contentId: String): String = {
        s"$LP_URL/$apiVersion/analytics/items/" + URLEncoder.encode(contentId, "UTF-8");
    }

    def getItemConcept(version: String, contentId: String, itemId: String): String = {
        s"$LP_URL/$version/analytics/item/$contentId/$itemId";
    }
    
    def getContentUpdateAPIUrl(contentId: String): String = {
        s"$LP_URL/v2/content/$contentId";
    }
}