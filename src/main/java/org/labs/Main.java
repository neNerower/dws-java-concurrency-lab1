package org.labs;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Main {

    private static final int FOOD_AMOUNT = 1000;
    private static final int SERVANTS_COUNT = 7;
    private static final int EATER_COUNT = 30;

    public static void main(String[] args) {
        // создать
        // - еду
        // - пул офиков
        Kitchen kitchen = new Kitchen(FOOD_AMOUNT, getServantsPool(SERVANTS_COUNT));

        // - массив ложек
        ReentrantLock[] spoons = new ReentrantLock[EATER_COUNT];
        for (int i = 0; i < EATER_COUNT; i++) {
            spoons[i] = new ReentrantLock();
        }

        // - массив едоков
        // запустить циклом по кол-ву
        CountDownLatch endUpLatch = new CountDownLatch(EATER_COUNT);
        for (int i = 0; i < EATER_COUNT; i++) {
            // две ложки и дерг офика
            Runnable eaterTask = getEater(i, spoons, kitchen, endUpLatch);
            // запуск поедания
            Thread.startVirtualThread(eaterTask);
        }

        // отслеживать завершенность
        try {
            endUpLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            kitchen.close();
            System.out.printf("Кухня закрыта%n");
        }
    }

    private static ExecutorService getServantsPool(int servantCount) {
        return new ThreadPoolExecutor(servantCount, servantCount, 0, TimeUnit.DAYS,
            new PriorityBlockingQueue<>());
    }

    private static Eater getEater(int i, ReentrantLock[] spoons, Kitchen kitchen, CountDownLatch endUpLatch) {
        return new Eater(
            i,
            i % 2 == 0,
            spoons[i],
            spoons[(i + 1) % EATER_COUNT],
            kitchen::order,
            endUpLatch::countDown
        );
    }

}