package kr.co.seoulit.his.outpatientservice.common.client.reception;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.seoulit.his.outpatientservice.common.client.reception.ReceptionEventDto.ReceptionRegisteredEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceptionEventDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void RCP_접수_이벤트_1_1의_초진재진과_예약당일_필드를_읽는다() throws Exception {
        String payload = """
                {
                  "eventId": "E-1",
                  "eventType": "ReceptionRegistered",
                  "version": "1.1",
                  "occurredAt": "2026-10-05T10:30:00+09:00",
                  "source": "RCP",
                  "data": {
                    "receptionId": "RCP-1",
                    "patientId": "P-1",
                    "departmentCode": "01",
                    "doctorId": "EMP-1",
                    "visitDate": "2026-10-05",
                    "status": "RECEPTION",
                    "visitReason": "기침",
                    "visitType": "INITIAL",
                    "receptionType": "WALK_IN"
                  }
                }
                """;

        ReceptionRegisteredEvent event = objectMapper.readValue(payload, ReceptionRegisteredEvent.class);

        assertThat(event.data().doctorId()).isEqualTo("EMP-1");
        assertThat(event.data().visitType()).isEqualTo("INITIAL");
        assertThat(event.data().receptionType()).isEqualTo("WALK_IN");
    }

    @Test
    void 두_필드가_없는_1_0_이벤트와_모르는_필드가_섞여도_에러없이_읽는다() throws Exception {
        String payload = """
                {
                  "eventId": "E-0",
                  "eventType": "ReceptionRegistered",
                  "version": "1.0",
                  "occurredAt": "2026-10-05T10:30:00+09:00",
                  "source": "RCP",
                  "data": {
                    "receptionId": "RCP-0",
                    "patientId": "P-1",
                    "departmentCode": "01",
                    "doctorId": "EMP-1",
                    "visitDate": "2026-10-05",
                    "status": "RECEPTION",
                    "visitReason": "기침",
                    "someFutureField": "x"
                  }
                }
                """;

        ReceptionRegisteredEvent event = objectMapper.readValue(payload, ReceptionRegisteredEvent.class);

        assertThat(event.data().receptionId()).isEqualTo("RCP-0");
        assertThat(event.data().visitType()).isNull();
        assertThat(event.data().receptionType()).isNull();
    }
}
