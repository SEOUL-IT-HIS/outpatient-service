# outpatient-service (외래관리 / OPD)

HIS MSA 중 **외래관리 서비스**. 현재 코드는 **1차 스프린트(GR2)** 범위만 포함합니다.

| 항목 | 값 |
| --- | --- |
| 서비스 코드 | `OPD` (사내 코드 `GR2`) |
| 현재 스프린트 | 1차 (2026-07-16 ~ 07-24) |
| 포함 업무 | GR2-4 환자 조회, GR2-5 진료기록 조회 |
| API 문서 | springdoc-openapi (Swagger UI) |

## 문서 목록

| 문서 | 내용 |
| --- | --- |
| [architecture.md](./architecture.md) | 서비스 경계·소유권 요약 |
| [api.md](./api.md) | 1차 스프린트 API + 이후 예정 |
| [swagger.md](./swagger.md) | Swagger UI 접근 방법 |
| [conventions.md](./conventions.md) | 응답 포맷·에러코드·DB 표준 |

## 빠른 시작

```bash
./gradlew bootRun
# Swagger UI
http://localhost:8080/swagger-ui.html
```
