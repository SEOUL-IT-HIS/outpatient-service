package kr.co.seoulit.his.outpatientservice.prescription.repository;

import kr.co.seoulit.his.outpatientservice.prescription.entity.ExamResultRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ExamResultRefRepository extends JpaRepository<ExamResultRef, String> {
    // 처방 상세 아이템별 검사결과 항목 조회 (여러 아이템을 한 번에, 결과 순번 순)
    List<ExamResultRef> findByItemIdInOrderBySeqAsc(Collection<String> itemIds);

    // 해당 아이템에 이미 검사결과가 저장돼 있는지
    boolean existsByItemId(String itemId);
}
