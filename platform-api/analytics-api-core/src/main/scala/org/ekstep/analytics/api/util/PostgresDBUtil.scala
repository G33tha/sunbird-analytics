package org.ekstep.analytics.api.util

import com.typesafe.config.{Config, ConfigFactory}
import scalikejdbc._

object PostgresDBUtil {

    implicit val config: Config = ConfigFactory.load()
    private lazy val url = config.getString("postgres.url")
    private lazy val user = config.getString("postgres.user")
    private lazy val pass = config.getString("postgres.pass")

    Class.forName("org.postgresql.Driver")
    ConnectionPool.singleton(url, user, pass)

    implicit val session = AutoSession

    def read(sqlString: String): List[ConsumerChannel] = {
        sql"""$sqlString""".map(rs => ConsumerChannel(rs)).list().apply()
    }

    def executeQuery(sqlString: String) = {
        sql"""$sqlString"""
    }
}