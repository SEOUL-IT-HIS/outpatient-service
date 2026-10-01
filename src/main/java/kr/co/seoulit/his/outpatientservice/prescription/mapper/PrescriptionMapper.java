package kr.co.seoulit.his.outpatientservice.prescription.mapper;

import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionDto;
import kr.co.seoulit.his.outpatientservice.prescription.dto.PrescriptionItemDto;
import kr.co.seoulit.his.outpatientservice.prescription.entity.ExamResultRef;
import kr.co.seoulit.his.outpatientservice.prescription.entity.Prescription;
import kr.co.seoulit.his.outpatientservice.prescription.entity.PrescriptionItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PrescriptionMapper {

    //처방 목록 조회
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "patientName", ignore = true)
    @Mapping(target = "priorityName", ignore = true)
    PrescriptionDto toPrescriptionDto(Prescription entity);
    List<PrescriptionDto> toPrescriptionDtoList(List<Prescription> list);

    //처방 상세 조회
    @Mapping(target = "resultDetails", ignore = true)
    PrescriptionItemDto toItemDto(PrescriptionItem entity);
    List<PrescriptionItemDto> toItemDtoList(List<PrescriptionItem> list);

    //검사결과 항목 (결과항목명은 서비스에서 공통코드로 채움)
    @Mapping(target = "detailName", ignore = true)
    PrescriptionItemDto.ResultDetail toResultDetail(ExamResultRef entity);
    List<PrescriptionItemDto.ResultDetail> toResultDetailList(List<ExamResultRef> list);
}
