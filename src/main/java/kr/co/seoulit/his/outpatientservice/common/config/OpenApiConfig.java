package kr.co.seoulit.his.outpatientservice.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI outpatientOpenApi(
            @Value("${spring.application.name}") String serviceName,
            @Value("${app.service.code}") String serviceCode,
            @Value("${app.api.base-url}") String baseUrl
    ) {
        String description = """
                HIS 외래관리 서비스 (%s) API — 1차 스프린트.

                - GR2-4 당일 진료 환자 목록: GET /api/outpatient/encounters
                - GR2-5 진료기록 조회: GET /api/outpatient/records
                - 환자명/번호는 Patient(PAT) API Consumer 조회 (스냅샷 저장 금지)
                - 개인정보 열람 감사는 Admin(ADM) personalInfoAccessHistories (최소)
                """.formatted(serviceCode);

        return new OpenAPI()
                .servers(List.of(new Server().url(baseUrl).description("Local")))
                .info(new Info()
                        .title(serviceName)
                        .description(description)
                        .version("v0.1.0"));
    }
}
