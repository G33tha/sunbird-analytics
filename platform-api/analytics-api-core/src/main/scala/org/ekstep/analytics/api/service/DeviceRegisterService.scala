package org.ekstep.analytics.api.service

import org.ekstep.analytics.api.util.{CommonUtil, DBUtil, JSONUtils}
import org.ekstep.analytics.api._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import akka.actor.Actor
import com.google.common.net.InetAddresses
import com.typesafe.config.Config
import org.ekstep.analytics.api.util.PostgresDBUtil
import com.typesafe.config.ConfigFactory
import com.datastax.driver.core.ResultSet
import org.ekstep.analytics.api.util.DeviceLocation
import is.tagomor.woothee.Classifier

case class RegisterDevice(did: String, ip: String, request: String, uaspec: String)

class DeviceRegisterService extends Actor {

    val config: Config = ConfigFactory.load()
    val geoLocationCityTableName: String = config.getString("postgres.table.geo_location_city.name")
    val geoLocationCityIpv4TableName: String = config.getString("postgres.table.geo_location_city_ipv4.name")

    def receive = {
        case RegisterDevice(did: String, ip: String, request: String, uaspec: String) => sender() ! registerDevice(did, ip, request, uaspec)
    }

    def registerDevice(did: String, ipAddress: String, request: String, uaspec: String): String = {
        val body = JSONUtils.deserialize[RequestBody](request)
        val validIp = if(ipAddress.startsWith("192")) body.request.ip_addr.getOrElse("") else ipAddress
        if(validIp.nonEmpty) {
            val location = resolveLocation(validIp)
            val channel = body.request.channel.getOrElse("")
            val deviceSpec = body.request.dspec
            val data = updateDeviceProfile(did, channel, Option(location.state).map(_.trim).filterNot(_.isEmpty),
                Option(location.city).map(_.trim).filterNot(_.isEmpty), deviceSpec, uaspec)
        }
        JSONUtils.serialize(CommonUtil.OK("analytics.device-register",
            Map("message" -> s"Device registered successfully")))
    }

    def resolveLocation(ipAddress: String): DeviceLocation = {
        val ipAddressInt = InetAddresses.coerceToInteger(InetAddresses.forString(ipAddress))
        val query =
            s"""
               |SELECT
               |  glc.continent_name,
               |  glc.country_name,
               |  glc.subdivision_1_name state,
               |  glc.subdivision_2_name sub_div_2,
               |  glc.city_name city
               |FROM $geoLocationCityIpv4TableName gip,
               |  $geoLocationCityTableName glc
               |WHERE gip.geoname_id = glc.geoname_id
               |  AND gip.network_start_integer <= $ipAddressInt
               |  AND gip.network_last_integer >= $ipAddressInt
               """.stripMargin
        PostgresDBUtil.readLocation(query).headOption.getOrElse(new DeviceLocation())
    }

    def updateDeviceProfile(did: String, channel: String, state: Option[String], district: Option[String],
                            deviceSpec: Option[Map[String, String]], uaspec: String): ResultSet = {
        val uaspecMap = Classifier.parse(uaspec)
        val finalMap = Map("agent" -> uaspecMap.get("name"), "ver" -> uaspecMap.get("version"),
            "system" -> uaspecMap.get("os"), "raw" -> uaspec)
        val uaspecStr = JSONUtils.serialize(finalMap).replaceAll("\"", "'")

        val query = if(deviceSpec.isEmpty) {
            s"""
               |INSERT INTO ${Constants.DEVICE_DB}.${Constants.DEVICE_PROFILE_TABLE}
               | (device_id, channel, state, district, uaspec, updated_date)
               |VALUES('$did','$channel','$state','$district', $uaspecStr, ${DateTime.now(DateTimeZone.UTC).getMillis})
             """.stripMargin
        } else if (state.isEmpty || district.isEmpty) {
            s"""
               |INSERT INTO ${Constants.DEVICE_DB}.${Constants.DEVICE_PROFILE_TABLE}
               | (device_id, channel, uaspec, updated_date)
               |VALUES('$did','$channel', $uaspecStr, ${DateTime.now(DateTimeZone.UTC).getMillis})
             """.stripMargin
        } else {
            val deviceSpecStr = JSONUtils.serialize(deviceSpec.get).replaceAll("\"", "'")
            s"""
               |INSERT INTO ${Constants.DEVICE_DB}.${Constants.DEVICE_PROFILE_TABLE}
               | (device_id, channel, state, district, uaspec, device_spec, updated_date)
               | VALUES('$did', '$channel', '$state', '$district', $uaspecStr, $deviceSpecStr, ${DateTime.now(DateTimeZone.UTC).getMillis})
             """.stripMargin
        }
        DBUtil.session.execute(query)
    }
}