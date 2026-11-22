package com.tradestore.service;

import com.tradestore.domain.LowerVersionException;
import com.tradestore.domain.Trade;
import com.tradestore.domain.TradeNotFoundException;
import com.tradestore.domain.TradeRepository;
import com.tradestore.domain.TradeRequest;
import com.tradestore.domain.TradeValidator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeStoreService {

    private final TradeRepository repository;
    private final TradeValidator validator;
    private final Clock clock;

    public TradeStoreService(TradeRepository repository, TradeValidator validator, Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.clock = clock;
    }

    /**
     * Validates and stores a trade. The read-then-validate gives a clear rejection message; the conditional
     * upsert is the safety net that keeps the rule correct under concurrent delivery of the same trade.
     */
    @Transactional
    public Trade store(TradeRequest request) {
        validator.validate(request, currentVersion(request.tradeId()));

        LocalDate createdDate = request.createdDate() != null ? request.createdDate() : LocalDate.now(clock);
        Trade trade = new Trade(request.tradeId(), request.version(), request.counterPartyId(), request.bookId(),
                request.maturityDate(), createdDate, false);

        if (repository.upsertUnlessLowerVersion(trade) == 0) {
            throw new LowerVersionException(request.tradeId(), request.version(),
                    currentVersion(request.tradeId()).orElse(request.version()));
        }
        return trade;
    }

    @Transactional(readOnly = true)
    public Trade get(String tradeId) {
        return repository.findById(tradeId).orElseThrow(() -> new TradeNotFoundException(tradeId));
    }

    @Transactional(readOnly = true)
    public List<Trade> getAll() {
        return repository.findAllByOrderByTradeIdAsc();
    }

    private OptionalInt currentVersion(String tradeId) {
        return repository.findById(tradeId)
                .map(t -> OptionalInt.of(t.getVersion()))
                .orElse(OptionalInt.empty());
    }
}
