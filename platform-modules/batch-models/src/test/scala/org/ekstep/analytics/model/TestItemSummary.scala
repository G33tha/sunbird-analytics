package org.ekstep.analytics.model

import org.ekstep.analytics.framework.Event
import org.ekstep.analytics.framework.util.JSONUtils

class TestItemSummary extends SparkSpec(null) {

    "ItemSummary" should "generate item responses" in {

        val rdd = loadFile[Event]("src/test/resources/item-summary/test-data.log");
        val oe_assessResValue = rdd.filter { x => x.eid.equals("OE_ASSESS") }.collect()(0).edata.eks.resvalues.last
        oe_assessResValue.get("ans1").get.asInstanceOf[Int] should be (10)
        
        val rdd2 = ItemSummary.execute(rdd, None);
        val me = rdd2.collect();
        for(e<-me){
            e.dimensions.gdata.get.id should be ("domain_3915")
            e.dimensions.gdata.get.ver should be ("5")
        }
        me.length should be(6);
        val event = me(0)
        event.eid should be("ME_ITEM_SUMMARY")
        event.syncts should be(1468473690224L)
        event.ver should be("1.0")
        event.mid should be("BB444778C3713EA749BBB472AC85E400")
        event.uid should be("3a80091a-47af-4baa-860d-8aafb0d27f69")
        event.context.granularity should be("EVENT")

        val eksMap = event.edata.eks.asInstanceOf[Map[String, AnyRef]]
        
        eksMap.get("itemId").get.asInstanceOf[String] should be ("domain_4491")
        eksMap.get("score").get.asInstanceOf[Int] should be (0)
        val res = eksMap.get("res").get.asInstanceOf[Array[String]]
        res.length should be (1)
        res(0) should be ("ans1:10")
        eksMap.get("mc").get.asInstanceOf[Array[AnyRef]].length should be (0)
        
        val itemRes = event.edata.eks.asInstanceOf[Map[String,AnyRef]].get("res").get.asInstanceOf[Array[String]](0)
        itemRes should be ("ans1:10")
    }
}