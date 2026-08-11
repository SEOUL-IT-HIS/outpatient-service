package kr.co.seoulit.his.outpatientservice.outpatientcare.repository;

import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.MedicalRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, String> {

    List<MedicalRecord> findByEncounter_Id(String encounterId); // encounter 연관관계를 타고 encounter.id로 조회

    List<MedicalRecord> findByOrderByCreatedAtDesc(Pageable pageable);


}
