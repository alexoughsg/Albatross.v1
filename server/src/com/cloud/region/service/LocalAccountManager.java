package com.cloud.region.service;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.utils.component.ComponentContext;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class LocalAccountManager {

    private static final Logger s_logger = Logger.getLogger(LocalAccountManager.class);

    //protected AccountDao accountDao;
    protected DomainDao domainDao;
    private AccountManager accountManager;

    public LocalAccountManager()
    {
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.accountManager = ComponentContext.getComponent(AccountManager.class);
    }

    private Account.State findState(String stateStr)
    {
        for(Account.State next : Account.State.values())
        {
            if (stateStr.equals(next.toString()))
            {
                return next;
            }
        }
        return null;
    }

    public Object create(JSONObject jsonObject, final Date created) throws Exception
    {
        // find domain id
        String domainPath = BaseService.getAttrValue(jsonObject, "path");
        DomainVO domain = domainDao.findDomainByPath(domainPath);
        if (domain == null)
        {
            throw new Exception("Failed to create a account because its domain[" + domainPath + "] cannot be found");
        }

        Long domainId = domain.getId();
        String accountName = BaseService.getAttrValue(jsonObject, "name");
        String stateStr = BaseService.getAttrValue(jsonObject, "state");
        Account.State state = findState(stateStr);
        String networkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
        short accountType = Short.parseShort(BaseService.getAttrValue(jsonObject, "accounttype"));
        Map<String, String> accountDetails = null;
        String accountUUID = UUID.randomUUID().toString();
        String initialName = BaseService.getAttrValue(jsonObject, "initialname");

        return accountManager.createAccount(accountName, accountType, domainId, networkDomain, accountDetails, accountUUID, state, initialName, created);
    }

    public void update(Object object, JSONObject jsonObject, Date modified)
    {
        AccountVO account = (AccountVO)object;

        /*Map<String, String> details = null;
        try
        {
            details = (Map<String, String>)jsonObject.get("details");
        }
        catch(Exception ex)
        {
            details = null;
        }*/

        String newAccountName = BaseService.getAttrValue(jsonObject, "name");
        String newNetworkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
        String initialName = BaseService.getAttrValue(jsonObject, "initialname");
        String stateStr = BaseService.getAttrValue(jsonObject, "state");
        Account.State state = findState(stateStr);
        Map<String, String> accountDetails = null;

        accountManager.updateAccount(account, newAccountName, newNetworkDomain, accountDetails, state, initialName, modified);
        s_logger.info("Successfully updated an account[" + account.getAccountName() + "]");
    }

    public void lock(Object object, Date modified)
    {
        AccountVO account = (AccountVO)object;
        accountManager.lockAccount(account.getAccountName(), account.getDomainId(), account.getId(), modified);
        s_logger.info("Successfully locked an account[" + account.getAccountName() + "]");
    }

    public void disable(Object object, Date modified) throws Exception
    {
        AccountVO account = (AccountVO)object;
        accountManager.disableAccount(account.getId(), modified);
        s_logger.info("Successfully disabled an account[" + account.getAccountName() + "]");
    }

    public void enable(Object object, Date modified)
    {
        AccountVO account = (AccountVO)object;
        accountManager.enableAccount(account.getId(), modified);
        s_logger.info("Successfully enabled an account[" + account.getAccountName() + "]");
    }

    public void remove(Object object, Date removed)
    {
        long callerUserId = 0;
        Account caller = null;
        AccountVO account = (AccountVO)object;
        accountManager.deleteAccount(account, callerUserId, caller, removed);
        s_logger.info("Successfully removed an account[" + account.getAccountName() + "]");
    }
}
