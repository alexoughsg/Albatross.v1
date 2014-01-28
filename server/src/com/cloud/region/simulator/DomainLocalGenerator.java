package com.cloud.region.simulator;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.region.service.LocalDomainManager;
import org.apache.log4j.Logger;

import java.util.Date;

public class DomainLocalGenerator extends LocalGenerator {

    private static final Logger s_logger = Logger.getLogger(DomainLocalGenerator.class);

    private LocalDomainManager localDomainManager;

    public DomainLocalGenerator()
    {
        this.localDomainManager = new LocalDomainManager();
    }

    public DomainVO create()
    {
        Date created = generateRandDate();
        JSONObject domainJson = new JSONObject();

        // select a random domain for a parent domain
        DomainVO parentDomain = randDomainSelect(true);

        // create a random string for a new domain
        String domainName = "D" + generateRandString();
        String domainPath = parentDomain.getPath() + domainName + "/";
        String networkDomain = "ND" + generateRandString();

        try
        {
            domainJson.put("path", domainPath);
            domainJson.put("parentdomainname", parentDomain.getName());
            domainJson.put("name", domainName);
            domainJson.put("networkdomain", networkDomain);

            DomainVO domain = (DomainVO)localDomainManager.create(domainJson, parentDomain.getPath(), created);
            s_logger.info("Successfully created domain[" + domain.getName() + "]");
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
        Date modified = generateRandDate();
        JSONObject domainJson = new JSONObject();

        // select a random domain
        if(domain == null)  domain = randDomainSelect(false);

        if (domain.getName().equals("ROOT") && domain.getPath().equals("/"))
        {
            s_logger.info("This is a root domain, so skip to update");
            return domain;
        }

        // create new attribute values
        String newDomainName = "D" + generateRandString();
        String newNetworkDomain = "ND" + generateRandString();

        try
        {
            domainJson.put("name", newDomainName);
            domainJson.put("networkdomain", newNetworkDomain);
            localDomainManager.update(domain, domainJson, modified);
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
        Date removed = generateRandDate();

        // select a random domain
        if(domain == null)  domain = randDomainSelect(false);

        if (domain.getName().equals("ROOT") && domain.getPath().equals("/"))
        {
            s_logger.info("This is a root domain, so skip to remove");
            return domain;
        }

        try
        {
            localDomainManager.remove(domain, removed);
            s_logger.info("Successfully removed domain[" + domain.getName() + "]");
        }
        catch(Exception ex)
        {
            s_logger.info("Failed to remove domain[" + domain.getName() + "]");
        }

        return domain;
    }
}
