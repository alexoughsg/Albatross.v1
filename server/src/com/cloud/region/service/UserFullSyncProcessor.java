package com.cloud.region.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.rmap.RmapVO;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.region.RegionVO;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class UserFullSyncProcessor extends FullSyncProcessor {

    private static final Logger s_logger = Logger.getLogger(UserFullSyncProcessor.class);

    protected UserDao userDao;
    protected AccountDao accountDao;
    protected DomainDao domainDao;

    protected DomainVO localParentDomain;
    protected AccountVO localParent;
    protected List<UserVO> localList;
    protected List<UserVO> processedLocalList = new ArrayList<UserVO>();

    protected List<AccountVO> localAccountList;

    protected JSONObject remoteParentDomain;
    protected List<JSONObject> remoteAccountList;

    private LocalUserManager localUserManager;
    private RemoteUserEventProcessor eventProcessor;

    //public UserFullSyncProcessor(String hostName, String endPoint, String userName, String password, Long parentDomainId) throws Exception
    public UserFullSyncProcessor(RegionVO region, DomainVO parentDomain) throws Exception
    {
        super(region);

        this.userDao = ComponentContext.getComponent(UserDao.class);
        this.accountDao = ComponentContext.getComponent(AccountDao.class);
        this.domainDao = ComponentContext.getComponent(DomainDao.class);

        //localParentDomain = domainDao.findById(parentDomainId);
        localParentDomain = parentDomain;
        localAccountList = accountDao.findActiveAccountsForDomain(localParentDomain.getId());
        localList = new ArrayList<UserVO>();
        for (AccountVO account : localAccountList)
        {
            if (localParentDomain.getName().equals("ROOT") && account.getAccountName().equals("system"))   continue;
            localList.addAll(userDao.listByAccount(account.getId()));
        }

        String remoteParentDomainId = null;
        DomainService domainService = new DomainService(hostName, endPoint, userName, password);
        RmapVO rmap = rmapDao.findRmapBySource(localParentDomain.getUuid(), region.getId());
        if (rmap == null)
        {
            JSONObject domainJson = domainService.findDomain(localParentDomain.getLevel(), localParentDomain.getName(), localParentDomain.getPath());
            if (domainJson == null)
            {
                throw new Exception("The parent domain[" + localParentDomain.getPath() + "] cannot be found in the remote region.");
            }
            remoteParentDomainId = BaseService.getAttrValue(domainJson, "id");
        }
        else
        {
            remoteParentDomainId = rmap.getUuid();
        }
        AccountService accountService = new AccountService(hostName, endPoint, userName, password);
        JSONArray remoteAccounts = accountService.list(remoteParentDomainId);
        remoteAccountList = new ArrayList<JSONObject>();
        for(int idx = 0; idx < remoteAccounts.length(); idx++)
        {
            try
            {
                remoteAccountList.add(remoteAccounts.getJSONObject(idx));
            }
            catch(Exception ex)
            {

            }
        }
        UserService userService = new UserService(hostName, endPoint, userName, password);
        //JSONArray remoteArray = userService.list(remoteDomainId, localParent.getAccountName());
        JSONArray remoteArray = userService.list(remoteParentDomainId, null);
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

        localUserManager = new LocalUserManager();
        eventProcessor = new RemoteUserEventProcessor(hostName, endPoint, userName, password);
    }

    private AccountVO getAccount(UserVO user)
    {
        for (AccountVO account : localAccountList)
        {
            if (account.getId() == user.getAccountId()) return account;
        }
        return null;
    }

    private JSONObject getAccount(JSONObject userJson)
    {
        String accountName = BaseService.getAttrValue(userJson, "account");

        for (JSONObject accountJson : remoteAccountList)
        {
            String name = BaseService.getAttrValue(accountJson, "name");
            if (name.equals(accountName)) return accountJson;
        }
        return null;
    }

    private void syncAttributes(UserVO user, JSONObject remoteJson) throws Exception
    {
        try
        {
            if (compare(user, remoteJson))
            {
                return;
            }

            Date localDate = user.getModified();
            Date remoteDate = getDate(remoteJson, "modified");
            if (localDate == null || remoteDate == null)
            {
                s_logger.error("Can't syncAttributes because null date, local modified[" + localDate + "], remote modified[" + remoteDate + "]");
                return;
            }
            if (localDate.equals(remoteDate))   return;
            if (localDate.after(remoteDate))   return;

            localUserManager.update(user, remoteJson, remoteDate);
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to synchronize users : " + ex.getStackTrace());
        }
    }

    protected void expungeProcessedLocals()
    {
        for (UserVO user : processedLocalList)
        {
            if (!localList.contains(user))    continue;
            localList.remove(user);
        }

        //processedLocalList.clear();
    }

    //@Override
    protected boolean compare(Object object, JSONObject jsonObject) throws Exception
    {
        UserVO user = (UserVO)object;

        try
        {
            String remoteName = BaseService.getAttrValue(jsonObject, "username");
            String remoteState = BaseService.getAttrValue(jsonObject, "state");
            String remoteInitialName = BaseService.getAttrValue(jsonObject, "initialname");
            if (!user.getUsername().equals(remoteName))   return false;
            if (!user.getState().toString().equals(remoteState)) return false;
            if (!strCompare(user.getInitialName(), remoteInitialName)) return false;
            return true;
        }
        catch(Exception ex)
        {
            throw new Exception("Failed to compare users : " + ex.getStackTrace());
        }
    }

    //@Override
    protected UserVO findLocal(JSONObject jsonObject)
    {
        JSONObject accountJson = getAccount(jsonObject);
        String accountName = BaseService.getAttrValue(accountJson, "name");
        String userName = BaseService.getAttrValue(jsonObject, "username");
        String remoteUuid = BaseService.getAttrValue(jsonObject, "id");
        RmapVO rmap = rmapDao.findSource(remoteUuid, region.getId());

        for (UserVO user : localList)
        {
            AccountVO account = getAccount(user);
            if (rmap == null)
            {
                if (!account.getAccountName().equals(accountName))  continue;
                if (!user.getUsername().equals(userName))  continue;
            }
            else
            {
                if(!rmap.getUuid().equals(user.getUuid()))    continue;
            }

            if (rmap == null)
            {
                rmap = new RmapVO(user.getUuid(), region.getId(), remoteUuid);
                rmapDao.create(rmap);
            }

            return user;
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
    protected UserVO findLocalByInitialName(JSONObject jsonObject)
    {
        JSONObject accountJson = getAccount(jsonObject);
        String accountName = BaseService.getAttrValue(accountJson, "name");
        String remoteName = BaseService.getAttrValue(jsonObject, "username");
        String remoteInitialName = BaseService.getAttrValue(jsonObject, "initialname");

        for (UserVO user : localList)
        {
            String localInitialName = user.getInitialName();
            if (localInitialName == null)   continue;

            AccountVO account = getAccount(user);
            if (!account.getAccountName().equals(accountName))  continue;

            if (!user.getUsername().equals(remoteName))  continue;

            if (remoteInitialName == null && localInitialName.equals(remoteName))    return user;
            if (localInitialName.equals(remoteInitialName))    return user;
        }

        return null;
    }

    //@Override
    public JSONObject findRemote(Object object)
    {
        UserVO user = (UserVO)object;
        AccountVO account = getAccount(user);

        RmapVO rmap = rmapDao.findRmapBySource(user.getUuid(), region.getId());

        for (JSONObject jsonObject : remoteList)
        {
            JSONObject accountJson = getAccount(jsonObject);
            String accountName = BaseService.getAttrValue(accountJson, "name");
            String remoteName = BaseService.getAttrValue(jsonObject, "username");
            String remoteUuid = BaseService.getAttrValue(jsonObject, "id");

            if (rmap == null)
            {
                if (!account.getAccountName().equals(accountName))  continue;
                if (!user.getUsername().equals(remoteName))  continue;
            }
            else
            {
                if(!rmap.getUuid().equals(remoteUuid))    continue;
            }

            if (rmap == null)
            {
                rmap = new RmapVO(user.getUuid(), region.getId(), remoteUuid);
                rmapDao.create(rmap);
            }

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
        UserVO user = (UserVO)object;
        AccountVO account = getAccount(user);
        String localInitialName = user.getInitialName();

        for (JSONObject jsonObject : remoteList)
        {
            String remoteInitialName = BaseService.getAttrValue(jsonObject, "initialname");
            if (remoteInitialName == null)   continue;

            JSONObject accountJson = getAccount(jsonObject);
            String accountName = BaseService.getAttrValue(accountJson, "name");
            if (!account.getAccountName().equals(accountName))  continue;

            if (localInitialName == null && remoteInitialName.equals(user.getUsername()))    return jsonObject;
            if (remoteInitialName.equals(localInitialName))    return jsonObject;
        }

        return null;
    }

    //@Override
    protected boolean synchronize(UserVO user) throws Exception
    {
        JSONObject remoteJson = findRemote(user);
        if (remoteJson == null) return false;

        // synchronize the attributes
        syncAttributes(user, remoteJson);

        processedLocalList.add(user);
        processedRemoteList.add(remoteJson);

        return true;
    }

    //@Override
    protected boolean synchronizeUsingEvent(UserVO user) throws Exception
    {
        JSONObject eventJson = eventProcessor.findLatestRemoteRemoveEvent(user);
        if (eventJson == null)  return false;

        Date eventDate = getDate(eventJson, "created");
        Date created = user.getCreated();
        if (created == null)
        {
            s_logger.error("Can't synchronizeUsingEvent because user created is null");
            return false;
        }
        if (eventDate.before(created))  return false;

        // remove this local
        localUserManager.remove(user, eventDate);

        processedLocalList.add(user);

        return true;
    }

    //@Override
    protected boolean synchronizeUsingInitialName(UserVO user) throws Exception
    {
        JSONObject remoteJson = findRemoteByInitialName(user);
        if (remoteJson == null) return false;

        // synchronize the attributes
        syncAttributes(user, remoteJson);

        processedLocalList.add(user);
        processedRemoteList.add(remoteJson);

        return true;
    }

    @Override
    protected void synchronizeByLocal()
    {
        for(UserVO user : localList)
        {
            try
            {
                boolean sync = synchronize(user);
                if (sync)
                {
                    s_logger.info("User[" + user.getUsername() + "] successfully synchronized");
                    continue;
                }
                s_logger.info("User[" + user.getUsername() + "] not synchronized");
            }
            catch(Exception ex)
            {
                s_logger.error("User[" + user.getUsername() + "] failed to synchronize : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();

        for(UserVO user : localList)
        {
            try
            {
                boolean sync = synchronizeUsingEvent(user);
                if (sync)
                {
                    s_logger.info("User[" + user.getUsername() + "] successfully synchronized using events");

                    continue;
                }
                s_logger.info("User[" + user.getUsername() + "] not synchronized using events");
            }
            catch(Exception ex)
            {
                s_logger.error("User[" + user.getUsername() + "] failed to synchronize using events : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();

        for(UserVO user : localList)
        {
            try
            {
                boolean sync = synchronizeUsingInitialName(user);
                if (sync)
                {
                    s_logger.info("User[" + user.getUsername() + "] successfully synchronized using initial names");
                    continue;
                }
                s_logger.info("User[" + user.getUsername() + "] not synchronized using initial names");
            }
            catch(Exception ex)
            {
                s_logger.error("User[" + user.getUsername() + "] failed to synchronize using initial names : " + ex.getStackTrace());
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
            s_logger.error("Can't synchronizeUsingRemoved because remote created is null");
            return false;
        }

        UserVO removedUser = null;
        for (UserVO user : userDao.listAllIncludingRemoved())
        {
            Date removed = user.getRemoved();
            if (removed == null)    continue;

            AccountVO account = getAccount(user);
            //if (user.getAccountId() != localParent.getId())  continue;
            if (account.getDomainId() != localParentDomain.getId())  continue;

            if (removedUser == null)
            {
                removedUser = user;
            }
            else
            {
                Date currentCreated = user.getCreated();
                if (currentCreated == null)
                {
                    s_logger.error("Can't synchronizeUsingRemoved because one of the removed user has null created");
                    return false;
                }
                else if (currentCreated.after(removedUser.getCreated()))
                {
                    removedUser = user;
                }
            }
        }

        if (removedUser == null)  return false;

        Date removed = removedUser.getRemoved();
        if (created.after(removed))
        {
            // create this remote in the local region
            String remoteUuid = BaseService.getAttrValue(remoteJson, "id");
            localUserManager.create(remoteJson, created);
            RmapVO rmap = new RmapVO(removedUser.getUuid(), region.getId(), remoteUuid);
            rmapDao.create(rmap);
        }

        processedRemoteList.add(remoteJson);
        return true;
    }

    //@Override
    protected boolean synchronizeUsingInitialName(JSONObject remoteJson) throws Exception
    {
        UserVO user = findLocalByInitialName(remoteJson);
        if (user == null)  return false;

        // synchronize the attributes
        syncAttributes(user, remoteJson);

        processedLocalList.add(user);
        processedRemoteList.add(remoteJson);

        return true;
    }

    @Override
    protected void synchronizeByRemote()
    {
        // not sure if this is necessary because this remote resources will be eventually created at 'createRemoteResourcesInLocal'
        /*for (JSONObject remoteJson : remoteList)
        {
            String name = BaseService.getAttrValue(remoteJson, "username");

            try
            {
                boolean sync = synchronizeUsingRemoved(remoteJson);
                if (sync)
                {
                    s_logger.info("UserJSON[" + name + "] successfully synchronized using events");
                    continue;
                }
                s_logger.info("UserJSON[" + name + "] not synchronized using events");
            }
            catch(Exception ex)
            {
                s_logger.error("UserJSON[" + name + "] failed to synchronize using events : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();*/

        for (JSONObject remoteJson : remoteList)
        {
            String name = BaseService.getAttrValue(remoteJson, "username");

            try
            {
                boolean sync = synchronizeUsingInitialName(remoteJson);
                if (sync)
                {
                    s_logger.info("UserJSON[" + name + "] successfully synchronized using initial names");
                    continue;
                }
                s_logger.info("UserJSON[" + name + "] not synchronized using initial names");
            }
            catch(Exception ex)
            {
                s_logger.error("UserJSON[" + name + "] failed to synchronize using initial names : " + ex.getStackTrace());
            }
        }

        expungeProcessedLocals();
        expungeProcessedRemotes();
    }

    @Override
    public void arrangeLocalResourcesToBeRemoved(FullSyncProcessor syncProcessor)
    {
        UserFullSyncProcessor userProcessor = (UserFullSyncProcessor)syncProcessor;

        for(int idx = localList.size()-1; idx >= 0; idx--)
        {
            UserVO user = localList.get(idx);
            for(UserVO processed : userProcessor.processedLocalList)
            {
                if (user.getId() != processed.getId())  continue;

                // move this user to the processed list
                processedLocalList.add(user);
                localList.remove(user);
                break;
            }
        }
    }

    @Override
    public void arrangeRemoteResourcesToBeCreated(FullSyncProcessor syncProcessor)
    {
        UserFullSyncProcessor userProcessor = (UserFullSyncProcessor)syncProcessor;

        for(int idx = remoteList.size()-1; idx >= 0; idx--)
        {
            JSONObject remoteJson = remoteList.get(idx);
            String name = BaseService.getAttrValue(remoteJson, "name");

            for(JSONObject processed : userProcessor.processedRemoteList)
            {
                String processedName = BaseService.getAttrValue(processed, "name");
                if (!name.equals(processedName))  continue;

                // move this user to the processed list
                processedRemoteList.add(remoteJson);
                remoteList.remove(remoteJson);
                break;
            }
        }
    }

    @Override
    public void createRemoteResourcesInLocal()
    {
        for (JSONObject remoteJson : remoteList)
        {
            String userName = BaseService.getAttrValue(remoteJson, "username");
            UserVO found = null;
            for (UserVO next : userDao.findUsersByName(userName))
            {
                AccountVO account = getAccount(next);
                if (account == null)    continue;
                if (account.getDomainId() == localParentDomain.getId())
                {
                    found = next;
                    break;
                }
            }
            if(found != null)
            {
                s_logger.info("UserJSON[" + userName + "] already created in the local region");
                continue;
            }

            try
            {
                // create this remote in the local region
                Date created = getDate(remoteJson, "created");
                String remoteUuid = BaseService.getAttrValue(remoteJson, "id");
                UserVO user = (UserVO)localUserManager.create(remoteJson, created);
                RmapVO rmap = new RmapVO(user.getUuid(), region.getId(), remoteUuid);
                rmapDao.create(rmap);
                s_logger.info("UserJSON[" + userName + "] successfully created in the local region");
            }
            catch(Exception ex)
            {
                s_logger.error("UserJSON[" + userName + "] failed to create in the local region : " + ex.getStackTrace());
            }
        }
    }

    @Override
    public void removeLocalResources()
    {
        for (UserVO user : localList)
        {
            String userName = user.getUsername();
            UserVO found = null;
            for (UserVO next : userDao.findUsersByName(userName))
            {
                AccountVO account = getAccount(next);
                if (account == null)    continue;
                if (account.getDomainId() == localParentDomain.getId())
                {
                    found = next;
                    break;
                }
            }
            if(found == null)
            {
                s_logger.info("User[" + userName + "] already removed from the local region");
                continue;
            }

            localUserManager.remove(user, null);
        }
    }
}
