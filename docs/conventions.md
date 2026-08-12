# 백엔드 컨벤션 (개발표준가이드 발췌 + 이 서비스 적용 상태)

개발표준가이드 v3.3는 프론트 중심이라, 여기서는 **백엔드가 반드시 지켜야 하는 항목**만 발췌하고
이 서비스의 현재 적용 상태를 표기합니다.

## 1. 공통 응답 포맷 (가이드 11.3)

가이드 표준:

```json
{ "code": 200, "message": "SUCCESS", "data": {} }
```

현재 구현 (`common/ApiResponse.java`):

```json
{ "code": "SUCCESS", "message": "OK", "data": {} }
```

> ⚠️ **협의 필요(불일치)**: 가이드는 `code`를 숫자(HTTP status 또는 서비스 정의 코드), `message`를 `"SUCCESS"`로 예시했는데,
> 현재 성공 응답은 `code="SUCCESS"`, `message="OK"`입니다. FE 공통 axios interceptor가 `code`로 성공/실패를 판정하므로,
> **성공 code 값(200 vs "SUCCESS")을 FE와 먼저 합의**해야 합니다. 에러 응답은 이미 `code`에 에러코드(`OPD001` 등)가 실려
> 가이드 15장 메시지 코드 체계와 맞습니다.

에러 응답 (`GlobalExceptionHandler`):

```json
{ "code": "OPD001", "message": "입력값이 올바르지 않습니다.", "data": null }
```

## 2. 메시지 / 에러 코드 (가이드 15.2)

- 체계: `{서비스코드 3자리}{일련번호 3자리}` → 외래는 `OPD001`~`OPD999`.
- 정의 위치: `common/exception/ErrorCode.java`.

| 코드 | 의미 | HTTP |
| --- | --- | --- |
| OPD001 | 입력값 오류 | 400 |
| OPD002 | 인증 필요 | 401 |
| OPD003 | 권한 없음 | 403 |
| OPD004 | 찾을 수 없음 | 404 |
| OPD005 | 중복/이미 처리됨 | 409 |
| OPD999 | 시스템 오류 | 500 |

- 사용자 노출 메시지와 개발자 로그를 분리한다. 스택트레이스를 응답 body에 넣지 않는다. (가이드 15.1)
- 처방코어 BC는 향후 분리 가능성이 있으므로, 코어 전용 에러가 필요해지면 `ORD###` 같은 별도 코드군을 검토한다.

## 3. REST 메서드 (가이드 11.2 / 21.8)

| 기능 | 방식 |
| --- | --- |
| 조회 | GET |
| 등록 | POST |
| 수정 | PUT / PATCH |
| 삭제 | **상태 변경 권장** (물리 삭제 지양) |

- 처방 취소는 DELETE가 아니라 `PATCH /cancel` + 사유 기록. (가이드 21.6)

## 4. DB 네이밍 / 데이터타입 (가이드 14, Oracle 단일)

- 테이블: `UPPER_SNAKE_CASE` (예: `OPD_ENCOUNTER`, `ORD_ORDER`)
- 컬럼: `lower_snake_case`
- PK: `{테이블명}_id`, FK: `{참조테이블명}_id`
- 모든 테이블 `created_at`, `updated_at` 공통 (`common/entity/BaseEntity.java`가 JPA Auditing으로 제공)

컬럼 접미사:

| 접미사 | 의미 | Oracle 타입 |
| --- | --- | --- |
| `_id` | 식별자(PK/FK) | `VARCHAR2(36)` 또는 `NUMBER` |
| `_no` | 채번 업무번호 | `VARCHAR2(20)` |
| `_cd` / `_code` | 코드성(코드마스터 참조) | `VARCHAR2` |
| `_yn` | 여부 | `CHAR(1)` `'Y'`/`'N'` |
| `_dt` | 날짜 | `DATE` |
| `_at` | 일시 | `TIMESTAMP` |
| `_nm` / `_name` | 명칭 | `VARCHAR2(100)` |

> ⚠️ 현재 DTO는 API 계약(camelCase) 기준입니다. MyBatis는 `map-underscore-to-camel-case: true`로
> `snake_case` 컬럼 ↔ camelCase 필드를 자동 매핑하므로, 엔티티/매퍼 추가 시 컬럼명은 위 규칙을 따릅니다.

## 5. 데이터 소유권 / 스냅샷 금지 (가이드 14.1 / 21.2)

- 타 서비스 소유 데이터(환자명, 처방의명 등)는 **값 복제 금지**, 참조 식별자만 저장하고 표시 시점에 API 조회.
- 다건 표시는 **배치 조회 API** 사용(N+1 방지).
- 예외: 그 화면에서 직접 입력·확정된 값(서명자명 등)은 원본이므로 저장한다.

## 6. 주석 (가이드 16)

- "무엇을"이 아니라 **"왜"** 를 쓴다. 코드 반복 주석 금지.
- 임시 처리/예외 로직에는 사유 + TODO. (현재 `*ServiceImpl`의 mock 구현부에 "스캐폴드 단계" 주석이 그 예)

## 7. 커밋 메시지 (가이드 9.1)

```text
(지라코드)
1. (내용) 기능추가
2. (내용) 기능수정
```
