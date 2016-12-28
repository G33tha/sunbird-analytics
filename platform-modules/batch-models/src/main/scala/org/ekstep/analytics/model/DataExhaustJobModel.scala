package org.ekstep.analytics.model

import org.ekstep.analytics.framework.IBatchModel
import org.ekstep.analytics.framework.Event
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.ekstep.analytics.framework._
import org.joda.time.DateTime
import scala.collection.mutable.Buffer
import org.apache.spark.HashPartitioner
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.util.CommonUtil
import java.io.File
import org.apache.hadoop.io.compress.GzipCodec
import com.datastax.spark.connector._
import org.ekstep.analytics.framework.util.JobLogger
import org.ekstep.analytics.framework.dispatcher.S3Dispatcher
import org.ekstep.analytics.util.Constants
import java.util.UUID
import org.ekstep.analytics.job.JobStage

case class JobRequest(client_key: String, request_id: String, job_id: Option[String], status: String, request_data: String,
                      location: Option[String], dt_file_created: Option[DateTime], dt_first_event: Option[DateTime], dt_last_event: Option[DateTime],
                      dt_expiration: Option[DateTime], iteration: Option[Int], dt_job_submitted: DateTime, dt_job_processing: Option[DateTime],
                      dt_job_completed: Option[DateTime], input_events: Option[Long], output_events: Option[Long], file_size: Option[Long], latency: Option[Int],
                      execution_time: Option[Long], err_message: Option[String], stage: Option[String], stage_status: Option[String]) extends AlgoOutput

case class RequestFilter(start_date: String, end_date: String, tags: List[String], events: Option[List[String]]);
case class RequestConfig(filter: RequestFilter);
case class RequestOutput(request_id: String, output_events: Int)
case class DataExhaustJobInput(eventDate: Long, event: String) extends AlgoInput;
case class JobResponse(client_key: String, request_id: String, job_id: String, output_events: Long, bucket: String, prefix: String, first_event_date: Long, last_event_date: Long);

object DataExhaustJobModel extends IBatchModel[String, JobResponse] with Serializable {

    val className = "org.ekstep.analytics.model.DataExhaustJobModel"
    override def name: String = "DataExhaustJobModel"

    def updateStage(request_id: String, client_key: String, satage: String, status: String)(implicit sc: SparkContext) {
        sc.makeRDD(Seq(JobStage(request_id, client_key, satage, status))).saveAsCassandraTable(Constants.PLATFORM_KEY_SPACE_NAME, Constants.JOB_REQUEST, SomeColumns("request_id", "client_key", "stage", "stage_status"))
    }

    def getRequest(request_id: String, client_key: String)(implicit sc: SparkContext): RequestFilter = {
        try {
            val request = sc.cassandraTable[JobRequest](Constants.PLATFORM_KEY_SPACE_NAME, Constants.JOB_REQUEST).where("client_key = ? and request_id = ?", client_key, request_id).first();
            val filter = JSONUtils.deserialize[RequestConfig](request.request_data).filter;
            updateStage(request_id, client_key, "FETCHING_THE_REQUEST", "COMPLETED")
            filter;
        } catch {
            case t: Throwable =>
                updateStage(request_id, client_key, "FETCHING_THE_REQUEST", "FAILED")
                null;
        }
    }

    def filterEvent(data: RDD[String], requestFilter: RequestFilter): RDD[DataExhaustJobInput] = {

        val startDate = CommonUtil.dateFormat.parseDateTime(requestFilter.start_date).withTimeAtStartOfDay().getMillis;
        val endDate = CommonUtil.dateFormat.parseDateTime(requestFilter.end_date).withTimeAtStartOfDay().getMillis + 86399000;
        val filters: Array[Filter] = Array(
            Filter("eventts", "RANGE", Option(Map("start" -> startDate, "end" -> endDate))),
            Filter("genieTag", "IN", Option(requestFilter.tags))) ++ {
                if (requestFilter.events.isDefined && requestFilter.events.get.nonEmpty) Array(Filter("eid", "IN", Option(requestFilter.events.get))) else Array[Filter]();
            }
        println("Filters", JSONUtils.serialize(filters));
        data.map { x =>
            try {
                val event = JSONUtils.deserialize[Event](x);
                val matched = DataFilter.matches(event, filters);
                if (matched) {
                    DataExhaustJobInput(CommonUtil.getEventTS(event), x)
                } else {
                    null;
                }
            } catch {
                case ex: Exception =>
                    null;
            }
        }.filter { x => x != null }
    }

    override def execute(events: RDD[String], jobParams: Option[Map[String, AnyRef]])(implicit sc: SparkContext): RDD[JobResponse] = {

        val config = jobParams.getOrElse(Map[String, AnyRef]());
        val inputRDD = preProcess(events, config);
        JobContext.recordRDD(inputRDD);
        val outputRDD = algorithm(inputRDD, config);
        JobContext.recordRDD(outputRDD);
        val resultRDD = postProcess(outputRDD, config);
        JobContext.recordRDD(resultRDD);
        resultRDD
    }

    def preProcess(data: RDD[String], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[DataExhaustJobInput] = {

        val request_id = config.get("request_id").get.asInstanceOf[String];
        val client_key = config.get("client_key").get.asInstanceOf[String];
        val requestFilter = getRequest(request_id, client_key)
        try {
            val filteredData = filterEvent(data, requestFilter);
            updateStage(request_id, client_key, "FILTERING_DATA", "COMPLETED")
            filteredData;
        } catch {
            case t: Throwable =>
                updateStage(request_id, client_key, "FILTERING_DATA", "FAILED")
                null;
        }
    }

    def algorithm(data: RDD[DataExhaustJobInput], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[JobResponse] = {

        val client_key = config.get("client_key").get.asInstanceOf[String];
        val request_id = config.get("request_id").get.asInstanceOf[String];
        val bucket = config.get("data-exhaust-bucket").get.asInstanceOf[String]
        val prefix = config.get("data-exhaust-prefix").get.asInstanceOf[String]
        val job_id = config.get("job_id").get.asInstanceOf[String];

        try {
            val events = data.cache();
            val output_events = data.count;
            if (output_events > 0) {
                val firstEventDate = events.sortBy { x => x.eventDate }.first().eventDate;
                val lastEventDate = events.sortBy({ x => x.eventDate }, false).first.eventDate;
                val uploadPrefix = prefix + "/" + request_id;
                val s3key = "s3n://" + bucket + "/" + uploadPrefix;
                events.map { x => x.event }.saveAsTextFile(s3key);
                events.unpersist(true);
                updateStage(request_id, client_key, "SAVE_DATA_TO_S3", "COMPLETED")
                sc.makeRDD(List(JobResponse(client_key, request_id, job_id, output_events, bucket, uploadPrefix, firstEventDate, lastEventDate)));
            } else {
                updateStage(request_id, client_key, "SAVE_DATA_TO_S3", "FAILED")
                sc.makeRDD(List());
            }
        } catch {
            case t: Throwable =>
                updateStage(request_id, client_key, "SAVE_DATA_TO_S3", "FAILED")
                sc.makeRDD(List());
        }

    }

    def postProcess(data: RDD[JobResponse], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[JobResponse] = {

        data;
    }

    def main(args: Array[String]): Unit = {
        val requestId = "6a54bfa283de43a89086"
        val localPath = "/tmp/dataexhaust/6a54bfa283de43a89086"
        CommonUtil.deleteFile(localPath + "/" + requestId + "_$folder$")
        CommonUtil.deleteFile(localPath + "/_SUCCESS")
        CommonUtil.zipFolder(localPath + ".zip", localPath)
    }
}