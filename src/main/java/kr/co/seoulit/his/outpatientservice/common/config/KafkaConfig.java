package kr.co.seoulit.his.outpatientservice.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

// messaging-enabled=false면 이 클래스의 빈이 아예 안 만들어져서, 토픽 자동생성 체크가
// 앱 시작 시 브로커에 접속을 시도하지 않는다 (REST 경로로 쓸 때 브로커 상태와 무관하게 뜨도록).
@Configuration
@ConditionalOnProperty(name = "app.services.lab.messaging-enabled", havingValue = "true")
public class KafkaConfig {

    // 검사서비스와 합의한 값: 단일 브로커 기준 partitions=1, replicas=1 (replicas 2 이상이면 생성 실패)
    private static final int PARTITIONS = 1;
    private static final short REPLICAS = 1;

    //검사 오더 요청토픽
    @Bean
    public NewTopic labOrderRequestedTopic(@Value("${app.kafka.topics.lab-order-requested}") String topic) {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    //검사 결과 회신 토픽
    @Bean
    public NewTopic labOrderResultedTopic(@Value("${app.kafka.topics.lab-order-resulted}") String topic) {
        return TopicBuilder.name(topic).partitions(PARTITIONS).replicas(REPLICAS).build();
    }

    // 컨슈머 처리 중 예외(DB 장애 등 일시적 오류)만 1초 간격 3회 재시도 후 {토픽}.DLT로 보낸다.
    // 업무 거절(REJECTED)은 예외 없이 정상 처리되는 메시지라 이 대상이 아니다
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        //에러가나면 메시지를 버리지않고 .DLT로 보관해주는 도구
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        //만약 메시지 처리에 실패하면 1초간격으로 최대 3번까지 재시도해도 실패하면 DLT토픽으로 넘김
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
