package org.ekstep.analytics.framework.dispatcher

import org.ekstep.analytics.framework.exception.DispatcherException
import java.io.FileWriter
import org.ekstep.analytics.framework.OutputDispatcher
import org.apache.spark.rdd.RDD
import org.ekstep.analytics.framework.util.JobLogger
import org.apache.commons.lang3.StringUtils
import java.nio.file.Files
import java.nio.file.Paths

/**
 * @author Santhosh
 */
object FileDispatcher extends IDispatcher {

    implicit val className = "org.ekstep.analytics.framework.dispatcher.FileDispatcher";

    @throws(classOf[DispatcherException])
    def dispatch(events: RDD[String], config: Map[String, AnyRef]){
        val filePath = config.getOrElse("file", null).asInstanceOf[String];
        if (null == filePath) {
            throw new DispatcherException("'file' parameter is required to send output to file");
        }
        val dir = filePath.substring(0, filePath.lastIndexOf("/"));
        Files.createDirectories(Paths.get(dir));
        val fw = new FileWriter(filePath, true);
        events.collect.foreach { x => { fw.write(x + "\n"); } };
        fw.close();
    }
    
    @throws(classOf[DispatcherException])
    def dispatch(events: Array[String], config: Map[String, AnyRef]){
        val filePath = config.getOrElse("file", null).asInstanceOf[String];
        if (null == filePath) {
            throw new DispatcherException("'file' parameter is required to send output to file");
        }
        val dir = filePath.substring(0, filePath.lastIndexOf("/"));
        Files.createDirectories(Paths.get(dir));
        val fw = new FileWriter(filePath, true);
        events.foreach { x => { fw.write(x + "\n"); } };
        fw.close();
    }

}