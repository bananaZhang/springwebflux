package com.zjy.springwebflux.configuration;

import com.zjy.springwebflux.web.TestHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.*;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RoutingConfiguration {
    /**
     * 路由配置
     * @author: ZJY
     * @date: 2020/2/23 上午11:53
     */
    @Bean
    public RouterFunction<ServerResponse> router(TestHandler testHandler) {
        // test的请求由testHandler的getData方法处理
        return route(GET("/test").and(accept(MediaType.APPLICATION_JSON)), testHandler::getData);
    }

    public RouterFunction<ServerResponse> patternRouter(TestHandler testHandler) {
        return RouterFunctions.nest(RequestPredicates.path("/index"),// 相当于类上的RequestMapping
                route(RequestPredicates.GET("/"), testHandler::getData));// 相当于方法上的RequestMapping，可叠加
    }
}
