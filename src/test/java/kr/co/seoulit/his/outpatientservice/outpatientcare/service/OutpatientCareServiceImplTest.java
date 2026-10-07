package kr.co.seoulit.his.outpatientservice.outpatientcare.service;

import kr.co.seoulit.his.outpatientservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientClient;
import kr.co.seoulit.his.outpatientservice.common.client.reception.ReceptionEventDto.ReceptionData;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.EncounterDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordCreateDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.VisitHistoryDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordDto;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.MedicalRecord;
import kr.co.seoulit.his.outpatientservice.outpatientcare.mapper.OutpatientCareMapper;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.EncounterRepository;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.MedicalRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutpatientCareServiceImplTest {

    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private MedicalRecordRepository medicalRecordRepository;
    @Mock
    private OutpatientCareMapper outpatientCareMapper;
    @Mock
    private PatientClient patientClient;
    @Mock
    private CommonCodeCache commonCodeCache;

    @InjectMocks
    private OutpatientCareServiceImpl service;

    private ReceptionData receptionData(String visitType, String receptionType) {
        return new ReceptionData(
                "RCP-1", "P-1", "01", "EMP-1", LocalDate.of(2026, 10, 5),
                "RECEPTION", "기침", visitType, receptionType);
    }

    private Encounter savedEncounter() {
        ArgumentCaptor<Encounter> captor = ArgumentCaptor.forClass(Encounter.class);
        verify(encounterRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 접수_이벤트로_Encounter를_생성하면_초진재진과_예약당일_값을_저장한다() {
        when(encounterRepository.findByReceptionId("RCP-1")).thenReturn(Optional.empty());

        service.registerEncounter(receptionData("INITIAL", "WALK_IN"));

        Encounter saved = savedEncounter();
        assertThat(saved.getVisitType()).isEqualTo("INITIAL");
        assertThat(saved.getReceptionType()).isEqualTo("WALK_IN");
        assertThat(saved.getDoctorId()).isEqualTo("EMP-1");
        assertThat(saved.getStatus()).isEqualTo("WAITING");
    }

    @Test
    void 같은_접수ID로_이벤트가_다시_오면_초진재진과_예약당일_값을_새_값으로_갱신한다() {
        // 옛 이벤트로 만들어져 두 값이 null인 기존 Encounter
        Encounter existing = new Encounter();
        existing.setEncounterId("ENC-1");
        when(encounterRepository.findByReceptionId("RCP-1")).thenReturn(Optional.of(existing));

        service.registerEncounter(receptionData("REVISIT", "RESERVATION"));

        Encounter saved = savedEncounter();
        assertThat(saved.getEncounterId()).isEqualTo("ENC-1");
        assertThat(saved.getVisitType()).isEqualTo("REVISIT");
        assertThat(saved.getReceptionType()).isEqualTo("RESERVATION");
    }

    private MedicalRecord record(String recordId) {
        MedicalRecord record = new MedicalRecord();
        record.setRecordId(recordId);
        record.setStatus("COMPLETED");
        return record;
    }

    private MedicalRecordDto recordDto(String recordId, String departmentCode) {
        MedicalRecordDto dto = new MedicalRecordDto();
        dto.setRecordId(recordId);
        dto.setDepartmentCode(departmentCode);
        return dto;
    }

    @Test
    void 진료기록_상세조회는_진료과코드로_진료과명을_채운다() {
        MedicalRecord record = record("REC-1");
        when(medicalRecordRepository.findById("REC-1")).thenReturn(Optional.of(record));
        when(outpatientCareMapper.toMedicalRecordDto(record)).thenReturn(recordDto("REC-1", "01"));
        when(commonCodeCache.findCodeName("DEPT_CD", "01")).thenReturn(Optional.of("신경외과"));

        MedicalRecordDto result = service.getRecord("REC-1");

        assertThat(result.getDepartmentCode()).isEqualTo("01");
        assertThat(result.getDepartmentName()).isEqualTo("신경외과");
    }

    @Test
    void 진료기록_상세조회에서_진료과코드가_공통코드에_없으면_진료과명은_null이다() {
        MedicalRecord record = record("REC-1");
        when(medicalRecordRepository.findById("REC-1")).thenReturn(Optional.of(record));
        when(outpatientCareMapper.toMedicalRecordDto(record)).thenReturn(recordDto("REC-1", "99"));
        when(commonCodeCache.findCodeName("DEPT_CD", "99")).thenReturn(Optional.empty());

        MedicalRecordDto result = service.getRecord("REC-1");

        assertThat(result.getDepartmentName()).isNull();
    }

    @Test
    void 진료기록_목록조회는_각_기록의_진료과명을_채운다() {
        MedicalRecord first = record("REC-1");
        MedicalRecord second = record("REC-2");
        when(medicalRecordRepository.findByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(outpatientCareMapper.toRecordDtoList(List.of(first, second)))
                .thenReturn(List.of(recordDto("REC-1", "01"), recordDto("REC-2", "02")));
        when(commonCodeCache.findCodeName("DEPT_CD", "01")).thenReturn(Optional.of("신경외과"));
        when(commonCodeCache.findCodeName("DEPT_CD", "02")).thenReturn(Optional.of("내과"));

        List<MedicalRecordDto> result = service.getRecords(null);

        assertThat(result).extracting(MedicalRecordDto::getDepartmentName).containsExactly("신경외과", "내과");
    }

    @Test
    void 당일_환자목록은_접수_순으로_조회한_순서를_그대로_돌려준다() {
        Encounter first = new Encounter();
        first.setEncounterId("ENC-1");
        Encounter second = new Encounter();
        second.setEncounterId("ENC-2");
        List<Encounter> inReceptionOrder = List.of(first, second);

        EncounterDto firstDto = new EncounterDto();
        firstDto.setEncounterId("ENC-1");
        EncounterDto secondDto = new EncounterDto();
        secondDto.setEncounterId("ENC-2");

        when(encounterRepository.findByVisitDateOrderByCreatedAtAsc(LocalDate.now())).thenReturn(inReceptionOrder);
        when(outpatientCareMapper.toEncounterDtoList(inReceptionOrder)).thenReturn(List.of(firstDto, secondDto));

        List<EncounterDto> result = service.getTodayEncounters();

        assertThat(result).extracting(EncounterDto::getEncounterId).containsExactly("ENC-1", "ENC-2");
    }

    private ReceptionData cancelEvent() {
        return new ReceptionData("RCP-1", "P-1", "01", "EMP-1", LocalDate.of(2026, 10, 5),
                "CANCELLED", "기침", "REVISIT", "WALK_IN");
    }

    @Test
    void 접수_취소_이벤트가_오면_대기중인_진료건을_취소로_바꾼다() {
        Encounter existing = new Encounter();
        existing.setEncounterId("ENC-1");
        existing.setStatus("WAITING");
        when(encounterRepository.findByReceptionId("RCP-1")).thenReturn(Optional.of(existing));

        service.registerEncounter(cancelEvent());

        assertThat(savedEncounter().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void 이미_진료완료된_건은_접수_취소_이벤트가_와도_취소로_바꾸지_않는다() {
        Encounter existing = new Encounter();
        existing.setEncounterId("ENC-1");
        existing.setStatus("COMPLETED");
        when(encounterRepository.findByReceptionId("RCP-1")).thenReturn(Optional.of(existing));

        service.registerEncounter(cancelEvent());

        assertThat(savedEncounter().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void 접수_등록_이벤트가_다시_와도_진행된_상태를_대기중으로_되돌리지_않는다() {
        Encounter existing = new Encounter();
        existing.setEncounterId("ENC-1");
        existing.setStatus("COMPLETED");
        when(encounterRepository.findByReceptionId("RCP-1")).thenReturn(Optional.of(existing));

        service.registerEncounter(receptionData("INITIAL", "WALK_IN"));

        assertThat(savedEncounter().getStatus()).isEqualTo("COMPLETED");
    }

    private Encounter encounterWithStatus(String status) {
        Encounter encounter = new Encounter();
        encounter.setEncounterId("ENC-1");
        encounter.setStatus(status);
        when(encounterRepository.findById("ENC-1")).thenReturn(Optional.of(encounter));
        org.mockito.Mockito.lenient().when(medicalRecordRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(outpatientCareMapper.toMedicalRecordDto(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new kr.co.seoulit.his.outpatientservice.outpatientcare.dto.MedicalRecordDto());
        return encounter;
    }

    @Test
    void 진료기록을_저장하면_외래_진료건이_진료완료로_바뀐다() {
        Encounter encounter = encounterWithStatus("WAITING");
        MedicalRecordCreateDto request = new MedicalRecordCreateDto();
        request.setEncounterId("ENC-1");

        service.createRecord(request);

        assertThat(encounter.getStatus()).isEqualTo("COMPLETED");
        assertThat(encounter.getEndedAt()).isNotNull();
    }

    @Test
    void 이미_진료완료된_건에는_진료기록을_다시_저장할_수_없다() {
        encounterWithStatus("COMPLETED");
        MedicalRecordCreateDto request = new MedicalRecordCreateDto();
        request.setEncounterId("ENC-1");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createRecord(request))
                .isInstanceOf(kr.co.seoulit.his.outpatientservice.common.exception.BusinessException.class);

        org.mockito.Mockito.verify(medicalRecordRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 취소된_진료건에는_진료기록을_저장할_수_없다() {
        Encounter encounter = encounterWithStatus("CANCELLED");
        MedicalRecordCreateDto request = new MedicalRecordCreateDto();
        request.setEncounterId("ENC-1");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createRecord(request))
                .isInstanceOf(kr.co.seoulit.his.outpatientservice.common.exception.BusinessException.class);

        assertThat(encounter.getStatus()).isEqualTo("CANCELLED");
        org.mockito.Mockito.verify(medicalRecordRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 진료기록이_있으면_진료이력_있음과_최근_진료일을_돌려준다() {
        LocalDate lastVisit = LocalDate.of(2026, 10, 1);
        when(medicalRecordRepository.findLastVisitDate("P-1", LocalDate.of(1900, 1, 1))).thenReturn(lastVisit);

        VisitHistoryDto result = service.getVisitHistory("P-1", null, null);

        assertThat(result.isHasVisitRecord()).isTrue();
        assertThat(result.getLastVisitDate()).isEqualTo(lastVisit);
    }

    @Test
    void 진료기록이_없으면_진료이력_없음으로_돌려준다() {
        when(medicalRecordRepository.findLastVisitDate("P-1", LocalDate.of(1900, 1, 1))).thenReturn(null);

        VisitHistoryDto result = service.getVisitHistory("P-1", "", null);

        assertThat(result.isHasVisitRecord()).isFalse();
        assertThat(result.getLastVisitDate()).isNull();
    }

    @Test
    void 진료과를_주면_해당_진료과_기록만_조회하고_기간을_주면_그_기간_이후만_본다() {
        LocalDate from = LocalDate.now().minusDays(30);
        when(medicalRecordRepository.findLastVisitDateByDepartment("P-1", "02", from))
                .thenReturn(LocalDate.now().minusDays(3));

        VisitHistoryDto result = service.getVisitHistory("P-1", "02", 30);

        assertThat(result.isHasVisitRecord()).isTrue();
    }

    @Test
    void 환자ID가_없거나_기간이_0이하면_INVALID_INPUT_예외를_던진다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getVisitHistory(" ", null, null))
                .isInstanceOf(kr.co.seoulit.his.outpatientservice.common.exception.BusinessException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getVisitHistory("P-1", null, 0))
                .isInstanceOf(kr.co.seoulit.his.outpatientservice.common.exception.BusinessException.class);
    }

    @Test
    void 초진재진과_예약당일이_없는_옛_이벤트는_null로_저장한다() {
        when(encounterRepository.findByReceptionId("RCP-1")).thenReturn(Optional.empty());

        service.registerEncounter(receptionData(null, null));

        Encounter saved = savedEncounter();
        assertThat(saved.getVisitType()).isNull();
        assertThat(saved.getReceptionType()).isNull();
    }
}
