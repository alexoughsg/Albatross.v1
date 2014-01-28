package com.cloud.region.api_interface;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.cloud.region.service.BaseService;
import com.cloud.utils.DateUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.Date;
import java.util.TimeZone;

public class BaseInterface {

    private Gson gson;
    private static final Logger s_logger = Logger.getLogger(BaseInterface.class);

    protected String url;
    protected String cookie;
    protected String sessionKey;

    public BaseInterface(String url)
    {
        this.url = url;
        this.cookie = null;
        this.sessionKey = null;
        this.gson = new GsonBuilder().create();
    }

    public JSONObject toJson(String serialized) throws Exception
    {
        JSONObject jsonObj = new JSONObject(serialized);
        JSONObject retObj = (JSONObject)jsonObj.get(jsonObj.keys().next().toString());

        String errorText = null;
        try
        {
            errorText = (String)retObj.get("errortext");
        }
        catch(Exception ex)
        {
        }

        if(errorText != null)
        {
            s_logger.error("Returned with failure : " + errorText);
            throw new Exception(errorText);
        }

        return retObj;
    }

    public JSONObject sendApacheGet(String paramStr) throws Exception {

        String connUrl = this.url;

        if (paramStr != null && !paramStr.equals(""))
            connUrl += "?" + paramStr;

        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(connUrl);

        // add request header
        request.addHeader("Cookie", "JSESSIONID=" + this.cookie);

        HttpResponse response = client.execute(request);
        s_logger.debug("\nSending 'GET' request to URL : " + connUrl);
        s_logger.debug("Response Code : " + response.getStatusLine().getStatusCode());

        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

        StringBuffer result = new StringBuffer();
        String line = "";
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }

        String resultStr = result.toString();

        s_logger.debug(resultStr);

        return toJson(resultStr);
    }

    // HTTP POST request
    public JSONObject sendApachePost(String paramStr) throws Exception {

        String connUrl = this.url;
        if (paramStr != null && !paramStr.equals(""))
            connUrl += "?" + paramStr;

        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(connUrl);

        // add header
        if (this.cookie != null)
        {
            post.setHeader("Cookie", "JSESSIONID=" + this.cookie);
        }

        HttpResponse response = client.execute(post);
        s_logger.debug("\nSending 'POST' request to URL : " + connUrl);
        s_logger.debug("Post parameters : " + post.getEntity());
        s_logger.debug("Response Code : " + response.getStatusLine().getStatusCode());

        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

        StringBuffer result = new StringBuffer();
        String line = "";
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }

        // if this is the response of 'login' command, store the returned cookie
        if (this.cookie == null)
        {
            try
            {
                this.cookie = response.getFirstHeader("Set-Cookie").getValue().split(";")[0].split("=")[1];
            }
            catch(Exception ex)
            {
                s_logger.error("Failed to parse 'Set-Cookie header", ex);
            }
        }

        String resultStr = result.toString();

        s_logger.debug(resultStr);

        return toJson(resultStr);
    }

    public JSONObject login(String userName, String password) throws Exception
    {
        String paramStr = "command=login&username=" + userName + "&password=" + password + "&response=json";
        JSONObject responseJson = sendApachePost(paramStr);
        this.sessionKey = (String)responseJson.get("sessionkey");

        // '{ "loginresponse" : { "timeout" : "1800", "sessionkey" : "GNUfHusIyEOsqpgFp/Q9O2zaRFQ=", "username" : "admin", "registered" : "false", "userid" : "813253a8-7c63-11e2-a26f-c9595fd30292", "lastname" : "User", "account" : "admin", "domainid" : "813221a8-7c63-11e2-a26f-c9595fd30292", "firstname" : "Admin", "type" : "1" } }'
        return responseJson;
    }


    public void logout()
    {
        try
        {
            String paramStr = "command=logout&response=json&sessionkey="  + URLEncoder.encode(this.sessionKey, "UTF-8");
            sendApacheGet(paramStr);
            // '{ "logoutresponse" : { "description" : "success" } }'
        }
        catch(Exception ex)
        {
            s_logger.error("Fail to logout", ex);
        }
    }

    public JSONObject queryAsyncJob(String jobId, String projectId)
    {
        try
        {
            // command=queryAsyncJobResult
            //    &jobId=2888ed5d-a42f-49df-9297-a4945e46d3c8
            //    &response=json
            //    &sessionkey=KPqcTgDRRT9rNMJeH%2FUc2OdBhGQ%3D
            //    &projectid=dd19bac8-38a7-43ca-af9c-eca4f9e97e13&_=1365832471891
            String paramStr = "command=queryAsyncJobResult&jobId=" + jobId + "&response=json&sessionkey=" +  URLEncoder.encode(this.sessionKey, "UTF-8");
            if (projectId != null)
            {
                paramStr += "&projectid=" + projectId;
            }
            return sendApacheGet(paramStr);
        }
        catch(Exception ex)
        {
            s_logger.error("Fail to queryAsyncJob", ex);
        }

        return null;
    }

    public JSONArray listEvents(String type, String keyword, Date startDate, Date endData)
    {
        try
        {
            TimeZone s_gmtTimeZone = TimeZone.getTimeZone("GMT");
            String paramStr = "command=listEvents&response=json&sessionkey=" +  URLEncoder.encode(this.sessionKey, "UTF-8");
            if (type != null)
            {
                paramStr += "&type=" + type;
            }
            if (keyword != null)
            {
                paramStr += "&keyword=" + URLEncoder.encode(keyword, "UTF-8");
            }
            if (startDate != null)
            {
                paramStr += "&startdate=" + URLEncoder.encode(DateUtil.displayDateInTimezone(s_gmtTimeZone, startDate), "UTF-8");
            }
            if (endData != null)
            {
                paramStr += "&endata=" + URLEncoder.encode(DateUtil.displayDateInTimezone(s_gmtTimeZone, endData), "UTF-8");
            }

            JSONObject retJson = sendApacheGet(paramStr);
            boolean hasEvents = BaseService.hasAttribute(retJson, "event");
            if (!hasEvents) return null;

            return retJson.getJSONArray("event");
        }
        catch(JSONException jex)
        {
            return null;
        }
        catch(Exception ex)
        {
            s_logger.error("Fail to listEvents", ex);
        }

        return null;
    }
}
