/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 循环栅栏，构造时设定等待线程数，当所有线程都到达栅栏后，栅栏放行；
 * 其内部通过ReentrantLock和Condition实现同步.
 *
 * 这个类的功能和我们之前介绍的CountDownLatch有些类似。我们知道，CountDownLatch是一个倒数计数器，
 * 在计数器不为0时，所有调用await的线程都会等待，当计数器降为0，线程才会继续执行，且计数器一旦变为0，
 * 就不能再重置了。
 * CyclicBarrier可以认为是一个栅栏，栅栏的作用是什么？就是阻挡前行。
 * 顾名思义，CyclicBarrier是一个可以循环使用的栅栏，它做的事情就是：
 * 让线程到达栅栏时被阻塞(调用await方法)，直到到达栅栏的线程数满足指定数量要求时，栅栏才会打开放行。
 *
 * A synchronization aid that allows a set of threads to all wait for
 * each other to reach a common barrier point.  CyclicBarriers are
 * useful in programs involving a fixed sized party of threads that
 * must occasionally wait for each other. The barrier is called
 * <em>cyclic</em> because it can be re-used after the waiting threads
 * are released.
 *
 * <p>A {@code CyclicBarrier} supports an optional {@link Runnable} command
 * that is run once per barrier point, after the last thread in the party
 * arrives, but before any threads are released.
 * This <em>barrier action</em> is useful
 * for updating shared-state before any of the parties continue.
 *
 * <p><b>Sample usage:</b> Here is an example of using a barrier in a
 * parallel decomposition design:
 *
 *  <pre> {@code
 * class Solver {
 *   final int N;
 *   final float[][] data;
 *   final CyclicBarrier barrier;
 *
 *   class Worker implements Runnable {
 *     int myRow;
 *     Worker(int row) { myRow = row; }
 *     public void run() {
 *       while (!done()) {
 *         processRow(myRow);
 *
 *         try {
 *           barrier.await();
 *         } catch (InterruptedException ex) {
 *           return;
 *         } catch (BrokenBarrierException ex) {
 *           return;
 *         }
 *       }
 *     }
 *   }
 *
 *   public Solver(float[][] matrix) {
 *     data = matrix;
 *     N = matrix.length;
 *     Runnable barrierAction =
 *       new Runnable() { public void run() { mergeRows(...); }};
 *     barrier = new CyclicBarrier(N, barrierAction);
 *
 *     List<Thread> threads = new ArrayList<Thread>(N);
 *     for (int i = 0; i < N; i++) {
 *       Thread thread = new Thread(new Worker(i));
 *       threads.add(thread);
 *       thread.start();
 *     }
 *
 *     // wait until done
 *     for (Thread thread : threads)
 *       thread.join();
 *   }
 * }}</pre>
 *
 * Here, each worker thread processes a row of the matrix then waits at the
 * barrier until all rows have been processed. When all rows are processed
 * the supplied {@link Runnable} barrier action is executed and merges the
 * rows. If the merger
 * determines that a solution has been found then {@code done()} will return
 * {@code true} and each worker will terminate.
 *
 * <p>If the barrier action does not rely on the parties being suspended when
 * it is executed, then any of the threads in the party could execute that
 * action when it is released. To facilitate this, each invocation of
 * {@link #await} returns the arrival index of that thread at the barrier.
 * You can then choose which thread should execute the barrier action, for
 * example:
 *  <pre> {@code
 * if (barrier.await() == 0) {
 *   // log the completion of this iteration
 * }}</pre>
 *
 * <p>The {@code CyclicBarrier} uses an all-or-none breakage model
 * for failed synchronization attempts: If a thread leaves a barrier
 * point prematurely because of interruption, failure, or timeout, all
 * other threads waiting at that barrier point will also leave
 * abnormally via {@link BrokenBarrierException} (or
 * {@link InterruptedException} if they too were interrupted at about
 * the same time).
 *
 * <p>Memory consistency effects: Actions in a thread prior to calling
 * {@code await()}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions that are part of the barrier action, which in turn
 * <i>happen-before</i> actions following a successful return from the
 * corresponding {@code await()} in other threads.
 *
 * @since 1.5
 * @see CountDownLatch
 *
 * @author Doug Lea
 */
public class CyclicBarrier {
    /**
     * 由于CyclicBarrier是可以循环复用的，
     * 所以用一个内部类Generation表示每一轮的Cyclibarrier的运行状态
     * 我们知道，CyclicBarrier 是可以循环复用的，所以CyclicBarrier 的每一轮任务都需要对应一个generation 对象。
     * generation 对象内部有个broken字段，用来标识当前轮次的CyclicBarrier 是否已经损坏。
     * nextGeneration方法用来创建一个新的generation 对象，并唤醒所有等待线程，重置内部参数。
     *
     *
     * Each use of the barrier is represented as a generation instance.
     * The generation changes whenever the barrier is tripped, or
     * is reset. There can be many generations associated with threads
     * using the barrier - due to the non-deterministic way the lock
     * may be allocated to waiting threads - but only one of these
     * can be active at a time (the one to which {@code count} applies)
     * and all the rest are either broken or tripped.
     * There need not be an active generation if there has been a break
     * but no subsequent reset.
     */
    private static class Generation {
        //broken为true表示栅栏损坏
        boolean broken = false;
    }

    /** The lock for guarding barrier entry */
    private final ReentrantLock lock = new ReentrantLock();
    /** Condition to wait on until tripped */
    private final Condition trip = lock.newCondition();
    /**
     * The number of parties
     * 剩余未到达的线程总数
     * 栅栏开启需要的到达线程总数
     */
    private final int parties;
    /*
     * The command to run when tripped
     * 最后一个线程到达后执行的任务
     * // 最后一个线程到达后执行的任务
     */
    private final Runnable barrierCommand;
    /**
     * The current generation
     * 当前轮次的运行状态
     */
    private Generation generation = new Generation();

    /**
     * Number of parties still waiting. Counts down from parties to 0
     * on each generation.  It is reset to parties on each new
     * generation or when broken.
     * 剩余未到达的线程总数
     */
    private int count;

    /**
     * 唤醒所有等待的线程，并开启下一轮
     * Updates state on barrier trip and wakes up everyone.
     * Called only while holding lock.
     */
    private void nextGeneration() {
        // signal completion of last generation
        //唤醒已经到达并正在等待的线程
        trip.signalAll();
        // set up next generation
        //重置未到达的线程数
        //创建一个新的Generation对象，表示下一轮的开始
        count = parties;
        generation = new Generation();
    }

    /**
     * Sets current barrier generation as broken and wakes up everyone.
     * Called only while holding lock.
     * 破坏栅栏
     */
    private void breakBarrier() {
        //置当前轮对应的Generation对象的状态为Broken
        generation.broken = true;
        //重置未达到的线程数
        count = parties;
        //唤醒所有线程
        trip.signalAll();
    }

    /**
     *
     * dowait方法并不复杂，一共有3部分：
     *
     * 判断栅栏是否已经损坏或当前线程已经被中断，如果是会分别抛出异常；
     * 如果当前线程是最后一个到达的线程，会尝试执行最终任务（如果构造CyclicBarrier对象时有传入
     * Runnable的话），执行成功即返回，失败会破坏栅栏；
     * 对于不是最后一个到达的线程，会在Condition队列上等待，为了防止被意外唤醒，这里用了一个自旋操作。
     *
     * Main barrier code, covering the various policies.
     * BrokenBarrierException表示当前的CyclicBarrier已经损坏了，
     * 可能等不到所有线程都到达栅栏了，所以已经在等待的线程也没必要再等了，可以散伙了。
     *
     * 出现以下几种情况之一时，当前等待线程会抛出BrokenBarrierException异常：
     * 1)其它某个正在await等待的线程被中断了
     * 2)其它某个正在await等待的线程超时了
     * 3)某个线程重置了CyclicBarrier(调用了reset方法，后面会讲到)
     *
     * 另外，只要正在Barrier上等待的任一线程抛出了异常，那么Barrier就会认为肯定是凑不齐所有线程了，
     * 就会将栅栏置为损坏（Broken）状态，并传播BrokenBarrierException
     * 给其它所有正在等待（await）的线程。
     */
    private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,
               TimeoutException {
        final ReentrantLock lock = this.lock;
        //加锁，控制对内部资源的访问
        lock.lock();
        try {
            final Generation g = generation;

            //如果展览已经损坏，则抛出异常
            if (g.broken)
                throw new BrokenBarrierException();

            //如果当前调用线程呗中断
            if (Thread.interrupted()) {
                //破坏栅栏，唤醒素有已经等待的线程
                breakBarrier();
                throw new InterruptedException();
            }

            //剩余未达到线程数-1，index表示当前线程
            int index = --count;
            //index == 0 说明当前线程Wie最后一个到达线程
            if (index == 0) {  // tripped
                //用来表示中种的任务是否执行成功
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand;
                    if (command != null)
                        //执行最终的任务
                        command.run();
                    ranAction = true;
                    //唤醒所度鞥带线程并开启下一轮
                    nextGeneration();
                    return 0;
                } finally {
                    //任务执行失败
                    //破坏栅栏，唤醒所有已经等待的线程
                    if (!ranAction)
                        breakBarrier();
                }
            }

            // loop until tripped, broken, interrupted, or timed out
            //自旋，放置线程被意外唤醒
            for (;;) {
                try {
                    //不需要限时等待
                    if (!timed)
                        //直接在Condition对象上等待
                        trip.await();
                    //需要限时等待
                    else if (nanos > 0L)
                        nanos = trip.awaitNanos(nanos);
                    //等待过程中被中断
                } catch (InterruptedException ie) {
                    //说明当前这轮还没偶遇结束，没有其他线程执行过breakBarrier方法
                    if (g == generation && ! g.broken) {
                        breakBarrier();
                        throw ie;
                        //说明当前这轮已经结束，或有其他线程执行过breakBarrier方法
                    } else {
                        // We're about to finish waiting even if we had not
                        // been interrupted, so this interrupt is deemed to
                        // "belong" to subsequent execution.
                        //自行中断
                        Thread.currentThread().interrupt();
                    }
                }

                if (g.broken)
                    throw new BrokenBarrierException();

                if (g != generation)
                    return index;
                //等待超时
                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and which
     * will execute the given barrier action when the barrier is tripped,
     * performed by the last thread entering the barrier.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     *                栅栏开启需要的到达线程总数
     * @param barrierAction the command to execute when the barrier is
     *        tripped, or {@code null} if there is no action
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }

    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and
     * does not perform a predefined action when the barrier is tripped.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    /**
     * Returns the number of parties required to trip this barrier.
     *
     * @return the number of parties required to trip this barrier
     */
    public int getParties() {
        return parties;
    }

    /**
     * Waits until all {@linkplain #getParties parties} have invoked
     * {@code await} on this barrier.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * one of the other waiting threads; or
     * <li>Some other thread times out while waiting for barrier; or
     * <li>Some other thread invokes {@link #reset} on this barrier.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting,
     * or if the barrier {@linkplain #isBroken is broken} when
     * {@code await} is invoked, or while any thread is waiting, then
     * {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@linkplain Thread#interrupt interrupted} while waiting,
     * then all other waiting threads will throw
     * {@link BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a
     * non-null barrier action was supplied in the constructor, then the
     * current thread runs the action before allowing the other threads to
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread and the barrier is placed in
     * the broken state.
     *
     * @return the arrival index of the current thread, where index
     *         {@code getParties() - 1} indicates the first
     *         to arrive and zero indicates the last to arrive
     * @throws InterruptedException if the current thread was interrupted
     *         while waiting
     * @throws BrokenBarrierException if <em>another</em> thread was
     *         interrupted or timed out while the current thread was
     *         waiting, or the barrier was reset, or the barrier was
     *         broken when {@code await} was called, or the barrier
     *         action (if present) failed due to an exception
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }

    /**
     * Waits until all {@linkplain #getParties parties} have invoked
     * {@code await} on this barrier, or the specified waiting time elapses.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>The specified timeout elapses; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * one of the other waiting threads; or
     * <li>Some other thread times out while waiting for barrier; or
     * <li>Some other thread invokes {@link #reset} on this barrier.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then {@link TimeoutException}
     * is thrown. If the time is less than or equal to zero, the
     * method will not wait at all.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting,
     * or if the barrier {@linkplain #isBroken is broken} when
     * {@code await} is invoked, or while any thread is waiting, then
     * {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@linkplain Thread#interrupt interrupted} while
     * waiting, then all other waiting threads will throw {@link
     * BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a
     * non-null barrier action was supplied in the constructor, then the
     * current thread runs the action before allowing the other threads to
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread and the barrier is placed in
     * the broken state.
     *
     * @param timeout the time to wait for the barrier
     * @param unit the time unit of the timeout parameter
     * @return the arrival index of the current thread, where index
     *         {@code getParties() - 1} indicates the first
     *         to arrive and zero indicates the last to arrive
     * @throws InterruptedException if the current thread was interrupted
     *         while waiting
     * @throws TimeoutException if the specified timeout elapses.
     *         In this case the barrier will be broken.
     * @throws BrokenBarrierException if <em>another</em> thread was
     *         interrupted or timed out while the current thread was
     *         waiting, or the barrier was reset, or the barrier was broken
     *         when {@code await} was called, or the barrier action (if
     *         present) failed due to an exception
     */
    public int await(long timeout, TimeUnit unit)
        throws InterruptedException,
               BrokenBarrierException,
               TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }

    /**
     * Queries if this barrier is in a broken state.
     *
     * @return {@code true} if one or more parties broke out of this
     *         barrier due to interruption or timeout since
     *         construction or the last reset, or a barrier action
     *         failed due to an exception; {@code false} otherwise.
     */
    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the barrier to its initial state.  If any parties are
     * currently waiting at the barrier, they will return with a
     * {@link BrokenBarrierException}. Note that resets <em>after</em>
     * a breakage has occurred for other reasons can be complicated to
     * carry out; threads need to re-synchronize in some other way,
     * and choose one to perform the reset.  It may be preferable to
     * instead create a new barrier for subsequent use.
     */
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // break the current generation
            nextGeneration(); // start a new generation
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of parties currently waiting at the barrier.
     * This method is primarily useful for debugging and assertions.
     *
     * @return the number of parties currently blocked in {@link #await}
     */
    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
}
