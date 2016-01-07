package org.ekstep.analytics.streaming

import kafka.serializer.Decoder
import org.ekstep.analytics.framework.Event
import kafka.utils.VerifiableProperties
import org.ekstep.analytics.framework.util.CommonUtil

class EventDecoder(props: VerifiableProperties = null) extends Decoder[Event] {
    val encoding =
        if (props == null)
            "UTF8"
        else
            props.getString("serializer.encoding", "UTF8");
            
    def fromBytes(bytes: Array[Byte]): Event = {
        CommonUtil.getEvent(new String(bytes, encoding));
    }
}