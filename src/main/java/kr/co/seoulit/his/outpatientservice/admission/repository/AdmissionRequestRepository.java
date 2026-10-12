package kr.co.seoulit.his.outpatientservice.admission.repository;

import kr.co.seoulit.his.outpatientservice.admission.entity.AdmissionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AdmissionRequestRepository extends JpaRepository<AdmissionRequest, String> {

    // 진료별 입원요청 목록 (최신 요청이 먼저)
    List<AdmissionRequest> findByEncounterIdOrderByRequestedAtDesc(String encounterId);

    // 요청상태별 입원요청 페이지 조회 (상태 조건이 없으면 findAll(Pageable)을 쓴다)
    Page<AdmissionRequest> findByRequestStatus(String requestStatus, Pageable pageable);

    // 여러 진료의 입원요청을 한 번에 조회 (환자 목록 배지용, N+1 방지)
    List<AdmissionRequest> findByEncounterIdIn(Collection<String> encounterIds);

    // 같은 진료에 진행 중(요청됨/병상 배정)인 요청이 있는지 — 재요청 차단용
    boolean existsByEncounterIdAndRequestStatusIn(String encounterId, Collection<String> requestStatuses);
}
