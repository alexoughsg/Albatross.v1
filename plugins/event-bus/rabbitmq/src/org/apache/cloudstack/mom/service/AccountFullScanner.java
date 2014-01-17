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
import org.apache.cloudstack.mom.api_interface.BaseInterface;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;

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

            if (!account.getAccountName().equals(BaseService.getAttrValue(jsonObject, "name")))    continue;
            if (!domain.getPath().equals(BaseService.getAttrValue(jsonObject, "path")))    continue;

            return account;
        }

        return null;
    }

    @Override
    protected boolean compare(Object object, JSONObject jsonObject)
    {
        AccountVO account = (AccountVO)object;
        boolean matched = account.getState().toString().equals(BaseService.getAttrValue(jsonObject, "state"));
        return matched;
    }

    @Override
    protected Object create(JSONObject jsonObject, final Date created)
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
        final String accountName = BaseService.getAttrValue(jsonObject, "name");
        final String networkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
        final short accountType = Short.parseShort(BaseService.getAttrValue(jsonObject, "accounttype"));
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

                s_logger.info("Successfully created an account[" + account.getAccountName() + "]");
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

        //account.setModified(modified);

        String newAccountName = BaseService.getAttrValue(jsonObject, "name");
        String newNetworkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
        final Map<String, String> accountDetails = details;

        accountManager.updateAccount(account, newAccountName, newNetworkDomain, details, modified, null);
        s_logger.info("Successfully updated an account[" + account.getAccountName() + "]");
    }

    @Override
    protected void lock(Object object, Date modified)
    {
        AccountVO account = (AccountVO)object;
        account.setState(Account.State.locked);
        account.setModified(modified);
        accountDao.update(account.getId(), account);
        s_logger.info("Successfully locked an account[" + account.getAccountName() + "]");
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
            s_logger.info("Successfully disabled an account[" + account.getAccountName() + "]");
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
        s_logger.info("Successfully enabled an account[" + account.getAccountName() + "]");
    }

    @Override
    protected void remove(Object object, Date removed)
    {
        AccountVO account = (AccountVO)object;
        account.setRemoved(removed);

        long callerUserId = 0;
        Account caller = null;
        accountManager.deleteAccount(account, callerUserId, caller);
        s_logger.info("Successfully removed an account[" + account.getAccountName() + "]");
    }

    protected JSONArray listEvents(String hostName, String userName, String password, Date created)
    {
        try
        {
            AccountService accountService = new AccountService(hostName, userName, password);
            return accountService.listEvents("ACCOUNT.DELETE", "completed", created, null);
        }
        catch(Exception ex)
        {
            s_logger.error(ex.getStackTrace());
            return null;
        }
    }

    @Override
    protected Date isRemoteCreated(JSONObject remoteObject)
    {
        String accountName = BaseService.getAttrValue(remoteObject, "name");

        Date remoteCreated = super.isRemoteCreated(remoteObject);
        if (remoteCreated == null)
        {
            s_logger.info("Account[" + accountName + "] : create is skipped because created time of remote is null.");
            return null;
        }

        List<AccountVO> accounts = accountDao.listAllIncludingRemoved();
        for(AccountVO account : accounts)
        {
            Date localRemoved = account.getRemoved();
            if (account.getAccountName().equals(accountName) && localRemoved != null && localRemoved.after(remoteCreated))
            {
                s_logger.info("Account[" + accountName + "] : create is skipped because created time of remote[" + remoteCreated + "] is before removed time of local[" + localRemoved + "]");
                return null;
            }
        }

        return remoteCreated;
    }

    @Override
    protected void syncUpdate(Object object, JSONObject jsonObject)
    {
        AccountVO account = (AccountVO)object;

        if (compare(object, jsonObject))
        {
            s_logger.info("Account[" + account.getAccountName() + "] : update is skipped because local & remote are same.");
            return;
        }

        Date localTimestamp = account.getModified();
        Date remoteTimestamp = getDate(jsonObject, "modified");
        if (localTimestamp == null || remoteTimestamp == null)
        {
            s_logger.info("Account[" + account.getAccountName() + "] : update is skipped because modified times of local[" + localTimestamp + "] and/or remote[" + remoteTimestamp + "] is/are null.");
            return;
        }
        if (localTimestamp.after(remoteTimestamp))
        {
            s_logger.info("Account[" + account.getAccountName() + "] : update is skipped because modified time of local[" + localTimestamp + "] is after remote[" + remoteTimestamp + "].");
            return;
        }

        // update local account with remote account's modified timestamp
        update(object, jsonObject, remoteTimestamp);
    }

    @Override
    protected boolean exist(Object object, ArrayList<Object> processedList)
    {
        AccountVO account = (AccountVO)object;
        for(Object next : processedList)
        {
            AccountVO nextAccount = (AccountVO)next;
            if (account.getDomainId() != nextAccount.getDomainId()) continue;
            if (!account.getAccountName().equals(nextAccount.getAccountName()))  continue;
            return true;
        }

        return false;
    }

    @Override
    protected Date isRemoteRemovedOrRenamed(Object object, JSONArray eventList)
    {
        AccountVO account = (AccountVO)object;
        DomainVO domain = domainDao.findById(account.getDomainId());

        Date created = account.getCreated();
        if (created == null)
        {
            s_logger.info("Account[" + account.getAccountName() + "]  : remove is skipped because local create time is null.");
            return null;
        }

        Date eventDate = isRemovedOrRenamed(account.getAccountName(), domain.getPath(), eventList);
        if (eventDate == null)
        {
            return null;
        }

        if (eventDate.before(created))
        {
            s_logger.info("Account[" + account.getAccountName() + "]  : remove is skipped because remote remove/rename event time is before local create time.");
            return null;
        }

        return eventDate;
    }

    private Date isRemovedOrRenamed(String accountName, String domainPath, JSONArray eventList)
    {
        for (int idx = 0; idx < eventList.length(); idx++)
        {
            try
            {
                JSONObject jsonObject = BaseService.parseEventDescription(eventList.getJSONObject(idx));
                String eventAccountName = BaseService.getAttrValue(jsonObject, "Account Name");
                String eventDomainPath = BaseService.getAttrValue(jsonObject, "Domain Path");
                String eventOldAccountName = BaseService.getAttrValue(jsonObject, "Old Entity Name");
                String eventNewAccountName = BaseService.getAttrValue(jsonObject, "New Entity Name");

                if (eventOldAccountName == null)
                {
                    if (eventAccountName == null)  continue;
                    if (!eventAccountName.equals(accountName))    continue;
                }
                else
                {
                    if (eventNewAccountName == null)    continue;
                    if (eventNewAccountName.equals(eventOldAccountName))    continue;
                    if (!eventOldAccountName.equals(accountName))    continue;
                }

                if (eventDomainPath == null)    continue;
                if (!eventDomainPath.equals(domainPath))    continue;

                if (!BaseInterface.hasAttribute(jsonObject, "created"))
                {
                    s_logger.info("Account[" + accountName + "]  : remove is skipped because remove/rename event created time is not available.");
                    return null;
                }

                return BaseService.parseDateStr(BaseService.getAttrValue(jsonObject, "created"));
            }
            catch(Exception ex)
            {
                s_logger.error(ex.getStackTrace());
                return null;
            }
        }

        s_logger.info("Account[" + accountName + "]  : remove is skipped because remove/rename history can't be found.");
        return null;
    }
}
