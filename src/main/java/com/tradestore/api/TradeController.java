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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping(TradeController.BASE_PATH)
public class TradeController {

    static final String BASE_PATH = "/api/v1/trades";

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
        URI location = UriComponentsBuilder.fromPath(BASE_PATH)
                .pathSegment(stored.tradeId())
                .build()
                .encode()
                .toUri();
        return ResponseEntity.created(location).body(stored);
    }

    /** Asynchronous ingestion through Kafka; rejections appear on the rejected-trades topic and in the audit. */
    @PostMapping("/async")
    public ResponseEntity<TradeQueuedResponse> publish(@RequestBody TradeRequest request) {
        publisher.publishAndAwait(request);
        return ResponseEntity.accepted().body(TradeQueuedResponse.queued(String.valueOf(request.tradeId())));
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
    public ExpiryRunResponse runExpiry() {
        return new ExpiryRunResponse(expiryService.expireMaturedTrades());
    }
}
