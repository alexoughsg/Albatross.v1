package org.apache.cloudstack.mom.simulator;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import org.apache.cloudstack.mom.service.LocalAccountManager;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.Map;

public class AccountLocalGenerator extends LocalGenerator {

    private static final Logger s_logger = Logger.getLogger(AccountLocalGenerator.class);

    private LocalAccountManager localAccountManager;

    public AccountLocalGenerator()
    {
        this.localAccountManager = new LocalAccountManager();
    }

    public AccountVO create()
    {
        Date created = generateRandDate();
        JSONObject accountJson = new JSONObject();

        // select a random account
        DomainVO domain = randDomainSelect(false);

        // create a random string for a new account
        String accountName = "A" + generateRandString();
        String networkDomain = "ND" + generateRandString();
        short accountType = randAccountTypeSelect();
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
        if (account == null)    account = randAccountSelect(false);
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
        if (account == null)    account = randAccountSelect(false);

        localAccountManager.lock(account, modified);
        s_logger.info("Successfully locked account[" + account.getAccountName() + "]");

        return account;
    }

    public AccountVO disable(AccountVO account)
    {
        Date modified = generateRandDate();

        // select a random account
        if (account == null)    account = randAccountSelect(false);

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
        if (account == null)    account = randAccountSelect(false);

        localAccountManager.enable(account, modified);
        s_logger.info("Successfully enabled account[" + account.getAccountName() + "]");

        return account;
    }

    public AccountVO remove(AccountVO account)
    {
        Date removed = generateRandDate();

        // select a random account
        if (account == null)    account = randAccountSelect(false);

        localAccountManager.remove(account, removed);
        s_logger.info("Successfully removed account[" + account.getAccountName() + "]");

        return account;
    }
}
