package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.utils.DateUtil;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class FullScanner {

    private static final Logger s_logger = Logger.getLogger(DomainService.class);

    public List findLocalList()
    {
        return new ArrayList();
    }

    protected String generateRandString()
    {
        int length = 10;
        String alpha = "abcdefghijklmnopqrstuvwxyz";
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < length; i++) {
            double index = Math.random() * alpha.length();
            buffer.append(alpha.charAt((int) index));
        }
        return buffer.toString();
    }

    protected Date generateRandDate()
    {
        Date date = new Date();
        long time = date.getTime();
        time -= 60 * 60 * 1000 * 24;
        date.setTime(time);
        return date;
    }

    public JSONArray findRemoteList(String[] remoteServerInfo)
    {
        return new JSONArray();
    }

    protected Object find(JSONObject jsonObject, List localList)
    {
        return null;
    }

    protected boolean compare(Object obj, JSONObject jsonObject)
    {
        return false;
    }

    protected Object create(JSONObject jsonObject, Date created)
    {
        return null;
    }

    protected void update(Object object, JSONObject jsonObject, Date modified)
    {

    }

    protected void lock(Object object, Date modified)
    {

    }

    protected void disable(Object object, Date modified)
    {

    }

    protected void enable(Object object, Date modified)
    {

    }

    protected void remove(Object object, Date removed)
    {

    }

    protected Date getDate(JSONObject jsonObject, String attrName)
    {
        try
        {
            TimeZone GMT_TIMEZONE = TimeZone.getTimeZone("GMT");
            return DateUtil.parseDateString(GMT_TIMEZONE, (String) jsonObject.get(attrName));
        }
        catch(Exception ex)
        {
            return null;
        }
    }

    protected String getAttrValueInJson(JSONObject jsonObject, String attrName)
    {
        try
        {
            return (String) jsonObject.get(attrName);
        }
        catch(Exception ex)
        {
            return null;
        }
    }

    protected Date isRemoteCreated(JSONObject remoteObject)
    {
        Date created = null;
        try
        {
            created = DateUtil.parseTZDateString(getAttrValueInJson(remoteObject, "created"));
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find removal history because the given created is not valid", ex);
            return null;
        }

        return created;
    }

    protected void syncCreate(JSONObject remoteObject)
    {
        // if
        //    1. this local resource has been deleted after being created and
        //    2. local resource's deletion timestamp is later than the remote resource's creation timestamp,
        // don't create a local resource
        //
        // otherwise, create a local resource with remote resource's create timestamp
        Date created = isRemoteCreated(remoteObject);
        if (created != null)
        {
            create(remoteObject, created);
        }
    }

    protected void syncUpdate(Object object, JSONObject jsonObject)
    {

    }

    protected Date isRemoteRemoved(Object object, String hostName, String userName, String password)
    {
        return null;
    }

    protected void syncRemove(Object object, String[][] remoteRegions)
    {
        for(String[] remoteRegion : remoteRegions)
        {
            String hostName = remoteRegion[0];
            String userName = remoteRegion[1];
            String password = remoteRegion[2];

            Date removed = isRemoteRemoved(object, hostName, userName, password);
            if (removed != null)
            {
                remove(object, removed);
                return;
            }
        }
    }

    protected void synchronize(JSONObject remoteJson, List localList, List processedList)
    {
        Object localObject = find(remoteJson, localList);
        if (localObject == null)
        {
            syncCreate(remoteJson);
        }
        else
        {
            syncUpdate(localObject, remoteJson);
        }

        if (localObject != null && !processedList.contains(localObject))    processedList.add(localObject);
    }

    protected void synchronize(String[] remoteServerInfo, List processedList)
    {
        JSONArray remoteList = findRemoteList(remoteServerInfo);
        List localList = findLocalList();

        for(int idx = 0; idx < remoteList.length(); idx++)
        {
            try
            {
                JSONObject remoteJson = remoteList.getJSONObject(idx);
                synchronize(remoteJson, localList, processedList);
            }
            catch (Exception ex)
            {
                s_logger.error("remote objects : " + remoteList);
                s_logger.error("Failed in synchronize object in index[" + idx + "]", ex);
            }
        }
    }

    public void refreshAll(String[][] remoteRegions)
    {
        ArrayList<Object> processedList = new ArrayList<Object>();
        for (int index = 0; index < remoteRegions.length; index++)
        {
            try
            {
                synchronize(remoteRegions[index], processedList);
            }
            catch(Exception ex)
            {
                s_logger.error("Failed in synchronize with remote region[" + remoteRegions[index][0] + "]", ex);
            }
        }

        // now process the local resources that were not sync'ed
        List localList = findLocalList();
        for(Object object : localList)
        {
            if (processedList.contains(object)) continue;

            try
            {
                syncRemove(object, remoteRegions);
            }
            catch(Exception ex)
            {
                s_logger.error("Failed in syncRemove", ex);
            }
        }
    }
}
