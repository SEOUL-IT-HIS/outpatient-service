package kr.co.seoulit.his.outpatientservice.common.client.patient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PatientClient {

    Optional<PatientApiDto.PatientSummary> getPatient(String patientId);

    Map<String, PatientApiDto.PatientSummary> getPatients(Collection<String> patientIds);
}
