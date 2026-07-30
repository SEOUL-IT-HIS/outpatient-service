package kr.co.seoulit.his.outpatientservice.outpatientcare.repository;

import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, String> {

    // 당일(혹은 조회 조건의 특정 일자) 진료 목록 조회 - VISIT_DATE는 DATE 타입이라 LocalDate로 조회
    List<Encounter> findByVisitDate(LocalDate visitDate);

    // 같은 접수(receptionId)가 이미 배정되어 중복 등록되는 것을 막기 위한 확인용
    boolean existsByReceptionId(String receptionId);
}
