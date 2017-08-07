#!/usr/bin/env bash

#export SPARK_HOME=/Users/santhosh/ekStep/spark-1.5.0-bin-hadoop2.3
#export PROJECT_HOME=/Users/santhosh/ekStep/github-repos/Learning-Platform-Analytics

export content2vec_scripts_path=$PROJECT_HOME/platform-scripts/python/main/vidyavaani
export KAFKA_HOME=/Users/santhosh/ekStep/kafka_2.11-0.10.0.1

## Job to run daily
cd $PROJECT_HOME/platform-scripts/shell/local
source model-config.sh
today=$(date "+%Y-%m-%d")

if [ -z "$job_config" ]; then job_config=$(config $1); fi

$KAFKA_HOME/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic local.analytics.job_queue

$SPARK_HOME/bin/spark-submit --master local[*] --jars $PROJECT_HOME/platform-framework/analytics-job-driver/target/analytics-framework-1.0.jar --class org.ekstep.analytics.job.JobExecutor $PROJECT_HOME/platform-modules/batch-models/target/batch-models-1.0.jar --model "$1" --config "$job_config" > "logs/$today-job-execution.log"