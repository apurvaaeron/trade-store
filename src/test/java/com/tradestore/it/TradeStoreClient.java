package com.tradestore.it;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Thin HTTP helper over {@link RestTestClient} that returns status, headers and body without asserting. */
public class TradeStoreClient {

    private final ObjectProvider<RestTestClient> client;

    public TradeStoreClient(ObjectProvider<RestTestClient> client) {
        this.client = client;
    }

    public <T> ResponseEntity<T> post(String uri, Object body, Class<T> type) {
        RestTestClient.RequestBodySpec request = client.getObject().post().uri(uri);
        RestTestClient.RequestHeadersSpec<?> spec = body == null
                ? request
                : request.contentType(MediaType.APPLICATION_JSON).body(body);
        return toEntity(spec.exchange().returnResult(type));
    }

    public <T> ResponseEntity<T> get(String uri, Class<T> type) {
        return toEntity(client.getObject().get().uri(uri).exchange().returnResult(type));
    }

    public <T> T getBody(String uri, Class<T> type) {
        return get(uri, type).getBody();
    }

    private static <T> ResponseEntity<T> toEntity(EntityExchangeResult<T> result) {
        return ResponseEntity.status(result.getStatus())
                .headers(result.getResponseHeaders())
                .body(result.getResponseBody());
    }
}
