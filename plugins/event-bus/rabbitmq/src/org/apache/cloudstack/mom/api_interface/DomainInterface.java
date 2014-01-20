package org.apache.cloudstack.mom.api_interface;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import org.apache.cloudstack.mom.service.BaseService;

import java.net.URLEncoder;

public class DomainInterface extends BaseInterface {

    public DomainInterface(String url)
    {
        super(url);
    }

    public JSONArray listDomains(boolean listAll) throws Exception
    {
        // command=listDomains&response=json&sessionkey=null&_=1362457544896
        // { "listdomainsresponse" : { "count":2 ,"domain" : [  {"id":"45152a26-a2ce-11e2-8da9-28fb734f3313","name":"ROOT","level":0,"haschild":true,"path":"ROOT"}, {"id":"3d12e7d5-a528-4626-a423-d1e17024ff91","name":"Ough","level":1,"parentdomainid":"45152a26-a2ce-11e2-8da9-28fb734f3313","parentdomainname":"ROOT","haschild":false,"path":"ROOT/Ough"} ] } }

        String paramStr = "command=listDomains&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (listAll)    paramStr += "&listall" + listAll;
        JSONObject retJson = sendApacheGet(paramStr);
        if (!BaseInterface.hasAttribute(retJson, "domain"))
        {
            return new JSONArray();
        }
        return retJson.getJSONArray("domain");
    }

    public JSONObject findDomain(int level, String name, String path) throws Exception
    {
        // command=listDomains&response=json&sessionkey=null&_=1362457544896
        // { "listdomainsresponse" : { "count":2 ,"domain" : [  {"id":"45152a26-a2ce-11e2-8da9-28fb734f3313","name":"ROOT","level":0,"haschild":true,"path":"ROOT"}, {"id":"3d12e7d5-a528-4626-a423-d1e17024ff91","name":"Ough","level":1,"parentdomainid":"45152a26-a2ce-11e2-8da9-28fb734f3313","parentdomainname":"ROOT","haschild":false,"path":"ROOT/Ough"} ] } }

        String paramStr = "command=listDomains&level=" + level + "&name=" + name + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        JSONArray domains = (JSONArray)sendApacheGet(paramStr).get("domain");
        if (domains == null)    return null;
        if (domains.length() == 0)  return null;
        if (path == null) return domains.getJSONObject(0);

        for(int idx = 0; idx < domains.length(); idx++)
        {
            JSONObject jsonObject = domains.getJSONObject(idx);
            String pathInJson = BaseService.getAttrValue(jsonObject, "path");
            if (BaseService.compareDomainPath(path, pathInJson))    return jsonObject;
        }
        return null;
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
        if (!BaseInterface.hasAttribute(retJson, "domain"))
        {
            return new JSONArray();
        }
        return retJson.getJSONArray("domain");
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
        if (parentDomainId != null)    paramStr += "&'parentdomainid'=" + parentDomainId;
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
