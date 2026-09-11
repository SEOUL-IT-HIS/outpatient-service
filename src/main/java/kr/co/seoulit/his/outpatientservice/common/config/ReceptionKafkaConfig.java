package kr.co.seoulit.his.outpatientservice.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// messaging-enabled=false면 이 빈이 아예 안 만들어져서, 브로커 상태와 무관하게 앱이 바로 뜬다
@Configuration
@ConditionalOnProperty(name = "app.services.reception.messaging-enabled", havingValue = "true")
public class ReceptionKafkaConfig {

    private static final int PARTITIONS = 1;
    private static final short REPLICAS = 1;

    @Bean
    public NewTopic receptionRegisteredTopic(@Value("${app.kafka.topics.reception-registered}") String topic) {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
