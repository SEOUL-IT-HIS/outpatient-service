package kr.co.seoulit.his.outpatientservice.common.client.admin;

import java.util.List;

public interface CommonCodeClient {

    /** 공통코드 그룹 전체 목록. */
    List<CommonCodeApiDto.CommonCodeGroup> getGroups();

    /** 그룹 하위 코드 항목 목록. */
    List<CommonCodeApiDto.CommonCodeItem> getItems(String groupId);
}
