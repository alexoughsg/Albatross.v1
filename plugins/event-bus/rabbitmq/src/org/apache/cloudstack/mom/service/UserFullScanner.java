package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.mom.api_interface.BaseInterface;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class UserFullScanner extends FullScanner {

    private static final String TEMP_PASSWORD = "temppassword";

    private static final Logger s_logger = Logger.getLogger(UserFullScanner.class);

    protected UserDao userDao;
    protected AccountDao accountDao;
    protected DomainDao domainDao;

    public UserFullScanner()
    {
        this.userDao = ComponentContext.getComponent(UserDao.class);
        this.accountDao = ComponentContext.getComponent(AccountDao.class);
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
    }

    @Override
    public List<UserVO> findLocalList()
    {
        return userDao.listAll();
    }

    @Override
    public JSONArray findRemoteList(String[] remoteServerInfo)
    {
        String hostName = remoteServerInfo[0];
        String userName = remoteServerInfo[1];
        String password = remoteServerInfo[2];

        try
        {
            UserService userService = new UserService(hostName, userName, password);
            JSONArray userArray = userService.list(null, null);
            return userArray;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find user list in hostName[ + " + hostName + "]", ex);
            return new JSONArray();
        }
    }

    @Override
    protected UserVO find(JSONObject jsonObject, List localList)
    {
        for (Object object : localList)
        {
            UserVO user = (UserVO)object;
            AccountVO account = accountDao.findById(user.getAccountId());
            DomainVO domain = domainDao.findById(account.getDomainId());

            if (!user.getUsername().equals(BaseService.getAttrValue(jsonObject, "username")))    continue;
            if (!account.getAccountName().equals(BaseService.getAttrValue(jsonObject, "account")))    continue;
            if (!domain.getPath().equals(BaseService.getAttrValue(jsonObject, "path")))    continue;

            return user;
        }

        return null;
    }

    @Override
    protected boolean compare(Object object, JSONObject jsonObject)
    {
        UserVO user = (UserVO)object;
        boolean matched = user.getState().equals(BaseService.getAttrValue(jsonObject, "state"));
        return matched;
    }

    @Override
    protected Object create(JSONObject jsonObject, Date created)
    {
        try
        {
            AccountVO account = null;
            String domainPath = BaseService.getAttrValue(jsonObject, "path");
            String accountName = BaseService.getAttrValue(jsonObject, "account");

            // find a domain using its path
            DomainVO domain = domainDao.findDomainByPath(domainPath);
            if (domain == null)
            {
                s_logger.error("Failed to create a user because its domain[" + domainPath + "] cannot be found");
                return null;
            }

            // find account where this user should belong
            List<AccountVO> accountList = accountDao.findAccountsLike(accountName);
            for(AccountVO next : accountList)
            {
                if (next.getDomainId() == domain.getId())
                {
                    account = next;
                    break;
                }
            }
            if (account == null)
            {
                s_logger.error("Failed to create a user because its account[" + accountName + "] cannot be found");
                return null;
            }

            long accountId = account.getId();
            String userName = BaseService.getAttrValue(jsonObject, "username");
            //String password = getAttrValueInJson(jsonObject, "passowrd");
            String password = TEMP_PASSWORD;
            String firstName = BaseService.getAttrValue(jsonObject, "firstname");
            String lastName = BaseService.getAttrValue(jsonObject, "lastname");
            String email = BaseService.getAttrValue(jsonObject, "email");
            String timezone = BaseService.getAttrValue(jsonObject, "timezone");
            String userUUID = UUID.randomUUID().toString();
            UserVO user = new UserVO(accountId, userName, password, firstName, lastName, email, timezone, userUUID, created);
            userDao.persist(user);
            s_logger.info("Successfully created a user[" + userName + "]");
            return user;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to create a user", ex);
            return null;
        }
    }

    @Override
    protected void update(Object object, JSONObject jsonObject, Date modified)
    {
        UserVO user = (UserVO)object;
        user.setApiKey(BaseService.getAttrValue(jsonObject, "apikey"));
        user.setFirstname(BaseService.getAttrValue(jsonObject, "firstname"));
        user.setEmail(BaseService.getAttrValue(jsonObject, "email"));
        user.setLastname(BaseService.getAttrValue(jsonObject, "lastname"));
        //user.setPassword("");
        user.setSecretKey(BaseService.getAttrValue(jsonObject, "secretkey"));
        user.setTimezone(BaseService.getAttrValue(jsonObject, "timezone"));
        user.setUsername(BaseService.getAttrValue(jsonObject, "username"));
        user.setModified(modified);
        userDao.update(user.getId(), user);
        s_logger.info("Successfully updated a user[" + user.getUsername() + "]");
    }

    @Override
    protected void lock(Object object, Date modified)
    {
        UserVO user = (UserVO)object;
        UserVO userForUpdate = userDao.createForUpdate();
        userForUpdate.setState(Account.State.locked);
        userForUpdate.setModified(modified);
        userDao.update(Long.valueOf(user.getId()), userForUpdate);
        s_logger.info("Successfully locked a user[" + user.getUsername() + "]");
    }

    @Override
    protected void disable(Object object, Date modified)
    {
        UserVO user = (UserVO)object;
        UserVO userForUpdate = userDao.createForUpdate();
        userForUpdate.setState(Account.State.disabled);
        userForUpdate.setModified(modified);
        userDao.update(Long.valueOf(user.getId()), userForUpdate);
        s_logger.info("Successfully disabled a user[" + user.getUsername() + "]");
    }

    @Override
    protected void enable(Object object, Date modified)
    {
        UserVO user = (UserVO)object;
        UserVO userForUpdate = userDao.createForUpdate();
        userForUpdate.setState(Account.State.enabled);
        userForUpdate.setModified(modified);
        userDao.update(Long.valueOf(user.getId()), userForUpdate);
        s_logger.info("Successfully enabled a user[" + user.getUsername() + "]");
    }

    @Override
    protected void remove(Object object, Date removed)
    {
        UserVO user = (UserVO)object;
        userDao.remove(user.getId(), removed);
        s_logger.info("Successfully removed a user[" + user.getUsername() + "]");
    }

    @Override
    protected JSONArray listEvents(String hostName, String userName, String password, Date created)
    {
        try
        {
            UserService userService = new UserService(hostName, userName, password);
            return userService.listEvents("USER.DELETE", "completed", created, null);
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
        String userName = BaseService.getAttrValue(remoteObject, "username");

        Date remoteCreated = super.isRemoteCreated(remoteObject);
        if (remoteCreated == null)
        {
            s_logger.info("User[" + userName + "] : create is skipped because created time of remote is null.");
            return null;
        }

        List<UserVO> users = userDao.listAll();
        for(UserVO user : users)
        {
            Date localRemoved = user.getRemoved();
            if (user.getUsername().equals(userName) && localRemoved != null && localRemoved.after(remoteCreated))
            {
                s_logger.info("User[" + userName + "] : create is skipped because created time of remote[" + remoteCreated + "] is before removed time of local[" + localRemoved + "]");
                return null;
            }
        }

        return remoteCreated;
    }

    @Override
    protected void syncUpdate(Object object, JSONObject jsonObject)
    {
        UserVO user = (UserVO)object;

        if (compare(object, jsonObject))
        {
            s_logger.info("User[" + user.getUsername() + "] : update is skipped because local & remote are same.");
            return;
        }

        Date localTimestamp = user.getModified();
        Date remoteTimestamp = getDate(jsonObject, "modified");
        if (localTimestamp == null || remoteTimestamp == null)
        {
            s_logger.info("User[" + ((UserVO)object).getUsername() + "] : update is skipped because modified times of local[" + localTimestamp + "] and/or remote[" + remoteTimestamp + "] is/are null.");
            return;
        }
        if (localTimestamp.after(remoteTimestamp))
        {
            s_logger.info("User[" + ((UserVO)object).getUsername() + "] : update is skipped because modified time of local[" + localTimestamp + "] is after remote[" + remoteTimestamp + "].");
            return;
        }

        // update local user with remote user's modified timestamp
        update(object, jsonObject, remoteTimestamp);
    }

    @Override
    protected boolean exist(Object object, ArrayList<Object> processedList)
    {
        UserVO user = (UserVO)object;
        for(Object next : processedList)
        {
            UserVO nextUser = (UserVO)next;
            if (user.getAccountId() != nextUser.getAccountId()) continue;
            if (!user.getUsername().equals(nextUser.getUsername()))  continue;
            return true;
        }

        return false;
    }

    @Override
    protected Date isRemoteRemovedOrRenamed(Object object, JSONArray eventList)
    {
        UserVO user = (UserVO)object;
        AccountVO account = accountDao.findById(user.getAccountId());
        DomainVO domain = domainDao.findById(account.getDomainId());

        Date created = user.getCreated();
        if (created == null)
        {
            s_logger.info("User[" + user.getUsername() + "]  : remove is skipped because local create time is null.");
            return null;
        }

        Date eventDate = isRemovedOrRenamed(user.getUsername(), account.getAccountName(), domain.getPath(), eventList);
        if (eventDate == null)
        {
            return null;
        }

        if (eventDate.before(created))
        {
            s_logger.info("User[" + user.getUsername() + "]  : remove is skipped because remote remove/rename event time is before local create time.");
            return null;
        }

        return eventDate;
    }

    private Date isRemovedOrRenamed(String userName, String accountName, String domainPath, JSONArray eventList)
    {
        for (int idx = 0; idx < eventList.length(); idx++)
        {
            try
            {
                JSONObject jsonObject = BaseService.parseEventDescription((JSONObject)eventList.get(idx));
                String eventUserName = (String)jsonObject.get("User Name");
                String eventAccountName = (String)jsonObject.get("Account Name");
                String eventDomainPath = BaseService.getAttrValue(jsonObject, "Domain Path");
                String eventOldUserName = BaseService.getAttrValue(jsonObject, "Old Entity Name");
                String eventNewUserName = BaseService.getAttrValue(jsonObject, "New Entity Name");

                if (eventOldUserName == null)
                {
                    if (eventUserName == null)  continue;
                    if (!eventUserName.equals(userName))    continue;
                }
                else
                {
                    if (eventNewUserName == null)    continue;
                    if (eventNewUserName.equals(eventOldUserName))    continue;
                    if (!eventOldUserName.equals(userName))    continue;
                }

                if (eventAccountName == null)  continue;
                if (!eventAccountName.equals(accountName))    continue;
                if (eventDomainPath == null)    continue;
                if (!eventDomainPath.equals(domainPath))    continue;

                if (!BaseInterface.hasAttribute(jsonObject, "created"))
                {
                    s_logger.info("User[" + userName + "]  : remove is skipped because remove/rename event created time is not available.");
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

        s_logger.info("User[" + userName + "]  : remove is skipped because remove/rename history can't be found.");
        return null;
    }
}
