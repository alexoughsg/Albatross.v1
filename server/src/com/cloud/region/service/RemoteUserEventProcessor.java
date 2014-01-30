package com.cloud.region.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.component.ComponentContext;

public class RemoteUserEventProcessor extends RemoteEventProcessor {

    public RemoteUserEventProcessor(String hostName, String endPoint, String userName, String password)
    {
        this.hostName = hostName;
        this.endPoint = endPoint;
        this.userName = userName;
        this.password = password;
    }

    public JSONObject findLatestRemoteRemoveEvent(Object object) throws Exception
    {
        DomainDao domainDao = ComponentContext.getComponent(DomainDao.class);
        AccountDao accountDao = ComponentContext.getComponent(AccountDao.class);
        UserVO user = (UserVO)object;
        AccountVO account = accountDao.findById(user.getAccountId());
        DomainVO domain = domainDao.findById(account.getDomainId());

        JSONArray events = listEvents(user.getCreated(), "USER.DELETE");
        if (events == null) return null;

        JSONObject latest = null;
        for(int idx = 0; idx < events.length(); idx++)
        {
            JSONObject eventJSON = events.getJSONObject(idx);
            JSONObject jsonObject = BaseService.parseEventDescription(eventJSON);
            String eventUserName = BaseService.getAttrValue(jsonObject, "User Name");
            String eventAccountName = BaseService.getAttrValue(jsonObject, "Account Name");
            String eventDomainPath = BaseService.getAttrValue(jsonObject, "Domain Path");

            if (eventUserName == null)    continue;
            if (eventAccountName == null)    continue;
            if (eventDomainPath == null)    continue;
            if (!BaseService.compareDomainPath(eventDomainPath, domain.getPath()))    continue;
            if (!eventAccountName.equals(account.getAccountName()))    continue;
            if (!eventUserName.equals(user.getUsername()))    continue;

            latest = getLatestEvent(latest, eventJSON);
        }

        return latest;
    }
}
