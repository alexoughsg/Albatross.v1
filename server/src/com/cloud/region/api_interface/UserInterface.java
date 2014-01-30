package com.cloud.region.api_interface;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.region.service.BaseService;

import java.net.URLEncoder;

public class UserInterface extends BaseInterface {

    public UserInterface(String url)
    {
        super(url);
    }

    public JSONArray listUsers(String domainId, String accountName) throws Exception
    {
        //String paramStr = "command=listUsers&listAll=true&page=1&pagesize=20&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        String paramStr = "command=listUsers&listAll=true&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (domainId != null)   paramStr += "&domainid=" + domainId;
        if (accountName != null)   paramStr += "&account=" + accountName;
        JSONObject retJson = sendApacheGet(paramStr);
        if (!BaseService.hasAttribute(retJson, "user"))
        {
            return new JSONArray();
        }

        if (retJson.length() == 0)  return new JSONArray();
        if (domainId == null)   return retJson.getJSONArray("user");

        JSONArray userArray = new JSONArray();
        JSONArray retArray = retJson.getJSONArray("user");
        for(int index = 0; index < retArray.length(); index++)
        {
            if (retArray.getJSONObject(index).get("domain").equals("ROOT") && retArray.getJSONObject(index).get("account").equals("system")) continue;
            if (!retArray.getJSONObject(index).get("domainid").equals(domainId)) continue;
            userArray.put(retArray.getJSONObject(index));
        }

        return userArray;
    }

    public JSONObject findUser(String uuid) throws Exception
    {
        String paramStr = "command=listUsers&id=" + uuid + "&listAll=true&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        JSONObject retJson = sendApacheGet(paramStr);
        if (!BaseService.hasAttribute(retJson, "user"))
        {
            return null;
        }

        if (retJson.length() == 0)  return null;

        JSONArray users = retJson.getJSONArray("user");
        if (users == null)    return null;
        if (users.length() == 0)  return null;

        return users.getJSONObject(0);
    }

    public JSONObject createUser(String userName, String password, String email, String firstName, String lastName, String accountName, String domainId, String timezone) throws Exception
    {
        String paramStr = "command=createUser&username=" + userName + "&password=" + password;
        paramStr += "&email=" + email + "&firstname=" + firstName + "&lastname=" + lastName + "&account=" + accountName;
        paramStr += "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (domainId != null)
            paramStr += "&domainid=" + domainId;
        if (timezone != null)
            paramStr += "&timezone=" + URLEncoder.encode(timezone, "UTF-8");

        return sendApachePost(paramStr);
    }

    public JSONObject deleteUser(String userId) throws Exception
    {
        String paramStr = "command=deleteUser&id=" + userId + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        return sendApacheGet(paramStr);
    }

    public JSONObject updateUser(String userId, String email, String firstName, String lastName, String password, String timezone, String userAPIKey, String userName, String userSecretKey) throws Exception
    {
        String paramStr = "command=updateUser&id=" + userId + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        if (email != null)  paramStr += "&email=" + email;
        if (firstName != null)  paramStr += "&firstname=" + firstName;
        if (lastName != null)   paramStr += "&lastname=" + lastName;
        if (password != null)    paramStr += "&password=" + password;
        if (timezone != null)    paramStr += "&timezone=" + URLEncoder.encode(timezone, "UTF-8");
        if (userAPIKey != null)    paramStr += "&userapikey=" + userAPIKey;
        if (userName != null)   paramStr += "&username=" + userName;
        if (userSecretKey != null) paramStr += "&usersecretkey=" + userSecretKey;

        return sendApacheGet(paramStr);
    }

    public JSONObject lockUser(String userId) throws Exception
    {
        String paramStr = "command=lockUser&id=" + userId + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        return sendApacheGet(paramStr);
    }

    public JSONObject disableUser(String userId) throws Exception
    {
        String paramStr = "command=disableUser&id=" + userId + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        return sendApacheGet(paramStr);
    }

    public JSONObject enableUser(String userId) throws Exception
    {
        String paramStr = "command=enableUser&id=" + userId + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        return sendApacheGet(paramStr);
    }

    public JSONObject getUser(String userAPIKey) throws Exception
    {
        String paramStr = "command=getUser&userapikey=" + userAPIKey + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        return sendApacheGet(paramStr);
    }

    public JSONObject registerUserKeys(String userId) throws Exception
    {
        String paramStr = "command=registerUserKeys&id=" + userId + "&response=json&sessionkey=" + URLEncoder.encode(this.sessionKey, "UTF-8");
        return sendApacheGet(paramStr);
    }
}
