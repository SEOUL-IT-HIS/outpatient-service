package kr.co.seoulit.his.outpatientservice.admission.controller;

import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestDto;
import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestPageDto;
import kr.co.seoulit.his.outpatientservice.admission.service.AdmissionRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdmissionRequestControllerTest {

    @Mock
    private AdmissionRequestService admissionRequestService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdmissionRequestController(admissionRequestService)).build();
    }

    private AdmissionRequestPageDto onePage() {
        AdmissionRequestDto dto = new AdmissionRequestDto();
        dto.setAdmissionRequestId("REQ-1");
        dto.setPatientName("홍길동");
        dto.setRequestStatus("02");
        return new AdmissionRequestPageDto(List.of(dto), 1, 10, 11, 2);
    }

    @Test
    void 입원요청_목록은_상태와_페이지_조건을_서비스에_전달한다() throws Exception {
        when(admissionRequestService.getAdmissionRequestPage("02", 1, 10)).thenReturn(onePage());

        mockMvc.perform(get("/api/outpatient/admission-requests")
                        .param("requestStatus", "02").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].admissionRequestId").value("REQ-1"))
                .andExpect(jsonPath("$.data.content[0].patientName").value("홍길동"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void 조건을_생략하면_전체_첫_페이지_20건으로_조회한다() throws Exception {
        when(admissionRequestService.getAdmissionRequestPage(null, 0, 20)).thenReturn(onePage());

        mockMvc.perform(get("/api/outpatient/admission-requests"))
                .andExpect(status().isOk());

        verify(admissionRequestService).getAdmissionRequestPage(null, 0, 20);
    }
}
