package org.ekstep.analytics.api.service

import org.ekstep.analytics.api.util.CommonUtil
import org.apache.spark.SparkContext
import org.ekstep.analytics.api.JobStatusResponse
import org.ekstep.analytics.api.JobOutput
import java.util.UUID
import org.ekstep.analytics.framework.util.JSONUtils
import scala.util.Random
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormat
import akka.actor.Actor
import com.typesafe.config.Config
import akka.actor.Props
import org.ekstep.analytics.api.RequestBody
import org.ekstep.analytics.api.Filter
import org.ekstep.analytics.api.ResponseCode
import org.ekstep.analytics.api.util.DBUtil
import org.ekstep.analytics.api.Request
import org.ekstep.analytics.api.JobRequest
import java.security.MessageDigest
import scala.util.Sorting
import org.ekstep.analytics.framework.Context
import org.ekstep.analytics.framework.PData
import org.ekstep.analytics.api.JobRequestEvent
import org.ekstep.analytics.framework.MEEdata
import org.ekstep.analytics.framework.JobStatus
import org.ekstep.analytics.api.JobStats
import org.ekstep.analytics.api.APIIds
import org.joda.time.DateTime


/**
 * @author mahesh
 */

object JobAPIService {
	
	def props = Props[JobAPIService];
	case class DataRequest(request: String, sc: SparkContext, config: Config);
	
	def dataRequest(request: String)(implicit sc: SparkContext, config: Config): String = {
		val body = JSONUtils.deserialize[RequestBody](request);
		val isValid = _validateReq(body)
		if ("true".equals(isValid.get("status").get)) {
			val requestId = _getRequestId(body.request.filter.get);
			val job = DBUtil.getJobRequest(requestId, body.params.get.client_key.get);
			val result = if (null == job) {
				val status = JobStatus.SUBMITTED.toString();
				val jobSubmitted = DateTime.now()
				val result = JobStatusResponse(requestId, status, jobSubmitted.getMillis, body.request);
				val jobRequest = JobRequest(body.params.get.client_key, Option(requestId), Option("data-exhaust"), Option(status), Option(JSONUtils.serialize(body.request)), Option(1), Option(jobSubmitted))
				DBUtil.saveJobRequest(jobRequest);
				CommonUtil.ccToMap(result)
			} else {
                val jobStatusRes = _getJobStatusResponse(job)
                JSONUtils.deserialize[Map[String, AnyRef]](JSONUtils.serialize(jobStatusRes))
            }
			JSONUtils.serialize(CommonUtil.OK(APIIds.DATA_REQUEST, result));
		} else {
			CommonUtil.errorResponseSerialized(APIIds.DATA_REQUEST, isValid.get("message").get, ResponseCode.CLIENT_ERROR.toString())
		}
	}
	
	def getJob(clientKey: String, requestId: String)(implicit sc: SparkContext): String = {
		JSONUtils.serialize(CommonUtil.OK("ekstep.analytics.job.info", Map()));	
	}
	
	def getJobList(clientKey: String)(implicit sc: SparkContext): String = {
		JSONUtils.serialize(CommonUtil.OK("ekstep.analytics.job.list", Map("count" -> Int.box(0), "jobs" -> Map())));	
	}
	
	private def _validateReq(body: RequestBody): Map[String, String] = {
		val params = body.params
		val filter = body.request.filter;
		if (filter.isEmpty || params.isEmpty) {
			val message = if (filter.isEmpty) "filter is empty" else "filter is empty";
			Map("status" -> "false", "message" -> message);
		} else {
			if (filter.get.start_date.isEmpty || filter.get.end_date.isEmpty || params.get.client_key.isEmpty || filter.get.tags.isEmpty) {
				val message = if (params.get.client_key.isEmpty) "client_key is empty" 
					else if(filter.get.tags.isEmpty)
						"tags are empty"
					else "start date or end date is empty"
				Map("status" -> "false", "message" -> message);
			} else {
				val endDate = filter.get.end_date.get
				val startDate = filter.get.start_date.get
				if (CommonUtil.getPeriod(endDate) >= CommonUtil.getPeriod(CommonUtil.getToday)) 
					Map("status" -> "false", "message" -> "end_date should be lesser than today's date.."); 
				else if (30 < CommonUtil.getDaysBetween(startDate, endDate)) 
					Map("status" -> "false", "message" -> "Date range should be < 30 days"); 
				else Map("status" -> "true");	
			}
		}
    }
	
	private def _getJobStatusResponse(job: JobRequest): JobStatusResponse = {
		val processed = List(JobStatus.COMPLETE.toString(), JobStatus.FAILED.toString()).contains(job.status);
		val created = if (job.dt_file_created.isEmpty) "" else job.dt_file_created.get.getMillis.toString()
        val output = if (processed) Option(JobOutput(job.location.getOrElse(""), job.file_size.getOrElse(0L), created, job.dt_first_event.get.getMillis, job.dt_last_event.get.getMillis, job.dt_expiration.get.getMillis)) else None;
        val stats = if (processed) Option(JobStats(job.dt_job_submitted.get.getMillis, job.dt_job_processing.get.getMillis, job.dt_job_completed.get.getMillis, job.input_events.getOrElse(0), job.output_events.getOrElse(0), job.latency.getOrElse(0), job.execution_time.getOrElse(0L))) else None;
        val request = JSONUtils.deserialize[Request](job.request_data.getOrElse("{}"))
        JobStatusResponse(job.request_id.get, job.status.get, CommonUtil.getMillis, request, output, stats)
    }
	
	private def _getRequestId(filter: Filter): String = {
		Sorting.quickSort(filter.tags.getOrElse(Array()));
		Sorting.quickSort(filter.events.getOrElse(Array()));
		val key = Array(filter.start_date.get, filter.end_date.get, filter.tags.get.mkString, filter.events.getOrElse(Array()).mkString).mkString("|");
		MessageDigest.getInstance("MD5").digest(key.getBytes).map("%02X".format(_)).mkString;
    }
	
	private def _getJobRequestEvent(requestId: String, body: RequestBody): JobRequestEvent = {
        val client_id = body.params.get.client_key.get
        val filter = body.request.filter
        val eksMap = Map("request_id" -> requestId, "job_id" -> "data-exhaust", "filter" -> filter.get)
        JobRequestEvent("BE_JOB_REQUEST", System.currentTimeMillis(), "1.0", Context(PData("AnalyticsAPIPipeline", null, "1.0", Option("")), None, null, null, Option(JobStatus.SUBMITTED.toString), Option(client_id), Option(1)), MEEdata(eksMap))
    }
}

class JobAPIService extends Actor {
	import JobAPIService._;
	
	def receive = {
		case DataRequest(request: String, sc: SparkContext, config: Config) =>
			sender() ! dataRequest(request)(sc, config);
	}
}