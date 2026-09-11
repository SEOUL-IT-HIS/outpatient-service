package kr.co.seoulit.his.outpatientservice.common.client.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ADM 미기동 로컬용 stub. app.services.admin.stub-enabled=true 일 때만 활성.
 * 실제 연동 시 stub-enabled=false 로 두고 CommonCodeClientImpl을 사용한다.
 */
@Component
@ConditionalOnProperty(name = "app.services.admin.stub-enabled", havingValue = "true")
public class StubCommonCodeClient implements CommonCodeClient {

    @Override
    public List<CommonCodeApiDto.CommonCodeGroup> getGroups() {
        return List.of(
                new CommonCodeApiDto.CommonCodeGroup("1", "SAMPLE_CD", "샘플 코드그룹", "Y")
        );
    }

    @Override
    public List<CommonCodeApiDto.CommonCodeItem> getItems(String groupId) {
        return List.of(
                new CommonCodeApiDto.CommonCodeItem("1", groupId, "01", "샘플항목1", "Y"),
                new CommonCodeApiDto.CommonCodeItem("2", groupId, "02", "샘플항목2", "Y")
        );
    }
}
