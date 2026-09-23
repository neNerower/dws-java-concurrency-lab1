package org.labs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class Kitchen {

    private AtomicInteger foodAmount;
    private final ExecutorService servants;

    public Kitchen(int foodAmount, int servantCount) {
        this.foodAmount = new AtomicInteger(foodAmount);
        this.servants = Executors.newFixedThreadPool(servantCount);
    }

    private boolean deliver() {
        int remains = foodAmount.decrementAndGet();
        System.out.printf("Доставка | Осталось еды: %d%n", remains);
        return remains >= 0;
    }

    public Future<Boolean> order() {
        return servants.submit(this::deliver);
    }

    public void close() {
        servants.shutdown();
    }
}
