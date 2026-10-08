package kr.co.seoulit.his.outpatientservice.outpatientcare.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

//[접수 서비스용 응답] GET /api/outpatient/encounters/by-reception/{receptionId}/status
// 접수가 취소 직전에 "이 접수가 진료 중인가"를 확인할 때 쓴다. 환자정보/진료내용은 담지 않는다.
@Getter
@AllArgsConstructor
public class EncounterStatusDto {

    private String receptionId;   // 접수 ID
    private String encounterId;   // 외래진료 ID
    private String status;        // 진료 상태 (WAITING, IN_PROGRESS, COMPLETED, CANCELLED)
    private boolean inProgress;   // 진료 중 여부 (true면 접수에서 취소할 수 없다)
}
