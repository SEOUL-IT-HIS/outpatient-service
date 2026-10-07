package kr.co.seoulit.his.outpatientservice.prescription.repository;

import kr.co.seoulit.his.outpatientservice.prescription.entity.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, String> {
    // 처방 상세 조회
    List<PrescriptionItem> findByPrescriptionId(String prescriptionId);

    // 처방 목록의 검사 전송상태 요약용 (N+1 방지 — 여러 처방의 항목을 한 번에 조회)
    List<PrescriptionItem> findByPrescriptionIdIn(List<String> prescriptionIds);
}
