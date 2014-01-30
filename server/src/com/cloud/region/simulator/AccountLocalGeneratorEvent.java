package com.cloud.region.simulator;

import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.region.service.AccountService;
import com.cloud.region.service.LocalUserManager;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.region.RegionVO;
import org.apache.cloudstack.region.dao.RegionDao;
import org.apache.log4j.Logger;

public class AccountLocalGeneratorEvent extends LocalGenerator {

    private static final Logger s_logger = Logger.getLogger(AccountLocalGeneratorEvent.class);

    private AccountService accountService;
    private DomainDao domainDao;

    public AccountLocalGeneratorEvent()
    {
        this.domainDao = ComponentContext.getComponent(DomainDao.class);

        RegionDao regionDao = ComponentContext.getComponent(RegionDao.class);
        RegionVO region = regionDao.findByName("Local");
        this.accountService = new AccountService(region.getName(), region.getEndPoint(), region.getUserName(), region.getPassword());
    }

    public JSONObject create()
    {
        // select a random account
        DomainVO domain = randDomainSelect(false);

        // create a random string for a new account
        String accountName = "A" + generateRandString();
        String userName = accountName;
        String password = LocalUserManager.TEMP_PASSWORD;
        String email = accountName + "@a.com";
        String firstName = accountName;
        String lastName = "Test";
        String networkDomain = "ND" + generateRandString();
        short accountType = randAccountTypeSelect();
        String accountDetails = null;
        String timezone = randUserTimezoneSelect();

        try
        {
            JSONObject account = accountService.create(accountName, domain.getPath(), userName, password, email, firstName, lastName, Short.toString(accountType), accountDetails, networkDomain, timezone);
            s_logger.info("Successfully created account[" + accountName + "]");
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
        // select a random account
        if (account == null)    account = randAccountSelect(false);

        if (!isUsable(account))
        {
            return null;
        }

        if (!account.getState().equals(Account.State.enabled))
        {
            s_logger.info("This account is not enabled, so skip update");
            return account;
        }

        DomainVO domain = domainDao.findById(account.getDomainId());

        // create new attribute values
        String newAccountName = "A" + generateRandString();
        String newNetworkDomain = "ND" + generateRandString();
        String accountDetails = null;

        try
        {
            accountService.update(account.getAccountName(), domain.getPath(), newAccountName, accountDetails, newNetworkDomain);
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
        // select a random account
        if (account == null)    account = randAccountSelect(false);

        if (!isUsable(account))
        {
            return null;
        }

        if (!account.getState().equals(Account.State.enabled))
        {
            s_logger.info("This account[" + account.getAccountName() + " is not enabled, but " + account.getState().toString() + ", so skip to lock this");
            return account;
        }

        DomainVO domain = domainDao.findById(account.getDomainId());

        accountService.lock(account.getAccountName(), domain.getPath());
        s_logger.info("Successfully locked account[" + account.getAccountName() + "]");

        return account;
    }

    public AccountVO disable(AccountVO account)
    {
        // select a random account
        if (account == null)    account = randAccountSelect(false);

        if (!isUsable(account))
        {
            return null;
        }

        if (!account.getState().equals(Account.State.enabled))
        {
            s_logger.info("This account[" + account.getAccountName() + " is not enabled, but " + account.getState().toString() + ", so skip to disable this");
            return account;
        }

        try
        {
            DomainVO domain = domainDao.findById(account.getDomainId());

            accountService.disable(account.getAccountName(), domain.getPath());
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
        // select a random account
        if (account == null)    account = randAccountSelect(false);

        if (!isUsable(account))
        {
            return null;
        }

        if (account.getState().equals(Account.State.enabled))
        {
            s_logger.info("This account[" + account.getAccountName() + " is already enabled, so skip to enable this");
            return account;
        }

        DomainVO domain = domainDao.findById(account.getDomainId());

        accountService.enable(account.getAccountName(), domain.getPath());
        s_logger.info("Successfully enabled account[" + account.getAccountName() + "]");

        return account;
    }

    public AccountVO remove(AccountVO account)
    {
        // select a random account
        if (account == null)    account = randAccountSelect(false);

        if (!isUsable(account))
        {
            return null;
        }

        try
        {
            DomainVO domain = domainDao.findById(account.getDomainId());

            accountService.delete(account.getAccountName(), domain.getPath());
            s_logger.info("Successfully removed account[" + account.getAccountName() + "]");
        }
        catch(Exception ex)
        {
            s_logger.info("Failed to remove account[" + account.getAccountName() + "] : " + ex);
        }

        return account;
    }
}
