package kr.co.seoulit.his.outpatientservice.common.client.patient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PAT 미기동 로컬용 stub. app.services.patient.stub-enabled=true 일 때만 활성.
 * 실제 연동 시 stub-enabled=false 로 두고 PatientClientImpl을 사용한다.
 */
@Component
@ConditionalOnProperty(name = "app.services.patient.stub-enabled", havingValue = "true")
public class StubPatientClient implements PatientClient {

    @Override
    public Optional<PatientApiDto.PatientSummary> getPatient(String patientId) {
        return Optional.of(sample(patientId));
    }

    @Override
    public Map<String, PatientApiDto.PatientSummary> getPatients(Collection<String> patientIds) {
        return patientIds.stream()
                .distinct()
                .map(this::sample)
                .collect(Collectors.toMap(PatientApiDto.PatientSummary::patientId, Function.identity(), (a, b) -> a));
    }

    private PatientApiDto.PatientSummary sample(String patientId) {
        String id = patientId != null ? patientId : "UNKNOWN";
        return new PatientApiDto.PatientSummary(id, "환자" + id);
    }
}
