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

    private static final Logger s_logger = Logger.getLogger(UserFullScanner.class);

    private UserDao userDao;
    private AccountDao accountDao;
    private DomainDao domainDao;

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
    protected void create(JSONObject jsonObject, Date created)
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
                return;
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
                return;
            }

            long accountId = account.getId();
            String userName = getAttrValueInJson(jsonObject, "username");
            String password = null;
            String firstName = getAttrValueInJson(jsonObject, "firstname");
            String lastName = getAttrValueInJson(jsonObject, "lastname");
            String email = getAttrValueInJson(jsonObject, "email");
            String timezone = getAttrValueInJson(jsonObject, "timezone");
            String userUUID = UUID.randomUUID().toString();;
            userDao.persist(new UserVO(accountId, userName, password, firstName, lastName, email, timezone, userUUID, created));
            s_logger.error("Successfully created a user[" + userName + "]");
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to create a user", ex);
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
    }

    @Override
    protected void lock(Object object, Date modified)
    {
        UserVO user = (UserVO)object;
        UserVO userForUpdate = userDao.createForUpdate();
        userForUpdate.setState(Account.State.locked);
        userForUpdate.setModified(modified);
        userDao.update(Long.valueOf(user.getId()), userForUpdate);
    }

    @Override
    protected void disable(Object object, Date modified)
    {
        UserVO user = (UserVO)object;
        UserVO userForUpdate = userDao.createForUpdate();
        userForUpdate.setState(Account.State.disabled);
        userForUpdate.setModified(modified);
        userDao.update(Long.valueOf(user.getId()), userForUpdate);
    }

    @Override
    protected void enable(Object object, Date modified)
    {
        UserVO user = (UserVO)object;
        UserVO userForUpdate = userDao.createForUpdate();
        userForUpdate.setState(Account.State.enabled);
        userForUpdate.setModified(modified);
        userDao.update(Long.valueOf(user.getId()), userForUpdate);
    }

    @Override
    protected void remove(Object object, Date removed)
    {
        UserVO user = (UserVO)object;
        userDao.remove(user.getId(), removed);
    }

    @Override
    protected Date isRemoteCreated(JSONObject remoteObject)
    {
        Date created = super.isRemoteCreated(remoteObject);
        if (created == null)    return created;

        String userName = getAttrValueInJson(remoteObject, "username");
        List<UserVO> users = userDao.listAll();
        for(UserVO user : users)
        {
            if (user.getUsername().equals(userName) && user.getRemoved().after(created))
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

        UserVO user = (UserVO)object;
        Date localTimestamp = user.getModified();
        Date remoteTimestamp = getDate(jsonObject, "modified");
        if (localTimestamp == null || remoteTimestamp == null)  return;
        if (localTimestamp.after(remoteTimestamp))  return;

        // update local user with remote user's modified timestamp
        update(object, jsonObject, remoteTimestamp);
    }

    @Override
    protected Date isRemoteRemoved(Object object, String hostName, String userName, String password)
    {
        UserVO user = (UserVO)object;
        AccountVO account = accountDao.findById(user.getAccountId());
        DomainVO domain = domainDao.findById(account.getId());
        UserService userService = new UserService(hostName, userName, password);
        //TimeZone GMT_TIMEZONE = TimeZone.getTimeZone("GMT");
        //Date removed = domainService.isRemoved("alex_test2", "ROOT", DateUtil.parseDateString(GMT_TIMEZONE, "2013-12-18 19:44:48"));
        Date removed = userService.isRemoved(user.getUsername(), account.getAccountName(), domain.getPath(), user.getCreated());
        return removed;
    }
}
