package org.ekstep.analytics.api.service

import org.apache.spark.SparkContext
import com.datastax.spark.connector._
import org.ekstep.analytics.api._
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.api.util.CommonUtil
import org.apache.commons.lang3.StringUtils
import org.ekstep.analytics.framework.util.RestUtil
import org.apache.spark.rdd.RDD
import org.apache.spark.broadcast.Broadcast
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

/**
 * @author mahesh
 */

object RecommendationAPIService {

    var contentRDD: RDD[(String, Map[String, AnyRef])] = null;
    var contentBroadcastMap : Broadcast[Map[String, Map[String, AnyRef]]] = null;
    var cacheTimestamp: Long = 0L;

    def initCache()(implicit sc: SparkContext, config: Map[String, String]) {

        val baseUrl = config.get("service.search.url").get;
        val searchUrl = s"$baseUrl/v2/search";
        val request = Map("request" -> Map("filters" -> Map("objectType" -> List("Content"), "contentType" -> List("Story", "Worksheet", "Collection", "Game"), "status" -> List("Live")), "limit" -> 1000));
        val resp = RestUtil.post[Response](searchUrl, JSONUtils.serialize(request));
        val contentList = resp.result.getOrElse(Map("content" -> List())).getOrElse("content", List()).asInstanceOf[List[Map[String, AnyRef]]];
        //contentRDD = sc.parallelize(contentList, 4).map(f => (f.get("identifier").get.asInstanceOf[String], f)).cache();
        val contentMap = contentList.map(f => (f.get("identifier").get.asInstanceOf[String], f)).toMap;
        contentBroadcastMap = sc.broadcast[Map[String, Map[String, AnyRef]]](contentMap);
        cacheTimestamp = DateTime.now(DateTimeZone.UTC).getMillis;
    }
    
    def validateCache()(implicit sc: SparkContext, config: Map[String, String]) {
        
        val timeAtStartOfDay = DateTime.now(DateTimeZone.UTC).withTimeAtStartOfDay().getMillis;
        if(cacheTimestamp < timeAtStartOfDay) {
            println("cacheTimestamp:" + cacheTimestamp, "timeAtStartOfDay:" + timeAtStartOfDay, " ### Resetting content cache...### ");
            if(null != contentBroadcastMap) contentBroadcastMap.destroy();
            initCache();
        }
    }

    def recommendations(requestBody: String)(implicit sc: SparkContext, config: Map[String, String]): String = {
        
        validateCache();
        val reqBody = JSONUtils.deserialize[RequestBody](requestBody);
        val context = reqBody.request.context;
        val did = context.get("did").asInstanceOf[String];
        val dlang = context.get("dlang").asInstanceOf[String];
        val limit = reqBody.request.limit.getOrElse(10);
        
        if (StringUtils.isBlank(did) || StringUtils.isBlank(dlang)) {
            throw new Exception("did or dlang is missing.");
        }

        val deviceRecos = sc.cassandraTable[(List[(String, Double)])](Constants.DEVICE_DB, Constants.DEVICE_RECOS_TABLE).select("scores").where("device_id = ?", did);
        val recoContent = if (deviceRecos.count() > 0) {
            /** The below code joins the data with content RDD but the order is removed from it - so the results require re-ordering of data */
            /** 
            val deviceRDD = sc.parallelize(deviceRecos.first.take(limit), 4);
            val contentRecos = deviceRDD.join(contentRDD).map(f => f._2._2 ++ Map("reco_score" -> f._2._1));
            val rec = contentRecos.collect();
            */
            
            deviceRecos.first.take(limit).map(f => contentBroadcastMap.value.getOrElse(f._1, Map()) ++ Map("reco_score" -> f._2)).filter(p => p.get("identifier").isDefined);
        } else {
            Array();
        }

        val result = Map[String, AnyRef]("content" -> recoContent);
        JSONUtils.serialize(CommonUtil.OK("ekstep.analytics.recommendations", result));
    }
}