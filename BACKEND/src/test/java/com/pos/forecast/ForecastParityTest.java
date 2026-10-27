package com.pos.forecast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Đối chiếu ForecastCalculator với bản Python trong AI_FORECAST/ trên cùng bộ dữ liệu
 * (AI_FORECAST/tests/fixtures/parity_cases.json, sinh bằng `python -m pos_forecast.parity`).
 */
class ForecastParityTest {

    private static final Path FIXTURE = Path.of("..", "AI_FORECAST", "tests", "fixtures", "parity_cases.json");

    @TestFactory
    Stream<DynamicTest> javaMatchesPython() throws IOException {
        assertTrue(Files.exists(FIXTURE), "Không tìm thấy " + FIXTURE.toAbsolutePath());
        JsonNode cases = new ObjectMapper().readTree(FIXTURE.toFile());
        ForecastCalculator calc = new ForecastCalculator(ForecastCalculator.Config.defaults());

        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : cases) {
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> {
                JsonNode in = c.get("input");
                JsonNode ex = c.get("expected");
                List<Double> sales = new ArrayList<>();
                in.get("sales").forEach(v -> sales.add(v.asDouble()));
                int days = in.get("days").asInt();

                ForecastCalculator.Model model = calc.model(sales, in.get("algorithm").asText());
                ForecastCalculator.Plan plan = calc.plan(model, in.get("currentStock").asInt(), days);

                assertEquals(ex.get("hasHistory").asBoolean(), model.hasHistory(), "hasHistory");
                assertEquals(ex.get("dailyDemandForecast").asDouble(), model.dailyDemand(), 0.006, "dailyDemandForecast");
                assertEquals(ex.get("movingAverage7").asDouble(), model.movingAverage(), 0.006, "movingAverage7");
                assertEquals(ex.get("stdDev").asDouble(), model.stdDev(), 0.006, "stdDev");
                assertEquals(ex.get("safetyStock").asDouble(), model.safetyStock(), 0.006, "safetyStock");
                assertEquals(ex.get("reorderPoint").asDouble(), model.reorderPoint(), 0.006, "reorderPoint");
                assertEquals(ex.get("intermittent").asBoolean(), model.intermittent(), "intermittent");
                assertEquals(ex.get("quality").asText(), model.quality(), "quality");
                assertEquals(ex.get("totalPredictedDemand").asInt(), plan.totalPredictedDemand(), "totalPredictedDemand");
                assertEquals(ex.get("suggestedRestockQty").asInt(), plan.suggestedRestockQty(), "suggestedRestockQty");
                assertEquals(ex.get("urgency").asText(), plan.urgency(), "urgency");
            }));
        }
        return tests.stream();
    }
}
