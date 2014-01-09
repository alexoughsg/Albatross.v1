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
import org.apache.log4j.Logger;

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
            JSONArray userArray = userService.list();
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

            if (!user.getUsername().equals(getAttrValueInJson(jsonObject, "username")))    continue;
            if (!account.getAccountName().equals(getAttrValueInJson(jsonObject, "account")))    continue;
            if (!domain.getPath().equals(getAttrValueInJson(jsonObject, "path")))    continue;

            return user;
        }

        return null;
    }

    @Override
    protected boolean compare(Object object, JSONObject jsonObject)
    {
        UserVO user = (UserVO)object;
        boolean matched = user.getState().equals(getAttrValueInJson(jsonObject, "state"));
        return matched;
    }

    @Override
    protected Object create(JSONObject jsonObject, Date created)
    {
        try
        {
            AccountVO account = null;
            String domainPath = getAttrValueInJson(jsonObject, "path");
            String accountName = getAttrValueInJson(jsonObject, "account");

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
            String userName = getAttrValueInJson(jsonObject, "username");
            //String password = getAttrValueInJson(jsonObject, "passowrd");
            String password = TEMP_PASSWORD;
            String firstName = getAttrValueInJson(jsonObject, "firstname");
            String lastName = getAttrValueInJson(jsonObject, "lastname");
            String email = getAttrValueInJson(jsonObject, "email");
            String timezone = getAttrValueInJson(jsonObject, "timezone");
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
        user.setApiKey(getAttrValueInJson(jsonObject, "apikey"));
        user.setFirstname(getAttrValueInJson(jsonObject, "firstname"));
        user.setEmail(getAttrValueInJson(jsonObject, "email"));
        user.setLastname(getAttrValueInJson(jsonObject, "lastname"));
        //user.setPassword("");
        user.setSecretKey(getAttrValueInJson(jsonObject, "secretkey"));
        user.setTimezone(getAttrValueInJson(jsonObject, "timezone"));
        user.setUsername(getAttrValueInJson(jsonObject, "username"));
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
    protected Date isRemoteCreated(JSONObject remoteObject)
    {
        String userName = getAttrValueInJson(remoteObject, "username");

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
    protected Date isRemoteRemoved(Object object, String hostName, String userName, String password)
    {
        UserVO user = (UserVO)object;
        AccountVO account = accountDao.findById(user.getAccountId());
        DomainVO domain = domainDao.findById(account.getDomainId());
        UserService userService = new UserService(hostName, userName, password);
        Date removed = userService.isRemoved(user.getUsername(), account.getAccountName(), domain.getPath(), user.getCreated());
        if (removed == null)
        {
            s_logger.info("User[" + user.getUsername() + "]  : remove is skipped because remote does not have removal history or remote removal is before local creation.");
        }
        return removed;
    }
}
