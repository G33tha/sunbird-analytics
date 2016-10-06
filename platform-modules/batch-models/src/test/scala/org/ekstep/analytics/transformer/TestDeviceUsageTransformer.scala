package org.ekstep.analytics.transformer

import org.ekstep.analytics.model.SparkSpec
import org.ekstep.analytics.model.DeviceUsageSummary
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.util.JSONUtils
import com.datastax.spark.connector.cql.CassandraConnector
import org.ekstep.analytics.util.Constants
import com.datastax.spark.connector._

class TestDeviceUsageTransformer extends SparkSpec(null) {
  
    "DeviceUsageTransformer" should "perform binning and outlier on DUS" in {

        CassandraConnector(sc.getConf).withSessionDo { session =>
            
            session.execute("TRUNCATE device_db.device_usage_summary;");
            session.execute("INSERT INTO device_db.device_usage_summary(device_id, avg_num_launches, avg_time, end_time, last_played_content, last_played_on, mean_play_time, mean_play_time_interval, num_contents, num_days, num_sessions, play_start_time, start_time, total_launches, total_play_time, total_timespent) VALUES ('9ea6702483ff7d4fcf9cb886d0ff0e1ebc25a036', 0.01, 0.07, 1459641600, 'domain_68601', 1461715199, 10, 0, 2, 410, 1, 1459641600, 1459641600, 3, 10, 30);");
            session.execute("INSERT INTO device_db.device_usage_summary(device_id, avg_num_launches, avg_time, end_time, last_played_content, last_played_on, mean_play_time, mean_play_time_interval, num_contents, num_days, num_sessions, play_start_time, start_time, total_launches, total_play_time, total_timespent) VALUES ('9ea6702483ff7d4fcf9cb886d0ff0e1ebc25a043', 0.01, 0.07, 1459641600, '', 1461715199, 10, 0, 2, 410, 1, 1459641600, 1459641600, 3, 10, 30);");
        
        }
        
        val table = sc.cassandraTable[DeviceUsageSummary](Constants.DEVICE_KEY_SPACE_NAME, Constants.DEVICE_USAGE_SUMMARY_TABLE)
        val out = DeviceUsageTransformer.excecute(table)
        out.count() should be(table.count())
    }
    
}