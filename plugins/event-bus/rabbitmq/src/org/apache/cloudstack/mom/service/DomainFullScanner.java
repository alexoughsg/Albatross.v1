package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.configuration.ResourceLimit;
import com.cloud.configuration.dao.ResourceCountDao;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionStatus;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class DomainFullScanner extends FullScanner {

    private static final Logger s_logger = Logger.getLogger(DomainFullScanner.class);

    private DomainDao domainDao;
    private ResourceCountDao resourceCountDao;

    public DomainFullScanner()
    {
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.resourceCountDao = ComponentContext.getComponent(ResourceCountDao.class);
    }

    // this is from com.cloud.user.DomainManagerImpl
    private String getUpdatedDomainPath(String oldPath, String newName)
    {
        String[] tokenizedPath = oldPath.split("/");
        tokenizedPath[tokenizedPath.length - 1] = newName;
        StringBuilder finalPath = new StringBuilder();
        for (String token : tokenizedPath) {
            finalPath.append(token);
            finalPath.append("/");
        }
        return finalPath.toString();
    }

    // this is from com.cloud.user.DomainManagerImpl
    private void updateDomainChildren(DomainVO domain, String updatedDomainPrefix)
    {
        List<DomainVO> domainChildren = domainDao.findAllChildren(domain.getPath(), domain.getId());
        // for each child, update the path
        for (DomainVO dom : domainChildren)
        {
            dom.setPath(dom.getPath().replaceFirst(domain.getPath(), updatedDomainPrefix));
            domainDao.update(dom.getId(), dom);
        }
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

    @Override
    protected DomainVO find(JSONObject jsonObject, List localList)
    {
        for (Object object : localList)
        {
            DomainVO domain = (DomainVO)object;
            if (!domain.getPath().equals(getAttrValueInJson(jsonObject, "path")))    continue;
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
    protected void create(JSONObject jsonObject, final Date created)
    {
        // find parent domain id from the domain path
        String domainPath = getAttrValueInJson(jsonObject, "path");
        String parentDomainName = getAttrValueInJson(jsonObject, "parentdomainname");
        String parentPath = getParentPath(domainPath, parentDomainName);
        DomainVO parentDomain = domainDao.findDomainByPath(parentPath);
        final Long parentId = parentDomain.getId();

        final String domainName = getAttrValueInJson(jsonObject, "name");
        final String networkDomain = getAttrValueInJson(jsonObject, "networkdomain");
        final String domainUUID = UUID.randomUUID().toString();



        // how do I get 'ownerId' here??????
        final long ownerId = 0;




        Transaction.execute(new TransactionCallbackNoReturn()
        {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status)
            {
                DomainVO domain = domainDao.create(new DomainVO(domainName, ownerId, parentId, networkDomain, domainUUID, created));
                resourceCountDao.createResourceCounts(domain.getId(), ResourceLimit.ResourceOwnerType.Domain);
            }
        });

    }

    @Override
    protected void update(Object object, final JSONObject jsonObject, final Date modified)
    {
        final DomainVO domain = (DomainVO)object;
        final Long domainId = domain.getId();

        Transaction.execute(new TransactionCallbackNoReturn()
        {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status)
            {
                String newDomainName = getAttrValueInJson(jsonObject, "name");
                if (!domain.getName().equals(newDomainName)) {
                    String updatedDomainPath = getUpdatedDomainPath(domain.getPath(), newDomainName);
                    updateDomainChildren(domain, updatedDomainPath);
                    domain.setName(newDomainName);
                    domain.setPath(updatedDomainPath);
                }

                String newNetworkDomain = getAttrValueInJson(jsonObject, "networkdomain");
                if (!domain.getNetworkDomain().equals(newNetworkDomain)) {
                    domain.setNetworkDomain(newNetworkDomain);
                }
                domain.setModified(modified);
                domainDao.update(domainId, domain);
            }
        });
    }

    @Override
    protected void remove(Object object, Date removed)
    {
        DomainVO domain = (DomainVO)object;
        domainDao.remove(domain.getId(), removed);
    }

    protected Date isRemoteCreated(JSONObject remoteObject)
    {
        Date created = super.isRemoteCreated(remoteObject);
        if (created == null)    return created;

        String domainName = getAttrValueInJson(remoteObject, "name");
        List<DomainVO> domains = domainDao.listAllIncludingRemoved();
        for(DomainVO domain : domains)
        {
            if (domain.getName().equals(domainName) && domain.getRemoved().after(created))
            {
                return null;
            }
        }

        return created;
    }

    @Override
    protected void syncUpdate(Object object, JSONObject jsonObject)
    {
        if (compare(object, jsonObject))    return;

        DomainVO domain = (DomainVO)object;
        Date localTimestamp = domain.getModified();
        Date remoteTimestamp = getDate(jsonObject, "modified");
        if (localTimestamp == null || remoteTimestamp == null)  return;
        if (localTimestamp.after(remoteTimestamp))  return;

        // update local domain with remote domain's modified timestamp
        update(object, jsonObject, remoteTimestamp);
    }

    @Override
    protected Date isRemoteRemoved(Object object, String hostName, String userName, String password)
    {
        DomainVO domain = (DomainVO)object;
        DomainService domainService = new DomainService(hostName, userName, password);
        //TimeZone GMT_TIMEZONE = TimeZone.getTimeZone("GMT");
        //Date removed = domainService.isRemoved("alex_test2", "ROOT", DateUtil.parseDateString(GMT_TIMEZONE, "2013-12-18 19:44:48"));
        Date removed = domainService.isRemoved(domain.getName(), domain.getPath(), domain.getCreated());
        return removed;
    }
}
