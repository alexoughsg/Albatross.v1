package com.cloud.region.service;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.DomainManager;
import com.cloud.utils.component.ComponentContext;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.UUID;

public class LocalDomainManager {

    private static final Logger s_logger = Logger.getLogger(LocalDomainManager.class);

    protected DomainDao domainDao;
    private DomainManager domainManager;

    public LocalDomainManager()
    {
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.domainManager = ComponentContext.getComponent(DomainManager.class);
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

    public Object create(JSONObject jsonObject, String parentPath, Date created) throws Exception
    {
        // find parent domain id from the domain path
        DomainVO parentDomain = domainDao.findDomainByPath(parentPath);
        if (parentDomain == null)
        {
            throw new Exception("Domain creation has been failed because its parent domain[" + parentPath + "] is not found");
        }

        final Long parentId = parentDomain.getId();
        final String domainName = BaseService.getAttrValue(jsonObject, "name");
        final String networkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
        final String initialName = BaseService.getAttrValue(jsonObject, "initialname");
        final String domainUUID = UUID.randomUUID().toString();

        Domain domain = domainManager.createDomain(domainName, parentId, parentId, networkDomain, domainUUID, initialName, created);
        s_logger.info("Successfully created a domain[" + domain.getName() + "]");
        return domain;
    }

    public void update(Object object, JSONObject jsonObject, Date modified)
    {
        DomainVO domain = (DomainVO)object;
        domain.setState(Domain.State.Active);
        String newDomainName = BaseService.getAttrValue(jsonObject, "name");
        String newNetworkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
        String initialName = BaseService.getAttrValue(jsonObject, "initialname");
        domainManager.updateDomain(domain, newDomainName, newNetworkDomain, initialName, modified);
        s_logger.info("Successfully updated a domain[" + domain.getName() + "]");
    }

    public void remove(Object object, Date removed)
    {
        DomainVO domain = (DomainVO)object;
        //delete(domain, removed);
        deactivate(domain, removed);
    }
}
