package org.labs;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

public class Kitchen {

    private AtomicInteger foodAmount;
    private final ExecutorService servants;

    public Kitchen(int foodAmount, ExecutorService servants) {
        this.foodAmount = new AtomicInteger(foodAmount);
        this.servants = servants;
    }

    private boolean deliver() {
        int remains = foodAmount.decrementAndGet();
        System.out.printf("Доставка | Осталось еды: %d%n", remains);
        return remains >= 0;
    }

    public Future<Boolean> order(int alreadyEatenCount) {
        OrderTicket order = new OrderTicket(alreadyEatenCount, this::deliver);
        servants.execute(order);
        return order;
    }

    public void close() {
        servants.shutdown();
    }

    private static final class OrderTicket extends FutureTask<Boolean> implements Comparable<OrderTicket> {

        private final int alreadyEatenCount;

        public OrderTicket(int alreadyEatenCount, Callable<Boolean> deliverer) {
            super(deliverer);
            this.alreadyEatenCount = alreadyEatenCount;
        }

        @Override
        public int compareTo(OrderTicket o) {
            return Integer.compare(alreadyEatenCount, o.alreadyEatenCount);
        }

    }
}
