package com.zjy.springwebflux.controller;

import com.zjy.springwebflux.bean.Sir;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/index")
public class IndexController {

    @GetMapping("/direct")
    public Mono<String> directController() {
        System.out.println("start = " + System.currentTimeMillis());
        Mono<String> result = Mono.just(createStr());// 只是数据包装
        System.out.println(Thread.currentThread().getId());
        System.out.println("end = " + System.currentTimeMillis());
        return result;
    }

    @GetMapping("/subscribe")
    public Mono<String> fasterTest() {
        System.out.println("start = " + System.currentTimeMillis());
        Mono<String> result = Mono.fromSupplier(this::createStr);
        System.out.println(Thread.currentThread().getId());
        System.out.println("end = " + System.currentTimeMillis());
        // 每次 Mono 发出了一个值，T 就会被异步序列化并发回客户端。这个时候你的 @Controller 方法是同步的，
        // 不过它应该是非阻塞的（短暂的处理）。请求处理在方法执行完毕时结束，返回的 T 被异步地序列化并发回客户端。
        return result;
    }

    @RequestMapping("/hello/{who}")
    public Mono<String> hello(@PathVariable String who) {
        return Mono.just(who).map(w -> "Hello " + w + "!");
    }

    @RequestMapping("/test")
    public Mono<String> test() {
        Flux.just("tom")
                .map(s -> {
                    System.out.println("[map] Thread name: " + Thread.currentThread().getName());
                    return s.concat("@mail.com");
                })
                .publishOn(Schedulers.newElastic("thread-publishOn"))
                .filter(s -> {
                    System.out.println("[filter] Thread name: " + Thread.currentThread().getName());
                    return s.startsWith("t");
                })
                .subscribeOn(Schedulers.newElastic("thread-subscribeOn"))
                .subscribe(s -> {
                    System.out.println("[subscribe] Thread name: " + Thread.currentThread().getName());
                    System.out.println(s);
                });
        return Mono.just("ok");
    }

    private String createStr() {
        System.out.println(Thread.currentThread().getId());
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "success";
    }
}
