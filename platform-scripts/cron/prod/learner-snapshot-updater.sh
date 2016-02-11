#!/usr/bin/env bash

export SPARK_HOME=/home/ec2-user/spark-1.5.2-bin-hadoop2.3
export JAVA_HOME=/usr/java/current

## Job to run daily
cd /mnt/data/analytics/scripts
endDate=$(date "+%Y-%m-%d")

ls_config='{"search":{"type":"s3","queries":[{"bucket":"ekstep-session-summary","prefix":"prod.analytics.screener-","endDate":"'$endDate'","delta":0}]},"filters":[{"name":"eventId","operator":"EQ","value":"ME_LEARNER_ACTIVITY_SUMMARY"}],"model":"org.ekstep.analytics.updater.UpdateLearnerActivity","modelParams":{"modelVersion":"1.0","modelId":"LearnerSnapshotUpdater"},"output":[{"to":"console","params":{"printEvent": false}},{"to":"kafka","params":{"brokerList":"10.10.1.171:9092","topic":"prod.analytics.screener"}}],"parallelization":8,"appName":"Learner Snapshot Updater","deviceMapping":false}'

nohup $SPARK_HOME/bin/spark-submit --master local[*] --jars /mnt/data/analytics/models/analytics-framework-0.5.jar --class org.ekstep.analytics.job.LearnerSnapshotUpdater /mnt/data/analytics/models/batch-models-1.0.jar --config "$ls_config" > "logs/$endDate-ls-updater.log" 2>&1&