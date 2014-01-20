package org.apache.cloudstack.mom.service;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.UUID;

public class LocalUserManager {

    private static final String TEMP_PASSWORD = "temppassword";

    private static final Logger s_logger = Logger.getLogger(LocalUserManager.class);

    protected AccountDao accountDao;
    protected DomainDao domainDao;
    private AccountManager accountManager;

    public LocalUserManager()
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

    public Object create(JSONObject jsonObject, Date created) throws Exception
    {
        String domainPath = BaseService.getAttrValue(jsonObject, "path");
        String accountName = BaseService.getAttrValue(jsonObject, "account");

        // find a domain using its path
        DomainVO domain = domainDao.findDomainByPath(domainPath);
        if (domain == null)
        {
            throw new Exception("Failed to create a user because its domain[" + domainPath + "] cannot be found");
        }

        // find account where this user should belong
        Account account = accountDao.findActiveAccount(accountName, domain.getId());
        if (account == null)
        {
            throw new Exception("Failed to create a user because its account[" + accountName + "] cannot be found");
        }

        String userName = BaseService.getAttrValue(jsonObject, "username");
        String password = TEMP_PASSWORD;
        String firstName = BaseService.getAttrValue(jsonObject, "firstname");
        String lastName = BaseService.getAttrValue(jsonObject, "lastname");
        String email = BaseService.getAttrValue(jsonObject, "email");
        String timezone = BaseService.getAttrValue(jsonObject, "timezone");
        String stateStr = BaseService.getAttrValue(jsonObject, "state");
        Account.State state = findState(stateStr);
        String initialName = BaseService.getAttrValue(jsonObject, "initialname");
        String userUUID = UUID.randomUUID().toString();

        User user = accountManager.createUser(userName, password, firstName, lastName, email, timezone, accountName, domain.getId(), userUUID, initialName, state, created);
        s_logger.info("Successfully created a user[" + userName + "]");
        return user;
    }

    public void update(Object object, JSONObject jsonObject, Date modified)
    {
        UserVO user = (UserVO)object;

        String apiKey = BaseService.getAttrValue(jsonObject, "apikey");
        String firstName = BaseService.getAttrValue(jsonObject, "firstname");
        String email = BaseService.getAttrValue(jsonObject, "email");
        String lastName = BaseService.getAttrValue(jsonObject, "lastname");
        String password = null;
        String secretKey = BaseService.getAttrValue(jsonObject, "secretkey");
        String timezone = BaseService.getAttrValue(jsonObject, "timezone");
        String userName = BaseService.getAttrValue(jsonObject, "username");
        String initialName = BaseService.getAttrValue(jsonObject, "initialname");
        String stateStr = BaseService.getAttrValue(jsonObject, "state");
        Account.State state = findState(stateStr);

        accountManager.updateUser(user.getId(), userName, firstName, lastName, password, email, apiKey, secretKey, timezone, state, initialName, modified);
        s_logger.info("Successfully updated a user[" + user.getUsername() + "]");
    }

    public void lock(Object object, Date modified)
    {
        UserVO user = (UserVO)object;
        accountManager.lockUser(user.getId(), modified);
        s_logger.info("Successfully locked a user[" + user.getUsername() + "]");
    }

    public void disable(Object object, Date modified)
    {
        UserVO user = (UserVO)object;
        accountManager.disableUser(user.getId(), modified);
        s_logger.info("Successfully disabled a user[" + user.getUsername() + "]");
    }

    public void enable(Object object, Date modified)
    {
        UserVO user = (UserVO)object;
        accountManager.enableUser(user.getId(), modified);
        s_logger.info("Successfully enabled a user[" + user.getUsername() + "]");
    }

    public void remove(Object object, Date removed)
    {
        UserVO user = (UserVO)object;
        accountManager.deleteUser(user.getId(), removed);
        s_logger.info("Successfully removed a user[" + user.getUsername() + "]");
    }
}