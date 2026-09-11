package kr.co.seoulit.his.outpatientservice.common.mybatis;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface ProcMapper {
    // Map 안에 진료id를 넣어서 전달하면 진료건수가 동일한 Map에 채워줌
    void countByEncounter(Map<String, Object> param);
}
