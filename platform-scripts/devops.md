## One click deployment of Data Products ##

### Setup ###

1. Install JDK 8
2. Download Apache Spark pre-built with hadoop 2.3 - `http://mirror.fibergrid.in/apache/spark/spark-1.5.2/spark-1.5.2-bin-hadoop2.3.tgz`
3. Extract Spark in the $USER_HOME directory - `/home/ec2-user/spark-1.5.2-bin-hadoop2.3`
4. Setup SPARK_HOME to as `/home/ec2-user/spark-1.5.2-bin-hadoop2.3`
5. Add `$SPARK_HOME/bin` to the `$PATH`
6. Add the aws keys to the `/etc/environment`. Key names are `aws_key` and `aws_secret`.
7. Mount the EBS volume to `/mnt/data`.
8. Create directory `analytics` under `/mnt/data`
9. Download cassandra from `http://mirror.fibergrid.in/apache/cassandra/2.2.6/apache-cassandra-2.2.6-bin.tar.gz`
10. Extract cassandra to `/mnt/data/analytics/apache-cassandra-2.2.6`
11. Setup CASSANDRA_HOME to as `/mnt/data/analytics/apache-cassandra-2.2.6`
12. Add `$CASSANDRA_HOME/bin` to the `$PATH`
13. Start cassandra service - `$CASSANDRA_HOME/bin/cassandra &`

### Build ###

Checkout analytics platform code - `https://github.com/ekstep/Learning-Platform-Analytics`

**1) Create keyspaces and tables in cassandra**

```sh
$CASSANDRA_HOME/bin/cqlsh -f Learning-Platform-Analytics/platform-scripts/database/data.cql
```

**2) Build analytics framework**

```sh
cd Learning-Platform-Analytics/platform-framework
mvn clean install # Build framework
mvn scoverage:report # Generate code coverage report

# Location of the Artifact
Learning-Platform-Analytics/platform-framework/analytics-job-driver/target/analytics-framework-1.0.jar
```

**3) Build data products**

```sh
cd Learning-Platform-Analytics/platform-modules
mvn clean install # Build data product
mvn scoverage:report # Generate code coverage report

# Location of the Artifact
Learning-Platform-Analytics/platform-modules/batch-models/target/batch-models-1.0.jar
```


### Deploy ###

1. Copy/replace artifacts `analytics-framework-1.0.jar` and `batch-models-1.0.jar` to `/mnt/data/analytics/models`
2. Copy/replace scripts from `Learning-Platform-Analytics/platform-scripts/shell/deploy` to `/mnt/data/analytics/scripts`
3. Run the cql script to create new cassandra tables - `$CASSANDRA_HOME/bin/cqlsh -f Learning-Platform-Analytics/platform-scripts/database/data.cql`
4. Setup cron jobs as per the cron script located in `Learning-Platform-Analytics/platform-scripts/shell/cron-instructions.md`

***

## One click deployment of Analytics API ##

### Setup ###

1. Install JDK 8
2. Create directory `/mnt/data/analytics/api` - Access should be to the home user

### Build ###

Checkout analytics platform code - `https://github.com/ekstep/Learning-Platform-Analytics`

**1) Build API**

```sh
cd Learning-Platform-Analytics/platform-api
mvn clean install # Build framework
mvn scoverage:report # Generate code coverage report
mvn play2:dist -pl analytics-api # To generate artifact

# Location of the Artifact
Learning-Platform-Analytics/platform-api/analytics-api/target/dist/analytics-api-1.0-dist.zip
```

### Deploy ###

1. Copy/replace artifacts `analytics-api-1.0-dist.zip` to `/mnt/data/analytics/api`
2. Extract `analytics-api-1.0-dist.zip` to `analytics-api-1.0`
3. Provide execute permissiont to the script `analytics-api-1.0/start`
4. Edit the `start` script and replace `play.core.server.NettyServer` with `play.core.server.ProdServerStart`
5. Update the command `exec java $* -cp "$classpath" play.core.server.ProdServerStart $scriptdir` to `exec java $* -cp "$classpath" -Dconfig.resource=prod.conf play.core.server.ProdServerStart $scriptdir` in the `start` script
6. Start the api server `nohup /analytics-api-1.0/start &`

***


## One click deployment of Secor ##

### Software Setup ###

1. Install JDK 8
2. 

