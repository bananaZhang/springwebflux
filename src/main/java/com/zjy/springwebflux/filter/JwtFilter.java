package com.zjy.springwebflux.filter;

import cn.hutool.core.lang.Assert;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.ByteBufAllocator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtFilter implements WebFilter {

    @Autowired
    private ObjectMapper objectMapper;

    private final DataBufferFactory dataBufferFactory = new NettyDataBufferFactory(ByteBufAllocator.DEFAULT);

    @Override
    public Mono<Void> filter(ServerWebExchange serverWebExchange, WebFilterChain webFilterChain) {
        ServerHttpRequest request = serverWebExchange.getRequest();
        String token = request.getHeaders().getFirst("token");// header中取出token
        if (StringUtils.isBlank(token)) {
            log.error("token is blank");
//            return Mono.empty();
            ServerHttpResponse response = serverWebExchange.getResponse();
            return response.writeWith(Mono.just(response.bufferFactory().wrap("\"msg\":{\"no token\"}".getBytes())));
        }
        ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                Flux<DataBuffer> body = super.getBody();// 原始请求的body flux
                InputStreamHolder holder = new InputStreamHolder();
                body.subscribe(buffer -> holder.inputStream = buffer.asInputStream());
                if (null != holder.inputStream) {
                    // 解析JSON的节点
                    JsonNode jsonNode = null;
                    try {
                        jsonNode = objectMapper.readTree(holder.inputStream);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Assert.isTrue(jsonNode instanceof ObjectNode, "JSON格式异常");
                    ObjectNode objectNode = (ObjectNode) jsonNode;
                    // JSON节点最外层写入新的属性
                    objectNode.put("userId", token);
                    DataBuffer dataBuffer = dataBufferFactory.allocateBuffer();
                    String json = objectNode.toString();
                    log.info("最终的JSON数据为:{}", json);
                    dataBuffer.write(json.getBytes(StandardCharsets.UTF_8));
                    return Flux.just(dataBuffer);
                } else {
                    return super.getBody();
                }
            }
        };
        // 通过修改后的装饰器重新生成一个request
        return webFilterChain.filter(serverWebExchange.mutate().request(decorator).build());
    }

    private class InputStreamHolder {
        InputStream inputStream;
    }
}
