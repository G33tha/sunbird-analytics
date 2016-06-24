

 #check process secor-script with pidfile /var/run/secor-script.pid
 #      start = "/bin/secor-script start"
 #      stop = "/bin/secor-script stop"

#The wrapper script:
 #!/bin/bash
 CLASSPATH=secor-0.2-SNAPSHOT.jar

 case $1 in
    start)
       echo $$ > /var/run/secor-script.pid
       cd /mnt/secor-raw
       exec 2>&1 nohup java -Xms256M -Xmx512M -ea -Dsecor_group=me -Dlog4j.configuration=log4j.{{ env }}.properties -Dconfig=secor.{{ env }}.partition.properties - cp secor-me/secor-{{ secor.version }}-SNAPSHOT.jar:lib/* com.pinterest.secor.main.ConsumerMain & 1>/tmp/secor-script.out 
       ;;
     
     stop)  
       kill `cat /var/run/secor-script.pid` ;;
     
     check)
       if [ -f /var/run/secor-script.pid ] then;
          echo "SECOR PROCESS IS RUNNING"
        else
          echo "SECOR PROCESS IS NOT RUNNING"
        fi
       ;;
     *)  
       echo "usage: secor-script {start|stop|check}" ;;
 esac
 exit 0