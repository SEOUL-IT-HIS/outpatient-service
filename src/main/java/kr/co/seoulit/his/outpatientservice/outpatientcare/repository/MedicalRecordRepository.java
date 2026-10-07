package kr.co.seoulit.his.outpatientservice.outpatientcare.repository;

import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.MedicalRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, String> {

    List<MedicalRecord> findByOrderByCreatedAtDesc(Pageable pageable);

    // 환자의 가장 최근 진료일 (비활성화(DELETED)된 기록은 제외, 진료과 무관). 기록이 없으면 null
    @Query("select max(e.visitDate) from MedicalRecord r join r.encounter e "
            + "where e.patientId = :patientId and r.status <> 'DELETED' and e.visitDate >= :from")
    LocalDate findLastVisitDate(@Param("patientId") String patientId, @Param("from") LocalDate from);

    // 위와 같고 특정 진료과 기록만 대상으로 한다
    @Query("select max(e.visitDate) from MedicalRecord r join r.encounter e "
            + "where e.patientId = :patientId and e.departmentCode = :departmentCode "
            + "and r.status <> 'DELETED' and e.visitDate >= :from")
    LocalDate findLastVisitDateByDepartment(@Param("patientId") String patientId,
                                            @Param("departmentCode") String departmentCode,
                                            @Param("from") LocalDate from);
}
