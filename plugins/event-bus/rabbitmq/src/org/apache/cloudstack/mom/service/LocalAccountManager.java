package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class LocalAccountManager extends FullScanner {

    private static final Logger s_logger = Logger.getLogger(LocalAccountManager.class);

    protected AccountDao accountDao;
    protected DomainDao domainDao;
    private AccountManager accountManager;

    public LocalAccountManager()
    {
        this.accountDao = ComponentContext.getComponent(AccountDao.class);
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

    // from com.cloud.user.AccountManagerImpl
    /*private boolean doDisableAccount(long accountId) throws ConcurrentOperationException, ResourceUnavailableException
    {
        List<VMInstanceVO> vms = vmDao.listByAccountId(accountId);
        boolean success = true;
        for (VMInstanceVO vm : vms) {
            try {
                try {
                    if (vm.getType() == VirtualMachine.Type.User) {
                        itMgr.advanceStop(vm.getUuid(), false);
                    } else if (vm.getType() == VirtualMachine.Type.DomainRouter) {
                        itMgr.advanceStop(vm.getUuid(), false);
                    } else {
                        itMgr.advanceStop(vm.getUuid(), false);
                    }
                } catch (OperationTimedoutException ote) {
                    s_logger.warn("Operation for stopping vm timed out, unable to stop vm " + vm.getHostName(), ote);
                    success = false;
                }
            } catch (AgentUnavailableException aue) {
                s_logger.warn("Agent running on host " + vm.getHostId() + " is unavailable, unable to stop vm " + vm.getHostName(), aue);
                success = false;
            }
        }

        return success;
    }*/

    public Object create(JSONObject jsonObject, final Date created)
    {
        // find domain id
        String domainPath = BaseService.getAttrValue(jsonObject, "path");
        DomainVO domain = domainDao.findDomainByPath(domainPath);
        if (domain == null)
        {
            s_logger.error("Failed to create a account because its domain[" + domainPath + "] cannot be found");
            return null;
        }

        // find account details
        /*Map<String, String> details = null;
        try
        {
            details = (Map<String, String>)jsonObject.get("details");
        }
        catch(Exception ex)
        {
            details = null;
        }*/

        Long domainId = domain.getId();
        String accountName = BaseService.getAttrValue(jsonObject, "name");
        String stateStr = BaseService.getAttrValue(jsonObject, "state");
        Account.State state = findState(stateStr);
        String networkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
        short accountType = Short.parseShort(BaseService.getAttrValue(jsonObject, "accounttype"));
        Map<String, String> accountDetails = null;
        String accountUUID = UUID.randomUUID().toString();

        return accountManager.createAccount(accountName, accountType, domainId, networkDomain, accountDetails, accountUUID, created, state);
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
        String stateStr = BaseService.getAttrValue(jsonObject, "state");
        Account.State state = findState(stateStr);
        Map<String, String> accountDetails = null;

        accountManager.updateAccount(account, newAccountName, newNetworkDomain, accountDetails, modified, state);
        s_logger.info("Successfully updated an account[" + account.getAccountName() + "]");
    }

    public void lock(Object object, Date modified)
    {
        AccountVO account = (AccountVO)object;
        account.setState(Account.State.locked);
        account.setModified(modified);
        accountDao.update(account.getId(), account);
        s_logger.info("Successfully locked an account[" + account.getAccountName() + "]");
    }

    public void disable(Object object, Date modified)
    {
        AccountVO account = (AccountVO)object;
        //account.setState(Account.State.disabled);
        //account.setModified(modified);
        //accountDao.update(account.getId(), account);
        try
        {
            //doDisableAccount(account.getId());
            accountManager.disableAccount(account.getId(), modified);
            s_logger.info("Successfully disabled an account[" + account.getAccountName() + "]");
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to disable vms", ex);
        }
    }

    public void enable(Object object, Date modified)
    {
        AccountVO account = (AccountVO)object;
        account.setState(Account.State.enabled);
        account.setModified(modified);
        account.setNeedsCleanup(false);
        accountDao.update(account.getId(), account);
        s_logger.info("Successfully enabled an account[" + account.getAccountName() + "]");
    }

    public void remove(Object object, Date removed)
    {
        AccountVO account = (AccountVO)object;
        //account.setRemoved(removed);

        long callerUserId = 0;
        Account caller = null;
        accountManager.deleteAccount(account, callerUserId, caller, removed);
        s_logger.info("Successfully removed an account[" + account.getAccountName() + "]");
    }
}
