package org.ekstep.analytics.model

import org.ekstep.analytics.framework.IBatchModel
import org.ekstep.analytics.framework.MeasuredEvent
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import scala.collection.mutable.Buffer
import org.ekstep.analytics.framework.JobContext
import org.apache.spark.HashPartitioner
import com.datastax.spark.connector._
import org.ekstep.analytics.framework.util.JSONUtils
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import scala.annotation.migration
import org.ekstep.analytics.updater.UpdateProficiencyModelParam

case class Assessment(learner_id: String, itemId: String, itemMC: List[String], itemMMC: List[String],
                      normScore: Double, maxScore: Int, itemMisconception: Array[String], timeSpent: Double);

case class LearnerProficiency(learner_id: String, proficiency: Map[String, Double], startTime: Long, endTime: Long)

class LearnerProficiencyMapper extends IBatchModel[MeasuredEvent] with Serializable {
    def execute(sc: SparkContext, events: RDD[MeasuredEvent], jobParams: Option[Map[String, AnyRef]]): RDD[String] = {

        val assessments = events.map(event => (event.uid.get, Buffer(event)))
            .partitionBy(new HashPartitioner(JobContext.parallelization))
            .reduceByKey((a, b) => a ++ b).mapValues { x =>
                var assessmentBuff = Buffer[Assessment]();
                x.foreach { x =>
                    val learner_id = x.uid.get
                    val itemResponses = x.edata.eks.asInstanceOf[Map[String, AnyRef]].get("itemResponses").get.asInstanceOf[List[Map[String, AnyRef]]]
                    itemResponses.foreach { f =>
                        val itemId = f.get("itemId").get.asInstanceOf[String];
                        val itemMC = f.getOrElse("mc", List()).asInstanceOf[List[String]]

                        if (itemMC.isEmpty && itemMC.length == 0) {
                            //fetch from content model
                        }

                        val itemMMC = f.getOrElse("mmc", List()).asInstanceOf[List[String]]
                        val score = f.get("score").get.asInstanceOf[Int]
                        val maxScore = f.getOrElse("maxScore", 0).asInstanceOf[Int]

                        if (maxScore == 0) {
                            //fetch from content model 
                        }
                        val timeSpent = f.get("timeSpent").get.asInstanceOf[Double]
                        val itemMisconception = Array[String]();
                        var normScore = 0d
                        if (maxScore != 0)
                            normScore = (score / maxScore);
                        val assess = Assessment(learner_id, itemId, itemMC, itemMMC, normScore, maxScore, itemMisconception, timeSpent)
                        assessmentBuff += assess
                    }
                }
                val model = assessmentBuff.map { x => (x.learner_id, x.itemMC.mkString(","), x.normScore, x.maxScore) }
                    .map { f =>
                        val alpha = UpdateProficiencyModelParam.getParameterAlpha(f._1, f._2).get
                        val beta = UpdateProficiencyModelParam.getParameterAlpha(f._1, f._2).get
                        val X = Math.round((f._3 * f._4))
                        val N = f._4

                        val alphaNew = alpha + X;
                        val betaNew = beta + N - X;
                        val pi = (alphaNew / (alphaNew + betaNew));
                        (f._2, alphaNew, betaNew, pi);
                    }
                model;
            }//.map { x => JSONUtils.serialize(x) };
        //val knwState = sc.cassandraTable[LearnerProficiency]("learner_db", "learnerproficiency").map { x => (x.learner_id,x.proficiency) };

        return null;
        //return assessments;
    }
}