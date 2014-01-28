package com.cloud.region.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.AccountVO;
import com.cloud.utils.component.ComponentContext;

public class RemoteAccountEventProcessor extends RemoteEventProcessor {

    public RemoteAccountEventProcessor(String hostName, String endPoint, String userName, String password)
    {
        this.hostName = hostName;
        this.endPoint = endPoint;
        this.userName = userName;
        this.password = password;
    }

    public JSONObject findLatestRemoteRemoveEvent(Object object) throws Exception
    {
        DomainDao domainDao = ComponentContext.getComponent(DomainDao.class);
        AccountVO account = (AccountVO)object;
        DomainVO domain = domainDao.findById(account.getDomainId());

        JSONArray events = listEvents(account.getCreated(), "ACCOUNT.DELETE");
        if (events == null) return null;

        JSONObject latest = null;
        for(int idx = 0; idx < events.length(); idx++)
        {
            JSONObject eventJSON = events.getJSONObject(idx);
            JSONObject jsonObject = BaseService.parseEventDescription(eventJSON);
            String eventAccountName = BaseService.getAttrValue(jsonObject, "Account Name");
            String eventDomainPath = BaseService.getAttrValue(jsonObject, "Domain Path");

            if (eventAccountName == null)    continue;
            if (eventDomainPath == null)    continue;
            if (!BaseService.compareDomainPath(eventDomainPath, domain.getPath()))    continue;
            if (!eventAccountName.equals(account.getAccountName()))    continue;

            latest = getLatestEvent(latest, eventJSON);
        }

        return latest;
    }
}
