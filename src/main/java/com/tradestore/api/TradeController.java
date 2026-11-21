package com.tradestore.api;

import com.tradestore.audit.Channel;
import com.tradestore.audit.TradeAuditEvent;
import com.tradestore.audit.TradeAuditService;
import com.tradestore.domain.TradeRequest;
import com.tradestore.messaging.TradePublisher;
import com.tradestore.service.TradeExpiryService;
import com.tradestore.service.TradeIngestionService;
import com.tradestore.service.TradeStoreService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trades")
public class TradeController {

    private final TradeIngestionService ingestionService;
    private final TradeStoreService storeService;
    private final TradeExpiryService expiryService;
    private final TradeAuditService auditService;
    private final TradePublisher publisher;

    public TradeController(TradeIngestionService ingestionService, TradeStoreService storeService,
                           TradeExpiryService expiryService, TradeAuditService auditService,
                           TradePublisher publisher) {
        this.ingestionService = ingestionService;
        this.storeService = storeService;
        this.expiryService = expiryService;
        this.auditService = auditService;
        this.publisher = publisher;
    }

    /** Synchronous ingestion: the caller gets the stored trade or the rejection reason immediately. */
    @PostMapping
    public ResponseEntity<TradeResponse> store(@RequestBody TradeRequest request) {
        TradeResponse stored = TradeResponse.from(ingestionService.ingest(request, Channel.REST));
        return ResponseEntity.created(URI.create("/api/v1/trades/" + stored.tradeId())).body(stored);
    }

    /** Asynchronous ingestion through Kafka; rejections appear on the rejected-trades topic and in the audit. */
    @PostMapping("/async")
    public ResponseEntity<Map<String, String>> publish(@RequestBody TradeRequest request) {
        publisher.publish(request).join();
        return ResponseEntity.accepted().body(Map.of("tradeId", String.valueOf(request.tradeId()), "status", "QUEUED"));
    }

    @GetMapping
    public List<TradeResponse> all() {
        return storeService.getAll().stream().map(TradeResponse::from).toList();
    }

    @GetMapping("/{tradeId}")
    public TradeResponse one(@PathVariable String tradeId) {
        return TradeResponse.from(storeService.get(tradeId));
    }

    @GetMapping("/{tradeId}/audit")
    public List<TradeAuditEvent> audit(@PathVariable String tradeId) {
        return auditService.history(tradeId);
    }

    /** Operational trigger for the expiry sweep (normally run by the scheduler). */
    @PostMapping("/expiry-run")
    public Map<String, Integer> runExpiry() {
        return Map.of("expired", expiryService.expireMaturedTrades());
    }
}
