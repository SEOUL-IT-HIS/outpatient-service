package kr.co.seoulit.his.outpatientservice.admission.mapper;

import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestDto;
import kr.co.seoulit.his.outpatientservice.admission.entity.AdmissionRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AdmissionRequestMapper {

    // 코드명(진료과명/상태명/병동명)은 서비스에서 ADM 공통코드로, 환자명은 PAT 조회로 채움
    @Mapping(target = "patientName", ignore = true)
    @Mapping(target = "admissionDeptName", ignore = true)
    @Mapping(target = "requestStatusName", ignore = true)
    @Mapping(target = "assignedWardName", ignore = true)
    AdmissionRequestDto toDto(AdmissionRequest entity);
    List<AdmissionRequestDto> toDtoList(List<AdmissionRequest> list);
}
