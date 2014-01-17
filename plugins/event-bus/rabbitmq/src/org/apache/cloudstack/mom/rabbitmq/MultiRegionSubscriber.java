package org.apache.cloudstack.mom.rabbitmq;

import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.component.ComponentContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import org.apache.cloudstack.framework.events.Event;
import org.apache.cloudstack.framework.events.EventSubscriber;
import org.apache.cloudstack.mom.service.AccountFullSyncProcessor;
import org.apache.cloudstack.mom.service.DomainFullSyncProcessor;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Iterator;

public class MultiRegionSubscriber  implements EventSubscriber {

    private static final Logger s_logger = Logger.getLogger(MultiRegionSubscriber.class);

    protected int id;
    protected Gson gson;
    protected Map<String,String> descMap = null;

    @Inject
    protected DomainDao domainDao;
    @Inject
    protected AccountDao accountDao;
    @Inject
    protected UserDao userDao;

    protected String[][] regions = {
            //{"10.88.90.82", "admin", "password"},     // sandbox01
            {"10.88.90.83", "admin", "password"},       // sandbox02
    };


    public MultiRegionSubscriber(int id)
    {
        this.id = id;
        this.gson = new GsonBuilder().create();
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.accountDao = ComponentContext.getComponent(AccountDao.class);
        this.userDao = ComponentContext.getComponent(UserDao.class);
    }

    protected boolean isCompleted(String status)
    {
        return (status != null && status.equals("Completed"));
    }

    protected boolean isExecutable()
    {
        String status = this.descMap.get("status");
        if (!isCompleted(status))  return false;

        String entityUUID = this.descMap.get("entityuuid");
        if (entityUUID == null || entityUUID.equals(""))
        {
            s_logger.info("entity uuid is not given");
            return false;
        }

        return true;
    }

        @Override
    public void onEvent(Event event)
    {
        s_logger.info("HANDLER" + id + " Category: " + event.getEventCategory() + " type: " + event.getEventType() +
                " resource type: " + event.getResourceType() + " resource UUID: " + event.getResourceUUID());
        s_logger.info("BODY : " + event.getDescription());

        Type stringStringMap = new TypeToken<Map<String, String>>(){}.getType();
        this.descMap = gson.fromJson(event.getDescription(), stringStringMap);
        Iterator i = this.descMap.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry e = (Map.Entry)i.next();
            s_logger.info("Key: " + e.getKey() + ", Value: " + e.getValue());
        }

        // if the event is user login from outside, do the full scan
        String eventType = event.getEventType();
        String description = event.getDescription();
        if (eventType.equals("USER-LOGIN") && !description.contains("127.0.0.1"))
        {
            fullScan();
        }
    }

    public void fullScan()
    {
        fullDomainScan();
    }

    protected void fullDomainScan()
    {
        List<DomainVO> localList = new ArrayList<DomainVO>();
        DomainVO root = domainDao.findDomainByPath("/");
        localList.add(root);
        fullDomainScan(localList);
    }

    protected void fullDomainScan(List<DomainVO> localList)
    {
        for(DomainVO domain : localList)
        {
            if (domain.getState().equals(Domain.State.Inactive))    continue;

            fullDomainScan(domain);
            fullAccountScan(domain);

            // recursive call
            List<DomainVO> childrenList = domainDao.findImmediateChildrenForParent(domain.getId());
            fullDomainScan(childrenList);
        }
    }

    protected void fullDomainScan(DomainVO domain)
    {
        List<DomainFullSyncProcessor> syncProcessors = new ArrayList<DomainFullSyncProcessor>();

        for (String[] region : regions)
        {
            DomainFullSyncProcessor syncProcessor = new DomainFullSyncProcessor(region[0], region[1], region[2], domain.getId());
            syncProcessor.synchronize();

            syncProcessors.add(syncProcessor);
        }

        // arrange the left & processed resources
        for(int idx = 0; idx < syncProcessors.size() - 1; idx++)
        {
            DomainFullSyncProcessor first = syncProcessors.get(idx);
            DomainFullSyncProcessor second = syncProcessors.get(idx+1);
            first.arrangeLocalResourcesToBeRemoved(second);
            second.arrangeLocalResourcesToBeRemoved(first);
            first.arrangeRemoteResourcesToBeCreated(second);
            second.arrangeRemoteResourcesToBeCreated(first);
        }

        // create or remove unprocessed resources
        for(int idx = 0; idx < syncProcessors.size(); idx++)
        {
            DomainFullSyncProcessor processor = syncProcessors.get(idx);
            processor.createRemoteResourcesInLocal();
            processor.removeLocalResources();
        }
    }


    protected void fullAccountScan(DomainVO domain)
    {
        List<AccountFullSyncProcessor> syncProcessors = new ArrayList<AccountFullSyncProcessor>();

        for (String[] region : regions)
        {
            AccountFullSyncProcessor syncProcessor = new AccountFullSyncProcessor(region[0], region[1], region[2], domain.getId());
            syncProcessor.synchronize();

            syncProcessors.add(syncProcessor);
        }

        // arrange the left & processed resources
        for(int idx = 0; idx < syncProcessors.size() - 1; idx++)
        {
            AccountFullSyncProcessor first = syncProcessors.get(idx);
            AccountFullSyncProcessor second = syncProcessors.get(idx+1);
            first.arrangeLocalResourcesToBeRemoved(second);
            second.arrangeLocalResourcesToBeRemoved(first);
            first.arrangeRemoteResourcesToBeCreated(second);
            second.arrangeRemoteResourcesToBeCreated(first);
        }

        // create or remove unprocessed resources
        for(int idx = 0; idx < syncProcessors.size(); idx++)
        {
            AccountFullSyncProcessor processor = syncProcessors.get(idx);
            processor.createRemoteResourcesInLocal();
            processor.removeLocalResources();
        }
    }
}