# 아키텍처 (1차 스프린트 기준)

## 현재 코드 범위

1차 스프린트(GR2-4, GR2-5)만 구현·스캐폴딩합니다. 처방코어·진단·원외처방 등은 **이후 스프린트에서 추가**합니다.

```text
outpatient-service
├── outpatientcare/   ← 환자·진료 목록 조회, 진료기록 조회
└── common/        ← ApiResponse, 예외, OpenAPI 설정
```

## 데이터 소유권 (참고)

| 데이터 | 소유(SoT) | 1차에서 |
| --- | --- | --- |
| 당일 진료(Encounter) 목록 | OPD | Provider (조회) |
| 진료기록 | OPD | Provider (조회) |
| 환자 원본 | PAT | Consumer(연계 예정) |
| 접수/대기 | RCP | Consumer(연계 예정) |

## 이후 스프린트에 넣을 것 (지금은 코드에 없음)

- 진료기록 등록/수정, 진단, 진단서, 진료 종료
- 처방코어(`/api/orders`) — Modular Monolith로 같은 서비스에 둘 예정
- 검사결과/PACS, 원외처방전, 협진/수술의뢰/입원요청
