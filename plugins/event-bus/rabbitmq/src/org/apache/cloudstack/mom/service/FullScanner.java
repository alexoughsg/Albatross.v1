package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.utils.DateUtil;
import org.apache.log4j.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

    protected JSONArray listEvents(String hostName, String userName, String password, Date created)
    {
        return null;
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

    protected Date isRemoteCreated(JSONObject remoteObject)
    {
        Date created = null;
        try
        {
            DateFormat dfParse = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            created = dfParse.parse(BaseService.getAttrValue(remoteObject, "created"));
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

    protected Date isRemoteRemoved(Object object, JSONArray eventList)
    {
        return null;
    }

    protected boolean exist(Object object, ArrayList<Object> processedList)
    {
        return false;
    }

    protected void syncRemove(List localList, JSONArray events, ArrayList<Object> processedList)
    {
        for(Object object : localList)
        {
            if(exist(object, processedList))   continue;

            Date removed = isRemoteRemoved(object, events);
            if (removed != null)
            {
                remove(object, removed);
                return;
            }
        }
    }

    protected void synchronize(JSONObject remoteJson, List localList, ArrayList<Object> processedList)
    {
        Object localObject = find(remoteJson, localList);
        s_logger.info("Sync object : " + remoteJson);
        if (localObject == null)
        {
            syncCreate(remoteJson);
        }
        else
        {
            syncUpdate(localObject, remoteJson);
            processedList.add(localObject);
        }
    }

    protected void synchronize(String[] remoteServerInfo, ArrayList<Object> processedList)
    {
        s_logger.info("Starting to full scan objects with region : " + remoteServerInfo[0]);
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
        List localListBeforeProcess = findLocalList();

        for (String[] remoteRegion : remoteRegions)
        {
            try
            {
                synchronize(remoteRegion, processedList);
            }
            catch(Exception ex)
            {
                s_logger.error("Full scan failed with remote region[" + remoteRegion[0] + "]", ex);
            }
        }

        // now process the local resources that were not sync'ed
        for(String[] remoteRegion : remoteRegions)
        {
            s_logger.info("Verify objects with region : " + remoteRegion[0]);

            String hostName = remoteRegion[0];
            String userName = remoteRegion[1];
            String password = remoteRegion[2];
            try
            {
                Date created = null;
                JSONArray events = listEvents(hostName, userName, password, created);
                if (events == null || events.length() == 0)
                {
                    s_logger.info("Skipping verification because there is no remove event found in the remote.");
                    continue;
                }
                syncRemove(localListBeforeProcess, events, processedList);
                s_logger.info("Verification completed with region : " + remoteRegion[0]);
            }
            catch(Exception ex)
            {
                s_logger.error("Verification failed with remote region[" + remoteRegion[0] + "]", ex);
            }
        }
    }
}
