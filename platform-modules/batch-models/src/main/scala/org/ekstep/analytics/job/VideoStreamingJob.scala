package org.ekstep.analytics.job

import java.util.UUID

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.framework.conf.AppConf
import org.ekstep.analytics.framework.util.{CommonUtil, JSONUtils, JobLogger, RestUtil}
import org.ekstep.analytics.framework.{IJob, JobConfig, JobContext, Level}
import org.ekstep.analytics.util.{Constants, JobRequest}
import org.ekstep.analytics.videostream.VideoStreamingUtils
import com.datastax.spark.connector._
import org.ekstep.media.common.{MediaRequest, MediaResponse}
import org.ekstep.media.service.impl.MediaServiceFactory

object VideoStreamingJob extends optional.Application with IJob {

  implicit val className = "org.ekstep.analytics.job.VideoStreamingJob"
  def name: String = "VideoStreamingJob"
  val mediaService = MediaServiceFactory.getMediaService()
  val contentServiceUrl = AppConf.getConfig("lp.url")

  case class StreamingStage(request_id: String, client_key: String, job_id: String, stage: String, stage_status: String, status: String, iteration: Int, err_message: String = "")

  def main(config: String)(implicit sc: Option[SparkContext] = None): Unit = {
    JobLogger.init(name)
    JobLogger.start("VideoStreaming Job Started executing", Option(Map("config" -> config, "model" -> name)))
    val jobConfig = JSONUtils.deserialize[JobConfig](config)

    if (null == sc.getOrElse(null)) {
      JobContext.parallelization = 10
      implicit val sparkContext = CommonUtil.getSparkContext(JobContext.parallelization, jobConfig.appName.getOrElse(jobConfig.model))
      try {
        execute(jobConfig)
      } finally {
        CommonUtil.closeSparkContext()
      }
    } else {
      implicit val sparkContext: SparkContext = sc.getOrElse(null)
      execute(jobConfig)
    }
  }

  private def execute(config: JobConfig)(implicit sc: SparkContext) = {
      val requests = VideoStreamingUtils.getAllRequest(10)
      if (null != requests) {
          _executeRequests(requests, config)
      } else {
        JobLogger.end("VideoStreaming Job Completed. But There is no job request in DB", "SUCCESS", Option(Map("model" -> name, "date" -> "", "timeTaken" -> 0)))
      }
  }

  private def _executeRequests(requests: RDD[JobRequest], config: JobConfig)(implicit sc: SparkContext) = {
      // TODO: add the implementation of Request VideoStreaming
      val submitted = requests.filter(r => List("SUBMITTED", "FAILED").contains(r.status)).cache()
      val processing = requests.filter(r => "PROCESSING".contentEquals(r.status)).cache()

      _submitJobRequests(submitted, config)
      _getCompletedRequests(processing, config)
      JobLogger.end("VideoStreaming Job Completed.", "SUCCESS", Option(Map("date" -> "", "inputEvents" -> 0, "outputEvents" -> 0, "timeTaken" -> 0, "jobCount" -> 0, "requestDetails" -> null)))
  }

  private def _submitJobRequests(submitted: RDD[JobRequest], config: JobConfig)(implicit sc: SparkContext): Unit = {
    val stageName = "STREAMING_JOB_SUBMISSION";
    submitted.map { jobRequest =>
      val mediaRequest = MediaRequest(UUID.randomUUID().toString, null, JSONUtils.deserialize[Map[String,AnyRef]](jobRequest.request_data.replaceAll("artifactUrl","artifact_url")))
      val response = mediaService.submitJob(mediaRequest)
      (jobRequest, response)
    }.map {
      x =>
        val request = x._1
        val response = x._2
        val iteration = request.iteration.getOrElse(0)
        if (response.responseCode.equals("OK")) {
            val jobId = response.result.getOrElse("job", Map()).asInstanceOf[Map[String, AnyRef]].getOrElse("id","").asInstanceOf[String];
            val jobStatus = response.result.getOrElse("job", Map()).asInstanceOf[Map[String, AnyRef]].getOrElse("status","").asInstanceOf[String];
            StreamingStage(request.request_id, request.client_key, jobId, stageName, jobStatus, "PROCESSING", iteration);
        } else {
            val errorMsg = JSONUtils.serialize(response.result)
            StreamingStage(request.request_id, request.client_key, null, stageName, "FAILED", "FAILED", iteration + 1, errorMsg);
        }
    }.saveToCassandra(Constants.PLATFORM_KEY_SPACE_NAME, Constants.JOB_REQUEST, SomeColumns("request_id", "client_key", "job_id", "stage", "stage_status", "status", "iteration", "err_message"))
  }

  private def _getCompletedRequests(processing: RDD[JobRequest], config: JobConfig)(implicit sc: SparkContext) = {
    val stageName = "STREAMING_JOB_COMPLETE"
    val count = processing.map { jobRequest =>
      val mediaResponse:MediaResponse = mediaService.getJob(jobRequest.job_id.get)
      (jobRequest, mediaResponse)
    }.map {
      x =>
        val request = x._1
        val response = x._2
        JobLogger.log("Get job details while saving.", Option(response), Level.INFO)
        val iteration = request.iteration.getOrElse(0)
        if(response.responseCode.contentEquals("OK")) {
          val status = response.result.getOrElse("job", Map()).asInstanceOf[Map[String, AnyRef]].getOrElse("status","").asInstanceOf[String];
          if(status.equalsIgnoreCase("FINISHED")) {
            val previewUrl = mediaService.getStreamingPaths(request.job_id.get).result.getOrElse("streamUrl","").asInstanceOf[String]
            val contentId = JSONUtils.deserialize[Map[String,AnyRef]](request.request_data).getOrElse("content_id", "").asInstanceOf[String]
            if(_updatePreviewUrl(contentId, previewUrl)){
              val jobStatus = response.result.getOrElse("job", Map()).asInstanceOf[Map[String, AnyRef]].getOrElse("status","").asInstanceOf[String];
              StreamingStage(request.request_id, request.client_key, request.job_id.get, stageName, jobStatus, "FINISHED", iteration);
            }
          } else if(status.equalsIgnoreCase("ERROR")){
            val jobStatus = response.result.getOrElse("job", Map()).asInstanceOf[Map[String, AnyRef]].getOrElse("status","").asInstanceOf[String];
            StreamingStage(request.request_id, request.client_key, request.job_id.get, stageName, jobStatus, "FAILED", iteration + 1);
          }
        }
    }.filter(x => x != null).count()
    JobLogger.log("Total processed content for vedio streaming: "+ count, None, Level.INFO)
//      .saveToCassandra(Constants.PLATFORM_KEY_SPACE_NAME, Constants.JOB_REQUEST, SomeColumns("request_id", "client_key", "job_id", "stage", "stage_status", "status", "iteration", "err_message"))
  }

  private def _updatePreviewUrl(contentId: String, previewUrl: String): Boolean = {
    //TODO: Need to check on how to get channel ID of the content
    if(!previewUrl.isEmpty && !contentId.isEmpty) {
      val requestBody = "{\"request\": {\"content\": {\"previewUrl\":\""+ previewUrl +"\"}}}"
      val url = contentServiceUrl + "/system/v3/content/update/" + contentId
      val response = RestUtil.patch[Map[String, AnyRef]](url, requestBody)
      if(response.getOrElse("responseCode","").asInstanceOf[String].contentEquals("OK")){
        true;
      } else{
        val errorMessage = response.getOrElse("params", Map).asInstanceOf[Map[String, AnyRef]].getOrElse("errmsg","").asInstanceOf[String] + JSONUtils.serialize(response.getOrElse("result", Map).asInstanceOf[Map[String, AnyRef]])
        JobLogger.end("Error while updating previewUrl for content : " + contentId + " with error : " + errorMessage, "FAILED", Option(Map("date" -> "", "inputEvents" -> 0, "outputEvents" -> 0, "timeTaken" -> 0, "jobCount" -> 0, "requestDetails" -> null)))
        false
      }
    }else{
      false
    }
  }

}