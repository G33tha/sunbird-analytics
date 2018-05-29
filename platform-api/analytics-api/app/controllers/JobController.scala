package controllers

import org.ekstep.analytics.api.service.JobAPIService
import org.ekstep.analytics.api.service.JobAPIService._
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import akka.routing.FromConfig
import javax.inject.Inject
import javax.inject.Singleton

import org.ekstep.analytics.api._
import org.ekstep.analytics.api.util.{CacheUtil, CommonUtil, JSONUtils}
import org.ekstep.analytics.api.{APIIds, ResponseCode}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.Future

/**
  * @author Amit Behera, mahesh
  */

@Singleton
class JobController @Inject()(system: ActorSystem) extends BaseController {
  implicit val className = "controllers.JobController";
  val jobAPIActor = system.actorOf(Props[JobAPIService].withRouter(FromConfig()), name = "jobApiActor");

  def dataRequest() = Action.async { implicit request =>
    val body: String = Json.stringify(request.body.asJson.get);
    val res = ask(jobAPIActor, DataRequest(body, config)).mapTo[Response];
    res.map { x =>
      result(x.responseCode, JSONUtils.serialize(x))
    }
  }

  def getJob(clientKey: String, requestId: String) = Action.async { implicit request =>
    val res = ask(jobAPIActor, GetDataRequest(clientKey, requestId, config)).mapTo[Response];
    res.map { x =>
      result(x.responseCode, JSONUtils.serialize(x))
    }
  }

  def getJobList(clientKey: String) = Action.async { implicit request =>
    val limit = Integer.parseInt(request.getQueryString("limit").getOrElse(config.getString("data_exhaust.list.limit")))
    val res = ask(jobAPIActor, DataRequestList(clientKey, limit, config)).mapTo[Response];
    res.map { x =>
      result(x.responseCode, JSONUtils.serialize(x))
    }
  }

  def getTelemetry(datasetId: String) = Action.async { implicit request =>
    val from = request.getQueryString("from").getOrElse("")
    val to = request.getQueryString("to").getOrElse(org.ekstep.analytics.api.util.CommonUtil.getToday())
    val consumerId = request.headers.get("X-Consumer-ID").getOrElse("")
    val channelId = request.headers.get("X-Channel-ID").getOrElse("")

    val authorizationCheck = config.getBoolean("dataexhaust.authorization_check")
    if (authorizationCheck && authorizeDataExhaustRequest(consumerId, channelId)) {
      val res = ask(jobAPIActor, ChannelData(channelId, datasetId, from, to, config)).mapTo[Response];
      res.map { x =>
        result(x.responseCode, JSONUtils.serialize(x))
      }
    } else {
      val msg = s"ConsumerID = '$consumerId' is not authorized for the ChannelID = '$channelId'"
      val res = CommonUtil.errorResponse(APIIds.CHANNEL_TELEMETRY_EXHAUST, msg, ResponseCode.FORBIDDEN.toString)
      Future {
        result(res.responseCode, JSONUtils.serialize(res))
      }
    }
  }
  def refreshCache(cacheType: String) = Action { implicit request =>
    cacheType match {
      case "ConsumerChannl" =>
        CacheUtil.initConsumerChannelCache()
      case _ =>
        CacheUtil.initCache()
    }
    result("OK", JSONUtils.serialize(CommonUtil.OK(APIIds.CHANNEL_TELEMETRY_EXHAUST, Map("msg" -> s"$cacheType cache refresed successfully"))))
  }
}