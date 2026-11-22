package com.tradestore.api;

import static com.tradestore.TestTrades.TODAY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tradestore.audit.Channel;
import com.tradestore.audit.TradeAuditService;
import com.tradestore.domain.InvalidTradeException;
import com.tradestore.domain.LowerVersionException;
import com.tradestore.domain.MaturityDateInPastException;
import com.tradestore.domain.Trade;
import com.tradestore.domain.TradeNotFoundException;
import com.tradestore.domain.TradeRequest;
import com.tradestore.messaging.TradePublishException;
import com.tradestore.messaging.TradePublisher;
import com.tradestore.service.TradeExpiryService;
import com.tradestore.service.TradeIngestionService;
import com.tradestore.service.TradeStoreService;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TradeController.class)
class TradeControllerTest {

    private static final String BODY = """
            {"tradeId":"T1","version":1,"counterPartyId":"CP-1","bookId":"B1","maturityDate":"20/05/2030"}
            """;

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private TradeIngestionService ingestionService;
    @MockitoBean
    private TradeStoreService storeService;
    @MockitoBean
    private TradeExpiryService expiryService;
    @MockitoBean
    private TradeAuditService auditService;
    @MockitoBean
    private TradePublisher publisher;

    private final Trade t1 = new Trade("T1", 1, "CP-1", "B1", LocalDate.of(2030, 5, 20), TODAY, false);

    @Test
    void storesTradeAndReturns201() throws Exception {
        when(ingestionService.ingest(any(TradeRequest.class), eq(Channel.REST))).thenReturn(t1);

        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/trades/T1"))
                .andExpect(jsonPath("$.tradeId").value("T1"))
                .andExpect(jsonPath("$.maturityDate").value("20/05/2030"))
                .andExpect(jsonPath("$.createdDate").value("21/11/2026"))
                .andExpect(jsonPath("$.expired").value("N"));
    }

    @Test
    void encodesTradeIdAsOneLocationPathSegment() throws Exception {
        Trade slashId = new Trade("T/1", 1, "CP-1", "B1", LocalDate.of(2030, 5, 20), TODAY, false);
        when(ingestionService.ingest(any(TradeRequest.class), eq(Channel.REST))).thenReturn(slashId);

        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/trades/T%2F1"));
    }

    @Test
    void lowerVersionReturns422WithReason() throws Exception {
        when(ingestionService.ingest(any(), any())).thenThrow(new LowerVersionException("T1", 1, 2));

        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("LOWER_VERSION"))
                .andExpect(jsonPath("$.tradeId").value("T1"))
                .andExpect(jsonPath("$.title").value("Trade rejected"));
    }

    @Test
    void pastMaturityReturns422() throws Exception {
        when(ingestionService.ingest(any(), any()))
                .thenThrow(new MaturityDateInPastException("T1", TODAY.minusDays(1), TODAY));

        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("MATURITY_DATE_IN_PAST"));
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        when(ingestionService.ingest(any(), any())).thenThrow(new InvalidTradeException("T1", "version must not be null"));

        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("INVALID_PAYLOAD"));
    }

    @Test
    void malformedDateReturns400() throws Exception {
        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("20/05/2030", "2030-05-20")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidMaturityCalendarDateReturns400() throws Exception {
        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("20/05/2030", "31/02/2030")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCreatedCalendarDateReturns400() throws Exception {
        String withInvalidCreatedDate = BODY.replace("\"maturityDate\":\"20/05/2030\"",
                "\"maturityDate\":\"20/05/2030\",\"createdDate\":\"31/02/2030\"");

        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content(withInvalidCreatedDate))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fractionalVersionReturns400() throws Exception {
        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"version\":1", "\"version\":2.9")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void asyncPublishReturns202() throws Exception {
        mvc.perform(post("/api/v1/trades/async").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.tradeId").value("T1"));
    }

    @Test
    void asyncPublishWithoutKafkaConfirmationReturns503OutcomeUnknown() throws Exception {
        doThrow(new TradePublishException("T1", TradePublishException.Outcome.UNKNOWN, new TimeoutException()))
                .when(publisher).publishAndAwait(any());

        mvc.perform(post("/api/v1/trades/async").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.title").value("Publish outcome unknown"))
                .andExpect(jsonPath("$.outcome").value("UNKNOWN"))
                .andExpect(jsonPath("$.tradeId").value("T1"));
    }

    @Test
    void asyncPublishRejectedByKafkaReturns503Failed() throws Exception {
        doThrow(new TradePublishException("T1", TradePublishException.Outcome.FAILED, new IllegalStateException()))
                .when(publisher).publishAndAwait(any());

        mvc.perform(post("/api/v1/trades/async").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Publish failed"))
                .andExpect(jsonPath("$.outcome").value("FAILED"));
    }

    @Test
    void listsAndFetchesTrades() throws Exception {
        when(storeService.getAll()).thenReturn(List.of(t1));
        when(storeService.get("T1")).thenReturn(t1);

        mvc.perform(get("/api/v1/trades")).andExpect(status().isOk()).andExpect(jsonPath("$[0].tradeId").value("T1"));
        mvc.perform(get("/api/v1/trades/T1")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void unknownTradeReturns404() throws Exception {
        when(storeService.get("X")).thenThrow(new TradeNotFoundException("X"));

        mvc.perform(get("/api/v1/trades/X")).andExpect(status().isNotFound());
    }

    @Test
    void exposesAuditAndExpiryRun() throws Exception {
        when(auditService.history("T1")).thenReturn(List.of());
        when(expiryService.expireMaturedTrades()).thenReturn(2);

        mvc.perform(get("/api/v1/trades/T1/audit")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(post("/api/v1/trades/expiry-run")).andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(2));
    }
}
