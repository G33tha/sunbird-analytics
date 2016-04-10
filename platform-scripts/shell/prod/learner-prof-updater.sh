#!/usr/bin/env bash

export SPARK_HOME=/home/ec2-user/spark-1.5.2-bin-hadoop2.3
export JAVA_HOME=/usr/java/current

## Job to run daily
cd /mnt/data/analytics/scripts
endDate=$(date "+%Y-%m-%d")

lp_config='{"search":{"type":"s3","queries":[{"bucket":"prod-data-store","prefix":"ss/","endDate":"'$endDate'","delta":0}]},"model":"org.ekstep.analytics.model.LearnerContentActivityUpdater","modelParams":{"apiVersion":"v2","alpha":1.0,"beta":1.0},"output":[{"to":"console","params":{"printEvent": false}},{"to":"kafka","params":{"brokerList":"10.10.1.171:9092","topic":"prod.analytics.screener"}}],"parallelization":8,"appName":"Learner Proficiency Summarizer","deviceMapping":false}'

nohup $SPARK_HOME/bin/spark-submit --master local[*] --jars /mnt/data/analytics/models/analytics-framework-0.5.jar --class org.ekstep.analytics.job.ProficiencyUpdater /mnt/data/analytics/models/batch-models-1.0.jar --config "$lp_config" > "logs/$endDate-lp-summ.log" 2>&1&