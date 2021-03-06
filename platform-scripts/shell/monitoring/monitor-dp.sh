#!/bin/bash

log_file=$1
#webhook_url="https://hooks.slack.com/services/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"


total_jobs=`grep "BE_JOB_END" $log_file`
comp_jobs=`grep "status\":\"SUCCESS" <<< "$total_jobs"`
failed_jobs=`grep "status\":\"FAILED" <<< "$total_jobs"`

today=$(date "+%Y-%m-%d")

comp_num=0
failed_num=0
total_events=0
total_time=0

file_content="Model,Job Status,Input Events Count, Output Events Count,Time Taken(in Seconds),Date\n"
job_status="COMPLETED"

if [ "$comp_jobs" != "" ]; then
	comp_num=`echo "$comp_jobs" | wc -l | bc`
	while read -r line
	do	
	    model=`sed 's/.*model":"\(.*\)","ver.*/\1/' <<< "$line" | sed -e "s/org.ekstep.analytics.model.//g" | sed -e "s/org.ekstep.analytics.updater.//g"`
		#message=`sed 's/.*message":"\(.*\)","class.*/\1/' <<< "$line"`

		eventsIn=`sed 's/.*","inputEvents":\(.*\),"outputEvents":.*/\1/' <<< "$line"`
		sub="BE_JOB_END"
		if [ "${eventsIn/$sub}" != "$eventsIn" ] ; then
  			eventsIn=0
  		fi
		events=`sed 's/.*"outputEvents":\(.*\),"timeTaken".*/\1/' <<< "$line"`
		if [ "${events/$sub}" != "$events" ] ; then
  			events=0
  		fi
		total_events=$((total_events + events))
		time_taken=`sed 's/.*"timeTaken":\(.*\)},".*/\1/' <<< "$line"`
		total_time=`echo $total_time + $time_taken | bc`		
		event_date=`sed 's/.*{"date":"\(.*\)","inputEvents":.*/\1/' <<< "$line"`
		if [ "${event_date/$sub}" != "$event_date" ] ; then
  			event_date=""
  		fi
		file_content+="$model,$job_status,$eventsIn,$events,$time_taken,$event_date\n"
	done <<< "$comp_jobs"
fi

events="0"
time_taken="0"
job_status="FAILED"
event_date=""

if [ "$failed_jobs" != "" ]; then
	failed_num=`echo "$failed_jobs" | wc -l | bc`
	while read -r line
	do
		model=`sed 's/.*model":"\(.*\)","ver.*/\1/' <<< "$line" | sed -e "s/org.ekstep.analytics.model.//g" | sed -e "s/org.ekstep.analytics.updater.//g"`
		#message=`grep -Po '(?<="localizedMessage":")[^"]*' <<< "$line"`
		sub="BE_JOB_END"
		eventsIn=`sed 's/.*","inputEvents":\(.*\)},".*/\1/' <<< "$line"`
		if [ "${eventsIn/$sub}" != "$eventsIn" ] ; then
  			eventsIn=0
  		fi
		event_date=`sed 's/.*"data":{"date":"\(.*\)","inputEvents".*/\1/' <<< "$line"`
		if [ "${event_date/$sub}" != "$event_date" ] ; then
  			event_date=""
  		fi
  		echo "$event_date"
		file_content+="$model,$job_status,$eventsIn,$events,$time_taken,$event_date\n"
	done <<< "$failed_jobs"
fi

#echo -e $file_content > dp-monitor-$today.csv

echo "-------- Status Report --------"
echo "Number of Completed Jobs: $comp_num"
echo "Number of failed Jobs: $failed_num"
echo "Total time taken: $total_time"
echo "Total events generated: $total_events"

data='{"channel": "#analytics_monitoring", "username": "dp-monitor", "text":"*Jobs | Monitoring Report | '$today'*\nNumber of Completed Jobs: `'$comp_num'` \nNumber of failed Jobs: `'$failed_num'` \nTotal time taken: `'$total_time'`\nTotal events generated: `'$total_events'`\n\nDetailed Report:\n```'$file_content'```", "icon_emoji": ":ghost:"}'
echo "$data"
curl -X POST -H 'Content-Type: application/json' --data "$data" https://hooks.slack.com/services/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