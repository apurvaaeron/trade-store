package com.tradestore.api;

import com.tradestore.domain.RejectionReason;
import com.tradestore.domain.TradeNotFoundException;
import com.tradestore.domain.TradeRejectedException;
import com.tradestore.messaging.TradePublishException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** 400 for malformed payloads, 422 for well-formed trades that break a business rule. */
    @ExceptionHandler(TradeRejectedException.class)
    public ProblemDetail rejected(TradeRejectedException e) {
        HttpStatus status = e.getReason() == RejectionReason.INVALID_PAYLOAD
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.UNPROCESSABLE_CONTENT;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle("Trade rejected");
        problem.setProperty("tradeId", e.getTradeId());
        problem.setProperty("reason", e.getReason());
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
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setTitle(e.getOutcome() == TradePublishException.Outcome.UNKNOWN
                ? "Publish outcome unknown" : "Publish failed");
        problem.setProperty("tradeId", e.getTradeId());
        problem.setProperty("outcome", e.getOutcome());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1").body(problem);
    }
}
