package kr.co.seoulit.his.outpatientservice.common.client.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Admin(ADM) Consumer — 개인정보 접근 이력.
 * 카탈로그: POST /api/admin/personalInfoAccessHistories
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.services.admin.stub-enabled", havingValue = "false", matchIfMissing = true)
public class AuditLogClientImpl implements AuditLogClient {

    private final RestClient adminRestClient;

    public AuditLogClientImpl(@Qualifier("adminRestClient") RestClient adminRestClient) {
        this.adminRestClient = adminRestClient;
    }

    @Override
    public void recordPersonalInfoAccess(PersonalInfoAccessRequest request) {
        try {
            adminRestClient.post()
                    .uri("/api/admin/personalInfoAccessHistories")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("[ADM] personalInfoAccessHistories failed resourceType={}, resourceId={}, message={}",
                    request.resourceType(), request.resourceId(), ex.getMessage());
        }
    }
}
