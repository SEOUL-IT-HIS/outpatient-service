package kr.co.seoulit.his.outpatientservice.common.client.pharmacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
    }
}