package com.zjy.springwebflux.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.zjy.springwebflux.util.AesUtils;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

/**
 * <refer https://juejin.im/post/5cdfb8fe6fb9a07f04201838#heading-7 />
 * @author: ZJY
 * @date: 2020/4/23 下午10:29
 */
@Slf4j
@Component
public class EncryptionFilter implements WebFilter {
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange serverWebExchange, WebFilterChain webFilterChain) {
        ServerHttpRequest request = serverWebExchange.getRequest();
        ServerHttpResponse response = serverWebExchange.getResponse();
        DataBufferFactory bufferFactory = serverWebExchange.getResponse().bufferFactory();
        ServerHttpRequestDecorator requestDecorator = processRequest(request, bufferFactory);
        ServerHttpResponseDecorator responseDecorator = processResponse(response, bufferFactory);
        return webFilterChain.filter(serverWebExchange.mutate().request(requestDecorator)
                .response(responseDecorator).build());
    }

    private ServerHttpRequestDecorator processRequest(ServerHttpRequest request, DataBufferFactory bufferFactory) {
        Flux<DataBuffer> body = request.getBody();
        DataBufferHolder holder = new DataBufferHolder();
        body.subscribe(dataBuffer -> {
            int len = dataBuffer.readableByteCount();
            holder.length = len;
            byte[] bytes = new byte[len];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            String text = new String(bytes, StandardCharsets.UTF_8);
            JsonNode jsonNode = readNode(text);
            JsonNode payload = jsonNode.get("payload");
            String payloadText = payload.asText();
            byte[] content = AesUtils.X.decrypt(payloadText);
            String requestBody = new String(content, StandardCharsets.UTF_8);
            log.info("修改请求体payload,修改前:{},修改后:{}", payloadText, requestBody);
            rewritePayloadNode(requestBody, jsonNode);
            DataBuffer data = bufferFactory.allocateBuffer();
            data.write(jsonNode.toString().getBytes(StandardCharsets.UTF_8));
            holder.dataBuffer = data;
        });
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(request.getHeaders());
        headers.remove(HttpHeaders.CONTENT_LENGTH);

        return new ServerHttpRequestDecorator(request){
            @Override
            public HttpHeaders getHeaders() {
                int contentLength = holder.length;
                if (contentLength > 0) {
                    headers.setContentLength(contentLength);
                } else {
                    headers.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
                }
                return headers;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.just(holder.dataBuffer);
            }
        };
    }

    private ServerHttpResponseDecorator processResponse(ServerHttpResponse response, DataBufferFactory bufferFactory) {
        return new ServerHttpResponseDecorator(response) {
            @SuppressWarnings("unchecked")
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> flux = (Flux<? extends DataBuffer>) body;
                    return super.writeWith(flux.map(buffer -> {
                        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer.asByteBuffer());
                        DataBufferUtils.release(buffer);
                        JsonNode jsonNode = readNode(charBuffer.toString());
                        JsonNode payload = jsonNode.get("payload");
                        String text = payload.toString();
                        String content = AesUtils.X.encrypt(text);
                        log.info("修改响应体payload,修改前:{},修改后:{}", text, content);
                        setPayloadTextNode(content, jsonNode);
                        return bufferFactory.wrap(jsonNode.toString().getBytes(StandardCharsets.UTF_8));
                    }));
                }
                return super.writeWith(body);
            }
        };
    }

    private void rewritePayloadNode(String text, JsonNode root) {
        try {
            JsonNode node = objectMapper.readTree(text);
            ObjectNode objectNode = (ObjectNode) root;
            objectNode.set("payload", node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void setPayloadTextNode(String text, JsonNode root) {
        try {
            ObjectNode objectNode = (ObjectNode) root;
            objectNode.set("payload", new TextNode(text));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode readNode(String in) {
        try {
            return objectMapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private class DataBufferHolder {
        DataBuffer dataBuffer;
        int length;
    }
}
