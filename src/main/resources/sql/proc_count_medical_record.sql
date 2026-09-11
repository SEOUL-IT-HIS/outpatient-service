-- 학습용: 진료ID(ENCOUNTER_ID)로 그 진료에 딸린 MEDICAL_RECORD 건수를 반환하는 프로시저
-- MyBatis CALLABLE statementType 연습용 스캐폴딩. Flyway/Liquibase가 없는 프로젝트라
-- 이 스크립트는 자동 실행되지 않으며, DB 계정(OUTPATIENT 스키마 소유 계정)으로 직접 실행해야 한다.

CREATE OR REPLACE PROCEDURE OUTPATIENT.PROC_COUNT_BY_ENCOUNTER (
    p_encounter_id IN  MEDICAL_RECORD.ENCOUNTER_ID%TYPE,
    p_record_count OUT NUMBER
) IS
BEGIN
    SELECT COUNT(*)
    INTO p_record_count
    FROM OUTPATIENT.MEDICAL_RECORD
    WHERE ENCOUNTER_ID = p_encounter_id;
END PROC_COUNT_BY_ENCOUNTER;
/
