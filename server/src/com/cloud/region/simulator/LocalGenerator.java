package com.cloud.region.simulator;

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
import java.util.Random;
import java.util.TimeZone;

public class LocalGenerator {

    private static final Logger s_logger = Logger.getLogger(LocalGenerator.class);

    protected UserDao userDao;
    protected AccountDao accountDao;
    protected DomainDao domainDao;

    public LocalGenerator()
    {
        this.userDao = ComponentContext.getComponent(UserDao.class);
        this.accountDao = ComponentContext.getComponent(AccountDao.class);
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
    }

    protected int generateRandNumber(int max)
    {
        double index = Math.random();
        return (int)((index * 1000) % max);
    }

    protected String generateRandString()
    {
        int length = 10;
        String alpha = "abcdefghijklmnopqrstuvwxyz";
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < length; i++) {
            double index = Math.random() * alpha.length();
            buffer.append(alpha.charAt((int) index));
        }
        return buffer.toString();
    }

    protected Date generateRandDate()
    {
        Date date = new Date();
        long time = date.getTime();
        time -= 60 * 60 * 1000 * 24;
        date.setTime(time);
        return date;
    }

    protected DomainVO randDomainSelect(boolean includeRoot)
    {
        List<DomainVO> domainList = domainDao.listAll();
        Random rand = new Random();
        int num = 0;
        DomainVO domain = null;
        while(num == 0)
        {
            // exclude the 'ROOT' domain
            num = rand.nextInt(domainList.size());
            if (!includeRoot && num == 0)   continue;

            domain = domainList.get(num);
            if (domain.getState().toString().equals("Active"))  break;
        }
        return domain;
    }

    protected AccountVO randAccountSelect(boolean includeSystem)
    {
        List<AccountVO> accountList = accountDao.listAll();
        Random rand = new Random();
        int num = 0;
        while(num == 0)
        {
            // exclude the 'ROOT' domain
            num = rand.nextInt(accountList.size());
            if (includeSystem)    break;
        }
        AccountVO account = accountList.get(num);
        return account;
    }

    protected short randAccountTypeSelect()
    {
        /*Random rand = new Random(10);
        int num = rand.nextInt();
        if (num % 2 == 0)   return Account.ACCOUNT_TYPE_ADMIN;*/
        return Account.ACCOUNT_TYPE_NORMAL;
    }

    protected UserVO randUserSelect()
    {
        List<UserVO> userList = userDao.listAll();
        Random rand = new Random();
        int num = rand.nextInt(userList.size());
        UserVO user = userList.get(num);
        return user;
    }

    protected String randUserTimezoneSelect()
    {
        String[] ids = TimeZone.getAvailableIDs();
        Random rand = new Random();
        int num = rand.nextInt(ids.length);
        return TimeZone.getTimeZone(ids[num]).getDisplayName();
    }

    protected boolean isUsable(AccountVO account)
    {
        if (account.getType() == Account.ACCOUNT_TYPE_PROJECT)
        {
            s_logger.info("Account[" + account.getAccountName() + "] is project type, so skip to remove this.");
            return false;
        }

        if (account.getId() == Account.ACCOUNT_ID_SYSTEM) {
            s_logger.info("Account[" + account.getAccountName() + "] is system account, skip to remove this.");
            return false;
        }

        if (account.getDomainId() == Account.ACCOUNT_ID_SYSTEM && account.getType() == Account.ACCOUNT_TYPE_ADMIN) {
            s_logger.info("Account[" + account.getAccountName() + "] is admin account, skip to remove this.");
            return false;
        }

        return true;
    }
}
