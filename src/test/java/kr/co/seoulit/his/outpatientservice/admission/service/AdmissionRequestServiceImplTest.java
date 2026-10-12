package kr.co.seoulit.his.outpatientservice.admission.service;

import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestCreateDto;
import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestDto;
import kr.co.seoulit.his.outpatientservice.admission.dto.AdmissionRequestPageDto;
import kr.co.seoulit.his.outpatientservice.admission.entity.AdmissionRequest;
import kr.co.seoulit.his.outpatientservice.admission.mapper.AdmissionRequestMapper;
import kr.co.seoulit.his.outpatientservice.admission.mapper.AdmissionRequestMapperImpl;
import kr.co.seoulit.his.outpatientservice.admission.repository.AdmissionRequestRepository;
import kr.co.seoulit.his.outpatientservice.common.cache.CommonCodeCache;
import kr.co.seoulit.his.outpatientservice.common.client.admission.AdmissionEventDto;
import kr.co.seoulit.his.outpatientservice.common.client.admission.AdmissionRequestPublisher;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientApiDto;
import kr.co.seoulit.his.outpatientservice.common.client.patient.PatientClient;
import kr.co.seoulit.his.outpatientservice.common.exception.BusinessException;
import kr.co.seoulit.his.outpatientservice.common.exception.ErrorCode;
import kr.co.seoulit.his.outpatientservice.outpatientcare.entity.Encounter;
import kr.co.seoulit.his.outpatientservice.outpatientcare.repository.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionRequestServiceImplTest {

    @Mock
    private AdmissionRequestRepository admissionRequestRepository;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private AdmissionRequestPublisher admissionRequestPublisher;
    @Mock
    private CommonCodeCache commonCodeCache;
    @Mock
    private PatientClient patientClient;

    private final AdmissionRequestMapper admissionRequestMapper = new AdmissionRequestMapperImpl();

    private AdmissionRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdmissionRequestServiceImpl(admissionRequestRepository, encounterRepository,
                admissionRequestMapper, admissionRequestPublisher, commonCodeCache, patientClient);
    }

    private Encounter encounter(String status) {
        Encounter encounter = new Encounter();
        encounter.setEncounterId("ENC-1");
        encounter.setPatientId("PAT-1");
        encounter.setDoctorId("DOC-1");
        encounter.setDepartmentCode("IM");
        encounter.setStatus(status);
        return encounter;
    }

    private AdmissionRequest storedRequest(String requestStatus, String sendStatus) {
        AdmissionRequest request = new AdmissionRequest();
        request.setAdmissionRequestId("REQ-1");
        request.setPatientId("PAT-1");
        request.setEncounterId("ENC-1");
        request.setAdmissionDeptCode("IM");
        request.setRequestNote("메모");
        request.setRequestStatus(requestStatus);
        request.setRequestedBy("DOC-1");
        request.setRequestedAt(LocalDateTime.now());
        request.setSendStatus(sendStatus);
        return request;
    }

    // ---- 등록

    @Test
    void 입원요청을_등록하면_요청됨_상태와_PENDING으로_저장한다() {
        when(encounterRepository.findById("ENC-1")).thenReturn(Optional.of(encounter("IN_PROGRESS")));
        when(admissionRequestRepository.existsByEncounterIdAndRequestStatusIn(eq("ENC-1"), anyCollection())).thenReturn(false);
        when(admissionRequestRepository.save(any(AdmissionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        AdmissionRequestCreateDto request = new AdmissionRequestCreateDto();
        request.setAdmissionDeptCode("GS");
        request.setRequestNote("  수술 후 입원  ");

        AdmissionRequestDto result = service.createAdmissionRequest("ENC-1", request);

        ArgumentCaptor<AdmissionRequest> saved = ArgumentCaptor.forClass(AdmissionRequest.class);
        verify(admissionRequestRepository).save(saved.capture());
        AdmissionRequest entity = saved.getValue();
        assertThat(entity.getAdmissionRequestId()).isNotBlank();
        assertThat(entity.getPatientId()).isEqualTo("PAT-1");
        assertThat(entity.getEncounterId()).isEqualTo("ENC-1");
        assertThat(entity.getAdmissionDeptCode()).isEqualTo("GS");
        assertThat(entity.getRequestNote()).isEqualTo("수술 후 입원");
        assertThat(entity.getRequestStatus()).isEqualTo("01");
        assertThat(entity.getRequestedBy()).isEqualTo("DOC-1");
        assertThat(entity.getRequestedAt()).isNotNull();
        assertThat(entity.getSendStatus()).isEqualTo("PENDING");

        assertThat(result.getRequestStatus()).isEqualTo("01");
        // 등록 단계에서는 병동으로 보내지 않는다 (커밋 이후 별도 호출)
        verifyNoInteractions(admissionRequestPublisher);
    }

    @Test
    void 입원진료과를_생략하면_진료의_진료과로_요청한다() {
        when(encounterRepository.findById("ENC-1")).thenReturn(Optional.of(encounter("IN_PROGRESS")));
        when(admissionRequestRepository.existsByEncounterIdAndRequestStatusIn(eq("ENC-1"), anyCollection())).thenReturn(false);
        when(admissionRequestRepository.save(any(AdmissionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        AdmissionRequestDto result = service.createAdmissionRequest("ENC-1", null);

        assertThat(result.getAdmissionDeptCode()).isEqualTo("IM");
    }

    @Test
    void 진료가_완료된_뒤에도_입원을_요청할_수_있다() {
        when(encounterRepository.findById("ENC-1")).thenReturn(Optional.of(encounter("COMPLETED")));
        when(admissionRequestRepository.existsByEncounterIdAndRequestStatusIn(eq("ENC-1"), anyCollection())).thenReturn(false);
        when(admissionRequestRepository.save(any(AdmissionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        AdmissionRequestDto result = service.createAdmissionRequest("ENC-1", new AdmissionRequestCreateDto());

        assertThat(result.getRequestStatus()).isEqualTo("01");
    }

    @Test
    void 존재하지_않는_진료면_NOT_FOUND를_던진다() {
        when(encounterRepository.findById("ENC-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAdmissionRequest("ENC-X", new AdmissionRequestCreateDto()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(admissionRequestRepository, never()).save(any());
    }

    @Test
    void 진료_시작_전이거나_취소된_진료면_CONFLICT를_던진다() {
        for (String status : new String[]{"WAITING", "PENDING", "CANCELLED"}) {
            when(encounterRepository.findById("ENC-1")).thenReturn(Optional.of(encounter(status)));

            assertThatThrownBy(() -> service.createAdmissionRequest("ENC-1", new AdmissionRequestCreateDto()))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        }
        verify(admissionRequestRepository, never()).save(any());
    }

    @Test
    void 같은_진료에_진행_중인_요청이_있으면_CONFLICT를_던진다() {
        when(encounterRepository.findById("ENC-1")).thenReturn(Optional.of(encounter("IN_PROGRESS")));
        when(admissionRequestRepository.existsByEncounterIdAndRequestStatusIn(eq("ENC-1"), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.createAdmissionRequest("ENC-1", new AdmissionRequestCreateDto()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(admissionRequestRepository, never()).save(any());
    }

    @Test
    void 진행_중_요청_확인은_요청됨과_병상배정만_대상으로_한다() {
        when(encounterRepository.findById("ENC-1")).thenReturn(Optional.of(encounter("IN_PROGRESS")));
        when(admissionRequestRepository.existsByEncounterIdAndRequestStatusIn(eq("ENC-1"), anyCollection())).thenReturn(false);
        when(admissionRequestRepository.save(any(AdmissionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createAdmissionRequest("ENC-1", new AdmissionRequestCreateDto());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> statuses = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(admissionRequestRepository).existsByEncounterIdAndRequestStatusIn(eq("ENC-1"), statuses.capture());
        // 거부(03)된 요청은 재요청을 막지 않는다
        assertThat(statuses.getValue()).containsExactlyInAnyOrder("01", "02");
    }

    @Test
    void 요청_메모가_500자를_넘으면_INVALID_INPUT을_던진다() {
        when(encounterRepository.findById("ENC-1")).thenReturn(Optional.of(encounter("IN_PROGRESS")));

        AdmissionRequestCreateDto request = new AdmissionRequestCreateDto();
        request.setRequestNote("가".repeat(501));

        assertThatThrownBy(() -> service.createAdmissionRequest("ENC-1", request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(admissionRequestRepository, never()).save(any());
    }

    // ---- 병동 전송

    @Test
    void 전송에_성공하면_SENT로_바꾸고_요청_내용을_발행한다() {
        when(admissionRequestRepository.findById("REQ-1")).thenReturn(Optional.of(storedRequest("01", "PENDING")));
        when(admissionRequestPublisher.publish(any())).thenReturn(true);
        when(admissionRequestRepository.save(any(AdmissionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        AdmissionRequestDto result = service.dispatchAdmissionRequest("REQ-1");

        ArgumentCaptor<AdmissionEventDto.RequestedData> data = ArgumentCaptor.forClass(AdmissionEventDto.RequestedData.class);
        verify(admissionRequestPublisher).publish(data.capture());
        assertThat(data.getValue().requestId()).isEqualTo("REQ-1");
        assertThat(data.getValue().patientId()).isEqualTo("PAT-1");
        assertThat(data.getValue().targetDeptCode()).isEqualTo("IM");
        assertThat(data.getValue().encounterId()).isEqualTo("ENC-1");
        assertThat(data.getValue().requestedBy()).isEqualTo("DOC-1");
        assertThat(data.getValue().note()).isEqualTo("메모");

        assertThat(result.getSendStatus()).isEqualTo("SENT");
        assertThat(result.getSentAt()).isNotNull();
    }

    @Test
    void 전송에_실패하면_FAILED로_남긴다() {
        when(admissionRequestRepository.findById("REQ-1")).thenReturn(Optional.of(storedRequest("01", "PENDING")));
        when(admissionRequestPublisher.publish(any())).thenReturn(false);
        when(admissionRequestRepository.save(any(AdmissionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        AdmissionRequestDto result = service.dispatchAdmissionRequest("REQ-1");

        assertThat(result.getSendStatus()).isEqualTo("FAILED");
        // 전송 실패여도 요청 상태는 그대로다
        assertThat(result.getRequestStatus()).isEqualTo("01");
    }

    @Test
    void FAILED_요청은_다시_전송할_수_있다() {
        when(admissionRequestRepository.findById("REQ-1")).thenReturn(Optional.of(storedRequest("01", "FAILED")));
        when(admissionRequestPublisher.publish(any())).thenReturn(true);
        when(admissionRequestRepository.save(any(AdmissionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        AdmissionRequestDto result = service.dispatchAdmissionRequest("REQ-1");

        assertThat(result.getSendStatus()).isEqualTo("SENT");
    }

    @Test
    void 이미_SENT인_요청은_다시_발행하지_않는다() {
        when(admissionRequestRepository.findById("REQ-1")).thenReturn(Optional.of(storedRequest("01", "SENT")));

        AdmissionRequestDto result = service.dispatchAdmissionRequest("REQ-1");

        assertThat(result.getSendStatus()).isEqualTo("SENT");
        verifyNoInteractions(admissionRequestPublisher);
        verify(admissionRequestRepository, never()).save(any());
    }

    @Test
    void 병동이_이미_회신한_요청은_다시_발행하지_않는다() {
        when(admissionRequestRepository.findById("REQ-1")).thenReturn(Optional.of(storedRequest("03", "FAILED")));

        service.dispatchAdmissionRequest("REQ-1");

        verifyNoInteractions(admissionRequestPublisher);
    }

    // ---- 입원요청 목록 조회 (모든 의사의 요청)

    private AdmissionRequest listedRequest(String id, String status, LocalDateTime requestedAt) {
        AdmissionRequest request = storedRequest(status, "SENT");
        request.setAdmissionRequestId(id);
        request.setRequestedAt(requestedAt);
        return request;
    }

    @Test
    void 목록은_요청일시_최신순으로_조회하고_환자명을_채운다() {
        Page<AdmissionRequest> page = new PageImpl<>(
                List.of(listedRequest("REQ-2", "01", LocalDateTime.of(2026, 10, 12, 11, 0)),
                        listedRequest("REQ-1", "02", LocalDateTime.of(2026, 10, 11, 9, 0))),
                PageRequest.of(0, 20), 2);
        when(admissionRequestRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(patientClient.getPatients(anyCollection()))
                .thenReturn(Map.of("PAT-1", new PatientApiDto.PatientSummary("PAT-1", "환자홍길동")));

        AdmissionRequestPageDto result = service.getAdmissionRequestPage(null, 0, 20);

        // 정렬: 요청일시 내림차순 + ID 오름차순(동률 정렬 고정)
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(admissionRequestRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("requestedAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("admissionRequestId").getDirection())
                .isEqualTo(Sort.Direction.ASC);

        assertThat(result.getContent()).extracting(AdmissionRequestDto::getAdmissionRequestId)
                .containsExactly("REQ-2", "REQ-1");
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
        // PAT 이름에 붙은 "환자" 접두어는 다른 목록과 같이 제거한다
        assertThat(result.getContent().get(0).getPatientName()).isEqualTo("홍길동");
    }

    @Test
    void 요청상태를_주면_해당_상태만_조회한다() {
        when(admissionRequestRepository.findByRequestStatus(eq("03"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listedRequest("REQ-3", "03", LocalDateTime.now())),
                        PageRequest.of(0, 20), 1));
        when(patientClient.getPatients(anyCollection())).thenReturn(Map.of());

        AdmissionRequestPageDto result = service.getAdmissionRequestPage("03", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        verify(admissionRequestRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void 알_수_없는_요청상태면_INVALID_INPUT을_던진다() {
        assertThatThrownBy(() -> service.getAdmissionRequestPage("99", 0, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verifyNoInteractions(admissionRequestRepository);
    }

    @Test
    void 페이지_크기는_1에서_100_사이로_보정한다() {
        when(admissionRequestRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        AdmissionRequestPageDto tooBig = service.getAdmissionRequestPage(null, -3, 1000);
        AdmissionRequestPageDto tooSmall = service.getAdmissionRequestPage(null, 0, 0);

        assertThat(tooBig.getSize()).isEqualTo(100);
        assertThat(tooBig.getPage()).isZero();
        assertThat(tooSmall.getSize()).isEqualTo(1);
    }

    @Test
    void 환자명_조회가_실패해도_목록은_환자명_없이_돌려준다() {
        when(admissionRequestRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listedRequest("REQ-1", "01", LocalDateTime.now())),
                        PageRequest.of(0, 20), 1));
        when(patientClient.getPatients(anyCollection())).thenThrow(new RuntimeException("PAT down"));

        AdmissionRequestPageDto result = service.getAdmissionRequestPage(null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPatientName()).isNull();
    }

    // ---- 진료별 최신 요청 조회 (환자 목록 배지용)

    @Test
    void 진료에_요청이_여러_건이면_가장_최근_요청만_돌려준다() {
        AdmissionRequest rejectedOld = storedRequest("03", "SENT");
        rejectedOld.setAdmissionRequestId("REQ-OLD");
        rejectedOld.setRequestedAt(LocalDateTime.of(2026, 10, 12, 9, 0, 0));

        AdmissionRequest requestedNew = storedRequest("01", "SENT");
        requestedNew.setAdmissionRequestId("REQ-NEW");
        requestedNew.setRequestedAt(LocalDateTime.of(2026, 10, 12, 11, 0, 0));

        AdmissionRequest otherEncounter = storedRequest("02", "SENT");
        otherEncounter.setAdmissionRequestId("REQ-OTHER");
        otherEncounter.setEncounterId("ENC-2");

        when(admissionRequestRepository.findByEncounterIdIn(anyCollection()))
                .thenReturn(List.of(requestedNew, rejectedOld, otherEncounter));

        Map<String, AdmissionRequestDto> result = service.getLatestAdmissionRequests(List.of("ENC-1", "ENC-2", "ENC-3"));

        assertThat(result).containsOnlyKeys("ENC-1", "ENC-2");
        assertThat(result.get("ENC-1").getAdmissionRequestId()).isEqualTo("REQ-NEW");
        assertThat(result.get("ENC-1").getRequestStatus()).isEqualTo("01");
        assertThat(result.get("ENC-2").getRequestStatus()).isEqualTo("02");
    }

    @Test
    void 진료_ID가_없으면_조회하지_않고_빈_결과를_돌려준다() {
        assertThat(service.getLatestAdmissionRequests(List.of())).isEmpty();
        assertThat(service.getLatestAdmissionRequests(null)).isEmpty();

        verifyNoInteractions(admissionRequestRepository);
    }

    @Test
    void 진료_ID가_많으면_나눠서_조회한다() {
        when(admissionRequestRepository.findByEncounterIdIn(anyCollection())).thenReturn(List.of());

        List<String> ids = IntStream.range(0, 1200).mapToObj(i -> "ENC-" + i).collect(Collectors.toList());
        service.getLatestAdmissionRequests(ids);

        // Oracle IN 절 1000개 제한 때문에 500개씩: 500 + 500 + 200
        verify(admissionRequestRepository, times(3)).findByEncounterIdIn(anyCollection());
    }

    // ---- 병동 회신

    @Test
    void 병상_배정_회신을_반영한다() {
        when(admissionRequestRepository.findById("REQ-1")).thenReturn(Optional.of(storedRequest("01", "SENT")));

        service.applyAdmissionResult("REQ-1", "02", "A001", "WARD-3", null);

        ArgumentCaptor<AdmissionRequest> saved = ArgumentCaptor.forClass(AdmissionRequest.class);
        verify(admissionRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getRequestStatus()).isEqualTo("02");
        assertThat(saved.getValue().getAdmissionId()).isEqualTo("A001");
        assertThat(saved.getValue().getAssignedWardCode()).isEqualTo("WARD-3");
        assertThat(saved.getValue().getRejectReason()).isNull();
        assertThat(saved.getValue().getRespondedAt()).isNotNull();
    }

    @Test
    void 거부_회신을_반영한다() {
        when(admissionRequestRepository.findById("REQ-1")).thenReturn(Optional.of(storedRequest("01", "SENT")));

        service.applyAdmissionResult("REQ-1", "03", null, null, "Patient already has an active admission");

        ArgumentCaptor<AdmissionRequest> saved = ArgumentCaptor.forClass(AdmissionRequest.class);
        verify(admissionRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getRequestStatus()).isEqualTo("03");
        assertThat(saved.getValue().getRejectReason()).isEqualTo("Patient already has an active admission");
        assertThat(saved.getValue().getAdmissionId()).isNull();
        assertThat(saved.getValue().getRespondedAt()).isNotNull();
    }

    @Test
    void 이미_처리된_요청의_중복_회신은_상태를_바꾸지_않는다() {
        when(admissionRequestRepository.findById("REQ-1")).thenReturn(Optional.of(storedRequest("02", "SENT")));

        // 배정 완료된 요청에 거부 회신이 다시 와도 뒤집히지 않는다
        service.applyAdmissionResult("REQ-1", "03", null, null, "late duplicate");

        verify(admissionRequestRepository, never()).save(any());
    }

    @Test
    void 없는_요청ID의_회신은_무시한다() {
        when(admissionRequestRepository.findById("REQ-X")).thenReturn(Optional.empty());

        service.applyAdmissionResult("REQ-X", "02", "A001", "WARD-3", null);

        verify(admissionRequestRepository, never()).save(any());
    }

    @Test
    void 알_수_없는_status의_회신은_무시한다() {
        service.applyAdmissionResult("REQ-1", "99", null, null, null);
        service.applyAdmissionResult("REQ-1", "01", null, null, null);
        service.applyAdmissionResult(null, "02", "A001", "WARD-3", null);

        verifyNoInteractions(admissionRequestRepository);
    }
}
