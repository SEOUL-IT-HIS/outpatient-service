package kr.co.seoulit.his.outpatientservice.admission.dto;

import lombok.Getter;

import java.util.List;

// [입원요청 목록 조회 응답] GET /api/outpatient/admission-requests (page는 0부터 시작)
@Getter
public class AdmissionRequestPageDto {

    private final List<AdmissionRequestDto> content; // 현재 페이지 목록
    private final int page;                          // 현재 페이지 (0부터)
    private final int size;                          // 페이지 크기
    private final long totalElements;                // 조건에 맞는 전체 건수
    private final int totalPages;                    // 전체 페이지 수

    public AdmissionRequestPageDto(List<AdmissionRequestDto> content, int page, int size,
                                   long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }
}
