package com.zjy.springwebflux;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@SpringBootTest
class SpringwebfluxApplicationTests {

    @Test
    public void test() {
        Flux.range(1, 10)
                .log()
                .publishOn(Schedulers.newParallel("myParallel"))
                .log()
                .subscribeOn(Schedulers.newElastic("myElastic"))
                .log()
                .blockLast();
    }
}