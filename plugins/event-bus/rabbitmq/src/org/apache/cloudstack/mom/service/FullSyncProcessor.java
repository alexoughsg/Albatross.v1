package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.utils.DateUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public abstract class FullSyncProcessor {

    protected List<DomainVO> localList;
    protected List<JSONObject> remoteList;
    protected JSONArray remoteArray;
    //protected List<JSONObject> remoteListForSearch;

    protected List<DomainVO> processedLocalList = new ArrayList<DomainVO>();
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

    abstract protected void synchronizeByLocal();
    abstract protected void synchronizeByRemote();

    public void synchronize()
    {
        synchronizeByLocal();

        synchronizeByRemote();
    }

    public List<DomainVO> getUnresolvedlocals()
    {
        return localList;
    }

    public List<JSONObject> getUnresolvedRemotes()
    {
        return remoteList;
    }
}
