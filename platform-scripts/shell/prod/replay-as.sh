#!/usr/bin/env bash

export SPARK_HOME=/home/ec2-user/spark-1.5.2-bin-hadoop2.3

cd /mnt/data/analytics/scripts

start_date=$1
end_date=$2
job_config='{"search":{"type":"s3","queries":[{"bucket":"prod-data-store","prefix":"raw/","endDate":"__endDate__","delta":0}]},"filters":[{"name":"ver","operator":"EQ","value":"1.0"}],"model":"org.ekstep.analytics.model.AserScreenSummary","modelParams":{"apiVersion":"v2"},"output":[{"to":"console","params":{"printEvent": false}},{"to":"kafka","params":{"brokerList":"10.10.1.171:9092","topic":"prod.analytics.screener"}}],"parallelization":8,"appName":"Aser Screen Summarizer","deviceMapping":true}'
echo "Backing up the aser screener summary records to s3://prod-data-store/backup-as"
./replay-backup.sh $start_date $end_date "prod-data-store" "as" "backup-as"
if [ $? == 0 ]
 	then
  	echo "Backup completed Successfully..."
  	echo "Running the Aser Screener Summary Replay..."
  	$SPARK_HOME/bin/spark-submit --master local[*] --jars /mnt/data/analytics/models/analytics-framework-0.5.jar --class org.ekstep.analytics.job.ReplaySupervisor /mnt/data/analytics/models/batch-models-1.0.jar --model "as" --fromDate "$start_date" --toDate "$end_date" --config "$job_config" > "logs/$end_date-as-replay.log"
else
  	echo "Unable to take backup"
fi

if [ $? == 0 ]
	then
  		echo "Aser Screener Summary Replay Executed Successfully..."
  		echo "Deleting the back-up files s3://prod-data-store/backup-as "
  		./replay-delete.sh "prod-data-store" "backup-as"
else
 	echo "Unable to take backup"
fi