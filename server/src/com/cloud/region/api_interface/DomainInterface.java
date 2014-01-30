package com.cloud.region.api_interface;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.region.service.BaseService;

import java.net.URLEncoder;

public class DomainInterface extends BaseInterface {

    public DomainInterface(String url)
    {
        super(url);
    }

    private void modifyPath(JSONObject domainJson)
    {
        String domainPath = BaseService.getAttrValue(domainJson, "path");
        if (domainPath != null)
        {
            domainPath = BaseService.modifyDomainPath(domainPath);
            BaseService.setAttrValue(domainJson, "path", domainPath);
        }
    }

    public JSONArray listDomains(boolean listAll) throws Exception
    {
        // command=listDomains&response=json&sessionkey=null&_=1362457544896
        // { "listdomainsresponse" : { "count":2 ,"domain" : [  {"id":"45152a26-a2ce-11e2-8da9-28fb734f3313","name":"ROOT","level":0,"haschild":true,"path":"ROOT"}, {"id":"3d12e7d5-a528-4626-a423-d1e17024ff91","name":"Ough","level":1,"parentdomainid":"45152a26-a2ce-11e2-8da9-28fb734f3313","parentdomainname":"ROOT","haschild":false,"path":"ROOT/Ough"} ] } }

        String paramStr = "command=listDomains&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (listAll)    paramStr += "&listall" + listAll;
        JSONObject retJson = sendApacheGet(paramStr);
        if (!BaseService.hasAttribute(retJson, "domain"))
        {
            return new JSONArray();
        }

        JSONArray retArray = retJson.getJSONArray("domain");
        for(int idx = 0; idx < retArray.length(); idx++)
        {
            modifyPath(retArray.getJSONObject(idx));
        }

        return retArray;
    }

    public JSONObject findDomain(String uuid) throws Exception
    {
        String paramStr = "command=listDomains&id=" + uuid + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        JSONObject retJson = sendApacheGet(paramStr);
        if (!BaseService.hasAttribute(retJson, "domain"))
        {
            return null;
        }

        JSONArray domains = retJson.getJSONArray("domain");
        if (domains == null)    return null;
        if (domains.length() == 0)  return null;

        JSONObject found = domains.getJSONObject(0);
        modifyPath(found);
        return found;
    }

    public JSONObject findDomain(int level, String name, String path) throws Exception
    {
        // command=listDomains&response=json&sessionkey=null&_=1362457544896
        // { "listdomainsresponse" : { "count":2 ,"domain" : [  {"id":"45152a26-a2ce-11e2-8da9-28fb734f3313","name":"ROOT","level":0,"haschild":true,"path":"ROOT"}, {"id":"3d12e7d5-a528-4626-a423-d1e17024ff91","name":"Ough","level":1,"parentdomainid":"45152a26-a2ce-11e2-8da9-28fb734f3313","parentdomainname":"ROOT","haschild":false,"path":"ROOT/Ough"} ] } }

        String paramStr = "command=listDomains&level=" + level + "&name=" + name + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        JSONObject retJson = sendApacheGet(paramStr);
        if (!BaseService.hasAttribute(retJson, "domain"))
        {
            return null;
        }

        JSONArray domains = retJson.getJSONArray("domain");
        if (domains == null)    return null;
        if (domains.length() == 0)  return null;
        if (path == null) return domains.getJSONObject(0);

        JSONObject found = null;
        for(int idx = 0; idx < domains.length(); idx++)
        {
            JSONObject jsonObject = domains.getJSONObject(idx);
            String pathInJson = BaseService.getAttrValue(jsonObject, "path");
            if (BaseService.compareDomainPath(path, pathInJson))
            {
                found = jsonObject;
                break;
            }
        }

        if (found == null)  return null;

        modifyPath(found);
        return found;
    }

    public JSONArray listChildDomains(String parentDomainId, boolean isRecursive) throws Exception
    {
        String paramStr = "command=listDomainChildren&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (parentDomainId != null)    paramStr += "&id=" + parentDomainId;
        if (isRecursive)
        {
            paramStr += "&isrecursive=true";
        }
        else
        {
            paramStr += "&isrecursive=false";
        }
        JSONObject retJson = (JSONObject)sendApacheGet(paramStr);
        if (!BaseService.hasAttribute(retJson, "domain"))
        {
            return new JSONArray();
        }

        JSONArray retArray = retJson.getJSONArray("domain");
        for(int idx = 0; idx < retArray.length(); idx++)
        {
            modifyPath(retArray.getJSONObject(idx));
        }

        return retArray;
    }

    public JSONObject createDomain(String name, String parentDomainId, String domainId, String networkDomain) throws Exception
    {
        /*
            command=createDomain
            &response=json
            &sessionkey=WyKKl72c8fi1d6y%2Bp%2BQuDGxDnZg%3D
            &parentdomainid=b8683900-a486-11e2-8da9-28fb734f3313
            &name=Eldridge
            &_=1365892060259
         */

        String paramStr = "command=createDomain&name=" + name + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (parentDomainId != null)    paramStr += "&parentdomainid=" + parentDomainId;
        if (domainId != null)   paramStr += "&domainid=" + domainId;
        if (networkDomain != null) paramStr += "&networkdomain=" + networkDomain;
        return sendApacheGet(paramStr);
    }

    public JSONObject updateDomain(String domainId, String name, String networkDomain) throws Exception
    {
        String paramStr = "command=updateDomain&id=" + domainId + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (name != null)  paramStr += "&name=" + name;
        if (networkDomain != null)    paramStr += "&networkdomain=" + networkDomain;

        return sendApacheGet(paramStr);
    }

    public JSONObject deleteDomain(String domainId, boolean cleanUp) throws Exception
    {
        String paramStr = "command=deleteDomain&id=" + domainId + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (cleanUp)    paramStr += "&cleanup=" + cleanUp;
        return sendApacheGet(paramStr);
    }
}
