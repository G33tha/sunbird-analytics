package org.ekstep.analytics.job

import org.ekstep.analytics.framework.JobConfig
import org.ekstep.analytics.framework.Fetcher
import org.ekstep.analytics.framework.Query
import org.ekstep.analytics.framework.Dispatcher
import org.ekstep.analytics.model.SparkSpec
import org.ekstep.analytics.framework.util.JSONUtils
import com.datastax.spark.connector._
import org.ekstep.analytics.framework.util.CommonUtil
import org.joda.time.DateTime
import org.ekstep.analytics.model.JobRequest
import org.ekstep.analytics.model.RequestFilter
import org.ekstep.analytics.model.RequestConfig
import org.ekstep.analytics.util.Constants
import com.datastax.spark.connector.cql.CassandraConnector
import org.ekstep.analytics.framework.util.S3Util
import java.io.File

class TestDataExhaustJob extends SparkSpec(null) {

    private def preProcess() {
        CassandraConnector(sc.getConf).withSessionDo { session =>
            session.execute("TRUNCATE platform_db.job_request");
        }
    }

    private def deleteS3File(bucket: String, prefix: String, request_ids: Array[String]) {

        for (request_id <- request_ids) {
            val keys1 = S3Util.getPath(bucket, prefix + "/" + request_id)
            for (key <- keys1) {
                S3Util.deleteObject(bucket, key.replace(s"s3n://$bucket/", ""))
            }
            S3Util.deleteObject(bucket, prefix + "/" + request_id + "_$folder$");
            S3Util.deleteObject(bucket, prefix + "/" + request_id + ".zip");
        }
    }

    private def deleteLocalFile(path: String) {
        val file = new File(path)
        if (file.exists())
            CommonUtil.deleteDirectory(path)
    }
    private def postProcess(fileDetails: Map[String, String], request_ids: Array[String]) {
        val fileType = fileDetails.get("fileType").get
        fileType match {
            case "s3" =>
                val bucket = fileDetails.get("bucket").get
                val prefix = fileDetails.get("prefix").get
                deleteS3File(bucket, prefix, request_ids)
            case "local" =>
                val path = fileDetails.get("path").get
                deleteLocalFile(path)
        }
    }

    it should "execute DataExhaustJob job from local data and won't throw any Exception" in {

        preProcess()
        
        val requests = Array(
            JobRequest("partner1", "1234", None, "SUBMITTED", JSONUtils.serialize(RequestConfig(RequestFilter("2016-11-19", "2016-11-20", List("becb887fe82f24c644482eb30041da6d88bd8150"), None))),
                None, None, None, None, None, None, DateTime.now(), None, None, None, None, None, None, None, None, None, None),
            JobRequest("partner1", "273645", None, "SUBMITTED", JSONUtils.serialize(RequestConfig(RequestFilter("2016-11-19", "2016-11-20", List("test-tag"), Option(List("OE_ASSESS"))))),
                None, None, None, None, None, None, DateTime.now(), None, None, None, None, None, None, None, None, None, None));

        
        sc.makeRDD(requests).saveToCassandra(Constants.PLATFORM_KEY_SPACE_NAME, Constants.JOB_REQUEST)
        val config = """{"search":{"type":"local","queries":[{"file":"src/test/resources/data-exhaust/*"}]},"model":"org.ekstep.analytics.model.DataExhaustJobModel","output":[{"to":"file","params":{"file": "/tmp/dataexhaust"}}],"parallelization":8,"appName":"Data Exhaust","deviceMapping":false, "modelParams":{"dispatch-to":"local"}}"""
        
        DataExhaustJob.main(config)(Option(sc));

        
        val files1 = new File("/tmp/dataexhaust/1234").listFiles()
        files1.length should not be (0)

        val files2 = new File("/tmp/dataexhaust/273645")
        files2.isDirectory() should be(false)

        val job1 = sc.cassandraTable[JobRequest](Constants.PLATFORM_KEY_SPACE_NAME, Constants.JOB_REQUEST).where("client_key = ? and request_id = ?", "partner1", "1234").first();

        job1.stage.getOrElse("") should be("UPDATE_RESPONSE_TO_DB")
        job1.stage_status.getOrElse("") should be("COMPLETED")
        job1.status should be("COMPLETED")
        job1.output_events.get should be > (0L)

        val job2 = sc.cassandraTable[JobRequest](Constants.PLATFORM_KEY_SPACE_NAME, Constants.JOB_REQUEST).where("client_key = ? and request_id = ?", "partner1", "273645").first();

        job2.stage.getOrElse("") should be("UPDATE_RESPONSE_TO_DB")
        job2.stage_status.getOrElse("") should be("COMPLETED")
        job2.status should be("COMPLETED")
        job2.output_events.get should be(0L)

        val fileDetails = Map("fileType" -> "local", "path" -> "/tmp/dataexhaust")
        val request_ids = Array("1234", "273645")
        postProcess(fileDetails, request_ids)
    }
    
    ignore should "run the data exhaust and save data to S3" in {
        
        preProcess()
        
        val requests = Array(
            JobRequest("partner1", "1234", None, "SUBMITTED", JSONUtils.serialize(RequestConfig(RequestFilter("2016-09-01", "2016-09-10", List("dff9175fa217e728d86bc1f4d8f818f6d2959303"), None))),
                None, None, None, None, None, None, DateTime.now(), None, None, None, None, None, None, None, None, None, None),
            JobRequest("partner1", "273645", None, "SUBMITTED", JSONUtils.serialize(RequestConfig(RequestFilter("2016-11-19", "2016-11-20", List("test-tag"), Option(List("OE_ASSESS"))))),
                None, None, None, None, None, None, DateTime.now(), None, None, None, None, None, None, None, None, None, None));

        sc.makeRDD(requests).saveToCassandra(Constants.PLATFORM_KEY_SPACE_NAME, Constants.JOB_REQUEST)
        
        val config = """{"search":{"type":"s3"},"model":"org.ekstep.analytics.model.DataExhaustJobModel","modelParams":{"dataset-read-bucket":"ekstep-datasets-test","dataset-read-prefix":"staging/datasets/D001/4208ab995984d222b59299e5103d350a842d8d41/","data-exhaust-bucket":"ekstep-public","data-exhaust-prefix":"dev/data-exhaust"}, "parallelization":8,"appName":"Data Exhaust","deviceMapping":false}"""
        DataExhaustJob.main(config)(Option(sc));
        
        val fileDetails = Map("fileType" -> "s3", "bucket" -> "ekstep-public", "prefix" -> "dev/data-exhaust")
        val request_ids = Array("1234")
        postProcess(fileDetails, request_ids)
    }
}
