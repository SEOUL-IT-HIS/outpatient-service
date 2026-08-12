package kr.co.seoulit.his.outpatientservice.common.client.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.services.admin.stub-enabled", havingValue = "true")
public class StubAuditLogClient implements AuditLogClient {

    @Override
    public void recordPersonalInfoAccess(PersonalInfoAccessRequest request) {
        log.info("[ADM-STUB] personalInfoAccess service={}, resourceType={}, resourceId={}, action={}",
                request.serviceCode(), request.resourceType(), request.resourceId(), request.action());
    }
}
