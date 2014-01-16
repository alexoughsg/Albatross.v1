package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.configuration.ResourceLimit;
import com.cloud.configuration.dao.ResourceCountDao;
import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.DomainManager;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;
import org.apache.cloudstack.mom.api_interface.BaseInterface;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class DomainFullScanner extends FullScanner {

    private static final Logger s_logger = Logger.getLogger(DomainFullScanner.class);

    protected DomainDao domainDao;
    private ResourceCountDao resourceCountDao;
    private DomainManager domainManager;

    public DomainFullScanner()
    {
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.resourceCountDao = ComponentContext.getComponent(ResourceCountDao.class);
        this.domainManager = ComponentContext.getComponent(DomainManager.class);
    }

    private String getParentPath(String domainPath, String parentDomainName)
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

    @Override
    public List<DomainVO> findLocalList()
    {
        return domainDao.listAll();
    }

    @Override
    public JSONArray findRemoteList(String[] remoteServerInfo)
    {
        String hostName = remoteServerInfo[0];
        String userName = remoteServerInfo[1];
        String password = remoteServerInfo[2];

        try
        {
            DomainService domainService = new DomainService(hostName, userName, password);
            JSONArray domainArray = domainService.list();
            return domainArray;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find domain list in hostName[ + " + hostName + "]", ex);
            return new JSONArray();
        }
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

    private void delete(DomainVO domain, Date removed)
    {
        boolean cleanup = false;
        domain.setRemoved(removed);
        domainManager.deleteDomain(domain, cleanup);
        s_logger.info("Successfully removed a domain[" + domain.getName() + "]");
    }

    private void deactivate(DomainVO domain, Date removed)
    {
        domain.setState(Domain.State.Inactive);
        domain.setModified(removed);
        domainDao.update(domain.getId(), domain);
        s_logger.info("Successfully deactivated a domain[" + domain.getName() + "]");
    }

    @Override
    protected DomainVO find(JSONObject jsonObject, List localList)
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

    @Override
    protected boolean compare(Object object, JSONObject jsonObject)
    {
        DomainVO domain = (DomainVO)object;


        return true;
    }

    @Override
    protected Object create(JSONObject jsonObject, final Date created)
    {
        // find parent domain id from the domain path
        String domainPath = convertPath(BaseService.getAttrValue(jsonObject, "path"));
        String parentDomainName = BaseService.getAttrValue(jsonObject, "parentdomainname");
        String parentPath = getParentPath(domainPath, parentDomainName);
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

    @Override
    protected void update(Object object, final JSONObject jsonObject, final Date modified)
    {
        DomainVO domain = (DomainVO)object;
        //domain.setModified(modified);

        String newDomainName = BaseService.getAttrValue(jsonObject, "name");
        String newNetworkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
        domainManager.updateDomain(domain, newDomainName, newNetworkDomain, modified);
        s_logger.info("Successfully updated a domain[" + domain.getName() + "]");
    }

    @Override
    protected void remove(Object object, Date removed)
    {
        DomainVO domain = (DomainVO)object;

        //delete(domain, removed);
        deactivate(domain, removed);
    }

    @Override
    protected JSONArray listEvents(String hostName, String userName, String password, Date created)
    {
        try
        {
            DomainService domainService = new DomainService(hostName, userName, password);
            return domainService.listEvents("DOMAIN.DELETE", "completed", created, null);
        }
        catch(Exception ex)
        {
            s_logger.error(ex.getStackTrace());
            return null;
        }
    }

    protected Date isRemoteCreated(JSONObject remoteObject)
    {
        String domainName = BaseService.getAttrValue(remoteObject, "name");

        Date remoteCreated = super.isRemoteCreated(remoteObject);
        if (remoteCreated == null)
        {
            s_logger.info("Domain[" + domainName + "] : create is skipped because created time of remote is null.");
            return null;
        }

        List<DomainVO> domains = domainDao.listAllIncludingRemoved();
        for(DomainVO domain : domains)
        {
            Date localRemoved = domain.getRemoved();
            if (domain.getName().equals(domainName) && localRemoved != null && localRemoved.after(remoteCreated))
            {
                s_logger.info("Domain[" + domainName + "] : create is skipped because created time of remote[" + remoteCreated + "] is before removed time of local[" + localRemoved + "]");
                return null;
            }
        }

        return remoteCreated;
    }

    @Override
    protected void syncUpdate(Object object, JSONObject jsonObject)
    {
        DomainVO domain = (DomainVO)object;

        if (compare(object, jsonObject))
        {
            s_logger.info("Domain[" + domain.getName() + "]  : update is skipped because local & remote are same.");
            return;
        }

        Date localTimestamp = domain.getModified();
        Date remoteTimestamp = getDate(jsonObject, "modified");
        if (localTimestamp == null || remoteTimestamp == null)
        {
            s_logger.info("Domain[" + domain.getName() + "] : update is skipped because modified times of local[" + localTimestamp + "] and/or remote[" + remoteTimestamp + "] is/are null.");
            return;
        }
        if (localTimestamp.after(remoteTimestamp))
        {
            s_logger.info("Domain[" + domain.getName() + "] : update is skipped because modified time of local[" + localTimestamp + "] is after remote[" + remoteTimestamp + "].");
            return;
        }

        // update local domain with remote domain's modified timestamp
        update(object, jsonObject, remoteTimestamp);
    }

    @Override
    protected boolean exist(Object object, ArrayList<Object> processedList)
    {
        DomainVO domain = (DomainVO)object;
        for(Object next : processedList)
        {
            DomainVO nextDomain = (DomainVO)next;
            if (domain.getPath().equals(nextDomain.getPath()))  return true;
        }

        return false;
    }

    @Override
    protected Date isRemoteRemovedOrRenamed(Object object, JSONArray eventList)
    {
        DomainVO domain = (DomainVO)object;

        Date created = domain.getCreated();
        if (created == null)
        {
            s_logger.info("Domain[" + domain.getName() + "]  : remove is skipped because local create time is null.");
            return null;
        }

        Date eventDate = isRemovedOrRenamed(domain.getName(), domain.getPath(), eventList);
        if (eventDate == null)
        {
            return null;
        }

        if (eventDate.before(created))
        {
            s_logger.info("Domain[" + domain.getName() + "]  : remove is skipped because remote remove/rename event time is before local create time.");
            return null;
        }

        return eventDate;
    }

    private Date isRemovedOrRenamed(String domainName, String domainPath, JSONArray eventList)
    {
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
                    if (eventNewDomainName.equals(eventOldDomainName))    continue;
                    if (!eventOldDomainName.equals(domainName))    continue;
                    if (!eventDomainPath.replace(domainName, eventOldDomainName).equals(domainPath))    continue;
                }

                if (!BaseInterface.hasAttribute(jsonObject, "created"))
                {
                    s_logger.info("Domain[" + domainName + "]  : remove is skipped because remove/rename event created time is not available.");
                    return null;
                }

                return BaseService.parseDateStr(BaseService.getAttrValue(jsonObject, "created"));
            }
            catch(Exception ex)
            {
                s_logger.error(ex.getStackTrace());
                return null;
            }
        }

        s_logger.info("Domain[" + domainName + "]  : remove is skipped because remove/rename history can't be found.");
        return null;
    }
}
