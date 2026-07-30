package kr.co.seoulit.his.outpatientservice.common.client.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * ADM(stub-enabled=false) 실연동 경로 검증용 테스트.
 * 실제 ADM 서비스 대신 JDK 내장 HttpServer로 계약(POST /api/admin/personalInfoAccessHistories)을 흉내내서
 * AuditLogClientImpl이 정확한 요청을 보내는지 확인한다.
 */
class AuditLogClientImplTest {

    private HttpServer fakeAdmServer;
    private final LinkedBlockingQueue<String> receivedBodies = new LinkedBlockingQueue<>();
    private AuditLogClientImpl auditLogClient;

    @BeforeEach
    void setUp() throws IOException {
        fakeAdmServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeAdmServer.createContext("/api/admin/personalInfoAccessHistories", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedBodies.add(body);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        fakeAdmServer.start();

        int port = fakeAdmServer.getAddress().getPort();
        RestClient adminRestClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
        auditLogClient = new AuditLogClientImpl(adminRestClient);
    }

    @AfterEach
    void tearDown() {
        fakeAdmServer.stop(0);
    }

    @Test
    void 개인정보_열람_감사_로그를_ADM에_전송한다() throws Exception {
        // OutpatientCareServiceImpl.recordAudit()이 실제로 만드는 요청과 동일한 형태
        PersonalInfoAccessRequest request = new PersonalInfoAccessRequest(
                "OPD", "SYSTEM", "MEDICAL_RECORD", "123", "VIEW", "외래 진료 업무 조회"
        );

        auditLogClient.recordPersonalInfoAccess(request);

        String body = receivedBodies.poll(3, TimeUnit.SECONDS);
        assertThat(body).isNotNull();

        Map<String, Object> json = new ObjectMapper().readValue(body, new TypeReference<>() {
        });
        assertThat(json).containsEntry("serviceCode", "OPD");
        assertThat(json).containsEntry("accessorId", "SYSTEM");
        assertThat(json).containsEntry("resourceType", "MEDICAL_RECORD");
        assertThat(json).containsEntry("resourceId", "123");
        assertThat(json).containsEntry("action", "VIEW");
        assertThat(json).containsEntry("reason", "외래 진료 업무 조회");
    }

    @Test
    void ADM이_다운되어도_예외를_던지지_않는다() {
        fakeAdmServer.stop(0); // ADM 서버가 꺼져 있는 상황(지금 로컬 환경과 동일) 재현

        PersonalInfoAccessRequest request = new PersonalInfoAccessRequest(
                "OPD", "SYSTEM", "ENCOUNTER", "TODAY", "LIST", "외래 진료 업무 조회"
        );

        assertThatCode(() -> auditLogClient.recordPersonalInfoAccess(request))
                .doesNotThrowAnyException();
    }
}
