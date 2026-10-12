package kr.co.seoulit.his.outpatientservice.admission.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.seoulit.his.outpatientservice.admission.service.AdmissionRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionResultListenerTest {

    @Mock
    private AdmissionRequestService admissionRequestService;

    private AdmissionResultListener listener;

    @BeforeEach
    void setUp() {
        listener = new AdmissionResultListener(admissionRequestService, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void 병상_배정_회신을_서비스에_전달한다() throws Exception {
        // occurredAt 같은 쓰지 않는 필드와 병동이 추가한 모르는 필드는 무시한다
        String payload = """
                {
                  "eventId": "EVT-1",
                  "eventType": "AdmissionBedAssigned",
                  "version": "1.0",
                  "occurredAt": "2026-10-12T10:29:00",
                  "source": "03",
                  "unknownField": "ignored",
                  "data": {
                    "requestId": "REQ-1",
                    "status": "02",
                    "admissionId": "A001",
                    "assignedWardCode": "WARD-3"
                  }
                }
                """;

        listener.onAdmissionResulted(payload);

        verify(admissionRequestService).applyAdmissionResult("REQ-1", "02", "A001", "WARD-3", null);
    }

    @Test
    void 거부_회신을_서비스에_전달한다() throws Exception {
        String payload = """
                {
                  "eventId": "EVT-2",
                  "eventType": "AdmissionRejected",
                  "version": "1.0",
                  "occurredAt": "2026-10-12T10:30:00",
                  "source": "03",
                  "data": {
                    "requestId": "REQ-2",
                    "status": "03",
                    "rejectReason": "Patient already has an active admission"
                  }
                }
                """;

        listener.onAdmissionResulted(payload);

        verify(admissionRequestService).applyAdmissionResult(
                "REQ-2", "03", null, null, "Patient already has an active admission");
    }

    @Test
    void data가_없는_메시지는_무시한다() throws Exception {
        listener.onAdmissionResulted("{\"eventId\":\"EVT-3\",\"eventType\":\"AdmissionRejected\"}");

        verifyNoInteractions(admissionRequestService);
    }
}
