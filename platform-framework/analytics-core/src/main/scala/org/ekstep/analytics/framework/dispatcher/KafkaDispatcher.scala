package org.ekstep.analytics.framework.dispatcher

import org.ekstep.analytics.framework.exception.DispatcherException
import org.ekstep.analytics.streaming.KafkaEventProducer

/**
 * @author Santhosh
 */
object KafkaDispatcher extends IDispatcher {
    
    @throws(classOf[DispatcherException])
    def dispatch(events: Array[String], config: Map[String, AnyRef]) : Array[String] = {
        val brokerList = config.getOrElse("brokerList", null).asInstanceOf[String];
        val topic = config.getOrElse("topic", null).asInstanceOf[String];
        if (null == brokerList) {
            throw new DispatcherException("brokerList parameter is required to send output to kafka");
        }
        if (null == topic) {
            throw new DispatcherException("topic parameter is required to send output to kafka");
        }
        KafkaEventProducer.sendEvents(events, topic, brokerList);
        events;
    }

}