package kr.co.seoulit.his.outpatientservice.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// messaging-enabled=false면 이 빈이 아예 안 만들어져서, 브로커 상태와 무관하게 앱이 바로 뜬다
@Configuration
@ConditionalOnProperty(name = "app.services.admission.messaging-enabled", havingValue = "true")
public class AdmissionKafkaConfig {

    // 병동과 합의한 값: 파티션 1 / 레플리카 1. 이미 있는 토픽이면 그대로 두므로 병동이 먼저 만들어도 충돌하지 않는다.
    private static final int PARTITIONS = 1;
    private static final short REPLICAS = 1;

    // 입원 요청 토픽 (외래 -> 병동)
    @Bean
    public NewTopic admissionRequestedTopic(@Value("${app.kafka.topics.admission-requested}") String topic) {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    // 결과 회신 토픽 (병동 -> 외래). 병동이 만들지만 외래가 먼저 떠도 구독할 수 있게 같은 설정으로 등록해 둔다.
    @Bean
    public NewTopic admissionResultedTopic(@Value("${app.kafka.topics.admission-resulted}") String topic) {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
