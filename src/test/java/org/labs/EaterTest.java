package org.labs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class EaterTest {

    /** Достаёт ровно `servings` порций, дальше говорит "супа нет". Запоминает все запросы. */
    private static class FakeSoupProvider implements Function<Integer, Future<Boolean>> {
        final int servings;
        final List<Integer> requests = Collections.synchronizedList(new ArrayList<>());

        FakeSoupProvider(int servings) { this.servings = servings; }

        @Override
        public Future<Boolean> apply(Integer count) {
            requests.add(count);
            return CompletableFuture.completedFuture(count < servings);
        }
    }

    /** Ложка, записывающая порядок lock/unlock. */
    private static class SpyLock extends ReentrantLock {
        final String name;
        final List<String> events;

        SpyLock(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public void lock() {
            super.lock();
            events.add("lock:" + name);
        }

        @Override
        public void unlock() {
            super.unlock();
            events.add("unlock:" + name);
        }
    }

    @Test
    @DisplayName("съедает все порции и завершается")
    void eatsAllPortionsThenStops() {
        FakeSoupProvider soup = new FakeSoupProvider(3);
        AtomicInteger finished = new AtomicInteger();

        Eater eater = new Eater(1, false, new ReentrantLock(), new ReentrantLock(),
            soup, finished::incrementAndGet);
        eater.run(); // вызываем напрямую, без отдельного потока

        // 3 запроса дали порцию, 4-й — "супа нет"
        assertEquals(List.of(0, 1, 2, 3), soup.requests);
        assertEquals(1, finished.get());
    }

    @Test
    @DisplayName("чётный едок берёт левую ложку первой")
    void evenEaterLocksLeftFirst() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        Eater eater = new Eater(2, true,
            new SpyLock("L", events), new SpyLock("R", events),
            new FakeSoupProvider(1), () -> {});
        eater.run();

        assertEquals(List.of("lock:L", "lock:R", "unlock:L", "unlock:R"), events);
    }

    @Test
    @DisplayName("нечётный едок берёт правую ложку первой")
    void oddEaterLocksRightFirst() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        Eater eater = new Eater(3, false,
            new SpyLock("L", events), new SpyLock("R", events),
            new FakeSoupProvider(1), () -> {});
        eater.run();

        assertEquals(List.of("lock:R", "lock:L", "unlock:L", "unlock:R"), events);
    }

}
