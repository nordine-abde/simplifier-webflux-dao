package anordine.dao.simplifier.webflux;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class R2dbcTestRuntimeSmokeTest {

    @Test
    void reactiveRuntimeExecutesPublisher() {
        StepVerifier.create(Mono.just("dao-simplifier-webflux").map(String::toUpperCase))
                .expectNext("DAO-SIMPLIFIER-WEBFLUX")
                .verifyComplete();
    }

    @Test
    void inMemoryR2dbcConnectionCanExecuteQuery() {
        ConnectionFactory connectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///t01_smoke");
        DatabaseClient databaseClient = DatabaseClient.create(connectionFactory);

        Mono<Integer> result = databaseClient.sql("SELECT 1")
                .map((row, metadata) -> row.get(0, Integer.class))
                .one();

        StepVerifier.create(result)
                .expectNext(1)
                .verifyComplete();
    }
}
