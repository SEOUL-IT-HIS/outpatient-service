package kr.co.seoulit.his.outpatientservice.common.client.admin;

public interface AuditLogClient {

    /**
     * 개인정보 열람 감사 로그 기록(최소 구현).
     * 감사 원장은 ADM 소유. 실패해도 업무 조회는 막지 않는다(로그만).
     */
    void recordPersonalInfoAccess(PersonalInfoAccessRequest request);
}
