package kr.co.seoulit.his.outpatientservice.prescription.repository;

import kr.co.seoulit.his.outpatientservice.prescription.entity.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, String> {
    // 처방 상세 조회
    List<PrescriptionItem> findByPrescriptionId(String prescriptionId);
}
