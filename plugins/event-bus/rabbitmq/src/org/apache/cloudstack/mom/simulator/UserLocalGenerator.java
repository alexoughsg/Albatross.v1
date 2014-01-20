package org.apache.cloudstack.mom.simulator;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.mom.service.LocalUserManager;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Random;

public class UserLocalGenerator extends LocalGenerator {
    private static final Logger s_logger = Logger.getLogger(UserLocalGenerator.class);

    private UserDao userDao;
    private AccountDao accountDao;
    private DomainDao domainDao;
    private LocalUserManager localUserManager;

    public UserLocalGenerator()
    {
        this.userDao = ComponentContext.getComponent(UserDao.class);
        this.accountDao = ComponentContext.getComponent(AccountDao.class);
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.localUserManager = new LocalUserManager();
    }

    protected UserVO randSelect()
    {
        List<UserVO> userList = userDao.listAll();
        Random rand = new Random();
        int num = rand.nextInt(userList.size());
        UserVO user = userList.get(num);
        return user;
    }

    protected String randSelectTimezone()
    {
        String[] ids = TimeZone.getAvailableIDs();
        Random rand = new Random();
        int num = rand.nextInt(ids.length);
        return TimeZone.getTimeZone(ids[num]).getDisplayName();
    }

    public UserVO create()
    {
        Date created = generateRandDate();
        JSONObject userJson = new JSONObject();

        // select a random user
        UserVO randUser = randSelect();
        AccountVO account = accountDao.findById(randUser.getAccountId());
        DomainVO domain = domainDao.findById(account.getDomainId());

        // create a random string for a new user
        String userName = "U" + generateRandString();
        String password = "PW" + generateRandString();
        String firstName = "FN" + generateRandString();
        String lastName = "LN" + generateRandString();
        String email = "EM" + generateRandString() + "@a.com";
        String timezone = randSelectTimezone();

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
        if(user == null)    user = randSelect();
        if (!user.getState().equals(Account.State.enabled))
        {
            localUserManager.enable(user, modified);
        }

        // create new attribute values
        String userName = "U" + generateRandString();
        //String password = "PW" + generateRandString();
        String firstName = "FN_" + generateRandString();
        String lastName = "LN" + generateRandString();
        String email = "EM" + generateRandString() + "@a.com";
        String timezone = randSelectTimezone();
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
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to set json attributes", ex);
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
        if(user == null)    user = randSelect();

        localUserManager.lock(user, modified);
        s_logger.info("Successfully locked user[" + user.getUsername() + "]");

        return user;
    }

    public UserVO disable(UserVO user)
    {
        Date modified = generateRandDate();

        // select a random user
        if(user == null)    user = randSelect();

        localUserManager.disable(user, modified);
        s_logger.info("Successfully disabled user[" + user.getUsername() + "]");

        return user;
    }

    public UserVO enable(UserVO user)
    {
        Date modified = generateRandDate();

        // select a random user
        if(user == null)    user = randSelect();

        localUserManager.enable(user, modified);
        s_logger.info("Successfully enabled user[" + user.getUsername() + "]");

        return user;
    }

    public UserVO remove(UserVO user)
    {
        Date removed = generateRandDate();

        // select a random user
        if(user == null)    user = randSelect();

        localUserManager.remove(user, removed);
        s_logger.info("Successfully removed user[" + user.getUsername() + "]");

        return user;
    }
}
