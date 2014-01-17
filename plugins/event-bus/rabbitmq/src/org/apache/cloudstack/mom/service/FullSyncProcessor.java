package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONObject;
import com.cloud.utils.DateUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public abstract class FullSyncProcessor {

    protected String hostName;
    protected String userName;
    protected String password;

    protected JSONObject remoteParent;
    protected List<JSONObject> remoteList;
    protected List<JSONObject> processedRemoteList = new ArrayList<JSONObject>();

    protected Date getDate(JSONObject jsonObject, String attrName) throws Exception
    {
        TimeZone GMT_TIMEZONE = TimeZone.getTimeZone("GMT");
        String formatString = "yyyy-MM-dd'T'HH:mm:ssZ";
        String dateStr = (String) jsonObject.get(attrName);

        try
        {
            return DateUtil.parseDateString(GMT_TIMEZONE, dateStr, formatString);
        }
        catch(Exception ex)
        {
            throw new Exception("Failed to parse date string[" + dateStr + "] : " + ex.getStackTrace());
        }
    }

    protected void expungeProcessedRemotes()
    {
        for (JSONObject remoteJson : processedRemoteList)
        {
            if (!remoteList.contains(remoteJson))    continue;
            remoteList.remove(remoteJson);
        }

        //processedRemoteList.clear();
    }

    abstract protected Object create(JSONObject jsonObject, final Date created);

    abstract protected void synchronizeByLocal();
    abstract protected void synchronizeByRemote();

    abstract public void arrangeLocalResourcesToBeRemoved(FullSyncProcessor syncProcessor);
    abstract public void arrangeRemoteResourcesToBeCreated(FullSyncProcessor syncProcessor);

    abstract public void createRemoteResourcesInLocal();
    abstract public void removeLocalResources();

    public void synchronize()
    {
        synchronizeByLocal();

        synchronizeByRemote();
    }

}
