package org.ekstep.analytics.framework

import org.scalatest._
import org.ekstep.analytics.framework.util.JSONUtils
import org.ekstep.analytics.framework.conf.AppConf
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * @author Santhosh
 */
class TestJobDriver extends FlatSpec with Matchers {

    "TestJobDriver" should "successfully test the job driver" in {

        val jobConfig = JobConfig(
            Fetcher("local", None, Option(Array(Query(None, None, None, None, None, None, None, None, None, Option("src/test/resources/sample_telemetry.log"))))),
            Option(Array[Filter](Filter("eventId", "IN", Option(Array("OE_ASSESS", "OE_START", "OE_END", "OE_LEVEL_SET"))))),
            None,
            "org.ekstep.analytics.framework.TestModel",
            Option(Map()),
            Option(Array(Dispatcher("console", Map("printEvent" -> false.asInstanceOf[AnyRef])))),
            Option(8),
            Option("TestJobDriver"),
            Option(true))

        noException should be thrownBy {
            val baos = new ByteArrayOutputStream
            val ps = new PrintStream(baos)
            Console.setOut(ps);
            JobDriver.run[Event]("batch", JSONUtils.serialize(jobConfig), new TestModel);
            baos.toString should include ("(Total Events Size,1699)");
            baos.close()
        }
    }

    it should "invoke stream job driver" in {
        val jobConfig = JobConfig(Fetcher("stream", None, None), None, None, "", None, None, None, None)
        JobDriver.run("streaming", JSONUtils.serialize(jobConfig), new TestModel);
    }

    it should "thrown an exception if unknown job type is found" in {
        val jobConfig = JobConfig(Fetcher("stream", None, None), None, None, "", None, None, None, None)
        a[Exception] should be thrownBy {
            JobDriver.run("xyz", JSONUtils.serialize(jobConfig), new TestModel);
        }
    }
    
    it should "thrown an exception if unable to parse the config file" in {
        a[Exception] should be thrownBy {
            JobDriver.run("streaming", JSONUtils.serialize(""), new TestModel);
        }
    }

}