package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AccountFullSyncProcessor extends FullSyncProcessor {

    private static final Logger s_logger = Logger.getLogger(AccountFullSyncProcessor.class);

    protected AccountDao accountDao;
    protected DomainDao domainDao;

    protected DomainVO localParent;
    protected List<AccountVO> localList;
    protected List<AccountVO> processedLocalList = new ArrayList<AccountVO>();

    private LocalAccountManager localAccountManager;
    private RemoteAccountEventProcessor eventProcessor;

    public AccountFullSyncProcessor(String hostName, String userName, String password, Long parentDomainId)
    {
        this.hostName = hostName;
        this.userName = userName;
        this.password = password;

        this.accountDao = ComponentContext.getComponent(AccountDao.class);
        this.domainDao = ComponentContext.getComponent(DomainDao.class);

        localParent = domainDao.findById(parentDomainId);
        localList = accountDao.findActiveAccountsForDomain(localParent.getId());
        /*List<Long> domainIds = new ArrayList<Long>();
        domainIds.add(localParent.getId());
        localList = accountDao.getAccountIdsForDomains(new List<Long>([localParent.getId()]);*/
        if (localParent.getName().equals("ROOT"))
        {
            for(int idx = localList.size()-1; idx >= 0; idx--)
            {
                AccountVO account = localList.get(idx);
                if (!account.getAccountName().equals("system"))   continue;
                localList.remove(account);
            }
        }

        DomainService domainService = new DomainService(hostName, userName, password);
        remoteParent = domainService.findDomain(localParent.getLevel(), localParent.getName(), localParent.getPath());
        String remoteParentAccountId = BaseService.getAttrValue(remoteParent, "id");
        AccountService accountService = new AccountService(hostName, userName, password);
        JSONArray remoteArray = accountService.list(remoteParentAccountId);
        remoteList = new ArrayList<JSONObject>();
        for(int idx = 0; idx < remoteArray.length(); idx++)
        {
            try
            {
                remoteList.add(remoteArray.getJSONObject(idx));
            }
            catch(Exception ex)
            {

            }
        }

        localAccountManager = new LocalAccountManager();
        eventProcessor = new RemoteAccountEventProcessor(hostName, userName, password);
    }

    private void syncAttributes(AccountVO account, JSONObject remoteJson) throws Exception
    {
        try
        {
            if (compare(account, remoteJson))
            {
                return;
            }

            Date localDate = account.getModified();
            Date remoteDate = getDate(remoteJson, "modified");
            if (localDate == null || remoteDate == null)
            {
                s_logger.info("Can't syncAttributes because null date, local modified[" + localDate + "], remote modified[" + remoteDate + "]");
                return;
            }
            if (localDate.equals(remoteDate))   return;
            if (localDate.after(remoteDate))   return;

            localAccountManager.update(account, remoteJson, remoteDate);
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to synchronize accounts : " + ex.getStackTrace());
        }
    }

    protected void expungeProcessedLocals()
    {
        for (AccountVO account : processedLocalList)
        {
            if (!localList.contains(account))    continue;
            localList.remove(account);
        }

        //processedLocalList.clear();
    }

    //@Override
    protected boolean compare(Object object, JSONObject jsonObject) throws Exception
    {
        AccountVO account = (AccountVO)object;

        try
        {
            String remoteName = BaseService.getAttrValue(jsonObject, "name");
            String remoteState = BaseService.getAttrValue(jsonObject, "state");
            String remoteNetworkDomain = BaseService.getAttrValue(jsonObject, "networkdomain");
            if (!account.getAccountName().equals(remoteName))   return false;
            if (!account.getState().toString().equals(remoteState)) return false;
            if (account.getNetworkDomain() == null && remoteNetworkDomain == null)   return true;
            if (account.getNetworkDomain() == null || remoteNetworkDomain == null)   return false;
            return (account.getNetworkDomain().equals(remoteNetworkDomain));
        }
        catch(Exception ex)
        {
            throw new Exception("Failed to compare accounts : " + ex.getStackTrace());
        }
    }

    //@Override
    protected AccountVO findLocal(JSONObject jsonObject)
    {
        String name = BaseService.getAttrValue(jsonObject, "name");

        for (AccountVO account : localList)
        {
            if (!account.getAccountName().equals(name))  continue;
            return account;
        }

        return null;
    }

    /*
        Find a local resource whose initial name is same with either the initial or resource name of the remote
        1. if the local initial name is null, return null
        2. if the remote initial name is null && the local initial name is equal to the remote resource name, return this local
        3. if the local initial name is equal to the remote initial name, return this local
        4. return null
     */
    //@Override
    protected AccountVO findLocalByInitialName(JSONObject jsonObject)
    {
        String remoteName = BaseService.getAttrValue(jsonObject, "name");
        String remoteInitialName = BaseService.getAttrValue(jsonObject, "initialname");

        for (AccountVO account : localList)
        {
            String localInitialName = account.getInitialName();
            if (localInitialName == null)   continue;

            if (!account.getAccountName().equals(remoteName))  continue;

            if (remoteInitialName == null && localInitialName.equals(remoteName))    return account;
            if (localInitialName.equals(remoteInitialName))    return account;
        }

        return null;
    }

    //@Override
    public JSONObject findRemote(Object object)
    {
        AccountVO account = (AccountVO)object;

        for (JSONObject jsonObject : remoteList)
        {
            String remoteName = BaseService.getAttrValue(jsonObject, "name");
            if (!account.getAccountName().equals(remoteName))  continue;
            return jsonObject;
        }

        return null;
    }

    /*
        Find a remote resource whose initial name is same with either the initial or resource name of the local
        1. if the remote initial name is null, return null
        2. if the local initial name is null && the remote initial name is equal to the local resource name, return this remote
        3. if the remote initial name is equal to the local initial name, return this remote
        4. return null
     */
    //@Override
    protected JSONObject findRemoteByInitialName(Object object)
    {
        AccountVO account = (AccountVO)object;
        String localInitialName = account.getInitialName();

        for (JSONObject jsonObject : remoteList)
        {
            String remoteName = BaseService.getAttrValue(jsonObject, "name");
            String remoteInitialName = BaseService.getAttrValue(jsonObject, "initialname");
            if (remoteInitialName == null)   continue;

            if (!account.getAccountName().equals(remoteName))  continue;

            if (localInitialName == null && remoteInitialName.equals(account.getAccountName()))    return jsonObject;
            if (remoteInitialName.equals(localInitialName))    return jsonObject;
        }

        return null;
    }

    //@Override
    protected boolean synchronize(AccountVO account) throws Exception
    {
        JSONObject remoteJson = findRemote(account);
        if (remoteJson == null) return false;

        // synchronize the attributes
        syncAttributes(account, remoteJson);

        processedLocalList.add(account);
        processedRemoteList.add(remoteJson);

        return true;
    }

    //@Override
    protected boolean synchronizeUsingEvent(AccountVO account) throws Exception
    {
        JSONObject eventJson = eventProcessor.findLatestRemoteRemoveEvent(account);
        if (eventJson == null)  return false;

        Date eventDate = getDate(eventJson, "created");
        Date created = account.getCreated();
        if (created == null)
        {
            s_logger.info("Can't synchronizeUsingEvent because account created is null");
            return false;
        }
        if (eventDate.before(created))  return false;

        // remove this local
        localAccountManager.remove(account, eventDate);

        processedLocalList.add(account);

        return true;
    }

    //@Override
    protected boolean synchronizeUsingInitialName(AccountVO account) throws Exception
    {
        JSONObject remoteJson = findRemoteByInitialName(account);
        if (remoteJson == null) return false;

        // synchronize the attributes
        syncAttributes(account, remoteJson);

        processedLocalList.add(account);
        processedRemoteList.add(remoteJson);

        return true;
    }

    @Override
    protected void synchronizeByLocal()
    {
        for(AccountVO account : localList)
        {
            try
            {
                boolean sync = synchronize(account);
                if (sync)
                {
                    s_logger.error("Account[" + account.getAccountName() + "] successfully synchronized");
                    continue;
                }
                s_logger.error("Account[" + account.getAccountName() + "] not synchronized");
            }
            catch(Exception ex)
            {
                s_logger.error("Account[" + account.getAccountName() + "] failed to synchronize : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();

        for(AccountVO account : localList)
        {
            try
            {
                boolean sync = synchronizeUsingEvent(account);
                if (sync)
                {
                    s_logger.error("Account[" + account.getAccountName() + "] successfully synchronized using events");

                    continue;
                }
                s_logger.error("Account[" + account.getAccountName() + "] not synchronized using events");
            }
            catch(Exception ex)
            {
                s_logger.error("Account[" + account.getAccountName() + "] failed to synchronize using events : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();

        for(AccountVO account : localList)
        {
            try
            {
                boolean sync = synchronizeUsingInitialName(account);
                if (sync)
                {
                    s_logger.error("Account[" + account.getAccountName() + "] successfully synchronized using initial names");
                    continue;
                }
                s_logger.error("Account[" + account.getAccountName() + "] not synchronized using initial names");
            }
            catch(Exception ex)
            {
                s_logger.error("Account[" + account.getAccountName() + "] failed to synchronize using initial names : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();
    }

    //@Override
    protected boolean synchronizeUsingRemoved(JSONObject remoteJson) throws Exception
    {
        String remotePath = BaseService.getAttrValue(remoteJson, "path");
        Date created = getDate(remoteJson, "created");
        if (created == null)
        {
            s_logger.info("Can't synchronizeUsingRemoved because remote created is null");
            return false;
        }

        AccountVO removedAccount = null;
        for (AccountVO account : accountDao.listAllIncludingRemoved())
        {
            Date removed = account.getRemoved();
            if (removed == null)    continue;

            if (account.getDomainId() != localParent.getId())  continue;

            if (removedAccount == null)
            {
                removedAccount = account;
            }
            else
            {
                Date currentCreated = account.getCreated();
                if (currentCreated == null)
                {
                    s_logger.info("Can't synchronizeUsingRemoved because one of the removed account has null created");
                    return false;
                }
                else if (currentCreated.after(removedAccount.getCreated()))
                {
                    removedAccount = account;
                }
            }
        }

        if (removedAccount == null)  return false;

        Date removed = removedAccount.getRemoved();
        if (created.after(removed))
        {
            // create this remote in the local region
            localAccountManager.create(remoteJson, created);
        }

        processedRemoteList.add(remoteJson);
        return true;
    }

    //@Override
    protected boolean synchronizeUsingInitialName(JSONObject remoteJson) throws Exception
    {
        AccountVO account = findLocalByInitialName(remoteJson);
        if (account == null)  return false;

        // synchronize the attributes
        syncAttributes(account, remoteJson);

        processedLocalList.add(account);
        processedRemoteList.add(remoteJson);

        return true;
    }

    @Override
    protected void synchronizeByRemote()
    {
        // not sure if this is necessary because this remote resources will be eventually created at 'createRemoteResourcesInLocal'
        /*for (JSONObject remoteJson : remoteList)
        {
            String name = BaseService.getAttrValue(remoteJson, "name");

            try
            {
                boolean sync = synchronizeUsingRemoved(remoteJson);
                if (sync)
                {
                    s_logger.error("AccountJSON[" + name + "] successfully synchronized using events");
                    continue;
                }
                s_logger.error("AccountJSON[" + name + "] not synchronized using events");
            }
            catch(Exception ex)
            {
                s_logger.error("AccountJSON[" + name + "] failed to synchronize using events : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();*/

        for (JSONObject remoteJson : remoteList)
        {
            String name = BaseService.getAttrValue(remoteJson, "name");

            try
            {
                boolean sync = synchronizeUsingInitialName(remoteJson);
                if (sync)
                {
                    s_logger.error("AccountJSON[" + name + "] successfully synchronized using initial names");
                    continue;
                }
                s_logger.error("AccountJSON[" + name + "] not synchronized using initial names");
            }
            catch(Exception ex)
            {
                s_logger.error("AccountJSON[" + name + "] failed to synchronize using initial names : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();
    }

    public List<AccountVO> getLocalProcessedList()
    {
        return processedLocalList;
    }

    @Override
    public void arrangeLocalResourcesToBeRemoved(FullSyncProcessor syncProcessor)
    {
        AccountFullSyncProcessor accountProcessor = (AccountFullSyncProcessor)syncProcessor;

        for(int idx = localList.size()-1; idx >= 0; idx--)
        {
            AccountVO account = localList.get(idx);
            for(AccountVO processed : accountProcessor.processedLocalList)
            {
                if (account.getId() != processed.getId())  continue;

                // move this account to the processed list
                processedLocalList.add(account);
                localList.remove(account);
                break;
            }
        }
    }

    @Override
    public void arrangeRemoteResourcesToBeCreated(FullSyncProcessor syncProcessor)
    {
        AccountFullSyncProcessor accountProcessor = (AccountFullSyncProcessor)syncProcessor;

        for(int idx = remoteList.size()-1; idx >= 0; idx--)
        {
            JSONObject remoteJson = remoteList.get(idx);
            String name = BaseService.getAttrValue(remoteJson, "name");

            for(JSONObject processed : accountProcessor.processedRemoteList)
            {
                String processedName = BaseService.getAttrValue(processed, "name");
                if (!name.equals(processedName))  continue;

                // move this account to the processed list
                processedRemoteList.add(remoteJson);
                remoteList.remove(remoteJson);
                break;
            }
        }
    }

    /*private AccountVO findFromList(List<AccountVO> list, String name)
    {
        for(AccountVO account : list)
        {
            if (account.getAccountName().equals(name))  return account;
        }
        return null;
    }*/

    @Override
    public void createRemoteResourcesInLocal()
    {
        for (JSONObject remoteJson : remoteList)
        {
            String accountName = BaseService.getAttrValue(remoteJson, "name");
            Account found = accountDao.findAccountIncludingRemoved(accountName, localParent.getId());
            if(found != null && found.getRemoved() == null)
            {
                s_logger.info("AccountJSON[" + accountName + "] already created in the local region");
                continue;
            }

            try
            {
                // create this remote in the local region
                Date created = getDate(remoteJson, "created");
                localAccountManager.create(remoteJson, created);
                s_logger.error("AccountJSON[" + accountName + "] successfully created in the local region");
            }
            catch(Exception ex)
            {
                s_logger.error("AccountJSON[" + accountName + "] failed to create in the local region : " + ex.getStackTrace());
            }
        }
    }

    @Override
    public void removeLocalResources()
    {
        for (AccountVO account : localList)
        {
            String accountName = account.getAccountName();
            Account found = accountDao.findAccountIncludingRemoved(accountName, localParent.getId());
            if(found == null || found.getRemoved() != null)
            {
                s_logger.info("Account[" + accountName + "] already removed from the local region");
                continue;
            }

            localAccountManager.remove(account, null);
        }
    }
}
