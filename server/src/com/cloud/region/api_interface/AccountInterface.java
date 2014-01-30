package com.cloud.region.api_interface;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.region.service.BaseService;

import java.net.URLEncoder;

public class AccountInterface extends BaseInterface {

    public AccountInterface(String url)
    {
        super(url);
    }

    public JSONArray listAccounts(String domainId) throws Exception
    {
        // command=listAccounts&response=json&sessionkey=XxjzeJWHV3S%2Brwq2m2EsYTSIYNE%3D&listAll=true&page=1&pagesize=20&_=1362457447296

        String paramStr = "command=listAccounts&listAll=true&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (domainId != null)   paramStr += "&domainId=" + domainId;
        JSONObject retJson = sendApacheGet(paramStr);
        if (!BaseService.hasAttribute(retJson, "account"))
        {
            return new JSONArray();
        }

        if (retJson.length() == 0)  return new JSONArray();
        if (domainId == null)   return retJson.getJSONArray("account");

        JSONArray accountArray = new JSONArray();
        JSONArray retArray = retJson.getJSONArray("account");
        for(int index = 0; index < retArray.length(); index++)
        {
            if (!retArray.getJSONObject(index).get("domainid").equals(domainId)) continue;
            accountArray.put(retArray.getJSONObject(index));
        }

        return accountArray;
    }

    public JSONObject findAccount(String domainId, String accountName) throws Exception
    {
        String paramStr = "command=listAccounts&domainid=" + domainId + "&name=" + accountName + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        JSONObject retJson = sendApacheGet(paramStr);
        if (!BaseService.hasAttribute(retJson, "account"))
        {
            return null;
        }

        if (retJson.length() == 0)  return null;

        JSONArray accounts = retJson.getJSONArray("account");
        if (accounts == null)    return null;
        if (accounts.length() == 0)  return null;

        return accounts.getJSONObject(0);
    }

    public JSONObject findAccount(String uuid) throws Exception
    {
        String paramStr = "command=listAccounts&id=" + uuid + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        JSONObject retJson = sendApacheGet(paramStr);
        if (!BaseService.hasAttribute(retJson, "account"))
        {
            return null;
        }

        if (retJson.length() == 0)  return null;

        JSONArray accounts = (JSONArray)retJson.get("account");
        if (accounts == null)    return null;
        if (accounts.length() == 0)  return null;

        return accounts.getJSONObject(0);
    }

    public JSONObject createAccount(String userName, String password, String email, String firstName, String lastName, String accountType, String domainId, String accountName, String accountDetails, String networkDomain, String timezone) throws Exception
    {
        /*
            command=createAccount&response=json&sessionkey=WyKKl72c8fi1d6y%2Bp%2BQuDGxDnZg%3D
                username
                email
                account
                lastname
                accounttype    # User : "0"
                domainid
                firstname
                password
         */

        String paramStr = "command=createAccount&username=" + userName + "&password=" + password;
        paramStr += "&email=" + email + "&firstname=" + firstName + "&lastname=" + lastName + "&accounttype=" + accountType;
        paramStr += "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (accountName != null)    paramStr += "&account=" + accountName;
        if (domainId != null)   paramStr += "&domainid=" + domainId;
        if (accountDetails != null) paramStr += "&accountdetails=" + accountDetails;
        if (networkDomain != null) paramStr += "&networkdomain=" + networkDomain;
        if (timezone != null)   paramStr += "&timezone=" + URLEncoder.encode(timezone, "UTF-8");

        return sendApachePost(paramStr);
    }

    public JSONObject updateAccount(String accountId, String currentName, String newName, String details, String domainId, String networkDomain) throws Exception
    {
        String paramStr = "command=updateAccount&id=" + accountId + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (currentName != null)  paramStr += "&account=" + currentName;
        if (newName != null)  paramStr += "&newname=" + newName;
        if (details != null)   paramStr += "&accountdetails=" + details;
        if (domainId != null)    paramStr += "&domainid=" + domainId;
        if (networkDomain != null)    paramStr += "&networkdomain=" + networkDomain;

        return sendApacheGet(paramStr);
    }

    public JSONObject deleteAccount(String accountId) throws Exception
    {
        String paramStr = "command=deleteAccount&id=" + accountId + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        return sendApacheGet(paramStr);
    }

    public JSONObject disableAccount(String accountId) throws Exception
    {
        String paramStr = "command=disableAccount&id=" + accountId + "&lock=false&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        return sendApacheGet(paramStr);
    }

    public JSONObject lockAccount(String accountId) throws Exception
    {
        String paramStr = "command=disableAccount&id=" + accountId + "&lock=true&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        return sendApacheGet(paramStr);
    }

    public JSONObject enableAccount(String accountId) throws Exception
    {
        String paramStr = "command=enableAccount&id=" + accountId + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        return sendApacheGet(paramStr);
    }

    public JSONObject lockAccount() throws Exception
    {
        throw new Exception("Not implemented");
    }

    public JSONObject markDefaultZoneForAccount() throws Exception
    {
        throw new Exception("Not implemented");
    }

    public JSONObject addAccountToProject() throws Exception
    {
        throw new Exception("Not implemented");
    }

    public JSONObject deleteAccountFromProject() throws Exception
    {
        throw new Exception("Not implemented");
    }

    public JSONObject listProjectAccounts() throws Exception
    {
        throw new Exception("Not implemented");
    }
}
