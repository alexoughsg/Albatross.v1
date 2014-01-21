package org.apache.cloudstack.mom.rabbitmq;

import com.cloud.domain.Domain;
import com.cloud.user.Account;
import com.cloud.user.User;
import org.apache.cloudstack.framework.events.Event;
import org.apache.cloudstack.mom.service.UserService;
import org.apache.cloudstack.region.RegionVO;
import org.apache.log4j.Logger;

import java.lang.reflect.Method;

public class UserSubscriber extends MultiRegionSubscriber {

    private static final Logger s_logger = Logger.getLogger(UserSubscriber.class);

    public UserSubscriber(int id)
    {
        super(id);
    }

    @Override
    public void onEvent(Event event)
    {
        super.onEvent(event);

        if (!isExecutable())    return;

        regions = findRemoteRegions();
        process(event);
    }

    protected void process(Event event)
    {
        String entityUUID = this.descMap.get("entityuuid");
        String oldUserName = this.descMap.get("oldentityname");
        User user = this.userDao.findByUuidIncludingRemoved(entityUUID);
        Account account = this.accountDao.findByIdIncludingRemoved(user.getAccountId());
        Domain domain = this.domainDao.findByIdIncludingRemoved(account.getDomainId());

        String methodName = event.getEventType().split("-")[1].toLowerCase();
        for (RegionVO region : regions)
        {
            try
            {
                UserService userService = new UserService(region.getName(), region.getEndPoint(), region.getUserName(), region.getPassword());
                Method method = userService.getClass().getMethod(methodName, User.class, Account.class, Domain.class, String.class);
                method.invoke(userService, user, account, domain, oldUserName);
            }
            catch(NoSuchMethodException mex)
            {
                s_logger.error(region.getName() + ": Not valid method[" + methodName + "]");
            }
            catch(Exception ex)
            {
                s_logger.error(region.getName() + ": Fail to invoke/process method[" + methodName + "]", ex);
            }
        }
    }
}
