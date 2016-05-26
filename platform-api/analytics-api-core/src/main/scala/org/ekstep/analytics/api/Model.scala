package org.ekstep.analytics.api

import org.joda.time.DateTime

/**
 * @author Santhosh
 */
object Model {

}

case class Filter(partner_id: Option[String], group_user: Option[Boolean]);
case class Trend(day: Option[Int], week: Option[Int], month: Option[Int])
case class Request(filter: Option[Filter], summaries: Option[Array[String]], trend: Option[Trend]);
case class RequestBody(id: String, ver: String, ts: String, request: Request);

case class ContentSummary(total_ts: Double, total_sessions: Long, avg_ts_session: Double, total_interactions: Long, avg_interactions_min: Double, avg_sessions_week: Option[Double], avg_ts_week: Option[Double])

case class Params(resmsgid: String, msgid: String, err: String, status: String, errmsg: String);
case class Response(id: String, ver: String, ts: String, params: Params, result: Map[String, AnyRef]);

case class Range(start: Int, end: Int);
case class ContentId(d_content_id: String);
case class ContentUsageSummary(d_content_id: String, d_period: Int, d_partner_id: String, d_group_user: Boolean, d_content_type: String, d_mime_type: String, m_publish_date: DateTime, m_total_ts: Double, m_total_sessions: Long, m_avg_ts_session: Double, m_total_interactions: Long, m_avg_interactions_min: Double, m_avg_sessions_week: Double, m_avg_ts_week: Double);

object Period extends Enumeration {
    type Period = Value
    val DAY, WEEK, MONTH, CUMULATIVE, LAST7, LAST30, LAST90 = Value
}