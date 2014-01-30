package com.cloud.region.simulator;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.region.service.DomainService;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.region.RegionVO;
import org.apache.cloudstack.region.dao.RegionDao;
import org.apache.log4j.Logger;

public class DomainLocalGeneratorEvent extends LocalGenerator {

    private static final Logger s_logger = Logger.getLogger(DomainLocalGeneratorEvent.class);

    private DomainService domainService;

    public DomainLocalGeneratorEvent()
    {
        RegionDao regionDao = ComponentContext.getComponent(RegionDao.class);
        RegionVO region = regionDao.findByName("Local");
        this.domainService = new DomainService(region.getName(), region.getEndPoint(), region.getUserName(), region.getPassword());
    }

    public JSONObject create()
    {
        // select a random domain for a parent domain
        DomainVO parentDomain = randDomainSelect(true);

        // create a random string for a new domain
        String domainName = "D" + generateRandString();
        String domainPath = parentDomain.getPath() + domainName + "/";
        String networkDomain = "ND" + generateRandString();

        try
        {
            JSONObject domain = domainService.create(domainName, domainPath, networkDomain);
            s_logger.info("Successfully created domain[" + domainName + "]");
            return domain;
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to create d domain", ex);
            return null;
        }
    }

    public DomainVO update(DomainVO domain)
    {
        // select a random domain
        if(domain == null)  domain = randDomainSelect(false);

        if (domain.getName().equals("ROOT") && domain.getPath().equals("/"))
        {
            s_logger.info("This is a root domain, so skip to update");
            return domain;
        }

        if (!domain.getState().equals(Domain.State.Active))
        {
            s_logger.info("This domain is not active, so skip to update");
            return domain;
        }

        // create new attribute values
        String newDomainName = "D" + generateRandString();
        String newNetworkDomain = "ND" + generateRandString();

        try
        {
            domainService.update(domain.getName(), newDomainName, domain.getPath(), newNetworkDomain);
            return domain;
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to update domain", ex);
            return null;
        }
    }

    public DomainVO remove(DomainVO domain)
    {
        // select a random domain
        if(domain == null)  domain = randDomainSelect(false);

        if (domain.getName().equals("ROOT") && domain.getPath().equals("/"))
        {
            s_logger.info("This is a root domain, so skip to remove");
            return domain;
        }

        if (!domain.getState().equals(Domain.State.Active))
        {
            s_logger.info("This domain is not active, so skip to remove");
            return domain;
        }

        try
        {
            domainService.delete(domain.getName(), domain.getPath());
            s_logger.info("Successfully removed domain[" + domain.getName() + "]");
        }
        catch(Exception ex)
        {
            s_logger.info("Failed to remove domain[" + domain.getName() + "]");
        }

        return domain;
    }
}
