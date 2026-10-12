package kr.co.seoulit.his.outpatientservice.admission.service;

import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestCreateDto;
import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestDto;
import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestPageDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AdmissionRequestService {

    // 입원 요청 등록 (저장만 한다. 병동 전송은 커밋 이후 dispatchAdmissionRequest로 따로 한다)
    AdmissionRequestDto createAdmissionRequest(String encounterId, AdmissionRequestCreateDto request);

    // 입원 요청을 병동으로 발행하고 전송상태를 반영한다 (발행 실패 건 재전송에도 쓴다)
    AdmissionRequestDto dispatchAdmissionRequest(String admissionRequestId);

    // 진료별 입원요청 목록 조회
    List<AdmissionRequestDto> getAdmissionRequests(String encounterId);

    // 입원요청 목록 조회 (모든 의사의 요청, 요청일시 최신순, 요청상태 조건은 선택)
    AdmissionRequestPageDto getAdmissionRequestPage(String requestStatus, int page, int size);

    // 여러 진료의 최신 입원요청을 진료 ID별로 한 번에 조회한다 (환자 목록 배지용, 요청이 없는 진료는 map에 없다)
    Map<String, AdmissionRequestDto> getLatestAdmissionRequests(Collection<String> encounterIds);

    // 병동의 결과 회신(병상 배정/거부)을 반영한다 — AdmissionResultListener가 호출
    void applyAdmissionResult(String admissionRequestId, String status, String admissionId,
                              String assignedWardCode, String rejectReason);
}
