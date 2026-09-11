package kr.co.seoulit.his.outpatientservice.outpatientcare.repository;

import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, String> {

    //진료기록 등록
    Optional<Encounter> findByReceptionId(String receptionId);

    //당일 환자 조회
    List<Encounter> findByVisitDate(LocalDate visitDate);
}
