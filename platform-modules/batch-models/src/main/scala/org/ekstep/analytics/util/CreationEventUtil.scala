package org.ekstep.analytics.util

import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.creation.model.CreationEvent
import org.ekstep.analytics.framework.conf.AppConf
import org.ekstep.analytics.creation.model.CreationPData
import org.ekstep.analytics.creation.model.ETags

object CreationEventUtil {
  
    def getEventSyncTS(event: CreationEvent): Long = {
        val timeInString = event.`@timestamp`;
        CommonUtil.getEventSyncTS(timeInString);
    }
    
    def getAppDetails(event: CreationEvent): CreationPData = {
        if(event.pdata.isEmpty) new CreationPData(AppConf.getConfig("default.creation.app.id"), "1.0") else event.pdata.get
    }
    
    def getChannelId(event: CreationEvent): String = {
        if(event.channel.isEmpty) AppConf.getConfig("default.channel.id") else event.channel.get
    }
    
    def getETags(event: CreationEvent): ETags = {
        if(event.etags.isDefined){
                event.etags.get
        }else {
               null 
        }
    }
}