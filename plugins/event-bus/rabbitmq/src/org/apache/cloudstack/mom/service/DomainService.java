package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.Domain;
import org.apache.cloudstack.mom.api_interface.BaseInterface;
import org.apache.cloudstack.mom.api_interface.DomainInterface;
import org.apache.log4j.Logger;

import java.util.Date;

public class DomainService extends BaseService {

    private static final Logger s_logger = Logger.getLogger(DomainService.class);
    private DomainInterface apiInterface;

    public DomainService(String hostName, String userName, String password)
    {
        super(hostName, userName, password);
        this.apiInterface = null;
    }

    private boolean isEqual(JSONObject domainJson, String domainName, String networkDomain)
    {
        String jsonDomainName = getAttrValue(domainJson, "name");
        String jsonNetworkDomain = getAttrValue(domainJson, "networkdomain");

        if(!jsonDomainName.equals(domainName)) return false;

        if (jsonNetworkDomain != null || networkDomain != null)
        {
            if (jsonNetworkDomain == null && networkDomain != null) return false;
            if (jsonNetworkDomain != null && networkDomain == null) return false;
            if(!jsonNetworkDomain.equals(networkDomain))    return false;
        }

        return true;
    }

    private String getParentPath(String domainPath)
    {
        if (domainPath.equals("/"))   return null;

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
    protected BaseInterface getInterface()
    {
        return this.apiInterface;
    }

    private JSONObject find(String[] attrNames, String[] attrValues)
    {
        try
        {
            JSONArray domainArray = this.apiInterface.listDomains(true);
            JSONObject domainObj = findJSONObject(domainArray, attrNames, attrValues);
            return domainObj;
        }
        catch(Exception ex)
        {
            return null;
        }
    }

    protected JSONObject findByPath(String domainPath)
    {
        this.apiInterface = new DomainInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            String[] attrNames = {"path"};
            String[] attrValues = {domainPath};
            JSONObject domainJson = find(attrNames, attrValues);
            s_logger.info("Successfully found domain by path[" + domainPath + "]");
            return domainJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find domain by path[" + domainPath + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONArray list()
    {
        this.apiInterface = new DomainInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            JSONArray domainArray = this.apiInterface.listDomains(true);
            s_logger.info("Successfully found domain list");
            return domainArray;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find domain list", ex);
            return new JSONArray();
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean create(Domain domain, String oldDomainName)
    {
        JSONObject resJson = create(domain.getName(), domain.getPath(), domain.getNetworkDomain());
        return (resJson != null);
    }

    protected JSONObject create(String domainName, String domainPath, String networkDomain)
    {
        this.apiInterface = new DomainInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            // check if the domain already exists
            String[] attrNames = {"name", "path"};
            String[] attrValues = {domainName, domainPath};
            JSONObject domainJson = find(attrNames, attrValues);
            if (domainJson != null)
            {
                s_logger.info("domain[" + domainName + "] already exists in host[" + this.hostName + "]");
                return domainJson;
            }

            // find the parent domain id
            String parentDomainId = null;
            String parentDomainPath = getParentPath(domainPath);
            if (parentDomainPath != null)
            {
                String[] pAttrNames = {"path"};
                String[] pAttrValues = {parentDomainPath};
                JSONObject pDomainJson = find(pAttrNames, pAttrValues);
                if (pDomainJson == null)
                {
                    s_logger.info("cannot find parent domain[" + parentDomainPath + "] in host[" + this.hostName + "]");
                    return null;
                }
                parentDomainId = getAttrValue(pDomainJson, "id");
            }

            domainJson = this.apiInterface.createDomain(domainName, parentDomainId, null, networkDomain);
            s_logger.info("Successfully created domain[" + domainName + "] in host[" + this.hostName + "]");
            return domainJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to create domain with name[" + domainName + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean delete(Domain domain, String oldDomainName)
    {
        return delete(domain.getName(), domain.getPath());
    }

    protected boolean delete(String domainName, String domainPath)
    {
        this.apiInterface = new DomainInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            // check if the domain already exists
            String[] attrNames = {"name", "path"};
            String[] attrValues = {domainName, domainPath};
            JSONObject domainJson = find(attrNames, attrValues);
            if (domainJson == null)
            {
                s_logger.info("domain[" + domainName + "] does not exists in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(domainJson, "id");
            JSONObject retJson = this.apiInterface.deleteDomain(id, false);
            queryAsyncJob(retJson);
            s_logger.info("Successfully deleted domain[" + domainName + "] in host[" + this.hostName + "]");
            return true;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to delete domain by name[" + domainName + "]", ex);
            return false;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean update(Domain domain, String oldDomainName)
    {
        // replace the new domain path with the old path
        String domainPath = domain.getPath().replace(domain.getName(), oldDomainName);
        return update(oldDomainName, domain.getName(), domainPath, domain.getNetworkDomain());
    }

    protected boolean update(String domainName, String newName, String domainPath, String networkDomain)
    {
        this.apiInterface = new DomainInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"name", "path"};
            String[] attrValues = {domainName, domainPath};
            JSONObject domainJson = find(attrNames, attrValues);
            if (domainJson == null)
            {
                s_logger.info("domain[" + domainName + "] does not exists in host[" + this.hostName + "]");
                return false;
            }

            if(isEqual(domainJson, newName, networkDomain))
            {
                s_logger.info("domain[" + newName + "] has same attrs in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(domainJson, "id");
            this.apiInterface.updateDomain(id, newName, networkDomain);
            s_logger.info("Successfully updated domain[" + domainName + "] in host[" + this.hostName + "]");
            return true;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to delete domain by name[" + domainName + "]", ex);
            return false;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public Date isRemoved(String domainName, String domainPath, Date created)
    {
        JSONArray eventList = null;
        try
        {
            eventList = listEvents("DOMAIN.DELETE", "completed", created, null);
        }
        catch(Exception ex)
        {
            s_logger.error(ex.getStackTrace());
            return null;
        }
        if (eventList == null || eventList.length() == 0)    return null;

        for (int idx = 0; idx < eventList.length(); idx++)
        {
            try
            {
                JSONObject jsonObject = parseEventDescription(eventList.getJSONObject(idx));
                String eventDomainPath = getAttrValue(jsonObject, "Domain Path");

                if (eventDomainPath == null)  continue;
                if (!eventDomainPath.equals(domainPath))    continue;

                return parseDateStr(getAttrValue(jsonObject, "created"));
            }
            catch(Exception ex)
            {
                s_logger.error(ex.getStackTrace());
                return null;
            }
        }

        return null;
    }
}
