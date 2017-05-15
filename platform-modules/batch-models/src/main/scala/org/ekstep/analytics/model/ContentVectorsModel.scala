package org.ekstep.analytics.model

import org.apache.commons.lang3.StringUtils
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.framework.AlgoInput
import org.ekstep.analytics.framework.AlgoOutput
import org.ekstep.analytics.framework.Context
import org.ekstep.analytics.framework.DtRange
import org.ekstep.analytics.framework.Empty
import org.ekstep.analytics.framework.IBatchModelTemplate
import org.ekstep.analytics.framework.MEEdata
import org.ekstep.analytics.framework.MeasuredEvent
import org.ekstep.analytics.framework.PData
import org.ekstep.analytics.framework.conf.AppConf
import org.ekstep.analytics.framework.dispatcher.ScriptDispatcher
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.util.RestUtil
import org.ekstep.analytics.framework.util.S3Util
import org.ekstep.analytics.util.Constants
import com.datastax.spark.connector.toRDDFunctions
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.Level._
import com.datastax.spark.connector._
import org.ekstep.analytics.framework.Level
import org.ekstep.analytics.framework.util.JobLogger
import java.net.URL
import org.apache.commons.lang3.StringEscapeUtils
import org.ekstep.analytics.util.ContentUsageSummaryFact
import org.ekstep.analytics.adapter.ContentAdapter
import org.apache.commons.io.FileUtils
import java.io.File

case class Params(resmsgid: String, msgid: String, err: String, status: String, errmsg: String);
case class Response(id: String, ver: String, ts: String, params: Params, result: Option[Map[String, AnyRef]]);
case class ContentVectors(content_vectors: Array[ContentVector]);
case class ContentVector(contentId: String, text_vec: List[Double], tag_vec: List[Double]);
case class ContentAsString(content: String) extends AlgoInput
case class ContentEnrichedJson(contentId: String, jsonData: Map[String, AnyRef]) extends AlgoOutput

object ContentVectorsModel extends IBatchModelTemplate[Empty, ContentAsString, ContentEnrichedJson, MeasuredEvent] with Serializable {

    implicit val className = "org.ekstep.analytics.model.ContentVectorsModel"
    override def name(): String = "ContentVectorsModel";

    override def preProcess(data: RDD[Empty], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ContentAsString] = {

        val contentList = ContentAdapter.getPublishedContent();

        val contentRDD = if("true".equals(config.getOrElse("content2vec.all_content_flag","true"))) sc.parallelize(contentList, 10).cache(); else sc.parallelize(Array(contentList.last), 10).cache();
        JobLogger.log("Content count", Option(Map("contentCount" -> contentList.size)), INFO);
        val downloadPath = config.getOrElse("content2vec.download_path", "/tmp/").asInstanceOf[String];
        val downloadFilePrefix = config.getOrElse("content2vec.download_file_prefix", "temp_").asInstanceOf[String];
        val downloadTime = CommonUtil.time {
            val downloadRDD = contentRDD.map { x => x.getOrElse("downloadUrl", "").asInstanceOf[String] }.filter { x => StringUtils.isNotBlank(x) }.distinct;
            println("Total download files:", downloadRDD.count());
            val localDownloadPath = if (downloadPath.endsWith(File.separator)) downloadPath else downloadPath + File.separator;
            downloadRDD.map { downloadUrl =>
                downloadECARFile(new URL(downloadUrl), localDownloadPath, downloadFilePrefix)
            }.collect; // Invoke the download
        }
        println("Time taken to download files(in secs):", (downloadTime._1 / 1000));
        println("### Ecar files download complete. ###");

        contentRDD.map(x => ContentAsString(JSONUtils.serialize(x)))
    }

    override def algorithm(data: RDD[ContentAsString], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ContentEnrichedJson] = {
        contentToVec(data, config);
    }

    def contentToVec(data: RDD[ContentAsString], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[ContentEnrichedJson] = {

        implicit val jobConfig = config;
        val scriptLoc = jobConfig.getOrElse("content2vec_scripts_path", "").asInstanceOf[String];
        val pythonExec = jobConfig.getOrElse("python.home", "").asInstanceOf[String] + "python";
        val env = Map("PATH" -> (sys.env.getOrElse("PATH", "/usr/bin") + ":/usr/local/bin"));

        JobLogger.log("Downloading concepts", None, INFO);
        val contentServiceUrl = AppConf.getConfig("lp.url");
        sc.makeRDD(Array(contentServiceUrl), 1).pipe(s"$pythonExec $scriptLoc/content/get_concepts.py").collect();

        JobLogger.log("Running content enrichment", None, INFO);
        val enrichedContentRDD = _doContentEnrichment(data.map { x => x.content }, scriptLoc, pythonExec, env).cache();
        action(enrichedContentRDD);

        JobLogger.log("Content enrichment done. Running content to corpus", None, INFO);
        val corpusRDD = _doContentToCorpus(enrichedContentRDD, scriptLoc, pythonExec, env);
        action(corpusRDD);

        JobLogger.log("Corpus creation completed. Running content to vec model training", None, INFO);
        _doTrainContent2VecModel(scriptLoc, pythonExec, env);

        JobLogger.log("Model training completed. Running content to vec", None, INFO);
        val vectors = _doUpdateContentVectors(scriptLoc, pythonExec, "", env);

        enrichedContentRDD.map { x =>
            val jsonData = JSONUtils.deserialize[Map[String, AnyRef]](x)
            ContentEnrichedJson(jsonData.get("identifier").get.asInstanceOf[String], jsonData);
        };
    }

    /*private def printRDD(rdd: RDD[String]) = {
        rdd.collect().foreach { x =>
            JobLogger.log("Debug execution", Option(JSONUtils.deserialize[Map[String, AnyRef]](x)), DEBUG);
        }
    }*/
    
    private def action(rdd: RDD[String]) = {
        rdd.count
    }

    override def postProcess(data: RDD[ContentEnrichedJson], config: Map[String, AnyRef])(implicit sc: SparkContext): RDD[MeasuredEvent] = {

        // TODO: Data science needs the temp working directories for debugging
        val downloadPath = config.getOrElse("content2vec.download_path", "/tmp").asInstanceOf[String];
        CommonUtil.deleteDirectory(downloadPath);
        data.map { x => getME(x) };
    }

    private def _doContentEnrichment(contentRDD: RDD[String], scriptLoc: String, pythonExec: String, env: Map[String, String])(implicit config: Map[String, AnyRef]): RDD[String] = {
        if (StringUtils.equalsIgnoreCase("true", config.getOrElse("content2vec.enrich_content", "true").asInstanceOf[String])) {
            contentRDD.pipe(s"$pythonExec $scriptLoc/content/enrich_content.py", env)
        } else {
            contentRDD
        }
    }

    private def _doContentToCorpus(contentRDD: RDD[String], scriptLoc: String, pythonExec: String, env: Map[String, String])(implicit config: Map[String, AnyRef]): RDD[String] = {

        if (StringUtils.equalsIgnoreCase("true", config.getOrElse("content2vec.content_corpus", "true").asInstanceOf[String])) {
            contentRDD.pipe(s"$pythonExec $scriptLoc/object2vec/update_content_corpus.py", env);
        } else {
            contentRDD
        }
    }

    private def _doTrainContent2VecModel(scriptLoc: String, pythonExec: String, env: Map[String, String])(implicit sc: SparkContext, config: Map[String, AnyRef]) = {

        if (StringUtils.equalsIgnoreCase("true", config.getOrElse("content2vec.train_model", "true").asInstanceOf[String])) {
            val bucket = config.getOrElse("content2vec.s3_bucket", "ekstep-dev-data-store").asInstanceOf[String];
            val modelPath = config.getOrElse("content2vec.model_path", "model").asInstanceOf[String];
            val prefix = config.getOrElse("content2vec.s3_key_prefix", "model").asInstanceOf[String];

            val scriptParams = Map(
                "corpus_loc" -> config.getOrElse("content2vec.corpus_path", "").asInstanceOf[String],
                "model" -> modelPath)

            sc.makeRDD(Seq(JSONUtils.serialize(scriptParams)), 1).pipe(s"$pythonExec $scriptLoc/object2vec/corpus_to_vec.py", env).collect();
            S3Util.uploadDirectory(bucket, prefix, modelPath);
        }
    }

    private def _doUpdateContentVectors(scriptLoc: String, pythonExec: String, contentId: String, env: Map[String, String])(implicit sc: SparkContext, config: Map[String, AnyRef]): RDD[String] = {

        val bucket = config.getOrElse("content2vec.s3_bucket", "ekstep-dev-data-store").asInstanceOf[String];
        val modelPath = config.getOrElse("content2vec.model_path", "model").asInstanceOf[String];
        val prefix = config.getOrElse("content2vec.s3_key_prefix", "model").asInstanceOf[String];
        S3Util.download(bucket, prefix, modelPath)
        val scriptParams = Map[String, AnyRef](
            "infer_all" -> "true",
            "corpus_loc" -> config.getOrElse("content2vec.corpus_path", "").asInstanceOf[String],
            "model" -> modelPath)
        val vectorRDD = sc.makeRDD(Seq(JSONUtils.serialize(scriptParams)), 1).pipe(s"$pythonExec $scriptLoc/object2vec/infer_query.py", env);
        vectorRDD.map { x => JSONUtils.deserialize[ContentVectors](x) }.flatMap { x => x.content_vectors.map { y => y } }.saveToCassandra(Constants.CONTENT_KEY_SPACE_NAME, Constants.CONTENT_TO_VEC)
        vectorRDD
    }

    private def getME(data: ContentEnrichedJson): MeasuredEvent = {
        val ts = System.currentTimeMillis()
        val dateRange = DtRange(ts, ts)
        val mid = org.ekstep.analytics.framework.util.CommonUtil.getMessageId("AN_ENRICHED_CONTENT", null, null, dateRange, data.contentId);
        MeasuredEvent("AN_ENRICHED_CONTENT", ts, ts, "1.0", mid, null, Option(data.contentId), None, Context(PData("AnalyticsDataPipeline", "ContentToVec", "1.0"), None, null, dateRange), null, MEEdata(Map("enrichedJson" -> data.jsonData)));
    }
    
    private def downloadECARFile(url: URL, localPath: String, filePrefix: String = "") {
    	val fileName = filePrefix + url.getFile.substring(url.getFile.lastIndexOf(File.separator)+1);
	    val file = new File(localPath+fileName);
    	try {
	        FileUtils.copyURLToFile(url, file)
    	} catch {
    	  case t: Exception => 
    	 	JobLogger.log("ECAR file download failed.", Option(Map("url" -> url, "localPath"-> file.getAbsolutePath, "message"-> t.getMessage)), ERROR)
    	}
    }
}