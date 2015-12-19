package org.ekstep.ilimi.analytics.framework

import java.io.Serializable
import java.util.Date

class Models extends Serializable {}

// Raw Event Model
case class Eks(loc: Option[String], mc: Option[Array[String]], mmc: Option[Array[String]], pass: Option[String], qid: Option[String], qtype: Option[String], qlevel: Option[String], score: Option[Int], maxscore: Option[Int], res: Option[Array[String]], exres: Option[Array[String]], length: Option[String], exlength: Option[Double], atmpts: Option[Int], failedatmpts: Option[Int], category: Option[String], current: Option[String], max: Option[String], `type`: Option[String], extype: Option[String], id: Option[String], gid: Option[String])
case class EData(eks: Eks)
case class GData(id: Option[String], ver: Option[String])
case class Event(eid: Option[String], ts: Option[String], `@timestamp`: Option[String], ver: Option[String], gdata: Option[GData], sid: Option[String], uid: Option[String], did: Option[String], edata: EData)

// Computed Event Model
case class CData(id: String, `type`: Option[String]);
case class MeasuredEvent(eid: String, ts: Long, ver: String, uid: Option[String], gdata: Option[GData], cdata: Option[CData], context: Context, dimensions: Dimensions, edata: MEEdata);
case class Dimensions(uid: Option[String], gdata: Option[GData], cdata: Option[CData], domain: Option[String], user: Option[UserProfile], loc: Option[String] = None);
case class PData(id: String, model: String, ver: String);
case class DtRange(from: Long, to: Long);
case class Context(pdata: PData, dspec: Option[Map[String, String]] = None, granularity: Option[String] = None, dt_range: Option[DtRange] = None);
case class MEEdata(eks: AnyRef);

// User Model
case class User(name: String, encoded_id: String, ekstep_id: String, gender: String, dob: Date, language_id: Int);
case class UserProfile(uid: String, gender: String, age: Int);

// Analytics Framework Job Models
case class Query(bucket: Option[String] = None, prefix: Option[String] = None, startDate: Option[String] = None, endDate: Option[String] = None, delta: Option[Int] = None, brokerList: Option[String] = None, topic: Option[String] = None, windowType: Option[String] = None, windowDuration: Option[Int] = None, file: Option[String] = None)
case class Filter(name: String, operator: String, value: Option[AnyRef] = None);
case class Sort(name: Option[String], order: Option[String]);
case class Dispatcher(to: String, params: Map[String, AnyRef]);
case class Fetcher(`type`: String, query: Option[Query], queries: Option[Array[Query]]);
case class JobConfig(search: Fetcher, filters: Option[Array[Filter]], sort: Option[Sort], model: String, modelParams: Option[Map[String, AnyRef]], output: Option[Array[Dispatcher]], parallelization: Option[Int], appName: Option[String]);

// LP API Response Model
case class Params(resmsgid: Option[String], msgid: Option[String], err: Option[String], status: Option[String], errmsg: Option[String])
case class Result(content: Option[Map[String, AnyRef]], questionnaire: Option[Map[String, AnyRef]], assessment_item: Option[Map[String, AnyRef]], assessment_items: Option[Array[Map[String, AnyRef]]], assessment_item_set: Option[Map[String, AnyRef]]);
case class Response(id: String, ver: String, ts: String, params: Params, responseCode: String, result: Result);

// Search Items
case class SearchFilter(property: String, operator: String, value: Option[AnyRef]);
case class Metadata(filters: Array[SearchFilter])
case class Request(metadata: Metadata, resultSize: Int)
case class Search(request: Request);

// Adapter Models
case class MicroConcept(id: String, metadata: Map[String, AnyRef]);
case class Item(id: String, metadata: Map[String, AnyRef], tags: Option[Array[String]], mc: Option[Array[String]], mmc: Option[Array[String]]);
case class ItemSet(id: String, metadata: Map[String, AnyRef], items: Array[Item], tags: Option[Array[String]], count: Int);
case class Questionnaire(id: String, metadata: Map[String, AnyRef], itemSets: Array[ItemSet], items: Array[Item], tags: Option[Array[String]]);
case class Content(id: String, metadata: Map[String, AnyRef], tags: Option[Array[String]], questionnaires: Option[Array[Questionnaire]]);

