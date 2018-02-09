package com.gerald.concurrency.futuretask;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


public class SettableFutureTask<V> extends ListenableFutureTask<V> {
    private static final Runnable INVALID_RUNNANBLE = () -> {
        throw new UnsupportedOperationException();
    };
    
    public SettableFutureTask() {
        super(INVALID_RUNNANBLE, null);
    }
    
    public void set(V result) {
        super.set(result);
    }
    
    public void setException(Throwable t) {
        super.setException(t);
    }
    
    //====================测试代码=====================
    
    public static void main(String[] args) throws InterruptedException {
        SettableFutureTask<Integer> task = new SettableFutureTask<>();
        
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    System.out.println("wait get to return");
                    Integer result = task.get();
                    System.out.println("result = " + result);
                } catch (InterruptedException e) {
                    System.out.println("task#get is interrupt");
                } catch (ExecutionException e) {
                    System.out.println("task throw exception, msg = " + e.getMessage());
                }
            }
        };
        
        t.setDaemon(false);
        t.start();
        
        for(int i = 5; i >= 0; --i) {
            System.out.println("count down = " + i);
            TimeUnit.SECONDS.sleep(1);
        }
        
        task.setException(new InterruptedException("task is interrupted"));
    }
}
