package kr.co.seoulit.his.outpatientservice.prescription.repository;

import kr.co.seoulit.his.outpatientservice.prescription.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, String> {
    //처방 조회
    List<Prescription> findByPatientId(String patientId);
}
