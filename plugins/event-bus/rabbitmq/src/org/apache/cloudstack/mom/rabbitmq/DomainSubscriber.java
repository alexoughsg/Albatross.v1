package org.apache.cloudstack.mom.rabbitmq;

import com.cloud.domain.Domain;
import org.apache.cloudstack.framework.events.Event;
import com.cloud.region.service.DomainService;
import org.apache.cloudstack.region.RegionVO;
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

        regions = findRemoteRegions();
        process(event);
    }

    protected void process(Event event)
    {
        String entityUUID = this.descMap.get("entityuuid");
        String oldDomainName = this.descMap.get("oldentityname");
        Domain domain = this.domainDao.findByUuidIncludingRemoved(entityUUID);

        String methodName = event.getEventType().split("-")[1].toLowerCase();
        for(RegionVO region : regions)
        {
            try
            {
                DomainService domainService = new DomainService(region.getName(), region.getEndPoint(), region.getUserName(), region.getPassword());
                Method method = domainService.getClass().getMethod(methodName, Domain.class, String.class);
                method.invoke(domainService, domain, oldDomainName);
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