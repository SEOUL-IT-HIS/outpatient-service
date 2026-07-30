package kr.co.seoulit.his.outpatientservice.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient patientRestClient(
            @Value("${app.services.patient.base-url}") String patientBaseUrl
    ) {
        return RestClient.builder()
                .baseUrl(patientBaseUrl)
                .build();
    }

    @Bean
    public RestClient adminRestClient(
            @Value("${app.services.admin.base-url}") String adminBaseUrl
    ) {
        return RestClient.builder()
                .baseUrl(adminBaseUrl)
                .build();
    }
}
