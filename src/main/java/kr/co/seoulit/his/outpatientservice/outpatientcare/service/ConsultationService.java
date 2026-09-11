package kr.co.seoulit.his.outpatientservice.outpatientcare.service;

import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.ConsultationSaveRequestDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.ConsultationSaveResponseDto;

public interface ConsultationService {

    // 진료기록 + 처방/처방아이템 저장, 검사실 전송은 여기 포함하지 않음 - 커밋 후 컨트롤러가 별도로 처리
    ConsultationSaveResponseDto saveRecordAndPrescription(String encounterId, ConsultationSaveRequestDto request);
}
