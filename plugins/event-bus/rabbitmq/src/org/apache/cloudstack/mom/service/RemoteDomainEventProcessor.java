package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;

import java.util.Date;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;

public class RemoteDomainEventProcessor {

    private String hostName;
    private String userName;
    private String password;

    public RemoteDomainEventProcessor(String hostName, String userName, String password)
    {
        this.hostName = hostName;
        this.userName = userName;
        this.password = password;
    }

    private JSONArray listEvents(Date created, String eventType) throws Exception
    {
        DomainService domainService = new DomainService(hostName, userName, password);
        return domainService.listEvents(eventType, "completed", created, null);
    }

    private JSONObject getLatestEvent(JSONObject object1, JSONObject object2)
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

    public JSONObject findLatestRemoteRemoveEvent(DomainVO domain) throws Exception
    {
        JSONArray events = listEvents(domain.getCreated(), "DOMAIN.DELETE");
        if (events == null) return null;

        JSONObject latest = null;
        for(int idx = 0; idx < events.length(); idx++)
        {
            JSONObject eventJSON = events.getJSONObject(idx);
            JSONObject jsonObject = BaseService.parseEventDescription(eventJSON);
            String eventDomainPath = BaseService.getAttrValue(jsonObject, "Domain Path");

            if (eventDomainPath == null)  continue;
            if (!eventDomainPath.equals(domain.getPath()))    continue;

            latest = getLatestEvent(latest, eventJSON);
        }

        return latest;
    }

    /*public JSONObject findLatestEvent(DomainVO domain, HashMap<String, JSONArray> hashMap) throws Exception
    {
        Date created = domain.getCreated();
        if (created == null)
        {
            throw new Exception("Domain[" + domain.getName() + "] has null local create time.");
        }

        JSONObject eventObject = null;

        for(String hostName : hashMap.keySet())
        {
            JSONArray eventList = hashMap.get(hostName);
            if (eventList == null) continue;

            JSONObject jsonObject = findLatestEvent(domain, eventList);
            eventObject = getLatestEvent(jsonObject, eventObject);
        }

        return eventObject;
    }

    private JSONObject findLatestEvent(DomainVO domain, JSONArray eventList) throws Exception
    {
        String domainName = domain.getName();
        String domainPath = domain.getPath();
        Date created = domain.getCreated();
        JSONObject eventObject = null;

        for (int idx = 0; idx < eventList.length(); idx++)
        {
            try
            {
                JSONObject jsonObject = BaseService.parseEventDescription(eventList.getJSONObject(idx));
                String eventDomainPath = BaseService.getAttrValue(jsonObject, "Domain Path");
                String eventOldDomainName = BaseService.getAttrValue(jsonObject, "Old Entity Name");
                String eventNewDomainName = BaseService.getAttrValue(jsonObject, "New Entity Name");


                if (eventOldDomainName == null)
                {
                    if (eventDomainPath == null)  continue;
                    if (!eventDomainPath.equals(domainPath))    continue;
                }
                else
                {
                    if (eventNewDomainName == null)    continue;
                    if (!eventOldDomainName.equals(domainName))    continue;
                    if (!eventDomainPath.replace(domainName, eventOldDomainName).equals(domainPath))    continue;
                }

                if (!BaseInterface.hasAttribute(jsonObject, "created")) continue;

                Date jsonDate = BaseService.parseDateStr(BaseService.getAttrValue(jsonObject, "created"));
                if (jsonDate.before(created))  continue;

                eventObject = getLatestEvent(jsonObject, eventObject);
            }
            catch(Exception ex)
            {
                s_logger.error(ex.getStackTrace());
                continue;
            }
        }

        return eventObject;
    }*/
}
