package kr.co.seoulit.his.outpatientservice.common.client.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ADM Provider 계약 DTO (카탈로그: GET /api/commonCodeGroup/list, GET /api/commonCodeItem/list?groupId=).
 * hisfrontend features/commonCode 의 필드명과 맞춤.
 */
public final class CommonCodeApiDto {

    private CommonCodeApiDto() {
    }

    @Schema(name = "CommonCodeGroup")
    public record CommonCodeGroup(
            String groupId,
            String groupCode,
            String groupName,
            String useYn
    ) {
    }

    @Schema(name = "CommonCodeItem")
    public record CommonCodeItem(
            String codeId,
            String groupId,
            String codeValue,
            String codeName,
            String useYn
    ) {
    }
}
