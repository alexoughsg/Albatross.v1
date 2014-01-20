package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.Domain;
import com.cloud.user.Account;
import com.cloud.user.User;
import org.apache.cloudstack.mom.api_interface.BaseInterface;
import org.apache.cloudstack.mom.api_interface.UserInterface;
import org.apache.log4j.Logger;

public class UserService extends BaseService {

    private static final Logger s_logger = Logger.getLogger(UserService.class);
    private UserInterface apiInterface;

    public UserService(String hostName, String userName, String password)
    {
        super(hostName, userName, password);
        this.apiInterface = null;
    }

    private boolean isEqual(JSONObject userJson, String userName, String email, String firstName, String lastName, String password, String timezone, String apiKey, String secretKey)
    {
        String jsonUserName = getAttrValue(userJson, "username");
        String jsonEmail = getAttrValue(userJson, "email");
        String jsonFirstName = getAttrValue(userJson, "firstname");
        String jsonLastName = getAttrValue(userJson, "lastname");
        String jsonApiKey = getAttrValue(userJson, "apikey");
        String jsonSecretKey = getAttrValue(userJson, "secretkey");
        String jsonTimezone = getAttrValue(userJson, "timezone");
        String jsonPassword = getAttrValue(userJson, "password");

        if(!jsonUserName.equals(userName))    return false;
        if(!jsonEmail.equals(email))    return false;
        if(!jsonFirstName.equals(firstName))    return false;
        if(!jsonLastName.equals(lastName))    return false;

        if(jsonApiKey != null || apiKey != null)
        {
            if(jsonApiKey == null && apiKey != null)  return false;
            if(jsonApiKey != null && apiKey == null)  return false;
            if(!jsonApiKey.equals(apiKey))    return false;
        }

        if(jsonSecretKey != null || secretKey != null)
        {
            if(jsonSecretKey == null && secretKey != null)    return false;
            if(jsonSecretKey != null && secretKey == null)    return false;
            if(!jsonSecretKey.equals(secretKey))    return false;
        }

        if(jsonTimezone != null || timezone != null)
        {
            if(jsonTimezone == null && timezone != null)  return false;
            if(jsonTimezone != null && timezone == null)  return false;
            if(!jsonTimezone.equals(timezone))    return false;
        }

        //if(!jsonPassword.equals(password))    return false;

        return true;
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
            JSONArray userArray = this.apiInterface.listUsers(null, null);
            JSONObject userObj = findJSONObject(userArray, attrNames, attrValues);
            return userObj;
        }
        catch(Exception ex)
        {
            return null;
        }
    }

    public JSONArray list(String domainId, String accountName)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            JSONArray userArray = this.apiInterface.listUsers(domainId, accountName);
            s_logger.info("Successfully found user list");
            return userArray;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find users", ex);
            return new JSONArray();
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject findById(String id)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            String[] attrNames = {"id"};
            String[] attrValues = {id};
            JSONObject userJson = find(attrNames, attrValues);
            s_logger.info("Successfully found user by id[" + id + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find user by id[" + id + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject findByName(String userName, String domainPath)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            String[] attrNames = {"username", "path"};
            String[] attrValues = {userName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            s_logger.info("Successfully found user by name[" + userName + ", " + domainPath + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find user by name[" + userName + ", " + domainPath + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean create(User user, Account account, Domain domain, String oldUserName)
    {
        JSONObject resJson = create(user.getUsername(), account.getAccountName(), domain.getPath(), user.getPassword(), user.getEmail(), user.getFirstname(), user.getLastname(), user.getTimezone());
        return (resJson != null);
    }

    protected JSONObject create(String userName, String accountName, String domainPath, String password, String email, String firstName, String lastName, String timezone)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            // check if the user already exists
            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson != null)
            {
                s_logger.info("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] already exists in host[" + this.hostName + "]");
                return userJson;
            }

            // find domain id
            DomainService domainService = new DomainService(this.hostName, this.userName, this.password);
            JSONObject domainObj = domainService.findByPath(domainPath);
            if (domainObj == null)
            {
                s_logger.info("cannot find domain[" + domainPath + "] in host[" + this.hostName + "]");
                return null;
            }
            String domainId = (String)domainObj.get("id");

            userJson = this.apiInterface.createUser(userName, password, email, firstName, lastName, accountName, domainId, timezone);
            s_logger.info("Successfully created user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to create user with name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean delete(User user, Account account, Domain domain, String oldUserName)
    {
        return delete(user.getUsername(), account.getAccountName(), domain.getPath());
    }

    protected boolean delete(String userName, String accountName, String domainPath)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            // check if the user already exists
            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson == null)
            {
                s_logger.info("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] does not exists in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(userJson, "id");
            this.apiInterface.deleteUser(id);
            s_logger.info("Successfully deleted user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return true;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to delete user by name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return false;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean enable(User user, Account account, Domain domain, String oldUserName)
    {
        return enable(user.getUsername(), account.getAccountName(), domain.getPath());
    }

    protected boolean enable(String userName, String accountName, String domainPath)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson == null)
            {
                s_logger.info("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] does not exists in host[" + this.hostName + "]");
                return false;
            }

            String state = getAttrValue(userJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_ENABLED))
            {
                s_logger.info("user[" + userName + "] in account[" + accountName + "] in domain[" + domainPath + "] is already enabled in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(userJson, "id");
            this.apiInterface.enableUser(id);
            s_logger.info("Successfully enabled user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return true;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to enable user by name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return false;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean disable(User user, Account account, Domain domain, String oldUserName)
    {
        return disable(user.getUsername(), account.getAccountName(), domain.getPath());
    }

    protected boolean disable(String userName, String accountName, String domainPath)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson == null)
            {
                s_logger.info("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "]  does not exists in host[" + this.hostName + "]");
                return false;
            }

            String state = getAttrValue(userJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_DISABLED))
            {
                s_logger.info("user[" + userName + "] in account[" + accountName + "] in domain[" + domainPath + "] is already disabled in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(userJson, "id");
            JSONObject retJson = this.apiInterface.disableUser(id);
            queryAsyncJob(retJson);
            s_logger.info("Successfully disabled user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return true;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to disable user by name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return false;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean lock(User user, Account account, Domain domain, String oldUserName)
    {
        return lock(user.getUsername(), account.getAccountName(), domain.getPath());
    }

    protected boolean lock(String userName, String accountName, String domainPath)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson == null)
            {
                s_logger.info("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "]  does not exists in host[" + this.hostName + "]");
                return false;
            }

            String state = getAttrValue(userJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_LOCKED))
            {
                s_logger.info("user[" + userName + "] in account[" + accountName + "] in domain[" + domainPath + "] is already locked in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(userJson, "id");
            this.apiInterface.disableUser(id);
            s_logger.info("Successfully disabled user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return true;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to disable user by name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return false;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean update(User user, Account account, Domain domain, String oldUserName)
    {
        return update(oldUserName, user.getUsername(), account.getAccountName(), domain.getPath(), user.getEmail(), user.getFirstname(), user.getLastname(), user.getPassword(), user.getTimezone(), user.getApiKey(), user.getSecretKey());
    }

    protected boolean update(String userName, String newName, String accountName, String domainPath, String email, String firstName, String lastName, String password, String timezone, String userAPIKey, String userSecretKey)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson == null)
            {
                s_logger.info("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "]  does not exists in host[" + this.hostName + "]");
                return false;
            }

            if(isEqual(userJson, newName, email, firstName, lastName, password, timezone, userAPIKey, userSecretKey))
            {
                s_logger.info("account[" + newName + "] has same attrs in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(userJson, "id");
            this.apiInterface.updateUser(id, email, firstName, lastName, password, timezone, userAPIKey, newName, userSecretKey);
            s_logger.info("Successfully updated user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return true;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to update user by name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return false;
        }
        finally {
            this.apiInterface.logout();
        }
    }
}
