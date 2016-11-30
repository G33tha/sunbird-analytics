package org.ekstep.analytics.api.service

import org.ekstep.analytics.api.MetricsRequestBody
import org.ekstep.analytics.api.MetricsResponse
import org.ekstep.analytics.api.Result
import org.ekstep.analytics.api.SparkSpec
import org.ekstep.analytics.api.util.ContentCacheUtil
import org.ekstep.analytics.api.util.JSONUtils
import org.joda.time.DateTimeUtils
import com.typesafe.config.ConfigFactory

class TestTagAggregation extends SparkSpec {

    implicit val config = ConfigFactory.load();
    override def beforeAll() {
        super.beforeAll()
        DateTimeUtils.setCurrentMillisFixed(1474963510000L); // Fix the date-time to be returned by DateTime.now() to 20160927
        ContentCacheUtil.initCache()(sc, config);
    }

    private def getContentUsageMetrics(request: String): MetricsResponse = {
        val result = MetricsAPIService.contentUsage(JSONUtils.deserialize[MetricsRequestBody](request))
        JSONUtils.deserialize[MetricsResponse](result);
    }

    private def getGenieLaunchMetrics(request: String): MetricsResponse = {
        val result = MetricsAPIService.genieLaunch(JSONUtils.deserialize[MetricsRequestBody](request));
        JSONUtils.deserialize[MetricsResponse](result);
    }

    private def checkContentUsageMetricsMultipleTags(metric: Map[String, AnyRef]) {
        metric.get("m_total_ts") should be(Some(979.0));
        metric.get("m_total_sessions") should be(Some(33));
        metric.get("m_avg_ts_session") should be(Some(29.67));
        metric.get("m_total_interactions") should be(Some(594));
        metric.get("m_avg_interactions_min") should be(Some(36.4));
        metric.get("m_total_devices") should be(Some(54));
        metric.get("m_avg_sess_device") should be(Some(0.61));
    }

    private def checkContentUsageMetrics(metric: Map[String, AnyRef]) {
        metric.get("m_total_ts") should be(Some(490.0));
        metric.get("m_total_sessions") should be(Some(17));
        metric.get("m_avg_ts_session") should be(Some(114.0));
        metric.get("m_total_interactions") should be(Some(297));
        metric.get("m_avg_interactions_min") should be(Some(29.0));
        metric.get("m_total_devices") should be(Some(27));
        metric.get("m_avg_sess_device") should be(Some(1.0));
    }

    private def checkGenieLaunchMetricsMultipleTags(metric: Map[String, AnyRef]) {
        metric.get("m_total_ts") should be(Some(1976.0));
        metric.get("m_total_sessions") should be(Some(72));
        metric.get("m_avg_ts_session") should be(Some(27.44));
    }

    private def checkGenieLaunchMetrics(metric: Map[String, AnyRef]) {
        metric.get("m_total_ts") should be(Some(0.0));
        metric.get("m_total_sessions") should be(Some(0));
        metric.get("m_avg_ts_session") should be(Some(0.0));

    }

    private def checkContentUsageSummary(metric: Map[String, AnyRef]) {
        metric.get("m_total_sessions") should be(Some(86));
        metric.get("m_avg_ts_session") should be(Some(40.58));
        metric.get("m_total_interactions") should be(Some(1674));
        metric.get("m_avg_interactions_min") should be(Some(28.78));
        metric.get("m_total_devices") should be(Some(96));
    }

    private def checkContentUsageSummaryMultipleTags(metric: Map[String, AnyRef]) {
        metric.get("m_total_sessions") should be(Some(167));
        metric.get("m_avg_ts_session") should be(Some(41.76));
        metric.get("m_total_interactions") should be(Some(3348));
        metric.get("m_avg_interactions_min") should be(Some(28.8));
        metric.get("m_total_devices") should be(Some(192));
    }

    private def genieLaunchSummary(metric: Map[String, AnyRef]) {
        metric.get("m_total_sessions") should be(Some(84));
        metric.get("m_avg_ts_session") should be(Some(45.74));
        metric.get("m_total_devices") should be(Some(107));
    }

    private def genieLaunchSummaryMultipleTags(metric: Map[String, AnyRef]) {
        metric.get("m_total_sessions") should be(Some(120));
        metric.get("m_avg_ts_session") should be(Some(40.25));
        metric.get("m_total_devices") should be(Some(121));
    }

    "Content usage service" should "return 7 days data present for multiple tags" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tags":["1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacc"]}}}""";
        val response = getContentUsageMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
        checkContentUsageMetricsMultipleTags(response.result.metrics(1));
        checkContentUsageSummaryMultipleTags(response.result.summary)
    }

    it should "return 5 weeks data present for multiple tags" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_5_WEEKS","filter":{"tags":["1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"]}}}""";
        val response = getContentUsageMetrics(request);
        response.result.metrics.length should be(5);
        response.result.summary should not be empty;
    }

    it should "return last 12 months metrics when, 12 months data present for multiple tags" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_12_MONTHS","filter":{"tags":["1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"]}}}""";
        val response = getContentUsageMetrics(request);
        response.result.metrics.length should be(12);
        response.result.summary should not be empty;
    }

    it should "return unique tags results, when duplicate tags are passed in tags" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_12_MONTHS","filter":{"tags":["1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"]}}}""";
        val response = getContentUsageMetrics(request);
        response.result.metrics.length should be(12);
    }

    it should "return tags results, when both tag and tags information is provided" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bac","tags":["1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"]}}}""";
        val response = getContentUsageMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
        checkContentUsageMetrics(response.result.metrics(1));
        checkContentUsageSummary(response.result.summary)
    }

    "Genie Lanuch service" should "return 7 days data present for multiple tags" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tags":["1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1475b1d70a66a0f2c22dd1096b98030cb7d9bacb"]}}}""";
        val response = getGenieLaunchMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
        checkGenieLaunchMetricsMultipleTags(response.result.metrics(0))
        genieLaunchSummaryMultipleTags(response.result.summary)
    }

    it should "return 5 weeks data present for multiple tags" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_5_WEEKS","filter":{"tags":["1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"]}}}""";
        val response = getGenieLaunchMetrics(request);
        response.result.metrics.length should be(5);
        response.result.summary should not be empty;
        checkGenieLaunchMetrics(response.result.metrics(0))
    }

    it should "return last 12 months metrics when, 12 months data present for multiple tags" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_12_MONTHS","filter":{"tags":["1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"]}}}""";
        val response = getGenieLaunchMetrics(request);
        response.result.metrics.length should be(12);
        response.result.summary should not be empty;
    }

    it should "return unique tags results, when duplicate tags are passed in tags" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_12_MONTHS","filter":{"tags":["1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"]}}}""";
        val response = getGenieLaunchMetrics(request);
        response.result.metrics.length should be(12);
    }

    it should "return tags results, when both tag and tags information is provided" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1475b1d70a66a0f2c22dd1096b98030cb7d9bacb","tags":["1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"]}}}""";
        val response = getGenieLaunchMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
        genieLaunchSummary(response.result.summary)
    }

}