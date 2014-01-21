package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import org.apache.cloudstack.mom.api_interface.BaseInterface;
import org.apache.log4j.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

public class BaseService {

    private static final Logger s_logger = Logger.getLogger(BaseService.class);

    protected String hostName;
    protected String endPoint;
    protected String userName;
    protected String password;
    protected String url;

    public BaseService(String hostName, String endPoint, String userName, String password)
    {
        this.hostName = hostName;
        this.endPoint = endPoint;
        this.userName = userName;
        this.password = password;
        //this.url = "http://" + hostName + ":8080/client/api";
        this.url = endPoint + "api";
    }

    protected BaseInterface getInterface()
    {
        return null;
    }

    public static boolean compareDomainPath(String path1, String path2)
    {
        path1 = path1.replace("ROOT", "");
        path2 = path2.replace("ROOT", "");

        if (path1.endsWith("/"))
        {
            path1 = path1.substring(0, path1.length()-1);
        }

        if (path2.endsWith("/"))
        {
            path2 = path2.substring(0, path2.length()-1);
        }


        return path1.equals(path2);
    }


    public static Date parseDateStr(String dateStr)
    {
        if (dateStr == null)    return null;
        Date created = null;
        try
        {
            DateFormat dfParse = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            created = dfParse.parse(dateStr);
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to parse date string", ex);
            return null;
        }

        return created;
    }

    public static String getAttrValue(JSONObject obj, String attrName)
    {
        try
        {
            if (!BaseInterface.hasAttribute(obj, attrName)) return null;

            return obj.getString(attrName);
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to get value of [" + attrName + "] : " + obj);
            //throw new Exception("Failed to find attr value for " + attrName);
            return null;
        }
    }

    public static JSONObject parseEventDescription(JSONObject eventJson)
    {
        JSONObject jsonObject = new JSONObject();

        String description = "";
        try
        {
            description = eventJson.getString("description");

        }
        catch(Exception ex)
        {
            return jsonObject;
        }

        StringTokenizer tz = new StringTokenizer(description, ".,");
        while(tz.hasMoreTokens())
        {
            try
            {
                String token = tz.nextToken();
                String[] splitted = token.split(":");
                if (splitted.length != 2)   continue;
                jsonObject.put(splitted[0].trim(), splitted[1].trim());
            }
            catch(Exception ex)
            {
                continue;
            }
        }

        return jsonObject;
    }

    protected String getErrorText(JSONObject jsonObject)
    {
        try
        {
            return jsonObject.getString("errortext");
        }
        catch(Exception ex)
        {
            return null;
        }
    }

    protected JSONObject findJSONObject(JSONArray jsonArray, String[] attrNames, String[] attrValues) throws Exception
    {
        for(int index = 0; index < jsonArray.length(); index++)
        {
            JSONObject obj = jsonArray.getJSONObject(index);

            int aIndex = 0;
            for(; aIndex < attrNames.length; aIndex++)
            {
                String value = BaseService.getAttrValue(obj, attrNames[aIndex]);
                if(!BaseService.compareDomainPath(value, attrValues[aIndex]))
                {
                    break;
                }
            }

            if (aIndex == attrNames.length)  return jsonArray.getJSONObject(index);
        }

        s_logger.error("Failed to find json for " + attrNames + ", " + attrValues);
        throw new Exception("Failed to find json for " + attrNames + ", " + attrValues);
    }

    protected JSONObject queryAsyncJob(JSONObject retJson) throws Exception
    {
        String jobId = BaseService.getAttrValue(retJson, "jobid");
        String projectId = null;
        try
        {
            projectId = BaseService.getAttrValue(retJson, "projectid");
        }
        catch(Exception ex)
        {
        }
        if (jobId == null && projectId == null)
        {
            s_logger.error("Failed to find async job status for " + retJson);
            return null;
        }

        int jobStatus = 0;
        int waitSeconds = 1;
        JSONObject resJson = null;

        while (jobStatus == 0)
        {
            Thread.sleep(waitSeconds * 1000);
            resJson = getInterface().queryAsyncJob(jobId, projectId);
            s_logger.info("res = " + resJson);
            jobStatus = resJson.getInt("jobstatus");
        }

        JSONObject jobResult = resJson.getJSONObject("jobresult");
        String errorText = getErrorText(jobResult);
        if (errorText != null)
        {
            s_logger.error("Async job failed : " + errorText);
            throw new Exception("Async job failed : " + errorText);
        }

        return jobResult;
    }

    protected JSONArray listEvents(String type, String keyword, Date startDate, Date endDate) throws Exception
    {
        BaseInterface apiInterface = new BaseInterface(this.url);
        try
        {
            apiInterface.login(this.userName, this.password);

            JSONArray eventArray = apiInterface.listEvents(type, keyword, startDate, endDate);
            s_logger.info("Successfully retrieved events with type[" + type + "], keyword[" + keyword + "], startDate[" + startDate + "], endDate[" + endDate + "] in host[" + this.hostName + "]");
            return eventArray;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to retrieve events with type[" + type + "], keyword[" + keyword + "], startDate[" + startDate + "], endDate[" + endDate + "] in host[" + this.hostName + "]", ex);
            throw ex;
        }
        finally {
            apiInterface.logout();
        }
    }
}
