package kr.co.seoulit.his.outpatientservice.outpatientcare.controller;

import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestDto;
import kr.co.seoulit.his.outpatientservice.admission.service.AdmissionRequestService;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.service.OutpatientCareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EncounterControllerTest {

    @Mock
    private OutpatientCareService outpatientCareService;
    @Mock
    private AdmissionRequestService admissionRequestService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EncounterController(outpatientCareService, admissionRequestService))
                .build();
    }

    private EncounterDto encounter(String encounterId, String patientName) {
        EncounterDto dto = new EncounterDto();
        dto.setEncounterId(encounterId);
        dto.setPatientName(patientName);
        dto.setStatus("COMPLETED");
        return dto;
    }

    private AdmissionRequestDto admissionRequest(String status, String statusName) {
        AdmissionRequestDto dto = new AdmissionRequestDto();
        dto.setRequestStatus(status);
        dto.setRequestStatusName(statusName);
        return dto;
    }

    @Test
    void 환자_목록에_진료별_최신_입원요청_상태가_담긴다() throws Exception {
        when(outpatientCareService.getTodayEncounters())
                .thenReturn(List.of(encounter("ENC-1", "홍길동"), encounter("ENC-2", "김철수")));
        when(admissionRequestService.getLatestAdmissionRequests(anyCollection()))
                .thenReturn(Map.of("ENC-1", admissionRequest("03", "Rejected")));

        mockMvc.perform(get("/api/outpatient/encounters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].admissionRequestStatus").value("03"))
                .andExpect(jsonPath("$.data[0].admissionRequestStatusName").value("Rejected"))
                // 입원요청이 없는 환자는 상태가 null이다
                .andExpect(jsonPath("$.data[1].admissionRequestStatus").doesNotExist())
                .andExpect(jsonPath("$.data[1].patientName").value("김철수"));
    }

    @Test
    void 입원요청_조회에_실패해도_환자_목록은_그대로_내려준다() throws Exception {
        when(outpatientCareService.getTodayEncounters()).thenReturn(List.of(encounter("ENC-1", "홍길동")));
        when(admissionRequestService.getLatestAdmissionRequests(anyCollection()))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(get("/api/outpatient/encounters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].patientName").value("홍길동"))
                .andExpect(jsonPath("$.data[0].admissionRequestStatus").doesNotExist());
    }

    @Test
    void 환자가_없으면_입원요청을_조회하지_않는다() throws Exception {
        when(outpatientCareService.getTodayEncounters()).thenReturn(List.of());

        mockMvc.perform(get("/api/outpatient/encounters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(admissionRequestService, never()).getLatestAdmissionRequests(anyCollection());
    }
}
