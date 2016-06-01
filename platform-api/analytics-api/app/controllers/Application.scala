package controllers

import org.ekstep.analytics.api.service.ContentAPIService
import org.ekstep.analytics.api.util._
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import context.Context

object Application extends Controller {
    
    def index = Action {
        println("###### Index invoked ####");
        Ok("Hello World!")
    }

    def contentUsageMetrics(contentId: String) = Action { implicit request =>
        
        try {
            val body: String = if(request.body.asText.isEmpty) Json.stringify(request.body.asJson.get) else request.body.asText.get; 
            val response = ContentAPIService.getContentUsageMetrics(contentId, body)(Context.sc);
            Ok(response).withHeaders(CONTENT_TYPE -> "application/json");    
        } catch {
            case ex: Exception =>
                ex.printStackTrace();
                Ok(CommonUtil.errorResponseSerialized("ekstep.analytics.contentusagesummary", ex.getMessage)).withHeaders(CONTENT_TYPE -> "application/json");
        }
        
    }
}