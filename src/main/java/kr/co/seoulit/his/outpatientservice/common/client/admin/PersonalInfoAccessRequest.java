package kr.co.seoulit.his.outpatientservice.common.client.admin;

/**
 * ADM Provider: POST /api/admin/personalInfoAccessHistories
 */
public record PersonalInfoAccessRequest(
        String serviceCode,
        String accessorId,
        String resourceType,
        String resourceId,
        String action,
        String reason
) {
}
