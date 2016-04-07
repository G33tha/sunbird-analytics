#!/usr/bin/env bash

export SPARK_HOME=/home/ec2-user/spark-1.5.2-bin-hadoop2.3

cd /mnt/data/analytics/scripts

start_date=$1
end_date=$2
job_config='{"search":{"type":"s3","queries":[{"bucket":"sandbox-data-store","prefix":"ss/","endDate":"__endDate__","delta":6}]},"model":"org.ekstep.analytics.updater.LearnerContentActivitySummary","output":[{"to":"console","params":{"printEvent":false}},{"to":"kafka","params":{"brokerList":"172.31.1.92:9092","topic":"sandbox.analytics.screener"}}],"parallelization":10,"appName":"TestReplaySupervisor","deviceMapping":false}'

./replay-backup $start_date $end_date "sandbox-data-store" "lcas" "backup-lcas"
if [ $? == 0 ]
 	then
  	echo "Backup Done Successfully..."
  	$SPARK_HOME/bin/spark-submit --master local[*] --jars /mnt/data/analytics/models/analytics-framework-0.5.jar --class org.ekstep.analytics.job.ReplaySupervisor /mnt/data/analytics/models/batch-models-1.0.jar --model "lcas" --fromDate "$start_date" --toDate "$end_date" --config "$job_config" > "logs/$end_date-lcas.log"
else
  	echo "Unable to take backup"
fi

if [ $? == 0 ] 
	then
  		echo "Replay Supervisor Executed Successfully..."
  		echo "Deleting the back-up files"
  		./replay-delete "sandbox-data-store" "backup-lcas"
else
 	echo "Unable to take backup"
fi