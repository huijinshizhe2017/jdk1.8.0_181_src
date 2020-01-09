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
import java.util.List;
import java.util.Collection;

/**
 * 一个{@link Executor}扩展者，用于提供管理可以产生{@link Future}用于跟踪进度的方法一个或多个异步任务的终止和终止的方法。
 * An {@link Executor} that provides methods to manage termination and
 * methods that can produce a {@link Future} for tracking progress of
 * one or more asynchronous tasks.
 *
 * 可以关闭{@code ExecutorService}，这将导致它拒绝新任务。提供了两种不同的方法来关闭{@code ExecutorService}。
 * {@link #shutdown}方法将允许先前提交的任务在终止之前执行，而{@link #shutdownNow}方法可防止等待的任务启动并尝试停止当前正在执行的任务。
 * 终止后，执行者将没有正在执行的任务，没有正在等待执行的任务，并且无法提交新任务。应该关闭未使用的{@code ExecutorService}以便回收其资源。
 * <p>An {@code ExecutorService} can be shut down, which will cause
 * it to reject new tasks.  Two different methods are provided for
 * shutting down an {@code ExecutorService}. The {@link #shutdown}
 * method will allow previously submitted tasks to execute before
 * terminating, while the {@link #shutdownNow} method prevents waiting
 * tasks from starting and attempts to stop currently executing tasks.
 * Upon termination, an executor has no tasks actively executing, no
 * tasks awaiting execution, and no new tasks can be submitted.  An
 * unused {@code ExecutorService} should be shut down to allow
 * reclamation of its resources.
 *
 * 方法{@code Submit}通过创建并返回可用于取消执行和/或等待完成的{@link Future}来扩展基本方法{@link Executor＃execute(Runnable)}。
 * 方法{@code invokeAny}和{@code invokeAll}执行最常用的批量执行形式，执行一组任务，然后等待至少一个或全部完成。
 * (类{@link ExecutorCompletionService}可用于编写这些方法的自定义变量。)
 * <p>Method {@code submit} extends base method {@link
 * Executor#execute(Runnable)} by creating and returning a {@link Future}
 * that can be used to cancel execution and/or wait for completion.
 * Methods {@code invokeAny} and {@code invokeAll} perform the most
 * commonly useful forms of bulk execution, executing a collection of
 * tasks and then waiting for at least one, or all, to
 * complete. (Class {@link ExecutorCompletionService} can be used to
 * write customized variants of these methods.)
 *
 * {@link Executors}类为此包中提供的执行程序服务提供了工厂方法。
 * <p>The {@link Executors} class provides factory methods for the
 * executor services provided in this package.
 *
 * <h3>Usage Examples</h3>
 *
 * 这是网络服务的示意图，其中线程池中的线程服务传入的请求。它使用预先配置的{@link Executors＃newFixedThreadPool}工厂方法：
 * Here is a sketch of a network service in which threads in a thread
 * pool service incoming requests. It uses the preconfigured {@link
 * Executors#newFixedThreadPool} factory method:
 *
 *  <pre> {@code
 * class NetworkService implements Runnable {
 *   private final ServerSocket serverSocket;
 *   private final ExecutorService pool;
 *
 *   public NetworkService(int port, int poolSize)
 *       throws IOException {
 *     serverSocket = new ServerSocket(port);
 *     pool = Executors.newFixedThreadPool(poolSize);
 *   }
 *
 *   public void run() { // run the service
 *     try {
 *       for (;;) {
 *         pool.execute(new Handler(serverSocket.accept()));
 *       }
 *     } catch (IOException ex) {
 *       pool.shutdown();
 *     }
 *   }
 * }
 *
 * class Handler implements Runnable {
 *   private final Socket socket;
 *   Handler(Socket socket) { this.socket = socket; }
 *   public void run() {
 *     // read and service request on socket
 *   }
 * }}</pre>
 *
 * 以下方法分两个阶段关闭{@code ExecutorService}：首先通过调用{@code shutdown}拒绝传入的任务，然后调用{@code shutdownNow}
 * (如有必要)以取消所有未完成的任务：
 * The following method shuts down an {@code ExecutorService} in two phases,
 * first by calling {@code shutdown} to reject incoming tasks, and then
 * calling {@code shutdownNow}, if necessary, to cancel any lingering tasks:
 *
 *  <pre> {@code
 * void shutdownAndAwaitTermination(ExecutorService pool) {
 *   pool.shutdown(); // Disable new tasks from being submitted
 *   try {
 *     // Wait a while for existing tasks to terminate
 *     if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
 *       pool.shutdownNow(); // Cancel currently executing tasks
 *       // Wait a while for tasks to respond to being cancelled
 *       if (!pool.awaitTermination(60, TimeUnit.SECONDS))
 *           System.err.println("Pool did not terminate");
 *     }
 *   } catch (InterruptedException ie) {
 *     // (Re-)Cancel if current thread also interrupted
 *     pool.shutdownNow();
 *     // Preserve interrupt status
 *     Thread.currentThread().interrupt();
 *   }
 * }}</pre>
 *
 * 内存一致性影响：在将{@code Runnable}或{@code Callable}任务提交到{@code ExecutorService}之前,
 * 线程中的操作在该任务执行的任何操作之前发生，而在该任务执行的任何操作之前发生。通过{@code Future.get()}检索结果。
 * <p>Memory consistency effects: Actions in a thread prior to the
 * submission of a {@code Runnable} or {@code Callable} task to an
 * {@code ExecutorService}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * any actions taken by that task, which in turn <i>happen-before</i> the
 * result is retrieved via {@code Future.get()}.
 *
 * @since 1.5
 * @author Doug Lea
 */
public interface ExecutorService extends Executor {

    /**
     * 启动有序关闭，在该关闭中执行先前提交的任务，但不接受任何新任务。如果已关闭，则调用不会产生任何其他影响。
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * 此方法不等待先前提交的任务完成执行。使用{@link #awaitTermination awaitTermination}可以做到这一点。
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * @throws SecurityException if a security manager exists and
     *         shutting down this ExecutorService may manipulate
     *         threads that the caller is not permitted to modify
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")},
     *         or the security manager's {@code checkAccess} method
     *         denies access.
     *         如果存在安全管理器并关闭此ExecutorService，则它可能会操纵不允许调用者修改的线程，
     *         因为该调用者不持有{@link java.lang.RuntimePermission}{@code ("modifyThread")}，
     *         或者安全管理器的{@code checkAccess}方法拒绝访问。
     */
    void shutdown();

    /**
     * 尝试停止所有正在执行的任务，暂停正在等待的任务的处理，并返回正在等待执行的任务的列表。
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution.
     *
     * 此方法不等待主动执行的任务终止。使用{@link #awaitTermination awaitTermination}可以做到这一点。
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * 除了尽最大努力尝试停止处理正在执行的任务之外，没有任何保证。例如，典型的实现将通过{@link Thread＃interrupt}取消，
     * 因此任何无法响应中断的任务都可能永远不会终止。
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  For example, typical
     * implementations will cancel via {@link Thread#interrupt}, so any
     * task that fails to respond to interrupts may never terminate.
     *
     * @return list of tasks that never commenced execution
     *          从未开始执行的任务列表
     * @throws SecurityException if a security manager exists and
     *         shutting down this ExecutorService may manipulate
     *         threads that the caller is not permitted to modify
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}{@code ("modifyThread")},
     *         or the security manager's {@code checkAccess} method
     *         denies access.
     *         如果存在安全管理器并关闭此ExecutorService，则它可能会操纵不允许调用者修改的线程，
     *         因为该调用者不持有{@link java.lang.RuntimePermission}{@code ("modifyThread")}，
     *         或者安全管理器的{@code checkAccess}方法拒绝访问。
     */
    List<Runnable> shutdownNow();

    /**
     * Returns {@code true} if this executor has been shut down.
     *                      如果该执行器已关闭。
     *
     */
    boolean isShutdown();

    /**
     * 如果所有任务在关闭后都已完成，则返回{@code true}。请注意，除非先调用{@code shutdown}或{@code shutdownNow}，
     * 否则{@code isTerminated}永远不会是{@code true}。
     * Returns {@code true} if all tasks have completed following shut down.
     * Note that {@code isTerminated} is never {@code true} unless
     * either {@code shutdown} or {@code shutdownNow} was called first.
     *
     * @return {@code true} if all tasks have completed following shut down
     *                      如果关闭后所有任务都已完成
     */
    boolean isTerminated();

    /**
     * 阻塞直到关闭请求后所有任务完成执行，或者发生超时，或者当前线程被中断（以先发生的为准）。
     * Blocks until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current thread is
     * interrupted, whichever happens first.
     *
     * @param timeout the maximum time to wait 等待的最长时间
     * @param unit the time unit of the timeout argument 超时参数的时间单位
     * @return {@code true} if this executor terminated and 如果该执行人终止，并且
     *         {@code false} if the timeout elapsed before termination 如果终止前超时
     * @throws InterruptedException if interrupted while waiting 如果在等待时被打断
     */
    boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;

    /**
     * 提交要执行的返回值任务，并返回表示任务的未决结果的Future。 Future的{@code get}方法将在成功完成后返回任务的结果。
     * Submits a value-returning task for execution and returns a
     * Future representing the pending results of the task. The
     * Future's {@code get} method will return the task's result upon
     * successful completion.
     *
     * 如果您希望立即阻止等待任务，则可以使用{@code result = exec.submit（aCallable）.get（）;}形式的构造
     * <p>
     * If you would like to immediately block waiting
     * for a task, you can use constructions of the form
     * {@code result = exec.submit(aCallable).get();}
     *
     * 注意：{@link Executors}类包含一组方法，这些方法可以将其他一些类似闭包的常见对象转换为{@link Callable}形式，
     * 例如将{@link java.security.PrivilegedAction}转换为{@link Callable}形式。
     * <p>Note: The {@link Executors} class includes a set of methods
     * that can convert some other common closure-like objects,
     * for example, {@link java.security.PrivilegedAction} to
     * {@link Callable} form so they can be submitted.
     *
     * @param task the task to submit 提交任务
     * @param <T> the type of the task's result 任务结果的类型
     * @return a Future representing pending completion of the task 代表待完成的任务
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     *         如果无法安排任务执行
     * @throws NullPointerException if the task is null 如果任务为空
     */
    <T> Future<T> submit(Callable<T> task);

    /**
     * 提交一个Runnable任务以执行并返回一个表示该任务的Future。 Future的{@code get}方法将在成功完成后返回给定的结果。
     * Submits a Runnable task for execution and returns a Future
     * representing that task. The Future's {@code get} method will
     * return the given result upon successful completion.
     *
     * @param task the task to submit
     * @param result the result to return
     * @param <T> the type of the result
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if the task is null
     */
    <T> Future<T> submit(Runnable task, T result);

    /**
     * 提交一个Runnable任务以执行并返回一个表示该任务的Future。 成功完成时，Future的{@code get}方法将返回{@code null}。
     * Submits a Runnable task for execution and returns a Future
     * representing that task. The Future's {@code get} method will
     * return {@code null} upon <em>successful</em> completion.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if the task is null
     */
    Future<?> submit(Runnable task);

    /**
     * 执行给定的任务，并在所有任务完成时返回保存其状态和结果的期货列表。对于返回列表的每个元素，{@link Future＃isDone}为{@code true}。
     * 请注意，已完成的任务可能已正常终止，也可能引发异常。如果在进行此操作时修改了给定的集合，则此方法的结果不确定。
     * Executes the given tasks, returning a list of Futures holding
     * their status and results when all complete.
     * {@link Future#isDone} is {@code true} for each
     * element of the returned list.
     * Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return a list of Futures representing the tasks, in the same
     *         sequential order as produced by the iterator for the
     *         given task list, each of which has completed
     *         代表任务的期货列表，其顺序与迭代器为给定任务列表生成的顺序相同，每个任务均已完成
     * @throws InterruptedException if interrupted while waiting, in
     *         which case unfinished tasks are cancelled
     * @throws NullPointerException if tasks or any of its elements are {@code null}
     * @throws RejectedExecutionException if any task cannot be
     *         scheduled for execution
     */
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException;

    /**
     * 执行给定的任务，并在所有任务完成或超时到期时（以先发生者为准）返回持有其状态和结果的期货列表。
     * 对于返回列表的每个元素，{@link Future＃isDone}为{@code true}。返回时，尚未完成的任务将被取消。
     * 请注意，已完成任务可能已正常终止或引发异常而终止。如果在进行此操作时修改了给定的集合，则此方法的结果不确定。
     * Executes the given tasks, returning a list of Futures holding
     * their status and results
     * when all complete or the timeout expires, whichever happens first.
     * {@link Future#isDone} is {@code true} for each
     * element of the returned list.
     * Upon return, tasks that have not completed are cancelled.
     * Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @param <T> the type of the values returned from the tasks
     * @return a list of Futures representing the tasks, in the same
     *         sequential order as produced by the iterator for the
     *         given task list. If the operation did not time out,
     *         each task will have completed. If it did time out, some
     *         of these tasks will not have completed.
     *         代表任务的期货列表，与给定任务列表相同。如果操作没有超时，则每个任务都将完成。如果超时，则其中一些任务将无法完成。
     * @throws InterruptedException if interrupted while waiting, in
     *         which case unfinished tasks are cancelled
     * @throws NullPointerException if tasks, any of its elements, or
     *         unit are {@code null}
     * @throws RejectedExecutionException if any task cannot be scheduled
     *         for execution
     */
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                  long timeout, TimeUnit unit)
        throws InterruptedException;

    /**
     * 执行给定的任务，如果成功，则返回成功完成任务（即不引发异常）的结果。在正常或异常返回时，尚未完成的任务将被取消。
     * 如果在进行此操作时修改了给定的集合，则此方法的结果不确定。
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do. Upon normal or exceptional return,
     * tasks that have not completed are cancelled.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return the result returned by one of the tasks
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks or any element task
     *         subject to execution is {@code null}
     * @throws IllegalArgumentException if tasks is empty
     * @throws ExecutionException if no task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled
     *         for execution
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException;

    /**
     * 执行给定的任务，如果在给定的超时时间过去之前完成任务，则返回成功完成任务的结果(即没有引发异常)。
     * 在正常或异常返回时，尚未完成的任务将被取消。如果在进行此操作时修改了给定的集合，则此方法的结果不确定。
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do before the given timeout elapses.
     * Upon normal or exceptional return, tasks that have not
     * completed are cancelled.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @param <T> the type of the values returned from the tasks
     * @return the result returned by one of the tasks
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks, or unit, or any element
     *         task subject to execution is {@code null}
     * @throws TimeoutException if the given timeout elapses before
     *         any task successfully completes
     * @throws ExecutionException if no task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled
     *         for execution
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                    long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
}
