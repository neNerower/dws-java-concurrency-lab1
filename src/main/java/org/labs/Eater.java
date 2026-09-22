package org.labs;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Eater implements Runnable {

    private final int id;
    private final boolean isEven;
    private final ReentrantLock leftSpoon;
    private final ReentrantLock rightSpoon;
    private final Supplier<Future<Boolean>> soupProvider;
    private final Runnable endUpRunner;


    @Override
    public void run() {
        System.out.printf("Едок %d | Пришел%n", id);

        while (true) {

            // дождаться очередной порции
            System.out.printf("Едок %d | Жду порцию%n", id);
            try {
                boolean wasSoupProvided = soupProvider.get().get();
                if (!wasSoupProvided) {
                    break;
                }
            } catch (InterruptedException e) {
                continue;
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
            System.out.printf("Едок %d | Получил порцию%n", id);

            // взять ложки
            // избегаем циклической блокировки
            if (isEven) {
                leftSpoon.lock();
                rightSpoon.lock();
            } else {
                rightSpoon.lock();
                leftSpoon.lock();
            }
            System.out.printf("Едок %d | Взял ложки%n", id);

            // кушаем и отпускаем ложки
            leftSpoon.unlock();
            rightSpoon.unlock();

            System.out.printf("Едок %d | Отдал ложки%n", id);
        }

        // отметиться, что закончил кушать
        System.out.printf("Едок %d | Наелся%n", id);
        endUpRunner.run();
    }

}
