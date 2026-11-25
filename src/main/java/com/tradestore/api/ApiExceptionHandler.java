package com.tradestore.api;

import com.tradestore.domain.RejectionReason;
import com.tradestore.domain.TradeNotFoundException;
import com.tradestore.domain.TradeRejectedException;
import com.tradestore.messaging.TradePublishException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain and messaging failures to RFC 9457 problem responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Problem extension members; part of the public API contract. */
    private static final String TRADE_ID = "tradeId";
    private static final String REASON = "reason";
    private static final String OUTCOME = "outcome";

    private static final String RETRY_AFTER_SECONDS = "1";

    /** 400 for malformed payloads, 422 for well-formed trades that break a business rule. */
    @ExceptionHandler(TradeRejectedException.class)
    public ProblemDetail rejected(TradeRejectedException e) {
        HttpStatus status = e.getReason() == RejectionReason.INVALID_PAYLOAD
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.UNPROCESSABLE_CONTENT;
        ProblemDetail problem = tradeProblem(status, "Trade rejected", e.getMessage(), e.getTradeId());
        problem.setProperty(REASON, e.getReason());
        return problem;
    }

    @ExceptionHandler(TradeNotFoundException.class)
    public ProblemDetail notFound(TradeNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * 503 when Kafka did not confirm an async trade. UNKNOWN means it may already be queued: resend the identical
     * payload only if no other change to this trade version was sent since, because an equal version replaces the
     * stored trade (assumption A14).
     */
    @ExceptionHandler(TradePublishException.class)
    public ResponseEntity<ProblemDetail> publishNotConfirmed(TradePublishException e) {
        String title = e.getOutcome() == TradePublishException.Outcome.UNKNOWN
                ? "Publish outcome unknown"
                : "Publish failed";
        ProblemDetail problem = tradeProblem(HttpStatus.SERVICE_UNAVAILABLE, title, e.getMessage(), e.getTradeId());
        problem.setProperty(OUTCOME, e.getOutcome());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem);
    }

    private static ProblemDetail tradeProblem(HttpStatus status, String title, String detail, String tradeId) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty(TRADE_ID, tradeId);
        return problem;
    }
}
