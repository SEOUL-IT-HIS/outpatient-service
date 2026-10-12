package kr.co.seoulit.his.outpatientservice.prescription.controller;

import kr.co.seoulit.his.outpatientservice.common.client.pharmacy.PharmacyApiDto;
import kr.co.seoulit.his.outpatientservice.prescription.service.PrescriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PrescriptionControllerTest {

    @Mock
    private PrescriptionService prescriptionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PrescriptionController(prescriptionService)).build();
    }

    private PharmacyApiDto.MedicationPage onePage() {
        PharmacyApiDto.Medication med = new PharmacyApiDto.Medication(
                1L, "모빅캡슐7.5밀리그램(멜록시캄)", null, null, null, null, null, null, null, "01", null, null, "653500890", null);
        return new PharmacyApiDto.MedicationPage(List.of(med), 1, 1, 0, 100, true, true);
    }

    // /medications 가 /{prescriptionId} 로 잘못 라우팅되지 않는지 함께 확인한다
    @Test
    void 약품_목록_요청은_처방_상세가_아니라_약품_목록으로_연결된다() throws Exception {
        when(prescriptionService.listMedications(null, 0, 100)).thenReturn(onePage());

        mockMvc.perform(get("/api/outpatient/prescriptions/medications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].ediCode").value("653500890"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.last").value(true));

        verify(prescriptionService, never()).getPrescription(anyString());
    }

    @Test
    void 이름과_페이지_파라미터를_서비스에_전달한다() throws Exception {
        when(prescriptionService.listMedications("모빅", 1, 20)).thenReturn(onePage());

        mockMvc.perform(get("/api/outpatient/prescriptions/medications")
                        .param("name", "모빅").param("page", "1").param("size", "20"))
                .andExpect(status().isOk());

        verify(prescriptionService).listMedications("모빅", 1, 20);
    }
}
