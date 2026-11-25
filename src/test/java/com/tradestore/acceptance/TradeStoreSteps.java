package com.tradestore.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import com.tradestore.audit.TradeAuditRepository;
import com.tradestore.domain.TradeRepository;
import com.tradestore.it.MutableClock;
import com.tradestore.it.TradeStoreClient;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

public class TradeStoreSteps {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private TradeStoreClient rest;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private TradeAuditRepository auditRepository;
    @Autowired
    private MutableClock clock;

    private final Map<String, ResponseEntity<JsonNode>> responses = new HashMap<>();

    @After
    public void resetClock() {
        clock.reset();
    }

    @Given("the trade store is empty")
    public void emptyStore() {
        tradeRepository.deleteAll();
        auditRepository.deleteAll();
        responses.clear();
    }

    @Given("today is {string}")
    public void todayIs(String date) {
        clock.setToday(LocalDate.parse(date, DATE));
    }

    @When("the following trades are received")
    public void tradesReceived(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            send(row);
        }
    }

    @When("trade {string} version {int} with maturity date {string} is received")
    public void tradeReceived(String tradeId, int version, String maturity) {
        send(Map.of("tradeId", tradeId, "version", String.valueOf(version), "counterPartyId", "CP-1",
                "bookId", "B1", "maturityDate", maturity));
    }

    @When("the business date moves to {string} and the expiry job runs")
    public void expiryRuns(String date) {
        todayIs(date);
        rest.post("/api/v1/trades/expiry-run", null, JsonNode.class);
    }

    @Then("the trade store contains")
    public void storeContains(DataTable expected) {
        JsonNode trades = rest.getBody("/api/v1/trades", JsonNode.class);
        List<Map<String, String>> actual = new java.util.ArrayList<>();
        for (JsonNode t : trades) {
            Map<String, String> row = new LinkedHashMap<>();
            expected.row(0).forEach(column -> row.put(column, t.get(column).asString()));
            actual.add(row);
        }
        assertThat(actual).containsExactlyElementsOf(expected.asMaps());
    }

    @Then("trade {string} version {int} is rejected with reason {string}")
    public void rejectedWith(String tradeId, int version, String reason) {
        ResponseEntity<JsonNode> response = responses.get(tradeId + "#" + version);
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("reason").asString()).isEqualTo(reason);
    }

    @Then("trade {string} version {int} is accepted")
    public void accepted(String tradeId, int version) {
        assertThat(responses.get(tradeId + "#" + version).getStatusCode().value()).isEqualTo(201);
    }

    @Then("trade {string} is not in the store")
    public void notStored(String tradeId) {
        assertThat(tradeRepository.findById(tradeId)).isEmpty();
    }

    @Then("trade {string} has expired flag {string}")
    public void expiredFlag(String tradeId, String flag) {
        assertThat(rest.getBody("/api/v1/trades/" + tradeId, JsonNode.class).get("expired").asString())
                .isEqualTo(flag);
    }

    @Then("the audit trail for trade {string} shows {string}")
    public void auditShows(String tradeId, String outcomes) {
        JsonNode audit = rest.getBody("/api/v1/trades/" + tradeId + "/audit", JsonNode.class);
        List<String> actual = new java.util.ArrayList<>();
        audit.forEach(e -> actual.add(e.get("outcome").asString()));
        assertThat(String.join(", ", actual)).isEqualTo(outcomes);
    }

    private void send(Map<String, String> row) {
        Map<String, Object> body = new HashMap<>();
        row.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                body.put(k, "version".equals(k) ? Integer.valueOf(v) : v);
            }
        });
        responses.put(row.get("tradeId") + "#" + row.get("version"),
                rest.post("/api/v1/trades", body, JsonNode.class));
    }
}
