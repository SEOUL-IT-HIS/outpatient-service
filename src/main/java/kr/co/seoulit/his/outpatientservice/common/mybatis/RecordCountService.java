package kr.co.seoulit.his.outpatientservice.common.mybatis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordCountService {

    private final ProcMapper procMapper;

    public Map<String, Object> countByEncounterId(String encounterId) {
        //프로시저에게 넘겨줄 데이터를 담을 map객체 생성
        Map<String, Object> param = new HashMap<>();

        //map에 입력값(in)으로 쓰일 진료id보관
        param.put("encounterId", encounterId);

        log.info(" 프로시저 실행 전 ");
        log.info("진료ID: {}", param.get("encounterId"));

        //실행후 param map내부네 진료건수로 out결과값이 담김
        procMapper.countByEncounter(param);

        log.info(" 프로시저 실행 후 ");
        log.info("진료기록 건수: {}", param.get("recordCount"));

        //결과가 담긴 map을 컨트롤러로 반환
        return param;
    }
}