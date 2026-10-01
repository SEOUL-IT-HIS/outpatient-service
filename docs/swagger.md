# Swagger / OpenAPI

## 접근

```bash
./gradlew bootRun
```

| 대상 | URL |
| --- | --- |
| Swagger UI | `http://localhost:8088/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8088/api-docs` |

## 설정 위치

- 의존성: `build.gradle` → `springdoc-openapi-starter-webmvc-ui`
- 경로: `application.yml` → `springdoc:`
- 문서 메타: `common/config/OpenApiConfig.java`
- API 설명: 컨트롤러 `@Tag`/`@Operation`, DTO `@Schema`

현재 UI에는 **1차 스프린트 API 2개**만 표시됩니다.
