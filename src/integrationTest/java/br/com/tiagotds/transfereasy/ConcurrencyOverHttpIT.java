package br.com.tiagotds.transfereasy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.infrastructure.config.Config;
import br.com.tiagotds.transfereasy.infrastructure.config.Settings;
import br.com.tiagotds.transfereasy.infrastructure.persistence.LockingStrategy;
import br.com.tiagotds.transfereasy.support.ApiClient;
import br.com.tiagotds.transfereasy.support.HttpRace;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The whole stack under concurrent HTTP traffic: bulkhead, routing, JSON, retry, idempotency, transactions and
 * locking together, once per locking strategy.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class ConcurrencyOverHttpIT {

    private static final int CLIENTS = 40;

    private Application app;
    private ApiClient api;

    private void start(LockingStrategy strategy) throws Exception {
        app = Application.start(Settings.from(Config.defaults()
                .with("http.port", "0")
                .with("account.locking", strategy.name())));
        api = new ApiClient(app.port());
    }

    @AfterEach
    void stop() {
        app.close();
    }

    private static long count(List<Integer> statuses, int status) {
        return statuses.stream().filter(s -> s == status).count();
    }

    @ParameterizedTest
    @EnumSource(LockingStrategy.class)
    void parallel_withdrawals_never_overdraw(LockingStrategy strategy) throws Exception {
        start(strategy);
        var account = api.accountWithBalance("100");

        var statuses = HttpRace.run(CLIENTS, i -> () ->
                api.post("/api/accounts/" + account + "/withdrawals", "{\"amount\":30}").status());

        assertEquals(3, count(statuses, 201), statuses.toString());
        assertEquals(CLIENTS - 3, count(statuses, 422), statuses.toString());
        assertEquals(0, new BigDecimal("10").compareTo(api.balanceOf(account)));
    }

    @ParameterizedTest
    @EnumSource(LockingStrategy.class)
    void opposite_transfers_complete_and_conserve_money(LockingStrategy strategy) throws Exception {
        start(strategy);
        var a = api.accountWithBalance("500");
        var b = api.accountWithBalance("500");

        var statuses = HttpRace.run(CLIENTS, i -> () -> {
            var from = i % 2 == 0 ? a : b;
            var to = i % 2 == 0 ? b : a;
            return api.post("/api/accounts/" + from + "/transfers",
                    "{\"toAccountNumber\":\"" + to + "\",\"amount\":1.5}").status();
        });

        assertEquals(CLIENTS, count(statuses, 201), statuses.toString());
        assertEquals(0, new BigDecimal("500").compareTo(api.balanceOf(a)), "equal traffic both ways nets out");
        assertEquals(0, new BigDecimal("500").compareTo(api.balanceOf(b)));
    }

    @ParameterizedTest
    @EnumSource(LockingStrategy.class)
    void a_storm_of_retries_with_one_idempotency_key_moves_money_once(LockingStrategy strategy) throws Exception {
        start(strategy);
        var from = api.accountWithBalance("100");
        var to = api.accountWithBalance("0");
        var body = "{\"toAccountNumber\":\"" + to + "\",\"amount\":40}";

        var replies = HttpRace.run(CLIENTS, i -> () ->
                api.post("/api/accounts/" + from + "/transfers", body, "storm-" + from));

        var transferIds = new HashSet<String>();
        replies.forEach(r -> {
            assertEquals(201, r.status(), r.body().toString());
            transferIds.add(r.body().get("transferId").asText());
        });
        assertEquals(1, transferIds.size(), "every client got the same transfer");
        assertEquals(1, replies.stream().filter(r -> r.header() == null).count(), "one fresh, the rest replayed");
        assertEquals(0, new BigDecimal("60").compareTo(api.balanceOf(from)));
        assertEquals(0, new BigDecimal("40").compareTo(api.balanceOf(to)));
        assertEquals(2, api.get("/api/accounts/" + from + "/statement").body().get("entries").size());
    }

    @ParameterizedTest
    @EnumSource(LockingStrategy.class)
    void a_burst_over_the_bulkhead_is_partly_shed_with_503_and_no_request_is_half_applied(LockingStrategy strategy)
            throws Exception {
        app = Application.start(Settings.from(Config.defaults()
                .with("http.port", "0")
                .with("account.locking", strategy.name())
                .with("http.max-in-flight", "1")
                .with("http.acquire-timeout", "0ms")));
        api = new ApiClient(app.port());
        var account = api.accountWithBalance("0");

        var statuses = HttpRace.run(CLIENTS, i -> () ->
                api.post("/api/accounts/" + account + "/deposits", "{\"amount\":1}").status());

        var accepted = count(statuses, 201);
        assertTrue(count(statuses, 503) > 0, "with one slot and no waiting, some requests must be shed");
        assertEquals(CLIENTS, accepted + count(statuses, 503), statuses.toString());
        assertEquals(0, BigDecimal.valueOf(accepted).compareTo(api.balanceOf(account)),
                "exactly the accepted deposits were applied");
    }
}
