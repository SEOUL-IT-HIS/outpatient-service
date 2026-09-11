package kr.co.seoulit.his.outpatientservice.common.client.pharmacy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * PHM 미기동 로컬용 stub. app.services.pharmacy.stub-enabled=true 일 때만 활성.
 */
@Component
@ConditionalOnProperty(name = "app.services.pharmacy.stub-enabled", havingValue = "true")
public class StubPharmacyClient implements PharmacyClient {

    @Override
    public List<PharmacyApiDto.Medication> searchMedication(String name) {
        PharmacyApiDto.Medication sample = new PharmacyApiDto.Medication(
                1L,
                "타이레놀정500mg",
                "195700020",
                "Tylenol Tab 500mg",
                "한국얀센",
                "일반의약품",
                "02160",
                "해열.진통.소염제",
                "정제",
                "흰색의 장방형 필름코팅정",
                LocalDate.of(2001, 5, 15),
                "645700210",
                "8806450702108"
        );

        return List.of(sample);
    }
}