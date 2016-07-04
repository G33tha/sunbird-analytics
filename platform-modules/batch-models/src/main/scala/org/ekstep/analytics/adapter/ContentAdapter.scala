package org.ekstep.analytics.adapter

import org.ekstep.analytics.framework._
import org.ekstep.analytics.framework.util.RestUtil
import org.ekstep.analytics.framework.exception.DataAdapterException
import org.ekstep.analytics.framework.util.CommonUtil
import org.ekstep.analytics.framework.exception.DataAdapterException
import org.ekstep.analytics.util.Constants

/**
 * @author Santhosh
 */
object ContentAdapter extends BaseAdapter {
    
    val relations = Array("concepts", "tags");

    def getAllContent(): Array[Content] = {
        val cr = RestUtil.get[Response](Constants.getContentList);
        checkResponse(cr);
        val contents = cr.result.contents.getOrElse(null);
        contents.map(f => {
            getContentWrapper(f);
        })
    }
    
    def getContentWrapper(content: Map[String, AnyRef]): Content = {
        val mc = content.getOrElse("concepts", List[String]()).asInstanceOf[List[String]].toArray;
        Content(content.get("identifier").get.asInstanceOf[String], content.filterNot(p => relations.contains(p._1)), CommonUtil.getTags(content), mc);
    }
    
    def getContentItems(contentId: String, apiVersion: String = "v2") : Array[Item] = {
        val cr = RestUtil.get[Response](Constants.getContentItems(apiVersion, contentId));
        checkResponse(cr);
        val items = cr.result.items.getOrElse(null);
        
        items.map(f => {
            getItemWrapper(f);
        })
    }
    
    def getItemWrapper(item: Map[String, AnyRef]): Item = {
        val mc = item.getOrElse("concepts", List[String]()).asInstanceOf[List[String]].toArray;
        Item(item.get("identifier").get.asInstanceOf[String], item.filterNot(p => relations.contains(p._1)), CommonUtil.getTags(item), Option(mc), None);
    }
    
}