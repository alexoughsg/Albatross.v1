package com.cloud.region.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.Domain;
import com.cloud.region.api_interface.BaseInterface;
import com.cloud.region.api_interface.DomainInterface;
import com.cloud.rmap.RmapVO;
import com.cloud.rmap.dao.RmapDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.region.RegionVO;
import org.apache.log4j.Logger;

public class DomainService extends BaseService {

    private static final Logger s_logger = Logger.getLogger(DomainService.class);
    private DomainInterface apiInterface;

    private RegionVO region;
    private RmapDao rmapDao;

    public DomainService(RegionVO region)
    {
        super(region.getName(), region.getEndPoint(), region.getUserName(), region.getPassword());
        this.apiInterface = null;

        this.region = region;
        this.rmapDao = ComponentContext.getComponent(RmapDao.class);
    }

    public DomainService(String hostName, String endPoint, String userName, String password)
    {
        super(hostName, endPoint, userName, password);
        this.apiInterface = null;

        this.rmapDao = ComponentContext.getComponent(RmapDao.class);
    }

    private boolean isEqual(JSONObject domainJson, String domainName, String networkDomain)
    {
        String jsonDomainName = getAttrValue(domainJson, "name");
        String jsonNetworkDomain = getAttrValue(domainJson, "networkdomain");

        if(!jsonDomainName.equals(domainName)) return false;

        if (jsonNetworkDomain == null && networkDomain == null) return false;
        if (jsonNetworkDomain == null || networkDomain == null) return false;
        return (jsonNetworkDomain.equals(networkDomain));
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

    private JSONObject find(String uuid)
    {
        try
        {
            JSONObject domainObj = this.apiInterface.findDomain(uuid);
            return domainObj;
        }
        catch(Exception ex)
        {
            return null;
        }
    }

    private void saveRmap(Domain domain, JSONObject resJson)
    {
        try
        {
            RmapVO rmapVO = new RmapVO(domain.getUuid(), region.getId(), BaseService.getAttrValue(resJson.getJSONObject("domain"), "id"));
            rmapDao.create(rmapVO);
        }
        catch(Exception ex)
        {

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
            s_logger.debug("Successfully found domain by path[" + domainPath + "]");
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
            s_logger.debug("Successfully found domain list");
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

    public JSONObject findDomain(String uuid)
    {
        this.apiInterface = new DomainInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            JSONObject domainJson = find(uuid);
            s_logger.debug("Successfully found a domain[" + uuid + "]");
            return domainJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find domain", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject findDomain(int level, String name, String path)
    {
        this.apiInterface = new DomainInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            JSONObject domainJson = this.apiInterface.findDomain(level, name, path);
            s_logger.debug("Successfully found a domain[" + name + "] in level[" + level + "]");
            return domainJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find domain", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONArray listChildren(String parentDomainId, boolean isRecursive)
    {
        this.apiInterface = new DomainInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            JSONArray domainArray = this.apiInterface.listChildDomains(parentDomainId, isRecursive);
            s_logger.debug("Successfully found domain list");
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
        if (resJson != null)
        {
            saveRmap(domain, resJson);
            return true;
        }

        return false;
    }

    public JSONObject create(String domainName, String domainPath, String networkDomain)
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
                s_logger.debug("domain[" + domainName + "] already exists in host[" + this.hostName + "]");
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
                    s_logger.error("cannot find parent domain[" + parentDomainPath + "] in host[" + this.hostName + "]");
                    return null;
                }
                parentDomainId = getAttrValue(pDomainJson, "id");
            }

            domainJson = this.apiInterface.createDomain(domainName, parentDomainId, null, networkDomain);
            s_logger.debug("Successfully created domain[" + domainName + "] in host[" + this.hostName + "]");
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
        RmapVO rmap = rmapDao.findRmapBySource(domain.getUuid(), region.getId());

        JSONObject resJson = null;
        if (rmap == null)
        {
            resJson = delete(domain.getName(), domain.getPath());
            if (resJson != null)
            {
                saveRmap(domain, resJson);
            }
        }
        else
        {
            deleteByUuid(rmap.getUuid());
        }

        return (resJson != null);
    }

    protected JSONObject deleteByUuid(String uuid)
    {
        this.apiInterface = new DomainInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            // check if the domain already exists
            JSONObject domainJson = find(uuid);
            if (domainJson == null)
            {
                s_logger.error("domain[" + uuid + "] does not exists in host[" + this.hostName + "]");
                return null;
            }

            JSONObject retJson = this.apiInterface.deleteDomain(uuid, false);
            queryAsyncJob(retJson);
            s_logger.debug("Successfully deleted domain[" + uuid + "] in host[" + this.hostName + "]");
            return domainJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to delete domain by name[" + uuid + "] in host[" + this.hostName + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject delete(String domainName, String domainPath)
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
                s_logger.error("domain[" + domainName + "] does not exists in host[" + this.hostName + "]");
                return null;
            }

            String id = getAttrValue(domainJson, "id");
            JSONObject retJson = this.apiInterface.deleteDomain(id, false);
            queryAsyncJob(retJson);
            s_logger.debug("Successfully deleted domain[" + domainName + "] in host[" + this.hostName + "]");
            return domainJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to delete domain by name[" + domainName + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean update(Domain domain, String oldDomainName)
    {
        RmapVO rmap = rmapDao.findRmapBySource(domain.getUuid(), region.getId());

        JSONObject resJson = null;
        if (rmap == null)
        {
            // replace the new domain path with the old path
            String domainPath = domain.getPath().replace(domain.getName(), oldDomainName);
            resJson = update(oldDomainName, domain.getName(), domainPath, domain.getNetworkDomain());
            if (resJson != null)
            {
                saveRmap(domain, resJson);
            }
        }
        else
        {
            updateByUuid(rmap.getUuid(), domain.getName(), domain.getNetworkDomain());
        }

        return (resJson != null);
    }

    protected JSONObject updateByUuid(String uuid, String newName, String networkDomain)
    {
        this.apiInterface = new DomainInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            JSONObject domainJson = find(uuid);
            if (domainJson == null)
            {
                s_logger.error("domain[" + uuid + "] does not exists in host[" + this.hostName + "]");
                return null;
            }

            if(isEqual(domainJson, newName, networkDomain))
            {
                s_logger.debug("domain[" + uuid + "] has same attrs in host[" + this.hostName + "]");
                return domainJson;
            }

            this.apiInterface.updateDomain(uuid, newName, networkDomain);
            s_logger.debug("Successfully updated domain[" + uuid + "] in host[" + this.hostName + "]");
            return domainJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to delete domain by name[" + uuid + "] in host[" + this.hostName + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject update(String domainName, String newName, String domainPath, String networkDomain)
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
                s_logger.error("domain[" + domainName + "] does not exists in host[" + this.hostName + "]");
                return null;
            }

            if(isEqual(domainJson, newName, networkDomain))
            {
                s_logger.debug("domain[" + newName + "] has same attrs in host[" + this.hostName + "]");
                return domainJson;
            }

            String id = getAttrValue(domainJson, "id");
            this.apiInterface.updateDomain(id, newName, networkDomain);
            s_logger.debug("Successfully updated domain[" + domainName + "] in host[" + this.hostName + "]");
            return domainJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to delete domain by name[" + domainName + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }
}
