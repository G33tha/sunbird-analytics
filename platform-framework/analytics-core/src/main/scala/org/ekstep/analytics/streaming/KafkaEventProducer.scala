package org.ekstep.analytics.streaming

import java.util.HashMap
import scala.collection.mutable.Buffer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.util.JSONUtils
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.RecordMetadata
import java.lang.Long
import org.ekstep.analytics.framework.exception.DispatcherException
import org.ekstep.analytics.framework.util.JobLogger
import org.ekstep.analytics.framework.Level._

object KafkaEventProducer {
    
    implicit val className: String = "KafkaEventProducer";

    def init(brokerList: String): KafkaProducer[String, String] = {

        // Zookeeper connection properties
        val props = new HashMap[String, Object]()
        props.put(ProducerConfig.METADATA_FETCH_TIMEOUT_CONFIG, 10000L.asInstanceOf[Long]);
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")

        new KafkaProducer[String, String](props);
    }

    def close(producer: KafkaProducer[String, String]) {
        producer.close();
    }

    def sendEvent(event: AnyRef, topic: String, brokerList: String) = {
        val producer = init(brokerList);
        val message = new ProducerRecord[String, String](topic, null, JSONUtils.serialize(event));
        producer.send(message);
        close(producer);
    }

    def sendEvents(events: Buffer[AnyRef], topic: String, brokerList: String) = {
        val producer = init(brokerList);
        events.foreach { event =>
            {
                val message = new ProducerRecord[String, String](topic, null, JSONUtils.serialize(event));
                producer.send(message);
            }
        }
        close(producer);
    }

    @throws(classOf[DispatcherException])
    def sendEvents(events: Array[String], topic: String, brokerList: String) = {
        val producer = init(brokerList);
        val firstEvent = events.head;
        val otherEvents = events.takeRight(events.length - 1);
        
        val message = new ProducerRecord[String, String](topic, firstEvent);
        val callback = new Callback {
            def onCompletion(metadata: RecordMetadata, exception: Exception) {
                if (exception != null) {
                    JobLogger.log("Exception sending events to kafka", Option(Map("message" -> exception.getLocalizedMessage)), ERROR);
                    close(producer);
                    throw new DispatcherException("Unable to send messages to Kafka", exception)
                } else {
                    JobLogger.log("Sending events to kafka", None, INFO);
                    otherEvents.foreach { event =>
                        {
                            val message = new ProducerRecord[String, String](topic, event);
                            producer.send(message);
                        }
                    }
                    close(producer);
                }
            }
        }
        producer.send(message, callback);
    }

    def publishEvents(events: Buffer[String], topic: String, brokerList: String) = {
        val producer = init(brokerList);
        events.foreach { event =>
            {
                val message = new ProducerRecord[String, String](topic, null, event);
                producer.send(message);
            }
        }
        close(producer);
    }

}