package org.ekstep.analytics.model

import org.ekstep.analytics.framework.MeasuredEvent
import org.ekstep.analytics.framework.DerivedEvent
import org.ekstep.analytics.framework.util.JSONUtils
import com.datastax.spark.connector.cql.CassandraConnector

class TestDeviceUsageSummary extends SparkSpec(null) {

    "DeviceUsageSummary" should "generate DeviceUsageSummary events from a sample file and pass all positive test cases " in {

        CassandraConnector(sc.getConf).withSessionDo { session =>
            session.execute("TRUNCATE learner_db.device_usage_summary;");
        }

        val rdd1 = loadFile[DerivedEvent]("src/test/resources/device-usage-summary/test_data_1.log");
        val rdd2 = DeviceUsageSummaryModel.execute(rdd1, Option(Map("modelId" -> "DeviceUsageSummarizer", "granularity" -> "DAY")));
        val me = rdd2.collect()
        me.length should be(1)
        val event1 = me(0);

        event1.eid should be("ME_DEVICE_USAGE_SUMMARY");
        event1.context.pdata.model should be("DeviceUsageSummarizer");
        event1.context.pdata.ver should be("1.0");
        event1.context.granularity should be("DAY");
        event1.context.date_range should not be null;

        val eks = event1.edata.eks.asInstanceOf[Map[String, AnyRef]]
        eks.get("num_days").get should be(12)
        eks.get("start_time").get should be(1460627512768L)
        eks.get("avg_time").get should be(2.5)
        eks.get("avg_num_launches").get should be(0.25)
        eks.get("end_time").get should be(1461669647260L)

        val rdd3 = loadFile[DerivedEvent]("src/test/resources/device-usage-summary/test_data_2.log");
        val rdd4 = DeviceUsageSummaryModel.execute(rdd3, Option(Map("modelVersion" -> "1.0", "producerId" -> "AnalyticsDataPipeline")));
        val me2 = rdd4.collect()
        me2.length should be(2)

        val event2 = me2(1);

        event2.eid should be("ME_DEVICE_USAGE_SUMMARY");
        event2.context.pdata.model should be("DeviceUsageSummarizer");
        event2.context.pdata.ver should be("1.0");
        event2.context.granularity should be("CUMULATIVE");
        event2.context.date_range should not be null;

        val eks2 = event2.edata.eks.asInstanceOf[Map[String, AnyRef]]
        eks2.get("num_days").get should be(46)
        eks2.get("start_time").get should be(1458886281000L)
        eks2.get("avg_time").get should be(1.2)
        eks2.get("avg_num_launches").get should be(0.09)
        eks2.get("end_time").get should be(1462869647260L)

        val event3 = me2(0);

        event3.eid should be("ME_DEVICE_USAGE_SUMMARY");
        event3.context.pdata.model should be("DeviceUsageSummarizer");
        event3.context.pdata.ver should be("1.0");
        event3.context.granularity should be("CUMULATIVE");
        event3.context.date_range should not be null;

        val eks3 = event3.edata.eks.asInstanceOf[Map[String, AnyRef]]
        eks3.get("num_days").get should be(0)
        eks3.get("start_time").get should be(1460627674628L)
        eks3.get("avg_time").get should be(70.0)
        eks3.get("avg_num_launches").get should be(5.0)
        eks3.get("end_time").get should be(1460627674628L)
    }

    it should "generate DeviceUsageSummary event with start_time = March 1st 2015 " in {

        CassandraConnector(sc.getConf).withSessionDo { session =>
            session.execute("TRUNCATE learner_db.device_usage_summary;");
        }

        val rdd1 = loadFile[DerivedEvent]("src/test/resources/device-usage-summary/test_data_4.log");
        val rdd2 = DeviceUsageSummaryModel.execute(rdd1, Option(Map("modelId" -> "DeviceUsageSummarizer", "granularity" -> "DAY")));
        val me = rdd2.collect()
        me.length should be(1)
        val event1 = me(0);

        event1.eid should be("ME_DEVICE_USAGE_SUMMARY");
        event1.context.pdata.model should be("DeviceUsageSummarizer");
        event1.context.pdata.ver should be("1.0");
        event1.context.granularity should be("DAY");
        event1.context.date_range should not be null;

        val eks = event1.edata.eks.asInstanceOf[Map[String, AnyRef]]
        eks.get("num_days").get should be(410)
        eks.get("start_time").get should be(1425168000000L)
        eks.get("avg_time").get should be(0.07)
        eks.get("avg_num_launches").get should be(0.01)
        eks.get("end_time").get should be(1460627728979L)
    }
}