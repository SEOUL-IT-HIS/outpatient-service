package kr.co.seoulit.his.outpatientservice.common.cache;

import kr.co.seoulit.his.outpatientservice.common.client.admin.CommonCodeApiDto.CommonCodeGroup;
import kr.co.seoulit.his.outpatientservice.common.client.admin.CommonCodeApiDto.CommonCodeItem;
import kr.co.seoulit.his.outpatientservice.common.client.admin.CommonCodeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 공통코드 로컬 캐시.
 * 서버 구동 시 ADM에서 전체 그룹/항목을 한 번에 받아 메모리에 적재한다(요청마다 조회 금지).
 * ADM 장애로 적재에 실패해도 앱 구동은 막지 않는다(캐시가 비어있는 채로 기동, 로그만 남김).
 */
@Slf4j
@Component
public class CommonCodeCache implements ApplicationRunner {

    private final CommonCodeClient commonCodeClient;
    private volatile Map<String, List<CommonCodeItem>> itemsByGroupCode = Map.of();

    public CommonCodeCache(CommonCodeClient commonCodeClient) {
        this.commonCodeClient = commonCodeClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        load();
    }

    /** 그룹코드로 사용중(Y) 항목 목록 조회. */
    public List<CommonCodeItem> getItems(String groupCode) {
        return itemsByGroupCode.getOrDefault(groupCode, List.of());
    }

    /** 그룹코드+코드값으로 코드명 조회. */
    public Optional<String> findCodeName(String groupCode, String codeValue) {
        return getItems(groupCode).stream()
                .filter(item -> item.codeValue().equals(codeValue))
                .map(CommonCodeItem::codeName)
                .findFirst();
    }

    private void load() {
        try {
            List<CommonCodeGroup> groups = commonCodeClient.getGroups().stream()
                    .filter(group -> "Y".equals(group.useYn()))
                    .toList();

            Map<String, List<CommonCodeItem>> loaded = new ConcurrentHashMap<>();
            for (CommonCodeGroup group : groups) {
                List<CommonCodeItem> items = commonCodeClient.getItems(group.groupId()).stream()
                        .filter(item -> "Y".equals(item.useYn()))
                        .collect(Collectors.toList());
                loaded.put(group.groupCode(), items);
            }

            this.itemsByGroupCode = loaded;
            log.info("[ADM] 공통코드 캐시 적재 완료 groupCount={}, itemCount={}",
                    loaded.size(), loaded.values().stream().mapToInt(List::size).sum());
        } catch (RuntimeException ex) {
            log.warn("[ADM] 공통코드 캐시 적재 실패, 빈 캐시로 기동합니다. message={}", ex.getMessage());
        }
    }
}
