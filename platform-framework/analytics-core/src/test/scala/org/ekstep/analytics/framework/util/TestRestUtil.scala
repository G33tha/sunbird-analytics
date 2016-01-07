package org.ekstep.analytics.framework.util

import org.ekstep.analytics.framework.BaseSpec
import org.ekstep.analytics.framework.Metadata
import org.ekstep.analytics.framework.Request
import org.ekstep.analytics.framework.Response
import org.ekstep.analytics.framework.Search
import org.ekstep.analytics.framework.SearchFilter

import com.fasterxml.jackson.core.JsonParseException

/**
 * @author Santhosh
 */
class TestRestUtil extends BaseSpec {

    "RestUtil" should "get data from learning platform API and parse it to Response Object" in {
        val url = Constants.getContentAPIUrl("org.ekstep.story.hi.elephant");
        val response = RestUtil.get[Response](url);
        response should not be null;
        response.responseCode should be("OK")
    }

    it should "throw Exception if unable to parse to Response object during GET" in {
        val url = "https://www.google.com";
        a[Exception] should be thrownBy {
            RestUtil.get[Response](url);
        }
    }
    
    it should "return error if the resource is not found" in {
        val url = Constants.getContentAPIUrl("xyz");
        val response = RestUtil.get[Response](url);
        response should not be null;
        response.responseCode should not be("OK")
    }
    
    it should "post data to learning platform API and parse body to Response Object" in {
        val url = Constants.getSearchItemAPIUrl("numeracy");
        val search = Search(Request(Metadata(Array(SearchFilter("identifier", "in", Option(Array("ek.n.q901", "ek.n.q902", "ek.n.q903"))))), 500));
        val response = RestUtil.post[Response](url, JSONUtils.serialize(search));
        response should not be null;
        response.responseCode should be("OK")
    }

    it should "throw JsonParseException if unable to parse to Response object during POST" in {
        val url = "https://www.google.com";
        a[JsonParseException] should be thrownBy {
            RestUtil.post[Response](url, "");
        }
    }
    
    it should "return error response if body is not passed during POST" in {
        val url = Constants.getSearchItemAPIUrl("numeracy");
        a[JsonParseException] should be thrownBy {
            RestUtil.post[Response](url, "");
        }
    }
    
    it should "return success response even if no data found for search query" in {
        val url = Constants.getSearchItemAPIUrl("numeracy");
        val search = Search(Request(Metadata(Array(SearchFilter("identifier", "in", Option(Array("xyz1"))))), 500));
        val response = RestUtil.post[Response](url, JSONUtils.serialize(search));
        response should not be null;
        response.responseCode should be("OK")
    }

}