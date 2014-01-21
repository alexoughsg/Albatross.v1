package org.apache.cloudstack.mom.service;

import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.region.RegionVO;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FullScanner {

    @Inject
    protected DomainDao domainDao;
    @Inject
    protected AccountDao accountDao;

    protected Date lastFullScan;
    private List<RegionVO> regions;

    public FullScanner()
    {
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.accountDao = ComponentContext.getComponent(AccountDao.class);

        this.lastFullScan = null;
        this.regions = null;
    }

    private boolean timeToFullScan()
    {
        if (lastFullScan == null)   return true;

        long time1 = lastFullScan.getTime();
        long time2 = (new Date()).getTime();
        long diff = time2 - time1;
        long secondInMillis = 1000;
        long elapsedSeconds = diff / secondInMillis;
        return elapsedSeconds > 60 * 5;
    }

    public void fullScan(List<RegionVO> regions)
    {
        if (!timeToFullScan())  return;

        this.lastFullScan = new Date();
        this.regions = regions;
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

        for (RegionVO region : regions)
        {
            DomainFullSyncProcessor syncProcessor = new DomainFullSyncProcessor(region.getName(), region.getEndPoint(), region.getUserName(), region.getPassword(), domain.getId());
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

        for (RegionVO region : regions)
        {
            AccountFullSyncProcessor syncProcessor = new AccountFullSyncProcessor(region.getName(), region.getEndPoint(), region.getUserName(), region.getPassword(), domain.getId());
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

        // now start the user full scan for each account belonging to the given domain
        for(AccountVO account : accountDao.findActiveAccountsForDomain(domain.getId()))
        {
            if (domain.getName().equals("ROOT") && account.getAccountName().equals("system"))
            {
                // skip the 'system' user
                continue;
            }
            fullUserScan(account);
        }
    }

    protected void fullUserScan(AccountVO account)
    {
        List<UserFullSyncProcessor> syncProcessors = new ArrayList<UserFullSyncProcessor>();

        for (RegionVO region : regions)
        {
            UserFullSyncProcessor syncProcessor = new UserFullSyncProcessor(region.getName(), region.getEndPoint(), region.getUserName(), region.getPassword(), account.getId());
            syncProcessor.synchronize();

            syncProcessors.add(syncProcessor);
        }

        // arrange the left & processed resources
        for(int idx = 0; idx < syncProcessors.size() - 1; idx++)
        {
            UserFullSyncProcessor first = syncProcessors.get(idx);
            UserFullSyncProcessor second = syncProcessors.get(idx+1);
            first.arrangeLocalResourcesToBeRemoved(second);
            second.arrangeLocalResourcesToBeRemoved(first);
            first.arrangeRemoteResourcesToBeCreated(second);
            second.arrangeRemoteResourcesToBeCreated(first);
        }

        // create or remove unprocessed resources
        for(int idx = 0; idx < syncProcessors.size(); idx++)
        {
            UserFullSyncProcessor processor = syncProcessors.get(idx);
            processor.createRemoteResourcesInLocal();
            processor.removeLocalResources();
        }
    }
}