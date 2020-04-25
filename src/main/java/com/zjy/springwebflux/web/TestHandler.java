package com.zjy.springwebflux.web;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Service
public class TestHandler {

    /**
     * 处理类，类似于controller
     * @author: ZJY
     * @date: 2020/2/23 上午11:53
     */
    public Mono<ServerResponse> getData(ServerRequest request) {
        Mono<String> mono = Mono.justOrEmpty(request.queryParam("data"))
                .defaultIfEmpty("data is empty")
                .map(it -> it.concat(" is from webflux"));
//        Flux.just("1", "2", "3").subscribe(System.out::print);
        return ServerResponse.ok().body(mono, String.class);
    }
}
