package com.alibaba.jbox.stream;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * TODO Test
 * @author jifang.zjf
 * @since 2017/6/26 下午5:28.
 */
public class StreamForker<T> {

    private static final Object END_OF_STREAM = new Object();

    private final Stream<T> stream;

    // Function<Stream<T>, ?> 看似在消费上面的stream, 实际是消费的BlockingQ内的数据
    private final Map<Object, Function<Stream<T>, ?>> forks = new HashMap<>();

    public StreamForker(Stream<T> stream) {
        this.stream = stream;
    }

    public StreamForker<T> fork(Object key, Function<Stream<T>, ?> function) {
        forks.put(key, function);
        return this;
    }

    public Results getResults() {
        ForkingStreamConsumer consumer = createStreamConsumer();
        try {
            stream.sequential().forEach(consumer);
        } finally {
            consumer.finish();
        }

        return consumer;
    }

    public interface Results {
        <R> R get(Object key);
    }

    private ForkingStreamConsumer createStreamConsumer() {

        List<BlockingQueue<T>> queues = new ArrayList<>(forks.size());

        // convert Map<Object, Function> -> Map<Object, Future>
        Map<Object, Future<?>> actions = new HashMap<>();
        forks.forEach((key, function) -> {
            BlockingQueue<T> queue = new LinkedBlockingQueue<>();
            queues.add(queue);

            Stream<T> source = StreamSupport.stream(new BlockingQueueSpliterator(queue), false);
            Future<?> future = CompletableFuture.supplyAsync(() -> function.apply(source));     // 启动异步消费任务
            actions.put(key, future);
        });

        return new ForkingStreamConsumer(queues, actions);
    }

    @SuppressWarnings("unchecked")
    private class ForkingStreamConsumer implements Consumer<T>, Results {

        private final List<BlockingQueue<T>> queues;

        private final Map<Object, Future<?>> actions;

        public ForkingStreamConsumer(List<BlockingQueue<T>> queues, Map<Object, Future<?>> actions) {
            this.queues = queues;
            this.actions = actions;
        }

        @Override
        public void accept(T t) {
            queues.forEach(queue -> queue.offer(t));
        }

        public void finish() {
            this.accept((T) END_OF_STREAM);
        }

        @Override
        public <R> R get(Object key) {
            try {
                return ((Future<R>) actions.get(key)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class BlockingQueueSpliterator implements Spliterator<T> {

        private BlockingQueue<T> queue;

        public BlockingQueueSpliterator(BlockingQueue<T> queue) {
            this.queue = queue;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            while (true) {
                try {
                    T item = queue.take();
                    if (item != END_OF_STREAM) {
                        action.accept(item);
                        return true;
                    } else {
                        return false;
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }

        @Override
        public Spliterator<T> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return 0;
        }

        @Override
        public int characteristics() {
            return 0;
        }
    }
}
