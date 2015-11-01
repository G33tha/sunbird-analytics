package org.ekstep.ilimi.analytics.framework.dispatcher

import org.ekstep.ilimi.analytics.framework.exception.DispatcherException
import scala.io.Source
import java.io.PrintWriter
import org.ekstep.ilimi.analytics.framework.exception.DispatcherException

/**
 * @author Santhosh
 */
object ScriptDispatcher {

    @throws(classOf[DispatcherException])
    def outputToScript(script: String, envVariables: Map[String, String], events: Array[String]) = {
        val envParams = envVariables.map(f => f._1 + "=" + f._2).toArray;
        val proc = Runtime.getRuntime.exec(script, envParams);
        new Thread("stderr reader for " + script) {
            override def run() {
                for (line <- Source.fromInputStream(proc.getErrorStream).getLines)
                    Console.err.println(line)
            }
        }.start();
        new Thread("stdin writer for " + script) {
            override def run() {
                val out = new PrintWriter(proc.getOutputStream)
                for (elem <- events)
                    out.println(elem)
                out.close()
            }
        }.start();
        val outputLines = Source.fromInputStream(proc.getInputStream).getLines;
        val exitStatus = proc.waitFor();
        if (exitStatus != 0) {
            throw new DispatcherException("Script exited with non zero status");
        }
    }

}