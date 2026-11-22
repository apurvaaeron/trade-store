package com.tradestore.audit;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TradeAuditRepository extends MongoRepository<TradeAuditEvent, String> {

    List<TradeAuditEvent> findByTradeIdOrderByReceivedAtAsc(String tradeId);
}
