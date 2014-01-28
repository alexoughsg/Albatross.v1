package com.cloud.region.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;

import java.util.Date;

public abstract class RemoteEventProcessor {

    protected String hostName;
    protected String endPoint;
    protected String userName;
    protected String password;

    public RemoteEventProcessor()
    {

    }

    protected JSONArray listEvents(Date created, String eventType) throws Exception
    {
        BaseService baseService = new BaseService(hostName, endPoint, userName, password);
        return baseService.listEvents(eventType, "completed", created, null);
    }

    protected JSONObject getLatestEvent(JSONObject object1, JSONObject object2)
    {
        if (object1 == null && object2 == null) return null;
        if (object1 == null)    return object2;
        if (object2 == null)    return object1;

        Date date1 = BaseService.parseDateStr(BaseService.getAttrValue(object1, "created"));
        Date date2 = BaseService.parseDateStr(BaseService.getAttrValue(object2, "created"));

        if (date1 == null && date2 == null) return null;
        if (date1 == null)    return object2;
        if (date2 == null)    return object1;

        if (date1.equals(date2))    return null;
        if (date1.before(date2))    return object2;
        return object1;
    }

    abstract public JSONObject findLatestRemoteRemoveEvent(Object object) throws Exception;
}
