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

object KafkaEventProducer {

    def init(brokerList: String): KafkaProducer[String, String] = {

        // Zookeeper connection properties
        val props = new HashMap[String, Object]()
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
        props.put(ProducerConfig.TIMEOUT_CONFIG, long2Long(60000))

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

    def sendEvents(events: Array[String], topic: String, brokerList: String) = {
        val producer = init(brokerList);
        events.foreach { event =>
            {
                val message = new ProducerRecord[String, String](topic, null, event);
                producer.send(message);
            }
        }
        close(producer);
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