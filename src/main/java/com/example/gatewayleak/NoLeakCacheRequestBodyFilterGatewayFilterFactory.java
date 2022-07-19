package com.example.gatewayleak;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR;

@Slf4j
@Component
public class NoLeakCacheRequestBodyFilterGatewayFilterFactory
        extends AbstractGatewayFilterFactory<NoLeakCacheRequestBodyFilterGatewayFilterFactory.Config> {

    private final List<HttpMessageReader<?>> messageReaders;

    public NoLeakCacheRequestBodyFilterGatewayFilterFactory() {
        super(Config.class);
        this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
    }

    @Override
    public String name() {
        return "NoLeakCacheRequestBody";
    }

    @Override
    public GatewayFilter apply(NoLeakCacheRequestBodyFilterGatewayFilterFactory.Config config) {
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                ServerHttpRequest request = exchange.getRequest();
                URI requestUri = request.getURI();
                String scheme = requestUri.getScheme();

                // Record only http requests (including https)
                if ((!"http".equals(scheme) && !"https".equals(scheme))) {
                    return chain.filter(exchange);
                }

                Object cachedBody = exchange.getAttribute(ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR);
                if (cachedBody != null) {
                    return chain.filter(exchange);
                }

                return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange, (serverHttpRequest) -> {
                    final ServerRequest serverRequest = ServerRequest
                            .create(exchange.mutate().request(serverHttpRequest).build(), messageReaders);
                    return serverRequest.bodyToMono((config.getBodyClass())).doOnNext(objectValue -> {

                        removeCachedRequestBody(exchange); //just add here releasing method

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
        };


    }

    public static class Config {

        private Class<?> bodyClass;

        public Class<?> getBodyClass() {
            return bodyClass;
        }

        public void setBodyClass(Class<?> bodyClass) {
            this.bodyClass = bodyClass;
        }

    }

}
