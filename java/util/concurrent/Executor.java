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

/**
 * 执行已提交的{@link Runnable}任务的对象。此接口提供了一种将任务提交与每个任务的运行机制（包括线程使用，调度的详细信息）分离的方式。
 * 通常使用{@code Executor}而不是显式创建线程。例如，可以为每个任务集调用{@code new Thread(new(RunnableTask())).start()}，
 * 而不必使用：
 *  Executor executor = anExecutor;
 *  executor.execute(new RunnableTask1());
 *  executor.execute(new RunnableTask2());
 *
 * An object that executes submitted {@link Runnable} tasks. This
 * interface provides a way of decoupling task submission from the
 * mechanics of how each task will be run, including details of thread
 * use, scheduling, etc.  An {@code Executor} is normally used
 * instead of explicitly creating threads. For example, rather than
 * invoking {@code new Thread(new(RunnableTask())).start()} for each
 * of a set of tasks, you might use:
 *
 * <pre>
 * Executor executor = <em>anExecutor</em>;
 * executor.execute(new RunnableTask1());
 * executor.execute(new RunnableTask2());
 * ...
 * </pre>
 *
 * 但是，{@code Executor}接口并不严格要求执行是异步的。在最简单的情况下，执行者可以在调用者的线程中立即运行提交的任务：
 * {@code
 * class DirectExecutor implements Executor {
 *      public void execute(Runnable r) {
 *          r.run();
 *      }
 * }}
 *
 * However, the {@code Executor} interface does not strictly
 * require that execution be asynchronous. In the simplest case, an
 * executor can run the submitted task immediately in the caller's
 * thread:
 *
 *  <pre> {@code
 * class DirectExecutor implements Executor {
 *   public void execute(Runnable r) {
 *     r.run();
 *   }
 * }}</pre>
 *
 *
 * 更典型地，任务在其他线程中执行而不是调用者的线程。下面的执行程序产生一个新线程用于每个任务。
 * {@code
 *  class ThreadPerTaskExecutor implements Executor {
 *     public void execute(Runnable r) {
 *       new Thread(r).start();
 *     }
 *  }}
 *
 * More typically, tasks are executed in some thread other
 * than the caller's thread.  The executor below spawns a new thread
 * for each task.
 *
 *  <pre> {@code
 * class ThreadPerTaskExecutor implements Executor {
 *   public void execute(Runnable r) {
 *     new Thread(r).start();
 *   }
 * }}</pre>
 *
 *
 * 许多{@code Executor}实现对计划任务的方式和时间施加了某种限制。下面的执行程序将任务提交序列化到第二个执行程序，说明了一个复合执行程序。
 *
 *
 * Many {@code Executor} implementations impose some sort of
 * limitation on how and when tasks are scheduled.  The executor below
 * serializes the submission of tasks to a second executor,
 * illustrating a composite executor.
 *
 *  <pre> {@code
 * class SerialExecutor implements Executor {
 *   final Queue<Runnable> tasks = new ArrayDeque<Runnable>();
 *   final Executor executor;
 *   Runnable active;
 *
 *   SerialExecutor(Executor executor) {
 *     this.executor = executor;
 *   }
 *
 *   public synchronized void execute(final Runnable r) {
 *     tasks.offer(new Runnable() {
 *       public void run() {
 *         try {
 *           r.run();
 *         } finally {
 *           scheduleNext();
 *         }
 *       }
 *     });
 *     if (active == null) {
 *       scheduleNext();
 *     }
 *   }
 *
 *   protected synchronized void scheduleNext() {
 *     if ((active = tasks.poll()) != null) {
 *       executor.execute(active);
 *     }
 *   }
 * }}</pre>
 *
 *
 * 此软件包中提供的{@code Executor}实现实现{@link ExecutorService}，这是一个更广泛的接口。
 * {@link ThreadPoolExecutor}类提供了可扩展的线程池实现。 {@link Executors}类为这些Executor提供了方便的工厂方法。
 *
 * The {@code Executor} implementations provided in this package
 * implement {@link ExecutorService}, which is a more extensive
 * interface.  The {@link ThreadPoolExecutor} class provides an
 * extensible thread pool implementation. The {@link Executors} class
 * provides convenient factory methods for these Executors.
 *
 * 内存一致性影响：在将{@code Runnable}对象提交给{@code Executor}对象之前，线程中的操作可能在另一个线程中开始执行。
 * <p>Memory consistency effects: Actions in a thread prior to
 * submitting a {@code Runnable} object to an {@code Executor}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * its execution begins, perhaps in another thread.
 *
 * @since 1.5
 * @author Doug Lea
 */
public interface Executor {

    /**
     * 在将来的某个时间执行给定命令。根据{@code Executor}实现的判断，命令可以在新线程中、线程池中或在调用线程中执行。
     * Executes the given command at some time in the future.  The command
     * may execute in a new thread, in a pooled thread, or in the calling
     * thread, at the discretion of the {@code Executor} implementation.
     *
     * @param command the runnable task
     * @throws RejectedExecutionException if this task cannot be
     * accepted for execution
     * @throws NullPointerException if command is null
     */
    void execute(Runnable command);
}
