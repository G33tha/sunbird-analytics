package org.ekstep.analytics.api.service

import org.ekstep.analytics.api.SparkSpec
import org.ekstep.analytics.api.util.JSONUtils
import org.ekstep.analytics.api.MetricsRequestBody
import org.ekstep.analytics.api.MetricsResponse
import org.ekstep.analytics.api.Response
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTimeUtils
import org.ekstep.analytics.api.Result
import org.ekstep.analytics.api.util.ContentCacheUtil

class TestMetricsAPIService extends SparkSpec {
    
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

    private def getContentPopularityMetrics(request: String): MetricsResponse = {
        val result = MetricsAPIService.contentPopularity(JSONUtils.deserialize[MetricsRequestBody](request), Array("m_comments"));
        JSONUtils.deserialize[MetricsResponse](result);
    }

    private def getItemUsageMetrics(request: String): MetricsResponse = {
        val result = MetricsAPIService.itemUsage(JSONUtils.deserialize[MetricsRequestBody](request));
        JSONUtils.deserialize[MetricsResponse](result);
    }

    private def getGenieLaunchMetrics(request: String): MetricsResponse = {
        val result = MetricsAPIService.genieLaunch(JSONUtils.deserialize[MetricsRequestBody](request));
        JSONUtils.deserialize[MetricsResponse](result);
    }

    private def getContentUsageListMetrics(request: String): MetricsResponse = {
        val result = MetricsAPIService.contentList(JSONUtils.deserialize[MetricsRequestBody](request));
        JSONUtils.deserialize[MetricsResponse](result);
    }

    private def checkCUMetricsEmpty(metric: Map[String, AnyRef]) {
        metric.get("m_total_ts") should be(Some(0.0));
        metric.get("m_total_sessions") should be(Some(0.0));
        metric.get("m_avg_ts_session") should be(Some(0.0));
        metric.get("m_total_interactions") should be(Some(0.0));
        metric.get("m_avg_interactions_min") should be(Some(0.0));
        metric.get("m_total_devices") should be(Some(0.0));
        metric.get("m_avg_sess_device") should be(Some(0.0));
    }

    private def checkCPMetricsEmpty(metric: Map[String, AnyRef]) {
        metric.get("m_downloads") should be(Some(0.0));
        metric.get("m_side_loads") should be(Some(0.0));
        metric.get("m_ratings") should be(Some(List()));
        metric.get("m_avg_rating") should be(Some(0.0));
    }

    private def checkGLMetricsEmpty(metric: Map[String, AnyRef]) {
        metric.get("m_total_sessions") should be(Some(0.0));
        metric.get("m_total_ts") should be(Some(0.0));
        metric.get("m_total_devices") should be(Some(0.0));
        metric.get("m_avg_sess_device") should be(Some(0.0));
        metric.get("m_avg_ts_session") should be(Some(0.0));
    }

    private def checkItemUsageMetrics(metric: Map[String, AnyRef]) {
        metric.get("m_top5_incorrect_res") should be(Some(List()));
        metric.get("m_inc_res_count").get should be(1);
        metric.get("m_top5_mmc") should be(Some(List()));

    }

    private def checkItemUsageSummary(summary: Map[String, AnyRef]) {
        summary.get("m_total_count").get should be(6);
        summary.get("m_inc_res_count").get should be(6);
        summary.get("m_top5_mmc") should be(Some(List()));

    }

    "ContentUsageMetricsAPIService" should "return empty result when, no pre-computed tag summary data is there in S3 location" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"4f04da60-1e24-4d31-aa7b-1daf91c46341","content_id":"do_435543"}}}""";
        val response = getContentUsageMetrics(request);
        response.result.summary should be(Map("m_total_interactions" -> 0, "m_avg_interactions_min" -> 0.0, "m_avg_ts_session" -> 0.0, "m_total_sessions" -> 0, "m_total_devices" -> 0, "m_total_ts" -> 0.0, "m_avg_sess_device" -> 0.0))
    }
    
    it should "return error response on invalid request" in {
    	val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{}}""";
    	the[Exception] thrownBy {
    		MetricsAPIService.contentUsage(JSONUtils.deserialize[MetricsRequestBody](request));
        } should have message "period is missing or invalid."
    }

    it should "return one day metrics of last 7days when, only one day pre-computed tag summary data is there in S3 location for last 7days" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_1day"}}}""";
        val response = getContentUsageMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
        checkCUMetricsEmpty(response.result.metrics(1));
        checkCUMetricsEmpty(response.result.metrics(2));
        checkCUMetricsEmpty(response.result.metrics(3));
        checkCUMetricsEmpty(response.result.metrics(4));
        checkCUMetricsEmpty(response.result.metrics(5));
        checkCUMetricsEmpty(response.result.metrics(6));

    }

    it should "return tag metrics when, last 7 days data present for tag1, tag2, tag3 in S3 & API inputs (tag: tag1)" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"}}}""";
        val response = getContentUsageMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
    }

    it should "return last 5 weeks metrics when, 5 weeks data present" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_5_WEEKS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"}}}""";
        val response = getContentUsageMetrics(request);
        response.result.metrics.length should be(5);
        response.result.summary should not be empty;
    }

    it should "return last 12 months metrics when, 12 months data present" in {
        val request = """{"id":"ekstep.analytics.metrics.content-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_12_MONTHS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"}}}""";
        val response = getContentUsageMetrics(request);
        response.result.metrics.length should be(12);
        response.result.summary should not be empty;
    }

    "ContentPopularityMetricsAPIService" should "return empty result when, no pre-computed tag summary data is there in S3 location" in {
        val request = """{"id":"ekstep.analytics.metrics.content-popularity","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"4f04da60-1e24-4d31-aa7b-1daf91c46341","content_id":"do_435543"}}}""";
        val response = getContentPopularityMetrics(request);
        val summary = response.result.summary;
		summary should be (Map("m_side_loads"-> 0,"m_avg_rating"-> 0.0,"m_downloads"-> 0,"m_ratings"-> List(),"m_comments"->List()))
    }
    
    it should "return error response on invalid request (without period)" in {
    	val request = """{"id":"ekstep.analytics.metrics.content-popularity","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{}}""";
    	the[Exception] thrownBy {
    		MetricsAPIService.contentPopularity(JSONUtils.deserialize[MetricsRequestBody](request), Array());
        } should have message "period is missing or invalid."
    }
    
    it should "return error response on invalid request (without content_id)" in {
    	val request = """{"id":"ekstep.analytics.metrics.content-popularity","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"4f04da60-1e24-4d31-aa7b-1daf91c46341"}}}""";
    	the[Exception] thrownBy {
    		MetricsAPIService.contentPopularity(JSONUtils.deserialize[MetricsRequestBody](request), Array());
        } should have message "filter.content_id is missing."
    }

    it should "return one day metrics of last 7days when, only one day pre-computed tag summary data is there in S3 location for last 7days" in {
        val request = """{"id":"ekstep.analytics.metrics.content-popularity","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_1day"}}}""";
        val response = getContentPopularityMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
        checkCPMetricsEmpty(response.result.metrics(0));
        checkCPMetricsEmpty(response.result.metrics(2));
        checkCPMetricsEmpty(response.result.metrics(3));
        checkCPMetricsEmpty(response.result.metrics(4));
        checkCPMetricsEmpty(response.result.metrics(5));
        checkCPMetricsEmpty(response.result.metrics(6));
    }

    it should "return tag metrics when, last 7 days data present for tag1, tag2, tag3 in S3 & API inputs (tag: tag1)" in {
        val request = """{"id":"ekstep.analytics.metrics.content-popularity","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_324353"}}}""";
        val response = getContentPopularityMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
    }

    it should "return last 5 weeks metrics when, when 5 weeks data present" in {
        val request = """{"id":"ekstep.analytics.metrics.content-popularity","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_5_WEEKS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_324353"}}}""";
        val response = getContentPopularityMetrics(request);
        response.result.metrics.length should be(5);
        response.result.summary should not be empty;
    }

    it should "return last 12 months metrics when, when 12 months data present" in {
        val request = """{"id":"ekstep.analytics.metrics.content-popularity","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_12_MONTHS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_324353"}}}""";
        val response = getContentPopularityMetrics(request);
        response.result.metrics.length should be(12);
        response.result.summary should not be empty;
    }

    "ItemUsageMetricsAPIService" should "return empty result when, no pre-computed tag summary data is there in S3 location" in {
        val request = """{"id":"ekstep.analytics.metrics.item-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"4f04da60-1e24-4d31-aa7b-1daf91c46341","content_id":"do_435543"}}}""";
        val response = getItemUsageMetrics(request);
        response.result.summary should be (Map("items" -> List()));
    }
    
    it should "return error response on invalid request(without period)" in {
    	val request = """{"id":"ekstep.analytics.metrics.item-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{}}""";
    	the[Exception] thrownBy {
    		MetricsAPIService.itemUsage(JSONUtils.deserialize[MetricsRequestBody](request));
        } should have message "period is missing or invalid."
    }
    
    it should "return error response on invalid request(without content_id)" in {
    	val request = """{"id":"ekstep.analytics.metrics.item-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"4f04da60-1e24-4d31-aa7b-1daf91c46341"}}}""";
    	the[Exception] thrownBy {
    		MetricsAPIService.itemUsage(JSONUtils.deserialize[MetricsRequestBody](request));
        } should have message "filter.content_id is missing."
    }

    it should "return one day metrics of last 7days when, only one day pre-computed tag summary data is there in S3 location for last 7days" in {
        val request = """{"id":"ekstep.analytics.metrics.item-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_1day"}}}""";
        val response = getItemUsageMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
        response.result.metrics(0).get("items") should be(Some(List()));
        response.result.metrics(2).get("items") should be(Some(List()));
        response.result.metrics(3).get("items") should be(Some(List()));
        response.result.metrics(4).get("items") should be(Some(List()));
        response.result.metrics(5).get("items") should be(Some(List()));
        response.result.metrics(6).get("items") should be(Some(List()));
    }

    it should "return tag metrics when, last 7 days data present for tag1, tag2, tag3 in S3 & API inputs (tag: tag1)" in {
        val request = """{"id":"ekstep.analytics.metrics.item-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_324353"}}}""";
        val response = getItemUsageMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
    }

    it should "return metrics of Item usage metrics for last 7 days data if values present in input data " in {
        val request = """{"id":"ekstep.analytics.metrics.item-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_324353","d_item_id":"pq.div.a36"}}}""";
        val response = getItemUsageMetrics(request);
        val metrics = response.result.metrics(0).get("items").get.asInstanceOf[List[Map[String, AnyRef]]]
        checkItemUsageMetrics(metrics(0))
    }

    it should "return summary of Item usage metrics for last 7 days data if values present in input data " in {
        val request = """{"id":"ekstep.analytics.metrics.item-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_324353","d_item_id":"pq.div.a36"}}}""";
        val response = getItemUsageMetrics(request);
        val summary = response.result.summary.get("items").get.asInstanceOf[List[Map[String, AnyRef]]]
        checkItemUsageSummary(summary(0))
    }

    it should "return summary of top5_mmc metrics for last 7 days data if values present in input data " in {
        val request = """{"id":"ekstep.analytics.metrics.item-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_324353","d_item_id":"pq.div.a36"}}}""";
        val response = getItemUsageMetrics(request);
        val summary = response.result.summary.get("items").get.asInstanceOf[List[Map[String, AnyRef]]]
        val top5_mmc = summary(14).get("m_top5_mmc").get.asInstanceOf[List[Map[String, AnyRef]]]
        top5_mmc(0).get("resp").get should be("m1")
        top5_mmc(0).get("count").get should be(10)
    }

    it should "return last 5 weeks metrics when, when 5 weeks data present" in {
        val request = """{"id":"ekstep.analytics.metrics.item-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_5_WEEKS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_324353"}}}""";
        val response = getItemUsageMetrics(request);
        response.result.metrics.length should be(5);
        response.result.summary should not be empty;
    }

    it should "return last 12 months metrics when, when 12 months data present" in {
        val request = """{"id":"ekstep.analytics.metrics.item-usage","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_12_MONTHS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb","content_id":"do_324353"}}}""";
        val response = getItemUsageMetrics(request);
        response.result.metrics.length should be(12);
        response.result.summary should not be empty;
    }

    "GenieLaunchMetricsAPIService" should "return empty result when, no pre-computed tag summary data is there in S3 location" in {
        val request = """{"id":"ekstep.analytics.metrics.genie-launch","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"4f04da60-1e24-4d31-aa7b-1daf91c46341"}}}""";
        val response = getGenieLaunchMetrics(request);
        response.result.summary should be (Map("m_avg_ts_session"-> 0, "m_total_sessions"-> 0, "m_total_devices"-> 0, "m_total_ts"-> 0,"m_avg_sess_device"-> 0))
    }
    
    it should "return error response on invalid request" in {
    	val request = """{"id":"ekstep.analytics.metrics.genie-launch","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{}}""";
    	the[Exception] thrownBy {
    		MetricsAPIService.genieLaunch(JSONUtils.deserialize[MetricsRequestBody](request));
        } should have message "period is missing or invalid."
    }
    
    it should "return one day metrics of last 7days when, only one day pre-computed tag summary data is there in S3 location for last 7days" in {
        val request = """{"id":"ekstep.analytics.metrics.genie-launch","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1475b1d70a66a0f2c22dd1096b98030cb7d9bacb"}}}""";
        val response = getGenieLaunchMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
        checkGLMetricsEmpty(response.result.metrics(1));
        checkGLMetricsEmpty(response.result.metrics(2));
        checkGLMetricsEmpty(response.result.metrics(3));
        checkGLMetricsEmpty(response.result.metrics(4));
        checkGLMetricsEmpty(response.result.metrics(5));
        checkGLMetricsEmpty(response.result.metrics(6));
    }

    it should "return tag metrics when, last 7 days data present for tag1, tag2, tag3 in S3 & API inputs (tag: tag1)" in {
        val request = """{"id":"ekstep.analytics.metrics.genie-launch","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"}}}""";
        val response = getGenieLaunchMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
    }

    it should "return last 5 weeks metrics when, when 5 weeks data present" in {
        val request = """{"id":"ekstep.analytics.metrics.genie-launch","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_5_WEEKS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"}}}""";
        val response = getGenieLaunchMetrics(request);
        response.result.metrics.length should be(5);
        response.result.summary should not be empty;
    }

    it should "return last 12 months metrics when, when 12 months data present" in {
        val request = """{"id":"ekstep.analytics.metrics.genie-launch","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_12_MONTHS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"}}}""";
        val response = getGenieLaunchMetrics(request);
        response.result.metrics.length should be(12);
        response.result.summary should not be empty;
    }

    "ContentUsageListMetricsAPIService" should "return empty result when, no pre-computed tag summary data is there in S3 location" in {
        val request = """{"id":"ekstep.analytics.content-list","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"4f04da60-1e24-4d31-aa7b-1daf91c46341"}}}""";
        val response = getContentUsageListMetrics(request);
        response.result.summary should be (Map("m_contents"-> List(),"content"->List()))
    }
    
    it should "return error response on invalid request" in {
    	val request = """{"id":"ekstep.analytics.content-list","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{}}""";
    	the[Exception] thrownBy {
    		MetricsAPIService.contentList(JSONUtils.deserialize[MetricsRequestBody](request));
        } should have message "period is missing or invalid."
    }

    it should "return one day metrics of last 7days when, only one day pre-computed tag summary data is there in S3 location for last 7days" in {
        val request = """{"id":"ekstep.analytics.content-list","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1475b1d70a66a0f2c22dd1096b98030cb7d9bacb"}}}""";
        val response = getContentUsageListMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
        response.result.metrics(1).get("content") should be(Some(List()));
        response.result.metrics(2).get("content") should be(Some(List()));
        response.result.metrics(3).get("content") should be(Some(List()));
        response.result.metrics(4).get("content") should be(Some(List()));
        response.result.metrics(5).get("content") should be(Some(List()));
        response.result.metrics(6).get("content") should be(Some(List()));
    }

    it should "return tag metrics when, last 7 days data present for tag1, tag2, tag3 in S3 & API inputs (tag: tag1)" in {
        val request = """{"id":"ekstep.analytics.content-list","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_7_DAYS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"}}}""";
        val response = getContentUsageListMetrics(request);
        response.result.metrics.length should be(7);
        response.result.summary should not be empty;
    }

    it should "return last 5 weeks metrics when, when 5 weeks data present" in {
        val request = """{"id":"ekstep.analytics.content-list","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_5_WEEKS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"}}}""";
        val response = getContentUsageListMetrics(request);
        response.result.metrics.length should be(5);
        response.result.summary should not be empty;
    }

    it should "return last 12 months metrics when, when 12 months data present" in {
        val request = """{"id":"ekstep.analytics.content-list","ver":"1.0","ts":"2016-09-12T18:43:23.890+00:00","params":{"msgid":"4f04da60-1e24-4d31-aa7b-1daf91c46341"},"request":{"period":"LAST_12_MONTHS","filter":{"tag":"1375b1d70a66a0f2c22dd1096b98030cb7d9bacb"}}}""";
        val response = getContentUsageListMetrics(request);
        response.result.metrics.length should be(12);
        response.result.summary should not be empty;
    }

}