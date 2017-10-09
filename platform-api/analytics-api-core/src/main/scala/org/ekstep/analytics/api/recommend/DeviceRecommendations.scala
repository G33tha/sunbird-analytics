package org.ekstep.analytics.api.recommend

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

import org.apache.commons.lang3.StringUtils
import org.ekstep.analytics.api.APIIds
import org.ekstep.analytics.api.Constants
import org.ekstep.analytics.api.IRecommendations
import org.ekstep.analytics.api.RequestBody
import org.ekstep.analytics.api.ResponseCode
import org.ekstep.analytics.api.util.CommonUtil
import org.ekstep.analytics.api.util.ContentCacheUtil
import org.ekstep.analytics.api.util.DBUtil
import org.ekstep.analytics.api.util.JSONUtils

import com.typesafe.config.Config
import java.util.Arrays.ArrayList
import java.util.ArrayList
import com.datastax.driver.core.TupleValue

object DeviceRecommendations extends IRecommendations {
	
	def isValidRequest(requestBody: RequestBody) : Validation = {
		val context = requestBody.request.context.getOrElse(Map());
		val dlang = context.getOrElse("dlang", "").asInstanceOf[String];
		val langName = ContentCacheUtil.getLanguageByCode(dlang);
		if (StringUtils.isEmpty(langName)) 
			Validation(false, Option("dlang should be a language code."));
		else
			Validation(true);
	}
	
	def fetch(requestBody: RequestBody)(implicit config: Config): String = {
		val validation = isValidRequest(requestBody)
		if (validation.value) {
			val context = requestBody.request.context.getOrElse(Map());
			val did = context.getOrElse("did", "").asInstanceOf[String];
			val uid = context.getOrElse("uid", "").asInstanceOf[String];
			val dlang = context.getOrElse("dlang", "").asInstanceOf[String];
			val langName = ContentCacheUtil.getLanguageByCode(dlang);
			val filters: Array[(String, List[String], String)] = Array(("language", List(langName), "LIST"));

			val deviceRecosFact = DBUtil.session.execute("select scores from " + Constants.DEVICE_DB + "." + Constants.DEVICE_RECOS_TABLE + " where device_id = '" + did + "'")
			val deviceRecos = deviceRecosFact.asScala.seq.map( row => row.getList("scores", classOf[TupleValue]).asScala.seq.map { f => (f.get(0, classOf[String]), f.get(1, classOf[Double]))  })
			val recoContents = getRecommendedContent(deviceRecos, filters);
			val result = applyLimit(recoContents, recoContents.size, getLimit(requestBody))
			JSONUtils.serialize(CommonUtil.OK(APIIds.RECOMMENDATIONS, Map[String, AnyRef]("content" -> result, "count" -> Int.box(recoContents.size))));
		} else {
			CommonUtil.errorResponseSerialized(APIIds.RECOMMENDATIONS, validation.message.getOrElse("request has invalid data."), ResponseCode.CLIENT_ERROR.toString());
		}
	}
	
	def applyLimit(contents: List[Map[String, Any]], total: Int, limit: Int)(implicit config: Config) : List[Map[String, Any]] = {
		contents.take(limit);
	} 
	
}