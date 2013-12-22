package org.apache.cloudstack.mom.rabbitmq;

import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import org.apache.cloudstack.framework.events.Event;
import org.apache.cloudstack.mom.service.DomainService;
import org.apache.cloudstack.mom.simulator.AccountLocalGenerator;
import org.apache.cloudstack.mom.simulator.DomainLocalGenerator;
import org.apache.cloudstack.mom.simulator.UserLocalGenerator;
import org.apache.log4j.Logger;

import java.lang.reflect.Method;

public class DomainSubscriber extends MultiRegionSubscriber {

    private static final Logger s_logger = Logger.getLogger(DomainSubscriber.class);

    public DomainSubscriber(int id)
    {
        super(id);
    }

    @Override
    public void onEvent(Event event)
    {
        super.onEvent(event);

        if (!isExecutable())    return;

        /*String[][] remoteRegions = {
                {"localhost", "admin", "password"},
                //{"10.88.90.82", "admin", "password"},
                //{"207.19.99.100", "admin", "password"}
        };
        DomainFullScanner domainScanner = new DomainFullScanner();
        domainScanner.refreshAll(remoteRegions);

        AccountFullScanner accountScanner = new AccountFullScanner();
        accountScanner.refreshAll(remoteRegions);

        UserFullScanner userScanner = new UserFullScanner();
        userScanner.refreshAll(remoteRegions);*/

        DomainLocalGenerator dgen = new DomainLocalGenerator();
        DomainVO domain = dgen.create();
        dgen.update(domain);
        //dgen.remove(domain);

        AccountLocalGenerator agen = new AccountLocalGenerator();
        AccountVO account = agen.create();
        agen.update(account);
        agen.lock(account);
        agen.enable(account);
        agen.disable(account);
        agen.enable(account);
        //agen.remove(account);

        UserLocalGenerator ugen = new UserLocalGenerator();
        UserVO user = ugen.create();
        ugen.update(user);
        ugen.lock(user);
        ugen.enable(user);
        ugen.disable(user);
        ugen.enable(user);
        //ugen.remove(user);




        process(event);
    }

    protected void process(Event event)
    {
        String entityUUID = this.descMap.get("entityuuid");
        String oldDomainName = this.descMap.get("oldentityname");
        Domain domain = this.domainDao.findByUuidIncludingRemoved(entityUUID);

        String methodName = event.getEventType().split("-")[1].toLowerCase();
        for (int index = 0; index < this.regions.length; index++)
        {
            String hostName = this.regions[index][0];
            String userName = this.regions[index][1];
            String password = this.regions[index][2];

            try
            {
                DomainService domainService = new DomainService(hostName, userName, password);
                Method method = domainService.getClass().getMethod(methodName, Domain.class, String.class);
                method.invoke(domainService, domain, oldDomainName);
            }
            catch(NoSuchMethodException mex)
            {
                s_logger.error(hostName + ": Not valid method[" + methodName + "]");
            }
            catch(Exception ex)
            {
                s_logger.error(hostName + ": Fail to invoke/process method[" + methodName + "]", ex);
            }
        }
    }

}