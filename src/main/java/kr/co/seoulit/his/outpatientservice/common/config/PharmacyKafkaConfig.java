package kr.co.seoulit.his.outpatientservice.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// messaging-enabled=false면 이 빈이 아예 안 만들어져서, 브로커 상태와 무관하게 앱이 바로 뜬다
@Configuration
@ConditionalOnProperty(name = "app.services.pharmacy.messaging-enabled", havingValue = "true")
public class PharmacyKafkaConfig {

    private static final int PARTITIONS = 1;
    private static final short REPLICAS = 1;

    @Bean
    public NewTopic pharmacyOrderRequestedTopic(@Value("${app.kafka.topics.pharmacy-order-requested}") String topic) {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    // 취소 토픽은 약제가 만들지 않고 구독만 한다 — 약제 배포 전에 이 토픽이 먼저 존재해야 한다
    // (요청 토픽과 파티션 수가 같아야 같은 key의 요청/취소 순서가 유지된다)
    @Bean
    public NewTopic pharmacyOrderCancelledTopic(@Value("${app.kafka.topics.pharmacy-order-cancelled}") String topic) {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}