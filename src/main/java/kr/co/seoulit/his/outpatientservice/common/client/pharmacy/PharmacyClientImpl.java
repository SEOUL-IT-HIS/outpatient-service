package kr.co.seoulit.his.outpatientservice.common.client.pharmacy;

import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.services.pharmacy.stub-enabled", havingValue = "false", matchIfMissing = true)
public class PharmacyClientImpl implements PharmacyClient {

    private final RestClient pharmacyRestClient;

    public PharmacyClientImpl(@Qualifier("pharmacyRestClient") RestClient pharmacyRestClient) {
        this.pharmacyRestClient = pharmacyRestClient;
    }

    @Override
    public List<PharmacyApiDto.Medication> searchMedication(String name) {
        try {
            PharmacyApiDto.MedicationSearchResponse response = pharmacyRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/pharmacy/medications")
                            .queryParam("name", name)
                            .build())
                    .retrieve()
                    .body(PharmacyApiDto.MedicationSearchResponse.class);

            if (response == null) {
                return List.of();
            }
            return response.data();
        } catch (RestClientException ex) {
            log.warn("[PHM] searchMedication failed name={}, message={}", name, ex.getMessage());
            // 약품 검색에 실패했습니다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to search medications.");
        }
    }

    // ediCodeOnly 파라미터는 일부러 보내지 않는다 — 약제의 ediCodeOnly=true가 항상 0건을 돌려주는 문제(2026-10-07 확인)가 있어
    // 코드 없는 약은 서비스 계층에서 거른다.
    @Override
    public PharmacyApiDto.MedicationPage listMedications(String name, int page, int size) {
        try {
            PharmacyApiDto.MedicationPageResponse response = pharmacyRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/pharmacy/medications/page");
                        if (name != null && !name.isBlank()) {
                            uriBuilder.queryParam("name", name);
                        }
                        return uriBuilder.queryParam("page", page).queryParam("size", size).build();
                    })
                    .retrieve()
                    .body(PharmacyApiDto.MedicationPageResponse.class);

            if (response == null || response.data() == null) {
                return new PharmacyApiDto.MedicationPage(List.of(), 0, 0, page, size, true, true);
            }
            return response.data();
        } catch (RestClientException ex) {
            log.warn("[PHM] listMedications failed name={}, page={}, message={}", name, page, ex.getMessage());
            // 약품 목록 조회에 실패했습니다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Failed to list medications.");
        }
    }
}