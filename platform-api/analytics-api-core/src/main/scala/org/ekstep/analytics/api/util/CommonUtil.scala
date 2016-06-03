package org.ekstep.analytics.api.util

import org.joda.time.LocalDate
import org.joda.time.Weeks
import org.joda.time.DateTimeZone
import org.ekstep.analytics.api.Response
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormat
import org.ekstep.analytics.api.Params
import java.util.UUID
import org.ekstep.analytics.api.RequestBody
import org.joda.time.Duration
import org.ekstep.analytics.api.Range
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf

/**
 * @author Santhosh
 */
object CommonUtil {

    @transient val dayPeriod: DateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd");
    @transient val monthPeriod: DateTimeFormatter = DateTimeFormat.forPattern("yyyyMM");
    @transient val df: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ").withZoneUTC();
    
    def getSparkContext(parallelization: Int, appName: String): SparkContext = {

        val conf = new SparkConf().setAppName(appName);
        val master = conf.getOption("spark.master");
        // $COVERAGE-OFF$ Disabling scoverage as the below code cannot be covered as they depend on environment variables
        if (master.isEmpty) {
            conf.setMaster("local[*]");
        }
        if (!conf.contains("spark.cassandra.connection.host")) {
            conf.set("spark.cassandra.connection.host", "127.0.0.1")
        }
        // $COVERAGE-ON$
        new SparkContext(conf);
    }
    
    def closeSparkContext()(implicit sc: SparkContext) {
        sc.stop();
    }

    def roundDouble(value: Double, precision: Int): Double = {
        BigDecimal(value).setScale(precision, BigDecimal.RoundingMode.HALF_UP).toDouble;
    }

    def getWeeksBetween(fromDate: Long, toDate: Long): Int = {
        val from = new LocalDate(fromDate, DateTimeZone.UTC)
        val to = new LocalDate(toDate, DateTimeZone.UTC)
        Weeks.weeksBetween(from, to).getWeeks;
    }

    def getDayRange(count: Int): Range = {
        val endDate = DateTime.now(DateTimeZone.UTC);
        val startDate = endDate.minusDays(count);
        Range(dayPeriod.print(startDate).toInt, dayPeriod.print(endDate).toInt)
    }

    def getMonthRange(count: Int): Range = {
        val endDate = DateTime.now(DateTimeZone.UTC);
        val startDate = endDate.minusDays(count * 30);
        Range(monthPeriod.print(startDate).toInt, monthPeriod.print(endDate).toInt)
    }

    def getWeekRange(count: Int): Range = {
        val endDate = DateTime.now(DateTimeZone.UTC);
        val startDate = endDate.minusDays(count * 7);
        Range(getWeekNumber(startDate.getWeekyear, startDate.getWeekOfWeekyear), getWeekNumber(endDate.getWeekyear, endDate.getWeekOfWeekyear))
    }

    private def getWeekNumber(year: Int, weekOfWeekyear: Int): Int = {

        if (weekOfWeekyear < 10) {
            (year + "70" + weekOfWeekyear).toInt
        } else {
            (year + "7" + weekOfWeekyear).toInt
        }
    }

    def errorResponse(apiId: String, err: String): Response = {
        Response(apiId, "1.0", df.print(System.currentTimeMillis()),
            Params(UUID.randomUUID().toString(), null, "SERVER_ERROR", "failed", err),
            None);
    }

    def errorResponseSerialized(apiId: String, err: String): String = {
        JSONUtils.serialize(errorResponse(apiId, err));
    }

    def OK(apiId: String, result: Map[String, AnyRef]): Response = {
        Response(apiId, "1.0", df.print(DateTime.now(DateTimeZone.UTC).getMillis), Params(UUID.randomUUID().toString(), null, null, "successful", null), Option(result));
    }

    def getRemainingHours(): Long = {
        val now = DateTime.now(DateTimeZone.UTC);
        new Duration(now, now.plusDays(1).withTimeAtStartOfDay()).getStandardHours;
    }

}