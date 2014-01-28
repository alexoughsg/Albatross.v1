package com.cloud.region.service;

import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.region.RegionVO;
import org.apache.cloudstack.region.dao.RegionDao;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class FullScanner {

    private static final Logger s_logger = Logger.getLogger(FullScanner.class);

    @Inject
    protected DomainDao domainDao;
    @Inject
    protected AccountDao accountDao;
    @Inject
    protected RegionDao regionDao;

    //protected Date lastFullScan;
    private List<RegionVO> regions;

    //private static final ConfigKey<Integer> FullScanInterval = new ConfigKey<Integer>("Advanced", Integer.class, "full.scan.interval.region.commands", "1",
    //        "The full scan with remote regions will occur if the last time is more than minutes of the given number. Default value is 1 minute.", true);

    public FullScanner()
    {
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.accountDao = ComponentContext.getComponent(AccountDao.class);
        this.regionDao = ComponentContext.getComponent(RegionDao.class);

        //this.lastFullScan = null;
        this.regions = null;
    }

    /*private boolean timeToFullScan()
    {
        if (lastFullScan == null)   return true;

        long time1 = lastFullScan.getTime();
        long time2 = (new Date()).getTime();
        long diff = time2 - time1;
        long secondInMillis = 1000;
        long elapsedSeconds = diff / secondInMillis;
        return elapsedSeconds > FullScanInterval.value() * 60;
    }*/

    protected List<RegionVO> findRemoteRegions()
    {
        List<RegionVO> regions = regionDao.listAll();
        for (int idx = regions.size()-1; idx >= 0; idx--)
        {
            RegionVO region = regions.get(idx);
            if (region.getName().equals("Local"))
            {
                regions.remove(region);
                continue;
            }
            if (!region.isActive())
            {
                regions.remove(region);
                continue;
            }
        }
        return regions;
    }

    public void fullScan()
    {
        //if (!timeToFullScan())  return;

        //this.lastFullScan = new Date();
        this.regions = findRemoteRegions();

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

            try
            {
                fullDomainScan(domain);
                fullAccountScan(domain);
                fullUserScan(domain);
            }
            catch(Exception ex)
            {
                s_logger.error("Failed to full sync : " + ex.toString());
                continue;
            }

            // recursive call
            List<DomainVO> childrenList = domainDao.findImmediateChildrenForParent(domain.getId());
            fullDomainScan(childrenList);
        }
    }

    protected void fullDomainScan(DomainVO domain) throws Exception
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

    protected void fullAccountScan(DomainVO domain) throws Exception
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
    }

    protected void fullUserScan(DomainVO domain) throws Exception
    {
        List<UserFullSyncProcessor> syncProcessors = new ArrayList<UserFullSyncProcessor>();

        for (RegionVO region : regions)
        {
            UserFullSyncProcessor syncProcessor = new UserFullSyncProcessor(region.getName(), region.getEndPoint(), region.getUserName(), region.getPassword(), domain.getId());
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
            //processor.removeLocalResources();
        }
    }
}