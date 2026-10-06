package org.labs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KitchenTest {

    /** Официант, который запоминает, на каких потоках выполнялись заказы. */
    private static class ThreadRecordingExecutor extends ThreadPoolExecutor {
        final List<Thread> workerThreads = Collections.synchronizedList(new ArrayList<>());

        ThreadRecordingExecutor() {
            super(1, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            workerThreads.add(Thread.currentThread());
            super.afterExecute(r, t);
        }
    }

    /**
     * Имитатор пула, который НЕ запускает задачи, а просто складывает их
     * в очередь с приоритетом. Позволяет детерминированно проверить,
     * в каком порядке OrderTicket выстраиваются по compareTo.
     */
    private static class QueueOnlyExecutor extends ThreadPoolExecutor {
        final PriorityBlockingQueue<Runnable> queue;

        QueueOnlyExecutor(PriorityBlockingQueue<Runnable> queue) {
            super(1, 1, 0L, TimeUnit.MILLISECONDS, queue);
            this.queue = queue;
        }

        @Override
        public void execute(Runnable command) {
            queue.offer(command); // воркеры не создаются — задачи выполним сами
        }
    }

    @Test
    @DisplayName("Порции выдаются, пока есть еда, дальше — отказ")
    void portionsAreServedUntilFoodRunsOut() throws Exception {
        ExecutorService servants = Executors.newSingleThreadExecutor();
        Kitchen kitchen = new Kitchen(2, servants);

        assertTrue(kitchen.order(0).get(1, TimeUnit.SECONDS));   // осталось 1
        assertTrue(kitchen.order(1).get(1, TimeUnit.SECONDS));   // осталось 0
        assertFalse(kitchen.order(2).get(1, TimeUnit.SECONDS));  // -1 => "супа нет"

        kitchen.close();
        assertTrue(servants.isShutdown());
    }

    @Test
    @DisplayName("Пустая кухня сразу отказывает")
    void emptyKitchenRefusesImmediately() throws Exception {
        ExecutorService servants = Executors.newSingleThreadExecutor();
        try {
            Kitchen kitchen = new Kitchen(0, servants);
            assertFalse(kitchen.order(0).get(1, TimeUnit.SECONDS));
        } finally {
            servants.shutdown();
        }
    }

    @Test
    @DisplayName("Кто съел меньше, тот и приоритетнее в очереди")
    void ordersWithSmallerEatenCountHaveHigherPriority() throws Exception {
        PriorityBlockingQueue<Runnable> queue = new PriorityBlockingQueue<>();
        QueueOnlyExecutor servants = new QueueOnlyExecutor(queue);
        Kitchen kitchen = new Kitchen(10, servants);

        // Заказы приходят в "неправильном" порядке: сначала тот, кто уже поел 5 раз
        Future<Boolean> ateMore = kitchen.order(5);
        Future<Boolean> ateLess = kitchen.order(2);

        // O(Ticket) реализует Comparable — PQueue должен поставить ateLess первым
        Runnable first = queue.poll();
        Runnable second = queue.poll();
        assertSame(ateLess, first, "Сначала должен быть обслужен едок, съевший меньше");
        assertSame(ateMore, second);

        // Выполняем вручную (исполнитель в этом тесте задачи не запускает)
        first.run();
        second.run();
        assertEquals(Boolean.TRUE, ateLess.get());
        assertEquals(Boolean.TRUE, ateMore.get());
        servants.shutdown();
    }

    @Test
    @DisplayName("Параллельные заказы не выдают больше порций, чем есть еды")
    void parallelOrdersNeverOverServe() throws Exception {
        ExecutorService servants = Executors.newCachedThreadPool();
        try {
            Kitchen kitchen = new Kitchen(8, servants);
            List<Future<Boolean>> orders = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                orders.add(kitchen.order(i % 3));
            }
            // 8 заказов при запасе 8: остаток убегает 7..0, все >= 0 => все true
            for (Future<Boolean> order : orders) {
                assertTrue(order.get(1, TimeUnit.SECONDS));
            }
        } finally {
            servants.shutdown();
        }
    }


}