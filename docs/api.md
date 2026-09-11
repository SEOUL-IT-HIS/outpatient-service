# API 목록

## 1차 스프린트 (코드에 존재)

| Jira | Method | Endpoint | 설명 |
| --- | --- | --- | --- |
| GR2-4 | GET | `/api/outpatient/encounters` | 당일 진료 환자 목록 (page/size/sort, PAT batch-query 연계) |
| GR2-5 | GET | `/api/outpatient/records?encounterId=` | 진료기록 목록 (encounter 기준) |
| GR2-5 | GET | `/api/outpatient/records/{recordId}` | 진료기록 상세 |

## 외부 Consumer (카탈로그)

| 대상 | API | 용도 |
| --- | --- | --- |
| PAT | `POST /api/patient/batch` | 목록 환자명/번호 일괄 조회 (N+1 방지) |
| PAT | `GET /api/patient/{patientId}` | 단건 환자 표시 정보 |
| ADM | `POST /api/admin/personalInfoAccessHistories` | 개인정보 열람 감사(최소, 실패해도 업무 조회 유지) |
| ADM | `GET /api/commonCodeGroup/list` | 공통코드 그룹 전체 목록 (서버 구동 시 1회, `CommonCodeCache`가 적재) |
| ADM | `GET /api/commonCodeItem/list?groupId=` | 그룹별 공통코드 항목 목록 (서버 구동 시 1회, `CommonCodeCache`가 적재) |

로컬 기본: `app.services.patient.stub-enabled=true`, `admin.stub-enabled=true`  
실제 연동: 환경변수 `PATIENT_STUB_ENABLED=false`, `ADMIN_STUB_ENABLED=false` + base-url 설정.

## 이후 스프린트 (예정 — 코드 없음)

전체 로드맵은 `GR2_외래관리_API목록.xlsx`, `GR2_처방코어_API목록.xlsx` 참고.
