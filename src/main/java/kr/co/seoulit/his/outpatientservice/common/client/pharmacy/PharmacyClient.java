package kr.co.seoulit.his.outpatientservice.common.client.pharmacy;

import java.util.List;

public interface PharmacyClient {
    List<PharmacyApiDto.Medication> searchMedication(String name);
}
