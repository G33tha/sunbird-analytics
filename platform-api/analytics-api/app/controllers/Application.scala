package controllers

import org.ekstep.analytics.api.service.ContentAPIService
import org.ekstep.analytics.api.service.HealthCheckAPIService
import org.ekstep.analytics.api.service.RecommendationAPIService
import org.ekstep.analytics.api.service.TagService
import org.ekstep.analytics.api.util._
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import context.Context
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration._
import scala.concurrent.Future
import javax.inject.Singleton
import javax.inject.Inject
import akka.actor.ActorSystem
import akka.pattern._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import akka.util.Timeout
import scala.concurrent.duration._
import org.ekstep.analytics.api.exception.ClientException
import org.ekstep.analytics.api.ResponseCode
import org.ekstep.analytics.framework.util.JobLogger
import org.ekstep.analytics.framework.Level._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._
import org.ekstep.analytics.api.service.DataProductManagementAPIService
import org.ekstep.analytics.api.service.DataProductManagementAPIService

@Singleton
class Application @Inject() (system: ActorSystem) extends BaseController {
	implicit val className = "controllers.Application";
	val contentAPIActor = system.actorOf(ContentAPIService.props, "content-api-service-actor");
	val dpmgmtAPIActor = system.actorOf(DataProductManagementAPIService.props, "dpmgmt-api-service-actor");

	def contentUsageMetrics(contentId: String) = Action { implicit request =>
		try {
			val body: String = Json.stringify(request.body.asJson.get);
			JobLogger.log("ekstep.analytics.contentusagesummary", Option(Map("requestBody" -> body, "requestHeaders" -> request.headers.toSimpleMap, "path" -> request.path)), INFO);
			val response = ContentAPIService.getContentUsageMetrics(contentId, body)(Context.sc);
			play.Logger.info(request + " body - " + body + "\n\t => " + response)
			Ok(response).withHeaders(CONTENT_TYPE -> "application/json");
		} catch {
			case ex: ClientException =>
				Ok(CommonUtil.errorResponseSerialized("ekstep.analytics.contentusagesummary", ex.getMessage, ResponseCode.CLIENT_ERROR.toString())).withHeaders(CONTENT_TYPE -> "application/json");
		}

	}

	def checkAPIhealth() = Action { implicit request =>
		JobLogger.log("ekstep.analytics-api.health", Option(Map("requestHeaders" -> request.headers.toSimpleMap)), INFO);
		val response = HealthCheckAPIService.getHealthStatus()(Context.sc)
		Ok(response).withHeaders(CONTENT_TYPE -> "application/json");
	}

	def contentToVec(contentId: String) = Action { implicit request =>
		JobLogger.log("ekstep.analytics.content-to-vec", Option(Map("requestHeaders" -> request.headers.toSimpleMap, "path" -> request.path)), INFO);
		(contentAPIActor ! ContentAPIService.ContentToVec(contentId, Context.sc, config))
		val response = JSONUtils.serialize(CommonUtil.OK("ekstep.analytics.content-to-vec", Map("message" -> "Job submitted for content enrichment")));
		Ok(response).withHeaders(CONTENT_TYPE -> "application/json");
	}

	def contentToVecTrainModel() = Action { implicit request =>
		JobLogger.log("ekstep.analytics.content-to-vec.train.model", Option(Map("requestHeaders" -> request.headers.toSimpleMap)), INFO);
		(contentAPIActor ! ContentAPIService.ContentToVecTrainModel(Context.sc, config))
		val response = JSONUtils.serialize(CommonUtil.OK("ekstep.analytics.content-to-vec.train.model", Map("message" -> "successful")));
		Ok(response).withHeaders(CONTENT_TYPE -> "application/json");
	}

	def recommendationsTrainModel() = Action { implicit request =>
		JobLogger.log("ekstep.analytics.recommendations.train.model", Option(Map("requestHeaders" -> request.headers.toSimpleMap)), INFO);
		(contentAPIActor ! ContentAPIService.RecommendationsTrainModel(Context.sc, config))
		val response = JSONUtils.serialize(CommonUtil.OK("ekstep.analytics.recommendations.train.model", Map("message" -> "successful")));
		Ok(response).withHeaders(CONTENT_TYPE -> "application/json");
	}

	def recommendations() = Action.async { implicit request =>
		val body: String = Json.stringify(request.body.asJson.get);
		JobLogger.log("ekstep.analytics.recommendations", Option(Map("requestBody" -> body, "requestHeaders" -> request.headers.toSimpleMap)), INFO);
		val futureRes = Future { RecommendationAPIService.recommendations(body)(Context.sc, config) };
		val timeoutFuture = play.api.libs.concurrent.Promise.timeout(CommonUtil.errorResponseSerialized("ekstep.analytics.recommendations", "request timeout", ResponseCode.REQUEST_TIMEOUT.toString()), 3.seconds);
		val firstCompleted = Future.firstCompletedOf(Seq(futureRes, timeoutFuture));
		val response: Future[String] = firstCompleted.recoverWith {
			case ex: ClientException =>
				Future { CommonUtil.errorResponseSerialized("ekstep.analytics.recommendations", ex.getMessage, ResponseCode.CLIENT_ERROR.toString()) };
		};
		response.map { resp =>
			JobLogger.log("ekstep.analytics.recommendations", Option(Map("request" -> body, "response" -> resp)), INFO);
			play.Logger.info(request + " body - " + body + "\n\t => " + resp);
			val result = if (resp.contains(ResponseCode.CLIENT_ERROR.toString())) resp
			else JSONUtils.serialize(CommonUtil.OK("ekstep.analytics.recommendations", Map[String, AnyRef]("content" -> List(), "count" -> Int.box(0))));
			Ok(result).withHeaders(CONTENT_TYPE -> "application/json");
		}
	}

	def runJob(job: String) = Action { implicit request =>
		val body: String = Json.stringify(request.body.asJson.get);
		JobLogger.log("ekstep.analytics.runjob", Option(Map("requestBody" -> body, "requestHeaders" -> request.headers.toSimpleMap, "path" -> request.path)), INFO);
		(dpmgmtAPIActor ! DataProductManagementAPIService.RunJob(job, body, config))
		val response = JSONUtils.serialize(CommonUtil.OK("ekstep.analytics.runjob", Map("message" -> "Job submitted")));
		Ok(response).withHeaders(CONTENT_TYPE -> "application/json");
	}

	def replayJob(job: String, from: String, to: String) = Action { implicit request =>
		val body: String = Json.stringify(request.body.asJson.get);
		JobLogger.log("ekstep.analytics.replay-job", Option(Map("requestBody" -> body, "requestHeaders" -> request.headers.toSimpleMap, "path" -> request.path)), INFO);
		(dpmgmtAPIActor ! DataProductManagementAPIService.ReplayJob(job, from, to, body, config))
		val response = JSONUtils.serialize(CommonUtil.OK("ekstep.analytics.replay-job", Map("message" -> "Job submitted")));
		Ok(response).withHeaders(CONTENT_TYPE -> "application/json");
	}
	
	def registerTag(tagId: String) = Action { implicit request =>
		JobLogger.log("ekstep.analytics.registerTag", Option(Map("requestHeaders" -> request.headers.toSimpleMap, "path" -> request.path)), INFO);
		val response = TagService.registerTag(tagId)(Context.sc);
		Ok(response).withHeaders(CONTENT_TYPE -> "application/json");
	}

	def deleteTag(tagId: String) = Action { implicit request =>
		JobLogger.log("ekstep.analytics.deleteTag", Option(Map("requestHeaders" -> request.headers.toSimpleMap, "path" -> request.path)), INFO);
		val response = TagService.deleteTag(tagId)(Context.sc);
		Ok(response).withHeaders(CONTENT_TYPE -> "application/json");
	}
}