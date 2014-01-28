package com.cloud.region.simulator;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.region.service.LocalUserManager;
import org.apache.log4j.Logger;

import java.util.Date;

public class UserLocalGenerator extends LocalGenerator {

    private static final Logger s_logger = Logger.getLogger(UserLocalGenerator.class);

    private LocalUserManager localUserManager;

    public UserLocalGenerator()
    {
        this.localUserManager = new LocalUserManager();
    }

    public UserVO create()
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

            UserVO user = (UserVO)localUserManager.create(userJson, created);
            s_logger.info("Successfully created user[" + user.getUsername() + "]");
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
        Date modified = generateRandDate();
        JSONObject userJson = new JSONObject();

        // select a random user
        if(user == null)    user = randUserSelect();

        AccountVO account = accountDao.findById(user.getAccountId());
        if (!isUsable(account))
        {
            return null;
        }

        if (!user.getState().equals(Account.State.enabled))
        {
            s_logger.info("User[" + user.getUsername() + "] is not enabled, skip to update a user.");
            return null;
        }

        // create new attribute values
        String userName = "U" + generateRandString();
        //String password = "PW" + generateRandString();
        String firstName = "FN_" + generateRandString();
        String lastName = "LN" + generateRandString();
        String email = "EM" + generateRandString() + "@a.com";
        String timezone = randUserTimezoneSelect();
        String apikey = "AK" + generateRandString();
        String scretkey = "SK" + generateRandString();

        try
        {
            userJson.put("apikey", apikey);
            userJson.put("firstname", firstName);
            userJson.put("email", email);
            userJson.put("lastname", lastName);
            //userJson.put("password", password);
            userJson.put("secretkey", scretkey);
            userJson.put("timezone", timezone);
            userJson.put("username", userName);
            userJson.put("state", user.getState());
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to update user", ex);
            return null;
        }

        localUserManager.update(user, userJson, modified);
        s_logger.info("Successfully updated user[" + user.getUsername() + "]");

        return user;
    }

    public UserVO lock(UserVO user)
    {
        Date modified = generateRandDate();

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

        localUserManager.lock(user, modified);
        s_logger.info("Successfully locked user[" + user.getUsername() + "]");

        return user;
    }

    public UserVO disable(UserVO user)
    {
        Date modified = generateRandDate();

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

        localUserManager.disable(user, modified);
        s_logger.info("Successfully disabled user[" + user.getUsername() + "]");

        return user;
    }

    public UserVO enable(UserVO user)
    {
        Date modified = generateRandDate();

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

        localUserManager.enable(user, modified);
        s_logger.info("Successfully enabled user[" + user.getUsername() + "]");

        return user;
    }

    public UserVO remove(UserVO user)
    {
        Date removed = generateRandDate();

        // select a random user
        if(user == null)    user = randUserSelect();

        AccountVO account = accountDao.findById(user.getAccountId());
        if (!isUsable(account))
        {
            return null;
        }

        try
        {
            localUserManager.remove(user, removed);
            s_logger.info("Successfully removed user[" + user.getUsername() + "]");
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to remove user[" + user.getUsername() + "] : " + ex);
        }

        return user;
    }
}
