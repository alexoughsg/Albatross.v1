package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
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

    @Override
    protected DomainVO find(JSONObject jsonObject, List localList)
    {
        /*String remotePath = convertPath(getAttrValueInJson(jsonObject, "path"));
        if (remotePath.endsWith("/"))
        {
            remotePath = remotePath.substring(0, remotePath.length()-1);
        }*/
        String remotePath = getAttrValueInJson(jsonObject, "path");

        for (Object object : localList)
        {
            DomainVO domain = (DomainVO)object;
            String localPath = domain.getPath();
            /*if (localPath.endsWith("/"))
            {
                localPath = localPath.substring(0, localPath.length()-1);
            }
            if (!localPath.equals(remotePath))    continue;*/
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
        String domainPath = convertPath(getAttrValueInJson(jsonObject, "path"));
        String parentDomainName = getAttrValueInJson(jsonObject, "parentdomainname");
        String parentPath = getParentPath(domainPath, parentDomainName);
        DomainVO parentDomain = domainDao.findDomainByPath(parentPath);
        if (parentDomain == null)
        {
            s_logger.error("Domain creation has been failed because its parent domain[" + parentDomainName + "] is not found");
            return null;
        }
        final Long parentId = parentDomain.getId();

        final String domainName = getAttrValueInJson(jsonObject, "name");
        final String networkDomain = getAttrValueInJson(jsonObject, "networkdomain");
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
        domain.setModified(modified);

        String newDomainName = getAttrValueInJson(jsonObject, "name");
        String newNetworkDomain = getAttrValueInJson(jsonObject, "networkdomain");
        domainManager.updateDomain(domain, newDomainName, newNetworkDomain);
        s_logger.info("Successfully updated a domain[" + domain.getName() + "]");
    }

    @Override
    protected void remove(Object object, Date removed)
    {
        DomainVO domain = (DomainVO)object;

        boolean cleanup = false;
        domain.setRemoved(removed);
        domainManager.deleteDomain(domain, cleanup);
        s_logger.info("Successfully removed a domain[" + domain.getName() + "]");
    }

    protected Date isRemoteCreated(JSONObject remoteObject)
    {
        String domainName = getAttrValueInJson(remoteObject, "name");

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
    protected Date isRemoteRemoved(Object object, String hostName, String userName, String password)
    {
        DomainVO domain = (DomainVO)object;
        DomainService domainService = new DomainService(hostName, userName, password);
        Date removed = domainService.isRemoved(domain.getName(), domain.getPath(), domain.getCreated());
        if (removed == null)
        {
            s_logger.info("Domain[" + domain.getName() + "]  : remove is skipped because remote does not have removal history or remote removal is before local creation.");
        }
        return removed;
    }
}
