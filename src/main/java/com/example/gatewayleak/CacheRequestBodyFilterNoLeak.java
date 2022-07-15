package com.example.gatewayleak;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR;

import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

//no leak version
//@Component
public class CacheRequestBodyFilterNoLeak implements GlobalFilter, Ordered {
    private final List<HttpMessageReader<?>> messageReaders =
            HandlerStrategies.withDefaults().messageReaders();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange, (serverHttpRequest) -> {
            final ServerRequest serverRequest = ServerRequest
                    .create(exchange.mutate().request(serverHttpRequest).build(), messageReaders);
            return serverRequest.bodyToMono(String.class).doOnNext(objectValue -> {
                removeCachedRequestBody(exchange);
                exchange.getAttributes().put(ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR, objectValue);
            }).then(Mono.defer(() -> {
                ServerHttpRequest cachedRequest = exchange
                        .getAttribute(CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR);
                Assert.notNull(cachedRequest, "cache request shouldn't be null");
                exchange.getAttributes().remove(CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR);
                return chain.filter(exchange.mutate().request(cachedRequest).build());
            }));
        });
    }

    private void removeCachedRequestBody(ServerWebExchange exchange) {
        PooledDataBuffer remove =
                (PooledDataBuffer) exchange.getAttributes().remove(CACHED_REQUEST_BODY_ATTR);
        remove.release();
    }


    @Override
    public int getOrder() {
        return 10001;
    }

}