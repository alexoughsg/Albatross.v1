package org.apache.cloudstack.mom.simulator;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.mom.service.LocalAccountManager;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AccountLocalGenerator extends LocalGenerator {

    private static final Logger s_logger = Logger.getLogger(AccountLocalGenerator.class);

    private AccountDao accountDao;
    private DomainDao domainDao;
    private LocalAccountManager localAccountManager;

    public AccountLocalGenerator()
    {
        this.accountDao = ComponentContext.getComponent(AccountDao.class);
        this.domainDao = ComponentContext.getComponent(DomainDao.class);
        this.localAccountManager = new LocalAccountManager();
    }

    protected AccountVO randSelect()
    {
        List<AccountVO> accountList = accountDao.listAll();
        Random rand = new Random();
        int num = rand.nextInt(accountList.size());
        AccountVO account = accountList.get(num);
        return account;
    }

    protected short randSelectType()
    {
        Random rand = new Random(10);
        int num = rand.nextInt();
        if (num % 2 == 0)   return Account.ACCOUNT_TYPE_ADMIN;
        return Account.ACCOUNT_TYPE_NORMAL;
    }

    public AccountVO create()
    {
        Date created = generateRandDate();
        JSONObject accountJson = new JSONObject();

        // select a random account
        AccountVO ranAccount = randSelect();
        DomainVO domain = domainDao.findById(ranAccount.getDomainId());

        // create a random string for a new account
        String accountName = "A" + generateRandString();
        String networkDomain = "ND" + generateRandString();
        short accountType = randSelectType();
        Map<String, String> accountDetails = null;

        try
        {
            accountJson.put("path", domain.getPath());
            accountJson.put("name", accountName);
            accountJson.put("networkdomain", networkDomain);
            accountJson.put("accounttype", Short.toString(accountType));
            accountJson.put("details", accountDetails);
            AccountVO account = (AccountVO)localAccountManager.create(accountJson, created);
            s_logger.info("Successfully created account[" + account.getAccountName() + "]");
            return account;
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to create an account", ex);
            return null;
        }
    }

    public AccountVO update(AccountVO account)
    {
        Date modified = generateRandDate();
        JSONObject accountJson = new JSONObject();

        // select a random account
        if (account == null)    account = randSelect();
        if (!account.getState().equals(Account.State.enabled))
        {
            localAccountManager.enable(account, modified);
        }

        // create new attribute values
        String newAccountName = "A" + generateRandString();
        String newNetworkDomain = "ND" + generateRandString();
        Map<String, String> accountDetails = null;

        try
        {
            accountJson.put("name", newAccountName);
            accountJson.put("networkdomain", newNetworkDomain);
            accountJson.put("details", accountDetails);
            localAccountManager.update(account, accountJson, modified);
            s_logger.info("Successfully updated account[" + account.getAccountName() + "]");
            return account;
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to set json attributes", ex);
            return null;
        }
    }

    public AccountVO lock(AccountVO account)
    {
        Date modified = generateRandDate();

        // select a random account
        if (account == null)    account = randSelect();

        localAccountManager.lock(account, modified);
        s_logger.info("Successfully locked account[" + account.getAccountName() + "]");

        return account;
    }

    public AccountVO disable(AccountVO account)
    {
        Date modified = generateRandDate();

        // select a random account
        if (account == null)    account = randSelect();

        try
        {
            localAccountManager.disable(account, modified);
            s_logger.info("Successfully disabled account[" + account.getAccountName() + "]");

            return account;
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to disable account", ex);
            return null;
        }
    }

    public AccountVO enable(AccountVO account)
    {
        Date modified = generateRandDate();

        // select a random account
        if (account == null)    account = randSelect();

        localAccountManager.enable(account, modified);
        s_logger.info("Successfully enabled account[" + account.getAccountName() + "]");

        return account;
    }

    public AccountVO remove(AccountVO account)
    {
        Date removed = generateRandDate();

        // select a random account
        if (account == null)    account = randSelect();

        localAccountManager.remove(account, removed);
        s_logger.info("Successfully removed account[" + account.getAccountName() + "]");

        return account;
    }
}
