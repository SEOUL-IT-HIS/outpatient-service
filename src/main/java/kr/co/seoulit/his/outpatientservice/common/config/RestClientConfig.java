package kr.co.seoulit.his.outpatientservice.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    // 상대 서비스가 응답 없거나 느려도 몇 초 안에 실패 처리하고 넘어가도록 짧게 잡음
    // (타임아웃이 없으면 스레드가 계속 매달려서, 결국 우리 쪽 API 응답까지 못 나가고 프론트 axios 타임아웃으로 이어짐)
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    private ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }

    @Bean
    public RestClient patientRestClient(
            @Value("${app.services.patient.base-url}") String patientBaseUrl
    ) {
        return RestClient.builder()
                .baseUrl(patientBaseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Bean
    public RestClient adminRestClient(
            @Value("${app.services.admin.base-url}") String adminBaseUrl
    ) {
        return RestClient.builder()
                .baseUrl(adminBaseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Bean
    public RestClient labRestClient(
            @Value("${app.services.lab.base-url}") String labBaseUrl
    ) {
        return RestClient.builder()
                .baseUrl(labBaseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Bean
    public RestClient pharmacyRestClient(
            @Value("${app.services.pharmacy.base-url}") String pharmacyBaseUrl
    ) {
        return RestClient.builder()
                .baseUrl(pharmacyBaseUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }
}
