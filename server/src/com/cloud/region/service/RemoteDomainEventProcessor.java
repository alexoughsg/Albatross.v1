package com.cloud.region.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;

public class RemoteDomainEventProcessor extends RemoteEventProcessor {

    public RemoteDomainEventProcessor(String hostName, String endPoint, String userName, String password)
    {
        this.hostName = hostName;
        this.endPoint = endPoint;
        this.userName = userName;
        this.password = password;
    }

    public JSONObject findLatestRemoteRemoveEvent(Object object) throws Exception
    {
        DomainVO domain = (DomainVO)object;

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
}
