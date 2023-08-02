package uk.nhs.prm.e2etests;

import org.junit.jupiter.api.Test;
import uk.nhs.prm.e2etests.configuration.TestData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDataTest {

    @Test
    void shouldGenerateRandom10DigitNumbersForPerfEnv() {
        List<String> perfData = TestData.perf(15);
        assertThat(perfData).hasSize(15);

        long distinctCount = perfData.stream().distinct().count();
        assertThat(distinctCount).isEqualTo(15);
        assertThat(perfData).allMatch(s -> s.length() == 10);
        assertThat(perfData).allMatch(s -> s.startsWith("969"));
    }
}
