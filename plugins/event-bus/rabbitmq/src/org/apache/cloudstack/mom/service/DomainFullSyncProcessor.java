package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONObject;
import com.cloud.configuration.ResourceLimit;
import com.cloud.configuration.dao.ResourceCountDao;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.DomainManager;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

public class DomainFullSyncProcessor extends FullSyncProcessor {

    private static final Logger s_logger = Logger.getLogger(DomainFullSyncProcessor.class);

    protected DomainDao domainDao;
    private ResourceCountDao resourceCountDao;
    private DomainManager domainManager;

    private RemoteDomainEventProcessor eventProcessor;

    public DomainFullSyncProcessor(String hostName, String userName, String password)
    {
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.resourceCountDao = ComponentContext.getComponent(ResourceCountDao.class);
        this.domainManager = ComponentContext.getComponent(DomainManager.class);

        localList = domainDao.listAll();

        DomainService domainService = new DomainService(hostName, userName, password);
        remoteArray = domainService.list();
        remoteList = new ArrayList<JSONObject>();
        for(int idx = 0; idx < remoteArray.length(); idx++)
        {
            try
            {
                remoteList.add(remoteArray.getJSONObject(idx));
            }
            catch(Exception ex)
            {

            }
        }

        eventProcessor = new RemoteDomainEventProcessor(hostName, userName, password);
    }

    private String convertPath(String path)
    {
        if (path.equals("ROOT"))
        {
            path = "/";
        }
        else
        {
            path = path.replace("ROOT/", "/");
        }
        return path;
    }

    private String buildParentPath(String domainPath, String parentDomainName)
    {
        if (parentDomainName == null)   return "/";

        String[] tokenizedPath = domainPath.split("/");
        StringBuilder parentPath = new StringBuilder();
        for (int idx = 0; idx < tokenizedPath.length-1; idx++)
        {
            parentPath.append(tokenizedPath[idx]);
            parentPath.append("/");
        }
        return parentPath.toString();
    }

    private DomainVO getParentInLocal(DomainVO domain)
    {
        Long parentId = domain.getParent();
        if (parentId == null)   return null;
        return domainDao.findById(parentId);
    }

    private JSONObject getParentInRemote(JSONObject domainJSON)
    {
        String parentId = BaseService.getAttrValue(domainJSON, "parentdomainid");
        if (parentId == null) return null;

        for(int idx = 0; idx < remoteArray.length(); idx++)
        {
            try
            {
                JSONObject remoteJson = remoteArray.getJSONObject(idx);
                String id = BaseService.getAttrValue(remoteJson, "id");
                if (!parentId.equals(id))  continue;
                return remoteJson;
            }
            catch(Exception ex)
            {
                continue;
            }
        }

        return null;
    }

    private void syncAttributes(DomainVO domain, JSONObject remoteJson) throws Exception
    {
        try
        {
            if (compare(domain, remoteJson))
            {
                return;
            }

            Date localDate = domain.getModified();
            Date remoteDate = getDate(remoteJson, "modified");
            if (localDate == null || remoteDate == null)
            {
                s_logger.info("Can't syncAttributes because null date, local modified[" + localDate + "], remote modified[" + remoteDate + "]");
                return;
            }
            if (localDate.equals(remoteDate))   return;
            if (localDate.after(remoteDate))   return;

            update(domain, remoteJson, remoteDate);
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to synchronize domains : " + ex.getStackTrace());
        }
    }

    private void expungeProcessedLocals()
    {
        for (DomainVO domain : processedLocalList)
        {
            if (!localList.contains(domain))    continue;
            localList.remove(domain);
        }

        processedLocalList.clear();
    }

    private void expungeProcessedRemotes()
    {
        /*for (int idx = processedRemoteList.size() - 1; idx >= 0 ; idx--)
        {
            Integer rIdx = processedRemoteList.get(idx);
            remoteArray.remove(rIdx);
        }*/
        for (JSONObject remoteJson : processedRemoteList)
        {
            if (!remoteList.contains(remoteJson))    continue;
            remoteList.remove(remoteJson);
        }

        processedRemoteList.clear();
    }

    //@Override
    protected boolean compare(Object object, JSONObject jsonObject) throws Exception
    {
        DomainVO domain = (DomainVO)object;

        try
        {
            String remoteName = BaseService.getAttrValue(jsonObject, "name");
            String remoteNetworkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
            if (!domain.getName().equals(remoteName))   return false;
            if (domain.getNetworkDomain() == null && remoteNetworkDomain == null)   return true;
            if (domain.getNetworkDomain() == null || remoteNetworkDomain == null)   return false;
            return (domain.getNetworkDomain().equals(remoteNetworkDomain));
        }
        catch(Exception ex)
        {
            throw new Exception("Failed to compare domains : " + ex.getStackTrace());
        }
    }

    //@Override
    protected DomainVO findLocal(JSONObject jsonObject)
    {
        String remotePath = BaseService.getAttrValue(jsonObject, "path");

        for (Object object : localList)
        {
            DomainVO domain = (DomainVO)object;
            String localPath = domain.getPath();
            if (!BaseService.compareDomainPath(localPath, remotePath))  continue;
            return domain;
        }

        return null;
    }

    /*
        Find a local resource whose initial name is same with either the initial or resource name of the remote
        1. if the local initial name is null, return null
        2. if the remote initial name is null && the local initial name is equal to the remote resource name, return this local
        3. if the local initial name is equal to the remote initial name, return this local
        4. return null
     */
    //@Override
    protected DomainVO findLocalByInitialName(JSONObject jsonObject)
    {
        String remoteName = BaseService.getAttrValue(jsonObject, "name");
        String remoteInitialName = BaseService.getAttrValue(jsonObject, "initialname");
        JSONObject remoteParent = getParentInRemote(jsonObject);
        String remoteParentPath = (remoteParent == null) ? null : BaseService.getAttrValue(remoteParent, "path");

        for (Object object : localList)
        {
            DomainVO domain = (DomainVO)object;
            DomainVO localParent = getParentInLocal(domain);

            String localInitialName = domain.getInitialName();
            if (localInitialName == null)   continue;

            String localParentPath = (localParent == null) ? null : localParent.getPath();
            if (!BaseService.compareDomainPath(localParentPath, remoteParentPath))  continue;

            if (remoteInitialName == null && localInitialName.equals(remoteName))    return domain;
            if (localInitialName.equals(remoteInitialName))    return domain;
        }

        return null;
    }

    //@Override
    //protected int findRemote(Object object)
    protected JSONObject findRemote(Object object)
    {
        DomainVO domain = (DomainVO)object;
        String localPath = domain.getPath();

        //for (int idx = 0; idx < remoteArray.length(); idx++)
        for (JSONObject jsonObject : remoteList)
        {
            /*JSONObject jsonObject = null;
            try
            {
                jsonObject = remoteArray.getJSONObject(idx);
            }
            catch(Exception ex)
            {
                s_logger.error("Failed to get the next json object from the remote array");
                continue;
            }*/
            String remotePath = BaseService.getAttrValue(jsonObject, "path");
            if (!BaseService.compareDomainPath(localPath, remotePath))  continue;
            //return idx;
            return jsonObject;
        }

        //return -1;
        return null;
    }

    /*
        Find a remote resource whose initial name is same with either the initial or resource name of the local
        1. if the remote initial name is null, return null
        2. if the local initial name is null && the remote initial name is equal to the local resource name, return this remote
        3. if the remote initial name is equal to the local initial name, return this remote
        4. return null
     */
    //@Override
    //protected int findRemoteByInitialName(Object object)
    protected JSONObject findRemoteByInitialName(Object object)
    {
        DomainVO domain = (DomainVO)object;
        String localInitialName = domain.getInitialName();
        DomainVO localParent = getParentInLocal(domain);
        String localParentPath = (localParent == null) ? null : localParent.getPath();

        //for (int idx = 0; idx < remoteArray.length(); idx++)
        for (JSONObject jsonObject : remoteList)
        {
            /*JSONObject jsonObject = null;
            try
            {
                jsonObject = remoteArray.getJSONObject(idx);
            }
            catch(Exception ex)
            {
                s_logger.error("Failed to get the next json object from the remote array");
                continue;
            }*/

            String remoteInitialName = BaseService.getAttrValue(jsonObject, "initialname");
            if (remoteInitialName == null)   continue;

            JSONObject remoteParent = getParentInRemote(jsonObject);
            String remoteParentPath = (remoteParent == null) ? null : BaseService.getAttrValue(remoteParent, "path");
            if (!BaseService.compareDomainPath(localParentPath, remoteParentPath))  continue;

            if (localInitialName == null && remoteInitialName.equals(domain.getName()))    return jsonObject;
            if (remoteInitialName.equals(localInitialName))    return jsonObject;
        }

        return null;
    }

    //@Override
    protected Object create(JSONObject jsonObject, final Date created)
    {
        // find parent domain id from the domain path
        String domainPath = convertPath(BaseService.getAttrValue(jsonObject, "path"));
        String parentDomainName = BaseService.getAttrValue(jsonObject, "parentdomainname");
        String parentPath = buildParentPath(domainPath, parentDomainName);
        DomainVO parentDomain = domainDao.findDomainByPath(parentPath);
        if (parentDomain == null)
        {
            s_logger.error("Domain creation has been failed because its parent domain[" + parentDomainName + "] is not found");
            return null;
        }
        final Long parentId = parentDomain.getId();

        final String domainName = BaseService.getAttrValue(jsonObject, "name");
        final String networkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
        final String domainUUID = UUID.randomUUID().toString();



        // how do I get 'ownerId' here??????
        final long ownerId = 0;



        return Transaction.execute(new TransactionCallback<DomainVO>() {
            @Override
            public DomainVO doInTransaction(TransactionStatus status) {
                DomainVO domain = domainDao.create(new DomainVO(domainName, ownerId, parentId, networkDomain, domainUUID, created));
                resourceCountDao.createResourceCounts(domain.getId(), ResourceLimit.ResourceOwnerType.Domain);
                s_logger.info("Successfully created a domain[" + domain.getName() + "]");
                return domain;
            }
        });

    }

    //@Override
    protected void update(Object object, JSONObject jsonObject, Date modified)
    {
        DomainVO domain = (DomainVO)object;
        //domain.setModified(modified);

        String newDomainName = BaseService.getAttrValue(jsonObject, "name");
        String newNetworkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
        domainManager.updateDomain(domain, newDomainName, newNetworkDomain, modified);
        s_logger.info("Successfully updated a domain[" + domain.getName() + "]");
    }

    //@Override
    protected void remove(Object object, Date removed)
    {
        DomainVO domain = (DomainVO)object;

        boolean cleanup = false;
        domain.setRemoved(removed);
        domainManager.deleteDomain(domain, cleanup);
        s_logger.info("Successfully removed a domain[" + domain.getName() + "]");
    }

    //@Override
    protected boolean synchronize(DomainVO domain) throws Exception
    {
        /*int remoteIdx = findRemote(domain);
        if (remoteIdx < 0)  return false;

        JSONObject remoteJson = remoteArray.getJSONObject(remoteIdx);*/
        JSONObject remoteJson = findRemote(domain);
        if (remoteJson == null) return false;

                // synchronize the attributes
        syncAttributes(domain, remoteJson);

        //localList.remove(domain);
        //remoteArray.remove(remoteIdx);
        processedLocalList.add(domain);
        //processedRemoteList.add(remoteIdx);
        processedRemoteList.add(remoteJson);

        return true;
    }

    //@Override
    protected boolean synchronizeUsingEvent(DomainVO domain) throws Exception
    {
        JSONObject eventJson = eventProcessor.findLatestRemoteRemoveEvent(domain);
        if (eventJson == null)  return false;

        Date eventDate = getDate(eventJson, "created");
        Date created = domain.getCreated();
        if (created == null)
        {
            s_logger.info("Can't synchronizeUsingEvent because domain created is null");
            return false;
        }
        if (eventDate.before(created))  return false;

        // remove this local
        remove(domain, eventDate);

        //localList.remove(domain);
        processedLocalList.add(domain);

        return true;
    }

    //@Override
    protected boolean synchronizeUsingInitialName(DomainVO domain) throws Exception
    {
        /*int remoteIdx = findRemoteByInitialName(domain);
        if (remoteIdx < 0)  return false;

        JSONObject remoteJson = remoteArray.getJSONObject(remoteIdx);*/
        JSONObject remoteJson = findRemote(domain);
        if (remoteJson == null) return false;

        // synchronize the attributes
        syncAttributes(domain, remoteJson);

        //localList.remove(domain);
        //remoteArray.remove(remoteIdx);
        processedLocalList.add(domain);
        processedRemoteList.add(remoteJson);

        return true;
    }

    @Override
    protected void synchronizeByLocal()
    {
        for(DomainVO domain : localList)
        {
            try
            {
                boolean sync = synchronize(domain);
                if (sync)
                {
                    s_logger.error("Domain[" + domain.getPath() + "] successfully synchronized");
                    continue;
                }
                s_logger.error("Domain[" + domain.getPath() + "] not synchronized");
            }
            catch(Exception ex)
            {
                s_logger.error("Domain[" + domain.getPath() + "] failed to synchronize : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();

        for(DomainVO domain : localList)
        {
            try
            {
                boolean sync = synchronizeUsingEvent(domain);
                if (sync)
                {
                    s_logger.error("Domain[" + domain.getPath() + "] successfully synchronized using events");

                    continue;
                }
                s_logger.error("Domain[" + domain.getPath() + "] not synchronized using events");
            }
            catch(Exception ex)
            {
                s_logger.error("Domain[" + domain.getPath() + "] failed to synchronize using events : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();

        for(DomainVO domain : localList)
        {
            try
            {
                boolean sync = synchronizeUsingInitialName(domain);
                if (sync)
                {
                    s_logger.error("Domain[" + domain.getPath() + "] successfully synchronized using initial names");
                    continue;
                }
                s_logger.error("Domain[" + domain.getPath() + "] not synchronized using initial names");
            }
            catch(Exception ex)
            {
                s_logger.error("Domain[" + domain.getPath() + "] failed to synchronize using initial names : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();
    }

    //@Override
    //protected boolean synchronizeUsingRemoved(int idx) throws Exception
    protected boolean synchronizeUsingRemoved(JSONObject remoteJson) throws Exception
    {
        //JSONObject remoteJson = remoteArray.getJSONObject(idx);
        String remotePath = BaseService.getAttrValue(remoteJson, "path");
        Date created = getDate(remoteJson, "created");
        if (created == null)
        {
            s_logger.info("Can't synchronizeUsingRemoved because remote created is null");
            return false;
        }

        DomainVO removedDomain = null;
        for (DomainVO domain : domainDao.listAllIncludingRemoved())
        {
            Date removed = domain.getRemoved();
            if (removed == null)    continue;

            if (!BaseService.compareDomainPath(domain.getPath(), remotePath))  continue;

            if (removedDomain == null)
            {
                removedDomain = domain;
            }
            else
            {
                Date currentCreated = domain.getCreated();
                if (currentCreated == null)
                {
                    s_logger.info("Can't synchronizeUsingRemoved because one of the removed domain has null created");
                    return false;
                }
                else if (currentCreated.after(removedDomain.getCreated()))
                {
                    removedDomain = domain;
                }
            }
        }

        if (removedDomain == null)  return false;

        Date removed = removedDomain.getRemoved();
        if (created.after(removed))
        {
            // create this remote in the local region
            create(remoteJson, created);
        }

        //remoteArray.remove(idx);
        processedRemoteList.add(remoteJson);
        return true;
    }

    //@Override
    //protected boolean synchronizeUsingInitialName(int idx) throws Exception
    protected boolean synchronizeUsingInitialName(JSONObject remoteJson) throws Exception
    {
        //JSONObject remoteJson = remoteArray.getJSONObject(idx);

        DomainVO domain = findLocalByInitialName(remoteJson);
        if (domain == null)  return false;

        // synchronize the attributes
        syncAttributes(domain, remoteJson);

        //localList.remove(domain);
        //remoteArray.remove(idx);
        processedLocalList.add(domain);
        processedRemoteList.add(remoteJson);

        return true;
    }

    @Override
    protected void synchronizeByRemote()
    {
        //for(int idx = 0; idx < remoteArray.length(); idx++)
        for (JSONObject remoteJson : remoteList)
        {
            String domainPath = BaseService.getAttrValue(remoteJson, "path");

            try
            {
                //JSONObject remoteJson = remoteArray.getJSONObject(idx);
                boolean sync = synchronizeUsingRemoved(remoteJson);
                if (sync)
                {
                    s_logger.error("DomainJSON[" + domainPath + "] successfully synchronized using events");
                    continue;
                }
                s_logger.error("DomainJSON[" + domainPath + "] not synchronized using events");
            }
            catch(Exception ex)
            {
                s_logger.error("DomainJSON[" + domainPath + "] failed to synchronize using events : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();

        //for(int idx = 0; idx < remoteArray.length(); idx++)
        for (JSONObject remoteJson : remoteList)
        {
            String domainPath = BaseService.getAttrValue(remoteJson, "path");

            try
            {
                //JSONObject remoteJson = remoteArray.getJSONObject(idx);
                boolean sync = synchronizeUsingInitialName(remoteJson);
                if (sync)
                {
                    s_logger.error("DomainJSON[" + domainPath + "] successfully synchronized using initial names");
                    continue;
                }
                s_logger.error("DomainJSON[" + domainPath + "] not synchronized using initial names");
            }
            catch(Exception ex)
            {
                s_logger.error("DomainJSON[" + domainPath + "] failed to synchronize using initial names : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();

        //for(int idx = 0; idx < remoteArray.length(); idx++)
        for (JSONObject remoteJson : remoteList)
        {
            String domainPath = BaseService.getAttrValue(remoteJson, "path");

            try
            {
                // create this remote in the local region
                //JSONObject remoteJson = remoteArray.getJSONObject(idx);
                Date created = getDate(remoteJson, "created");
                create(remoteJson, created);
                s_logger.error("DomainJSON[" + domainPath + "] successfully created in the local region");
            }
            catch(Exception ex)
            {
                s_logger.error("DomainJSON[" + domainPath + "] failed to create in the local region : " + ex.getStackTrace());
            }
        }
    }
}
