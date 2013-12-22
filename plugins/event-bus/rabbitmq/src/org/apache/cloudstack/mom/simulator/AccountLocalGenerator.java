package org.apache.cloudstack.mom.simulator;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import org.apache.cloudstack.mom.service.AccountFullScanner;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AccountLocalGenerator extends AccountFullScanner {

    private static final Logger s_logger = Logger.getLogger(AccountLocalGenerator.class);

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
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to set json attributes", ex);
            return null;
        }

        AccountVO account = (AccountVO)super.create(accountJson, created);
        s_logger.info("Successfully created account[" + account.getAccountName() + "]");

        return account;
    }

    public AccountVO update(AccountVO account)
    {
        Date modified = generateRandDate();
        JSONObject accountJson = new JSONObject();

        // select a random account
        if (account == null)    account = randSelect();
        if (!account.getState().equals(Account.State.enabled)) super.enable(account, modified);

        // create new attribute values
        String newAccountName = "A" + generateRandString();
        String newNetworkDomain = "ND" + generateRandString();
        Map<String, String> accountDetails = null;

        try
        {
            accountJson.put("name", newAccountName);
            accountJson.put("networkdomain", newNetworkDomain);
            accountJson.put("details", accountDetails);
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to set json attributes", ex);
            return null;
        }

        super.update(account, accountJson, modified);
        s_logger.info("Successfully updated account[" + account.getAccountName() + "]");

        return account;
    }

    public AccountVO lock(AccountVO account)
    {
        Date modified = generateRandDate();

        // select a random account
        if (account == null)    account = randSelect();

        super.lock(account, modified);
        s_logger.info("Successfully locked account[" + account.getAccountName() + "]");

        return account;
    }

    public AccountVO disable(AccountVO account)
    {
        Date modified = generateRandDate();

        // select a random account
        if (account == null)    account = randSelect();

        super.disable(account, modified);
        s_logger.info("Successfully disabled account[" + account.getAccountName() + "]");

        return account;
    }

    public AccountVO enable(AccountVO account)
    {
        Date modified = generateRandDate();

        // select a random account
        if (account == null)    account = randSelect();

        super.enable(account, modified);
        s_logger.info("Successfully enabled account[" + account.getAccountName() + "]");

        return account;
    }

    public AccountVO remove(AccountVO account)
    {
        Date removed = generateRandDate();

        // select a random account
        if (account == null)    account = randSelect();

        super.remove(account, removed);
        s_logger.info("Successfully removed account[" + account.getAccountName() + "]");

        return account;
    }
}
