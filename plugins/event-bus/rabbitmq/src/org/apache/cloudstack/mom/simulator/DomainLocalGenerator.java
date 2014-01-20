package org.apache.cloudstack.mom.simulator;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.mom.service.DomainFullScanner;
import org.apache.cloudstack.mom.service.LocalDomainManager;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.Random;

public class DomainLocalGenerator extends LocalGenerator {

    private static final Logger s_logger = Logger.getLogger(DomainFullScanner.class);

    private DomainDao domainDao;
    private LocalDomainManager localDomainManager;

    public DomainLocalGenerator()
    {
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.localDomainManager = new LocalDomainManager();
    }

    protected DomainVO randSelect(boolean includeRoot)
    {
        List<DomainVO> domainList = domainDao.listAll();
        Random rand = new Random();
        int num = 0;
        while(num == 0)
        {
            // exclude the 'ROOT' domain
            num = rand.nextInt(domainList.size());
            if (includeRoot)    break;
        }
        DomainVO domain = domainList.get(num);
        return domain;
    }

    public DomainVO create()
    {
        Date created = generateRandDate();
        JSONObject domainJson = new JSONObject();

        // select a random domain for a parent domain
        DomainVO parentDomain = randSelect(true);

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
        if(domain == null)  domain = randSelect(false);

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
            s_logger.error("Failed to set json attributes", ex);
            return null;
        }
    }

    public DomainVO remove(DomainVO domain)
    {
        Date removed = generateRandDate();

        // select a random domain
        if(domain == null)  domain = randSelect(false);

        localDomainManager.remove(domain, removed);
        s_logger.info("Successfully removed domain[" + domain.getName() + "]");

        return domain;
    }
}
