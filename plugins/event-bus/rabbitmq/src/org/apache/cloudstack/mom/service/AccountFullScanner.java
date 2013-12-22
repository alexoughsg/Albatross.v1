package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.configuration.ResourceLimit;
import com.cloud.configuration.dao.ResourceCountDao;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.security.SecurityGroupManager;
import com.cloud.user.Account;
import com.cloud.user.AccountDetailsDao;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AccountFullScanner extends FullScanner {

    private static final Logger s_logger = Logger.getLogger(AccountFullScanner.class);

    protected AccountDao accountDao;
    protected DomainDao domainDao;
    private ResourceCountDao resourceCountDao;
    private AccountDetailsDao accountDetailsDao;
    private SecurityGroupManager networkGroupMgr;
    private VMInstanceDao vmDao;
    private VirtualMachineManager itMgr;
    private AccountManager accountManager;

    public AccountFullScanner()
    {
        this.accountDao = ComponentContext.getComponent(AccountDao.class);
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.resourceCountDao = ComponentContext.getComponent(ResourceCountDao.class);
        this.accountDetailsDao = ComponentContext.getComponent(AccountDetailsDao.class);
        this.networkGroupMgr = ComponentContext.getComponent(SecurityGroupManager.class);
        this.vmDao = ComponentContext.getComponent(VMInstanceDao.class);
        this.itMgr = ComponentContext.getComponent(VirtualMachineManager.class);
        this.accountManager = ComponentContext.getComponent(AccountManager.class);
    }

    @Override
    public List<AccountVO> findLocalList()
    {
        return accountDao.listAll();
    }

    // from com.cloud.user.AccountManagerImpl
    private boolean doDisableAccount(long accountId) throws ConcurrentOperationException, ResourceUnavailableException
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
    }

    @Override
    public JSONArray findRemoteList(String[] remoteServerInfo)
    {
        String hostName = remoteServerInfo[0];
        String userName = remoteServerInfo[1];
        String password = remoteServerInfo[2];

        try
        {
            AccountService accountService = new AccountService(hostName, userName, password);
            JSONArray accountArray = accountService.list(null);
            return accountArray;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find account list in hostName[ + " + hostName + "]", ex);
            return new JSONArray();
        }
    }

    @Override
    protected AccountVO find(JSONObject jsonObject, List localList)
    {
        for (Object object : localList)
        {
            AccountVO account = (AccountVO)object;
            DomainVO domain = domainDao.findById(account.getDomainId());

            if (!account.getAccountName().equals(getAttrValueInJson(jsonObject, "name")))    continue;
            if (!domain.getPath().equals(getAttrValueInJson(jsonObject, "path")))    continue;

            return account;
        }

        return null;
    }

    @Override
    protected boolean compare(Object object, JSONObject jsonObject)
    {
        AccountVO account = (AccountVO)object;
        boolean matched = account.getState().equals(getAttrValueInJson(jsonObject, "state"));
        return matched;
    }

    @Override
    protected Object create(JSONObject jsonObject, final Date created)
    {
        // find domain id
        String domainPath = getAttrValueInJson(jsonObject, "path");
        DomainVO domain = domainDao.findDomainByPath(domainPath);
        if (domain == null)
        {
            s_logger.error("Failed to create a account because its domain[" + domainPath + "] cannot be found");
            return null;
        }

        // find account details
        Map<String, String> details = null;
        try
        {
            details = (Map<String, String>)jsonObject.get("details");
        }
        catch(Exception ex)
        {
            details = null;
        }

        final Long domainId = domain.getId();
        final String accountName = getAttrValueInJson(jsonObject, "name");
        final String networkDomain = getAttrValueInJson(jsonObject, "networkdomain");
        final short accountType = Short.parseShort(getAttrValueInJson(jsonObject, "accounttype"));
        final Map<String, String> accountDetails = details;
        final String accountUUID = UUID.randomUUID().toString();

        return Transaction.execute(new TransactionCallback<AccountVO>()
        {
            @Override
            public AccountVO doInTransaction(TransactionStatus status)
            {
                AccountVO account = accountDao.persist(new AccountVO(accountName, domainId, networkDomain, accountType, accountUUID, created));
                if (account == null) {
                    s_logger.error("Failed to create account name " + accountName + " in domain id=" + domainId);
                    return null;
                }

                Long accountId = account.getId();

                if (accountDetails != null) {
                    accountDetailsDao.persist(accountId, accountDetails);
                }

                // Create resource count records for the account
                resourceCountDao.createResourceCounts(accountId, ResourceLimit.ResourceOwnerType.Account);

                // Create default security group
                networkGroupMgr.createDefaultSecurityGroup(accountId);

                return account;
            }
        });
    }

    @Override
    protected void update(Object object, JSONObject jsonObject, Date modified)
    {
        AccountVO account = (AccountVO)object;

        Map<String, String> details = null;
        try
        {
            details = (Map<String, String>)jsonObject.get("details");
        }
        catch(Exception ex)
        {
            details = null;
        }

        account.setModified(modified);

        String newAccountName = getAttrValueInJson(jsonObject, "name");
        String newNetworkDomain = getAttrValueInJson(jsonObject, "networkdomain");
        final Map<String, String> accountDetails = details;

        /*Transaction.execute(new TransactionCallbackNoReturn()
        {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status)
            {
                boolean success = accountDao.update(acctForUpdate.getId(), acctForUpdate);

                if (accountDetails != null && success)
                {
                    accountDetailsDao.update(acctForUpdate.getId(), accountDetails);
                }
            }
        });*/

        accountManager.updateAccount(account, newAccountName, newNetworkDomain, details);
    }

    @Override
    protected void lock(Object object, Date modified)
    {
        AccountVO account = (AccountVO)object;
        account.setState(Account.State.locked);
        account.setModified(modified);
        accountDao.update(account.getId(), account);
    }

    @Override
    protected void disable(Object object, Date modified)
    {
        AccountVO account = (AccountVO)object;
        account.setState(Account.State.disabled);
        account.setModified(modified);
        accountDao.update(account.getId(), account);
        try
        {
            doDisableAccount(account.getId());
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to disable vms", ex);
        }
    }

    @Override
    protected void enable(Object object, Date modified)
    {
        AccountVO account = (AccountVO)object;
        account.setState(Account.State.enabled);
        account.setModified(modified);
        account.setNeedsCleanup(false);
        accountDao.update(account.getId(), account);
    }

    @Override
    protected void remove(Object object, Date removed)
    {
        AccountVO account = (AccountVO)object;
        account.setRemoved(removed);

        long callerUserId = 0;
        Account caller = null;
        accountManager.deleteAccount(account, callerUserId, caller);
    }

    @Override
    protected Date isRemoteCreated(JSONObject remoteObject)
    {
        Date created = super.isRemoteCreated(remoteObject);
        if (created == null)    return created;

        String accountName = getAttrValueInJson(remoteObject, "name");
        List<AccountVO> accounts = accountDao.listAllIncludingRemoved();
        for(AccountVO account : accounts)
        {
            if (account.getAccountName().equals(accountName) && account.getRemoved().after(created))
            {
                return null;
            }
        }

        return created;
    }

    @Override
    protected void syncUpdate(Object object, JSONObject jsonObject)
    {
        if (compare(object, jsonObject))    return;

        AccountVO account = (AccountVO)object;
        Date localTimestamp = account.getModified();
        Date remoteTimestamp = getDate(jsonObject, "modified");
        if (localTimestamp == null || remoteTimestamp == null)  return;
        if (localTimestamp.after(remoteTimestamp))  return;

        // update local account with remote account's modified timestamp
        update(object, jsonObject, remoteTimestamp);
    }

    @Override
    protected Date isRemoteRemoved(Object object, String hostName, String userName, String password)
    {
        AccountVO account = (AccountVO)object;
        DomainVO domain = domainDao.findById(account.getId());
        AccountService accountService = new AccountService(hostName, userName, password);
        //TimeZone GMT_TIMEZONE = TimeZone.getTimeZone("GMT");
        //Date removed = domainService.isRemoved("alex_test2", "ROOT", DateUtil.parseDateString(GMT_TIMEZONE, "2013-12-18 19:44:48"));
        Date removed = accountService.isRemoved(account.getAccountName(), domain.getPath(), account.getCreated());
        return removed;
    }
}
