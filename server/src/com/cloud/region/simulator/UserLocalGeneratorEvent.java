package com.cloud.region.simulator;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.region.service.UserService;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.region.RegionVO;
import org.apache.cloudstack.region.dao.RegionDao;
import org.apache.log4j.Logger;

import java.util.Date;

public class UserLocalGeneratorEvent extends LocalGenerator {

    private static final Logger s_logger = Logger.getLogger(UserLocalGeneratorEvent.class);

    private UserService userService;
    private DomainDao domainDao;

    public UserLocalGeneratorEvent()
    {
        this.domainDao = ComponentContext.getComponent(DomainDao.class);

        RegionDao regionDao = ComponentContext.getComponent(RegionDao.class);
        RegionVO region = regionDao.findByName("Local");
        this.userService = new UserService(region.getName(), region.getEndPoint(), region.getUserName(), region.getPassword());
    }

    public JSONObject create()
    {
        Date created = generateRandDate();
        JSONObject userJson = new JSONObject();

        // select a random user
        AccountVO account = randAccountSelect(false);
        if (!isUsable(account))
        {
            return null;
        }

        if (!account.getState().equals(Account.State.enabled))
        {
            s_logger.info("Account[" + account.getAccountName() + "] is not enabled, skip to create a user.");
            return null;
        }

        DomainVO domain = domainDao.findById(account.getDomainId());

        // create a random string for a new user
        String userName = "U" + generateRandString();
        String password = "PW" + generateRandString();
        String firstName = "FN" + generateRandString();
        String lastName = "LN" + generateRandString();
        String email = "EM" + generateRandString() + "@a.com";
        String timezone = randUserTimezoneSelect();

        try
        {
            userJson.put("path", domain.getPath());
            userJson.put("account", account.getAccountName());
            userJson.put("username", userName);
            userJson.put("password", password);
            userJson.put("firstname", firstName);
            userJson.put("lastname", lastName);
            userJson.put("email", email);
            userJson.put("timezone", timezone);
            userJson.put("state", "enabled");

            JSONObject user = userService.create(userName, account.getAccountName(), domain.getPath(), password, email, firstName, lastName, timezone);
            s_logger.info("Successfully created user[" + userName + "]");
            return user;
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to create a user", ex);
            return null;
        }
    }

    public UserVO update(UserVO user)
    {
        // select a random user
        if(user == null)    user = randUserSelect();

        AccountVO account = accountDao.findById(user.getAccountId());
        if (!isUsable(account))
        {
            return null;
        }

        if (!user.getState().equals(Account.State.enabled))
        {
            s_logger.info("Account[" + account.getAccountName() + "] is not enabled, skip to create a user.");
            return null;
        }

        if (!user.getState().equals(Account.State.enabled))
        {
            s_logger.info("User[" + user.getUsername() + "] is not enabled, skip to update a user.");
            return null;
        }

        DomainVO domain = domainDao.findById(account.getDomainId());

        // create new attribute values
        String userName = "U" + generateRandString();
        String password = user.getPassword();
        String firstName = "FN_" + generateRandString();
        String lastName = "LN" + generateRandString();
        String email = "EM" + generateRandString() + "@a.com";
        String timezone = randUserTimezoneSelect();
        String apikey = "AK" + generateRandString();
        String secretkey = "SK" + generateRandString();

        try
        {
            userService.update(user.getUsername(), userName, account.getAccountName(), domain.getPath(), email, firstName, lastName, password, timezone, apikey, secretkey);
            s_logger.info("Successfully updated user[" + user.getUsername() + "]");
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to update user", ex);
            return null;
        }

        return user;
    }

    public UserVO lock(UserVO user)
    {
        // select a random user
        if(user == null)    user = randUserSelect();

        AccountVO account = accountDao.findById(user.getAccountId());
        if (!isUsable(account))
        {
            return null;
        }

        if (!user.getState().equals(Account.State.enabled))
        {
            s_logger.info("This user[" + user.getUsername() + " is not enabled, but " + user.getState().toString() + ", so skip to lock this");
            return user;
        }

        DomainVO domain = domainDao.findById(account.getDomainId());

        userService.lock(user.getUsername(), account.getAccountName(), domain.getPath());
        s_logger.info("Successfully locked user[" + user.getUsername() + "]");

        return user;
    }

    public UserVO disable(UserVO user)
    {
        // select a random user
        if(user == null)    user = randUserSelect();

        AccountVO account = accountDao.findById(user.getAccountId());
        if (!isUsable(account))
        {
            return null;
        }

        if (!user.getState().equals(Account.State.enabled))
        {
            s_logger.info("This user[" + user.getUsername() + " is not enabled, but " + user.getState().toString() + ", so skip to disable this");
            return user;
        }

        DomainVO domain = domainDao.findById(account.getDomainId());

        userService.disable(user.getUsername(), account.getAccountName(), domain.getPath());
        s_logger.info("Successfully disabled user[" + user.getUsername() + "]");

        return user;
    }

    public UserVO enable(UserVO user)
    {
        // select a random user
        if(user == null)    user = randUserSelect();

        AccountVO account = accountDao.findById(user.getAccountId());
        if (!isUsable(account))
        {
            return null;
        }

        if (user.getState().equals(Account.State.enabled))
        {
            s_logger.info("This user[" + user.getUsername() + " is already enabled, so skip to enable this");
            return user;
        }

        DomainVO domain = domainDao.findById(account.getDomainId());

        userService.enable(user.getUsername(), account.getAccountName(), domain.getPath());
        s_logger.info("Successfully enabled user[" + user.getUsername() + "]");

        return user;
    }

    public UserVO remove(UserVO user)
    {
        // select a random user
        if(user == null)    user = randUserSelect();

        AccountVO account = accountDao.findById(user.getAccountId());
        if (!isUsable(account))
        {
            return null;
        }

        try
        {
            DomainVO domain = domainDao.findById(account.getDomainId());

            userService.delete(user.getUsername(), account.getAccountName(), domain.getPath());
            s_logger.info("Successfully removed user[" + user.getUsername() + "]");
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to remove user[" + user.getUsername() + "] : " + ex);
        }

        return user;
    }
}
