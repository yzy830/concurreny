package com.gerald.concurrency.futuretask;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ListenableFutureTask<V> extends FutureTask<V> {
    private final ConcurrentLinkedQueue<Listener<V>> listeners = new ConcurrentLinkedQueue<>();
    
    private volatile V result;
    
    private volatile Throwable t;
    
    private volatile boolean isDone = false;

    public ListenableFutureTask(Callable<V> callable) {
        super(callable);
    }

    public ListenableFutureTask(Runnable runnable, V v) {
        super(runnable, v);
    }
    
    public void addListener(Listener<V> listener) {
        if(listener == null) {
            throw new NullPointerException();
        }
        
        if(isDone) {
            callListener(listener);
        } else {
            // 这个检测时序和TPE的任务入队检测时序一致
            listeners.add(listener);
            if(isDone && listeners.remove(listener)) {
                callListener(listener);
            }
        }
    }
    
    private void callListener(Listener<V> listener) {
        if(isCancelled()) {
            listener.onCancelled();
        } else if(t != null) {
            listener.onException(t);
        } else if(result != null) {
            listener.onResult(result);
        } else {
            throw new IllegalStateException();
        }
    }
    
    @Override
    protected void set(V result) {
        this.result = result;
        super.set(result);
    }
    
    @Override
    protected void setException(Throwable t) {
        this.t = t;
        super.setException(t);
    }
    
    @Override
    protected void done() {
        isDone = true;
        
        Listener<V> listener;
        
        if(isCancelled()) {
            while((listener = listeners.poll()) != null) {
                listener.onCancelled();
            }
        } else if(t != null) {
            while((listener = listeners.poll()) != null) {
                listener.onException(t);
            }
        } else if(result != null) {
            while((listener = listeners.poll()) != null) {
                listener.onResult(result);
            }
        } else {
            throw new IllegalStateException();
        }
    }
    
    public static interface Listener<V> {
        void onResult(V result);
        
        void onException(Throwable t);
        
        void onCancelled();
    }
    
    
    //===================================测试代码================================
    
    private static class TestListener implements Listener<Integer> {
        public static final AtomicInteger resultCount = new AtomicInteger(0);
        
        public static final AtomicInteger exceptionCount = new AtomicInteger(0);
        
        public static final AtomicInteger cancelCount = new AtomicInteger(0);

        @Override
        public void onResult(Integer result) {
            resultCount.addAndGet(result);
        }

        @Override
        public void onException(Throwable t) {
            exceptionCount.incrementAndGet();
        }

        @Override
        public void onCancelled() {
            cancelCount.incrementAndGet();
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread() {
            @Override
            public void run() {
                testNormal(200000, 2);
            }
        };
        
        Thread t2 = new Thread() {
            @Override
            public void run() {
                testException(200000);
            }
        };
        
        Thread t3 = new Thread() {
            @Override
            public void run() {
                testCancel(200000);
            }
        };
        
        t1.start();
        t2.start();
        t3.start();
        t1.join();
        t2.join();
        t3.join();
        
        System.out.println(TestListener.resultCount.get());
        System.out.println(TestListener.exceptionCount.get());
        System.out.println(TestListener.cancelCount.get());
    }
    
    private static void testNormal(int n, int v) {
        for(int i = 0; i < n; ++i) {
            ListenableFutureTask<Integer> callable = normal(v);
            ExecutorService executor = Executors.newFixedThreadPool(15);
            for(int t = 0; t < 20; ++t) {
                executor.execute(() -> {
                    callable.addListener(new TestListener());
                });
            }
            callable.run();
            
            executor.shutdown();
            try {
                if(!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.out.println("fail to wait termination");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private static void testException(int n) {
        for(int i = 0; i < n; ++i) {
            ListenableFutureTask<Integer> callable = exception();
            ExecutorService executor = Executors.newFixedThreadPool(15);
            for(int t = 0; t < 20; ++t) {
                executor.execute(() -> {
                    callable.addListener(new TestListener());
                });
            }
            callable.run();
            
            executor.shutdown();
            try {
                if(!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.out.println("fail to wait termination");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private static void testCancel(int n) {
        for(int i = 0; i < n; ++i) {
            ListenableFutureTask<Integer> callable = normal(0);
            ExecutorService executor = Executors.newFixedThreadPool(15);
            for(int t = 0; t < 20; ++t) {
                executor.execute(() -> {
                    callable.addListener(new TestListener());
                });
            }
            callable.cancel(false);
            
            executor.shutdown();
            try {
                if(!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.out.println("fail to wait termination");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private static ListenableFutureTask<Integer> normal(int v) {
        return new ListenableFutureTask<>(() -> v);
    }
    
    private static ListenableFutureTask<Integer> exception() {
        return new ListenableFutureTask<>(() -> {
            throw new RuntimeException();
        });
    }
}
