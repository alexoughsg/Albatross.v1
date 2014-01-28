package com.cloud.region.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.Domain;
import com.cloud.user.Account;
import com.cloud.user.User;
import com.cloud.region.api_interface.AccountInterface;
import com.cloud.region.api_interface.BaseInterface;
import org.apache.log4j.Logger;

public class AccountService extends BaseService {

    private static final Logger s_logger = Logger.getLogger(AccountService.class);
    private AccountInterface apiInterface;

    public AccountService(String hostName, String endPoint, String userName, String password)
    {
        super(hostName, endPoint, userName, password);
        this.apiInterface = null;
    }

    private boolean isEqual(JSONObject accountJson, String accountName, String networkDomain)
    {
        String jsonAccountName = getAttrValue(accountJson, "name");
        String jsonNetworkDomain = getAttrValue(accountJson, "networkdomain");

        if(!jsonAccountName.equals(accountName)) return false;
        if (jsonNetworkDomain == null && networkDomain == null) return true;
        if (jsonNetworkDomain == null || networkDomain == null) return false;
        return (jsonNetworkDomain.equals(networkDomain));
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
            JSONArray accountArray = this.apiInterface.listAccounts(null);
            JSONObject accountObj = findJSONObject(accountArray, attrNames, attrValues);
            return accountObj;
        }
        catch(Exception ex)
        {
            return null;
        }
    }

    private String findDomainId(String domainPath)
    {
        DomainService domainService = new DomainService(this.hostName, this.endPoint, this.userName, this.password);
        JSONObject domainObj = domainService.findByPath(domainPath);
        if (domainObj == null)
        {
            return null;
        }

        return getAttrValue(domainObj, "id");
    }

    public JSONArray list(String domainId)
    {
        this.apiInterface = new AccountInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            JSONArray accountArray = this.apiInterface.listAccounts(domainId);
            s_logger.debug("Successfully found account list");
            return accountArray;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find accounts", ex);
            return new JSONArray();
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject findAccount(String domainId, String accountName)
    {
        this.apiInterface = new AccountInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            JSONObject accountJson = this.apiInterface.findAccount(domainId, accountName);
            s_logger.debug("Successfully found account");
            return accountJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find accounts", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean create(User user, Account account, Domain domain, String oldAccountName)
    {
        if (user == null)
        {
            s_logger.error("Failed to create account with name[" + account.getAccountName() + "] because user information was not provided.");
            return false;
        }
        JSONObject resJson = create(account.getAccountName(), domain.getPath(), user.getUsername(), user.getPassword(), user.getEmail(), user.getFirstname(), user.getLastname(), String.valueOf(account.getType()),  null, account.getNetworkDomain(), user.getTimezone());
        return (resJson != null);
    }

    public JSONObject create(String accountName, String domainPath, String userName, String password, String email, String firstName, String lastName, String accountType, String accountDetails, String networkDomain, String timezone)
    {
        this.apiInterface = new AccountInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            // check if the account already exists
            String[] attrNames = {"name", "path"};
            String[] attrValues = {accountName, domainPath};
            JSONObject accountJson = find(attrNames, attrValues);
            if (accountJson != null)
            {
                s_logger.debug("account[" + accountName + "] in domain[" + domainPath + "] already exists in host[" + this.hostName + "]");
                return accountJson;
            }

            // find domain id of the given domain
            String domainId = findDomainId(domainPath);
            if (domainId == null)
            {
                s_logger.info("cannot find domain[" + domainPath + "] in host[" + this.hostName + "]");
                return null;
            }

            accountJson = this.apiInterface.createAccount(userName, password, email, firstName, lastName, accountType, domainId, accountName, accountDetails, networkDomain, timezone);
            s_logger.debug("Successfully created account[" + accountName + "] in domain[" + domainPath + "] in host[" + this.hostName + "]");
            return accountJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to create account with name[" + accountName + "] in domain[" + domainPath + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean delete(User user, Account account, Domain domain, String oldAccountName)
    {
        return delete(account.getAccountName(), domain.getPath());
    }

    public boolean delete(String accountName, String domainPath)
    {
        this.apiInterface = new AccountInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            // check if the account already exists
            String[] attrNames = {"name", "path"};
            String[] attrValues = {accountName, domainPath};
            JSONObject accountJson = find(attrNames, attrValues);
            if (accountJson == null)
            {
                s_logger.error("account[" + accountName + "] in domain[" + domainPath + "] does not exists in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(accountJson, "id");
            JSONObject retJson = this.apiInterface.deleteAccount(id);
            queryAsyncJob(retJson);
            s_logger.debug("Successfully deleted account[" + accountName + "] in domain[" + domainPath + "] in host[" + this.hostName + "]");
            return true;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to delete account by name[" + accountName + "] in domain[" + domainPath + "]", ex);
            return false;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean enable(User user, Account account, Domain domain, String oldAccountName)
    {
        return enable(account.getAccountName(), domain.getPath());
    }

    public boolean enable(String accountName, String domainPath)
    {
        this.apiInterface = new AccountInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"name", "path"};
            String[] attrValues = {accountName, domainPath};
            JSONObject accountJson = find(attrNames, attrValues);
            if (accountJson == null)
            {
                s_logger.error("account[" + accountName + "] in domain[" + domainPath + "] does not exists in host[" + this.hostName + "]");
                return false;
            }

            String state = getAttrValue(accountJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_ENABLED))
            {
                s_logger.debug("account[" + accountName + "] in domain[" + domainPath + "] is already enabled in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(accountJson, "id");
            this.apiInterface.enableAccount(id);
            s_logger.debug("Successfully enabled account[" + accountName + "] in domain[" + domainPath + "] in host[" + this.hostName + "]");
            return true;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to enable account by name[" + accountName + "] in domain[" + domainPath + "]", ex);
            return false;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean disable(User user, Account account, Domain domain, String oldAccountName)
    {
        return disable(account.getAccountName(), domain.getPath());
    }

    public boolean disable(String accountName, String domainPath)
    {
        this.apiInterface = new AccountInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"name", "path"};
            String[] attrValues = {accountName, domainPath};
            JSONObject accountJson = find(attrNames, attrValues);
            if (accountJson == null)
            {
                s_logger.error("account[" + accountName + "] in domain[" + domainPath + "] does not exists in host[" + this.hostName + "]");
                return false;
            }

            String state = getAttrValue(accountJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_DISABLED))
            {
                s_logger.debug("account[" + accountName + "] in domain[" + domainPath + "] is already disabled in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(accountJson, "id");
            JSONObject retJson = this.apiInterface.disableAccount(id);
            queryAsyncJob(retJson);
            s_logger.debug("Successfully disabled account[" + accountName + "] in domain[" + domainPath + "] in host[" + this.hostName + "]");
            return true;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to disable account by name[" + accountName + "] in domain[" + domainPath + "]", ex);
            return false;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean lock(User user, Account account, Domain domain, String oldAccountName)
    {
        return lock(account.getAccountName(), domain.getPath());
    }

    public boolean lock(String accountName, String domainPath)
    {
        this.apiInterface = new AccountInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"name", "path"};
            String[] attrValues = {accountName, domainPath};
            JSONObject accountJson = find(attrNames, attrValues);
            if (accountJson == null)
            {
                s_logger.error("account[" + accountName + "] in domain[" + domainPath + "] does not exists in host[" + this.hostName + "]");
                return false;
            }

            String state = getAttrValue(accountJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_LOCKED))
            {
                s_logger.debug("account[" + accountName + "] in domain[" + domainPath + "] is already locked in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(accountJson, "id");
            this.apiInterface.lockAccount(id);
            s_logger.debug("Successfully locked account[" + accountName + "] in domain[" + domainPath + "] in host[" + this.hostName + "]");
            return true;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to lock account by name[" + accountName + "] in domain[" + domainPath + "]", ex);
            return false;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean update(User user, Account account, Domain domain, String oldAccountName)
    {
        return update(oldAccountName, domain.getPath(), account.getAccountName(), null, account.getNetworkDomain());
    }

    public boolean update(String accountName, String domainPath, String newName, String details, String networkDomain)
    {
        this.apiInterface = new AccountInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"name", "path"};
            String[] attrValues = {accountName, domainPath};
            JSONObject accountJson = find(attrNames, attrValues);
            if (accountJson == null)
            {
                s_logger.error("account[" + accountName + "] in domain[" + domainPath + "] does not exists in host[" + this.hostName + "]");
                return false;
            }

            if(isEqual(accountJson, newName, networkDomain))
            {
                s_logger.debug("account[" + newName + "] has same attrs in host[" + this.hostName + "]");
                return false;
            }

            String id = getAttrValue(accountJson, "id");

            // find domain id of the given domain
            String domainId = findDomainId(domainPath);
            if (domainId == null)
            {
                s_logger.error("cannot find domain[" + domainPath + "] in host[" + this.hostName + "]");
                return false;
            }

            this.apiInterface.updateAccount(id, accountName, newName, details, domainId, networkDomain);
            s_logger.debug("Successfully updated user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
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
