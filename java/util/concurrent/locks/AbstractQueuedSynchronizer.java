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

package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import sun.misc.Unsafe;

/**
 * AbstractQueuedSynchronizer抽象类（以下简称AQS）是整个java.util.concurrent包的核心。
 * 在JDK1.5时，Doug Lea引入了J.U.C包，该包中的大多数同步器都是基于AQS来构建的。
 * AQS框架提供了一套通用的机制来管理同步状态（synchronization state）、阻塞/唤醒线程、管理等待队列。
 * 我们所熟知的ReentrantLock、CountDownLatch、CyclicBarrier等同步器，其实都是通过内部类实现了AQS框架暴露的API，
 * 以此实现各类同步器功能。这些同步器的主要区别其实就是对同步状态（synchronization state）的定义不同。
 * AQS框架，分离了构建同步器时的一系列关注点，它的所有操作都围绕着资源——同步状态（synchronization state）来展开，并替用户解决了如下问题：
 * 资源是可以被同时访问？还是在同一时间只能被一个线程访问？（共享/独占功能）
 * 访问资源的线程如何进行并发管理？（等待队列）
 * 如果线程等不及资源了，如何从等待队列退出？（超时/中断）
 * 这其实是一种典型的模板方法设计模式：父类（AQS框架）定义好骨架和内部操作细节，具体规则由子类去实现。
 *
 * ReentrantLock	        资源表示独占锁。State为0表示锁可用；为1表示被占用；为N表示重入的次数
 * CountDownLatch	        资源表示倒数计数器。State为0表示计数器归零，所有线程都可以访问资源；为N表示计数器未归零，所有线程都需要阻塞。
 * Semaphore	            资源表示信号量或者令牌。State≤0表示没有令牌可用，所有线程都需要阻塞；大于0表示由令牌可用，线程每获取一个令牌，
 *                          State减1，线程没释放一个令牌，State加1。
 * ReentrantReadWriteLock	资源表示共享的读锁和独占的写锁。state逻辑上被分成两个16位的unsigned short，
 *                          分别记录读锁被多少线程使用和写锁被重入的次数。
 *
 * 由于并发的存在，需要考虑的情况非常多，因此能否以一种相对简单的方法来完成这两个目标就非常重要，因为对于用户（AQS框架的使用者来说），
 * 很多时候并不关心内部复杂的细节。而AQS其实就是利用模板方法模式来实现这一点，AQS中大多数方法都是final或是private的，
 * 也就是说Doug Lea并不希望用户直接使用这些方法，而是只覆写部分模板规定的方法。
 * AQS通过暴露以下API来让让用户自己解决上面提到的“如何定义资源是否可以被访问”的问题：
 * 钩子方法	                描述
 * tryAcquire	            排它获取（资源数）
 * tryRelease	            排它释放（资源数）
 * tryAcquireShared	        共享获取（资源数）
 * tryReleaseShared	        共享获取（资源数）
 * isHeldExclusively	    是否排它状态
 *
 * 支持Condition条件等待
 * Condition接口，可以看做是Obejct类的wait()、notify()、notifyAll()方法的替代品，与Lock配合使用。
 * AQS框架内部通过一个内部类ConditionObject，实现了Condition接口，以此来为子类提供条件等待的功能。
 *
 * 提供一个框架，用于实现依赖于先进先出（FIFO）等待队列的阻塞锁和相关的同步器（信号灯，事件等）。
 * 此类旨在为大多数依赖单个原子{@code int}值表示状态的同步器提供有用的基础。
 * 子类必须定义更改此状态的受保护方法，并定义该状态对于获取或释放此对象而言意味着什么。
 * 鉴于这些，此类中的其他方法将执行所有排队和阻塞机制。子类可以维护其他状态字段，
 * 但是仅跟踪关于同步的使用方法{@link #getState}，{@link #setState}
 * 和{@link #compareAndSetState}操作的原子更新的{@code int}值。
 *
 *
 * CAS操作
 * CAS，即CompareAndSet，在Java中CAS操作的实现都委托给一个名为UnSafe类，关于Unsafe类，以后会专门详细介绍该类，
 * 目前只要知道，通过该类可以实现对字段的原子操作。
 *
 * 方法名	修饰符	描述
 * compareAndSetState	        protected final	            CAS修改同步状态值
 * compareAndSetHead	        private final	            CAS修改等待队列的头指针
 * compareAndSetTail	        private final	            CAS修改等待队列的尾指针
 * compareAndSetWaitStatus	    private static final	    CAS修改结点的等待状态
 * compareAndSetNext	        private static final	    CAS修改结点的next指针
 *
 * 等待队列的核心操作
 * 方法名	                修饰符	        描述
 * enq	                private	        入队操作
 * addWaiter	        private	        入队操作
 * setHead	            private	        设置头结点
 * unparkSuccessor	    private	        唤醒后继结点
 * doReleaseShared	    private	        释放共享结点
 * setHeadAndPropagate	private	        设置头结点并传播唤醒
 *
 *
 * 资源的获取操作
 * 方法名	                            修饰符	        描述
 * cancelAcquire	                private	        取消获取资源
 * shouldParkAfterFailedAcquire	    private static	判断是否阻塞当前调用线程
 * acquireQueued	                final	        尝试获取资源,获取失败尝试阻塞线程
 * doAcquireInterruptibly	        private	        独占地获取资源（响应中断）
 * doAcquireNanos	                private	        独占地获取资源（限时等待）
 * doAcquireShared	                private	        共享地获取资源
 * doAcquireSharedInterruptibly	    private	        共享地获取资源（响应中断）
 * doAcquireSharedNanos	            private	        共享地获取资源（限时等待）
 *
 * 方法名                      	修饰符	描述
 * acquire	                    public final	独占地获取资源
 * acquireInterruptibly	        public final	独占地获取资源（响应中断）
 * acquireInterruptibly	        public final	独占地获取资源（限时等待）
 * acquireShared	            public final	共享地获取资源
 * acquireSharedInterruptibly	public final	共享地获取资源（响应中断）
 * tryAcquireSharedNanos	    public final	共享地获取资源（限时等待）
 *
 * 资源的释放操作
 * 方法名      	    修饰符	        描述
 * release	        public final	释放独占资源
 * releaseShared	public final	释放共享资源
 *
 *
 * 我们在第一节中讲到，AQS框架分离了构建同步器时的一系列关注点，它的所有操作都围绕着资源——同步状态（synchronization state）来展开因此，
 * 围绕着资源，衍生出三个基本问题：
 *
 * ->同步状态（synchronization state）的管理:同步状态，其实就是资源。AQS使用单个int（32位）来保存同步状态，
 *  并暴露出getState、setState以及compareAndSetState操作来读取和更新这个状态。
 *
 * ->阻塞/唤醒线程的操作
 *           JDK1.5之前，除了内置的监视器机制外，没有其它方法可以安全且便捷得阻塞和唤醒当前线程。
 *           JDK1.5以后，java.util.concurrent.locks包提供了LockSupport类来作为线程阻塞和唤醒的工具。
 * ->线程等待队列的管理
 * 等待队列，是AQS框架的核心，整个框架的关键其实就是如何在并发状态下管理被阻塞的线程。
 * 等待队列是严格的FIFO队列，是Craig，Landin和Hagersten锁（CLH锁）的一种变种，采用双向链表实现，因此也叫CLH队列。
 * 1. 结点定义
 * CLH队列中的结点是对线程的包装，结点一共有两种类型：独占（EXCLUSIVE）和共享（SHARED）。
 * 每种类型的结点都有一些状态，其中独占结点使用其中的CANCELLED(1)、SIGNAL(-1)、CONDITION(-2)，
 * 共享结点使用其中的CANCELLED(1)、SIGNAL(-1)、PROPAGATE(-3)。
 * 结点状态	    值	描述
 * CANCELLED	1	取消。表示后驱结点被中断或超时，需要移出队列
 * SIGNAL	    -1	发信号。表示后驱结点被阻塞了（当前结点在入队后、阻塞前，应确保将其prev结点类型改为SIGNAL，以便prev结点取消或释放时将当前结点唤醒。）
 * CONDITION	-2	Condition专用。表示当前结点在Condition队列中，因为等待某个条件而被阻塞了
 * PROPAGATE	-3	传播。适用于共享模式（比如连续的读操作结点可以依次进入临界区，设为PROPAGATE有助于实现这种迭代操作。）
 * INITIAL	    0	默认。新结点会处于这种状态
 *
 * AQS使用CLH队列实现线程的结构管理，而CLH结构正是用前一结点某一属性表示当前结点的状态，之所以这种做是因为在双向链表的结构下，
 * 这样更容易实现取消和超时功能。
 *
 * next指针：用于维护队列顺序，当临界区的资源被释放时，头结点通过next指针找到队首结点。
 * prev指针：用于在结点（线程）被取消时，让当前结点的前驱直接指向当前结点的后驱完成出队动作。
 *
 * 2. 队列定义
 * 对于CLH队列，当线程请求资源时，如果请求不到，会将线程包装成结点，将其挂载在队列尾部。
 * CLH队列的示意图如下：
 *
 * ①初始状态，队列head和tail都指向空
 *
 * ②首个线程入队，先创建一个空的头结点，然后以自旋的方式不断尝试插入一个包含当前线程的新结点
 *
 * Provides a framework for implementing blocking locks and related
 * synchronizers (semaphores, events, etc) that rely on
 * first-in-first-out (FIFO) wait queues.  This class is designed to
 * be a useful basis for most kinds of synchronizers that rely on a
 * single atomic {@code int} value to represent state. Subclasses
 * must define the protected methods that change this state, and which
 * define what that state means in terms of this object being acquired
 * or released.  Given these, the other methods in this class carry
 * out all queuing and blocking mechanics. Subclasses can maintain
 * other state fields, but only the atomically updated {@code int}
 * value manipulated using methods {@link #getState}, {@link
 * #setState} and {@link #compareAndSetState} is tracked with respect
 * to synchronization.
 *
 * 子类应该定义为用于实现其非公共类的同步属性的非公共内部帮助器类。
 * 类{@code AbstractQueuedSynchronizer}没有实现任何同步接口。
 * 相反，它定义了{@link #acquireInterruptible}之类的方法，
 * 可以通过具体的锁和相关的同步器适当地调用这些方法来实现其公共方法。
 * <p>Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties
 * of their enclosing class.  Class
 * {@code AbstractQueuedSynchronizer} does not implement any
 * synchronization interface.  Instead it defines methods such as
 * {@link #acquireInterruptibly} that can be invoked as
 * appropriate by concrete locks and related synchronizers to
 * implement their public methods.
 *
 * 这个类单一或者全部支持默认的互斥模式和共享模式。当处于互斥模式，在另外一个线程还没有成功的时候视图去尝试。
 * 共享模式要求多线程可以并不一定成功。该类不理解这些差异，只是从机械意义上说，当成功获取共享模式时，
 * 下一个等待线程（如果存在）也必须确定它是否也可以获取。
 * 在不同模式下等待的线程共享相同的FIFO队列。通常，实现子类仅支持这些模式之一，
 * 但例如可以在{@link ReadWriteLock}中发挥作用。
 * 仅支持互斥模式或仅支持共享模式的子类无需定义支持未使用模式的方法。
 * <p>This class supports either or both a default <em>exclusive</em>
 * mode and a <em>shared</em> mode. When acquired in exclusive mode,
 * attempted acquires by other threads cannot succeed. Shared mode
 * acquires by multiple threads may (but need not) succeed. This class
 * does not &quot;understand&quot; these differences except in the
 * mechanical sense that when a shared mode acquire succeeds, the next
 * waiting thread (if one exists) must also determine whether it can
 * acquire as well. Threads waiting in the different modes share the
 * same FIFO queue. Usually, implementation subclasses support only
 * one of these modes, but both can come into play for example in a
 * {@link ReadWriteLock}. Subclasses that support only exclusive or
 * only shared modes need not define the methods supporting the unused mode.
 *
 * 此类定义了一个嵌套的{@link ConditionObject}类，
 * 该类可以被支持排他模式的子类用作{@link Condition}实现，
 * 为此方法{@link #isHeldExclusively}报告是否针对当前线程专有地保持同步，
 * 使用当前{@link #getState}值调用的方法{@link #release}会完全释放此对象，
 * 并且给定已保存的状态值，{@link #acquire}最终会将此对象恢复为先前的获取状态。
 * 否则，没有{@code AbstractQueuedSynchronizer}方法会创建这样的条件，
 * 因此，如果无法满足此约束，请不要使用它。
 * {@link ConditionObject}的行为当然取决于其同步器实现的语义。
 * <p>This class defines a nested {@link ConditionObject} class that
 * can be used as a {@link Condition} implementation by subclasses
 * supporting exclusive mode for which method {@link
 * #isHeldExclusively} reports whether synchronization is exclusively
 * held with respect to the current thread, method {@link #release}
 * invoked with the current {@link #getState} value fully releases
 * this object, and {@link #acquire}, given this saved state value,
 * eventually restores this object to its previous acquired state.  No
 * {@code AbstractQueuedSynchronizer} method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * behavior of {@link ConditionObject} depends of course on the
 * semantics of its synchronizer implementation.
 *
 * 此类提供了内部队列的检查，检测和监视方法，以及条件对象的类似方法。
 * 可以根据需要使用{@code AbstractQueuedSynchronizer}将它们导出到类中以实现其同步机制。
 * <p>This class provides inspection, instrumentation, and monitoring
 * methods for the internal queue, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an {@code AbstractQueuedSynchronizer} for their
 * synchronization mechanics.
 *
 * 此类的序列化仅存储基础原子整数维护状态，因此反序列化的对象具有空线程队列。
 * 需要可序列化的典型子类将定义一个{@code readObject}方法，
 * 该方法可在反序列化时将其恢复为已知的初始状态。
 * <p>Serialization of this class stores only the underlying atomic
 * integer maintaining state, so deserialized objects have empty
 * thread queues. Typical subclasses requiring serializability will
 * define a {@code readObject} method that restores this to a known
 * initial state upon deserialization.
 *
 * <h3>Usage</h3>
 *
 * 要将此类用作同步器的基础，请使用{@link #getState}，{@link #setState}
 * 和/或{@link #compareAndSetState检查和/或修改同步状态，重新定义以下方法（如适用） }：
 * <p>To use this class as the basis of a synchronizer, redefine the
 * following methods, as applicable, by inspecting and/or modifying
 * the synchronization state using {@link #getState}, {@link
 * #setState} and/or {@link #compareAndSetState}:
 *
 * <ul>
 * <li> {@link #tryAcquire}
 * <li> {@link #tryRelease}
 * <li> {@link #tryAcquireShared}
 * <li> {@link #tryReleaseShared}
 * <li> {@link #isHeldExclusively}
 * </ul>
 *
 * 默认情况下，这些方法中的每一个都会引发{@link UnsupportedOperationException}。
 * 这些方法的实现必须在内部是线程安全的，并且通常应简短且不阻塞。
 * 定义这些方法是只支持的使用此类的方法。所有其他方法都声明为{@code final}，因为它们不能独立变化。
 * Each of these methods by default throws {@link
 * UnsupportedOperationException}.  Implementations of these methods
 * must be internally thread-safe, and should in general be short and
 * not block. Defining these methods is the <em>only</em> supported
 * means of using this class. All other methods are declared
 * {@code final} because they cannot be independently varied.
 *
 * 您还可以从{@link AbstractOwnableSynchronizer}有助于跟踪拥有独占同步器的线程。
 * 鼓励您使用它们-这将启用监视和诊断工具，以帮助用户确定哪些线程持有锁。
 * <p>You may also find the inherited methods from {@link
 * AbstractOwnableSynchronizer} useful to keep track of the thread
 * owning an exclusive synchronizer.  You are encouraged to use them
 * -- this enables monitoring and diagnostic tools to assist users in
 * determining which threads hold locks.
 *
 * 即使此类基于内部FIFO队列，它也不会自动执行FIFO获取策略。独占同步的核心采取以下形式：
 * <p>Even though this class is based on an internal FIFO queue, it
 * does not automatically enforce FIFO acquisition policies.  The core
 * of exclusive synchronization takes the form:
 *
 * <pre>
 * Acquire:
 *     while (!tryAcquire(arg)) {
 *        <em>enqueue thread if it is not already queued</em>;
 *        <em>possibly block current thread</em>;
 *     }
 *
 * Release:
 *     if (tryRelease(arg))
 *        <em>unblock the first queued thread</em>;
 * </pre>
 *
 * 共享模式相似，但可能涉及级联信号
 * (Shared mode is similar but may involve cascading signals.)
 *
 * 因为获取队列中的获取检查是在排队之前被调用的，
 * 所以新获取线程可能会在被阻塞和排队的其他线程之前插入。
 * 但是，如果需要，您可以定义{@code tryAcquire}和/或{@code tryAcquireShared}
 * 以通过内部调用一种或多种检查方法来禁用插入，从而提供一个公平的 FIFO获取顺序。
 * 特别是，如果{@link #hasQueuedPredecessors}（一种专门为公平同步器设计的方法）
 * 返回{@code true}，则大多数公平同步器都可以定义{@code tryAcquire}以返回{@code false}。
 * 其他变化是可能的。
 * <p id="barging">Because checks in acquire are invoked before
 * enqueuing, a newly acquiring thread may <em>barge</em> ahead of
 * others that are blocked and queued.  However, you can, if desired,
 * define {@code tryAcquire} and/or {@code tryAcquireShared} to
 * disable barging by internally invoking one or more of the inspection
 * methods, thereby providing a <em>fair</em> FIFO acquisition order.
 * In particular, most fair synchronizers can define {@code tryAcquire}
 * to return {@code false} if {@link #hasQueuedPredecessors} (a method
 * specifically designed to be used by fair synchronizers) returns
 * {@code true}.  Other variations are possible.
 *
 * 对于默认插入（也称为贪婪，放弃和避免拥护）策略，吞吐量和可伸缩性通常最高。
 * 尽管不能保证这是公平的，也不会出现饥饿现象，
 * 但是可以让较早排队的线程在较晚排队的线程之前进行重新竞争，
 * 并且每个重新争用都可以毫无偏向地成功抵御传入线程。同样，尽管获取通常不会“旋转”，
 * 但是在阻塞之前，它们可能会执行{@code tryAcquire}的多次调用，并插入其他计算。
 * 当仅短暂地保持排他同步时，这将提供旋转的大部分好处，而在不进行排他同步时，则不会带来很多负担。
 * 如果需要的话，您可以通过在调用之前对获取方法进行“快速路径”检查来增强此功能，
 * 可能会预先检查{@link #hasContended}和/或{@link #hasQueuedThreads}
 * 以仅在同步器可能不这样做的情况下这样做争辩。
 * <p>Throughput and scalability are generally highest for the
 * default barging (also known as <em>greedy</em>,
 * <em>renouncement</em>, and <em>convoy-avoidance</em>) strategy.
 * While this is not guaranteed to be fair or starvation-free, earlier
 * queued threads are allowed to recontend before later queued
 * threads, and each recontention has an unbiased chance to succeed
 * against incoming threads.  Also, while acquires do not
 * &quot;spin&quot; in the usual sense, they may perform multiple
 * invocations of {@code tryAcquire} interspersed with other
 * computations before blocking.  This gives most of the benefits of
 * spins when exclusive synchronization is only briefly held, without
 * most of the liabilities when it isn't. If so desired, you can
 * augment this by preceding calls to acquire methods with
 * "fast-path" checks, possibly prechecking {@link #hasContended}
 * and/or {@link #hasQueuedThreads} to only do so if the synchronizer
 * is likely not to be contended.
 *
 * 此类为同步提供了有效且可扩展的基础，
 * 部分原因是该类的使用范围专门用于可以依靠{@code int}状态，
 * 获取和释放参数以及内部FIFO等待队列的同步器。
 * 如果不足够，则可以使用{@link java.util.concurrent.atomic atomic}类，
 * 您自己的自定义{@link java.util.Queue}类和{@link LockSupport}支持阻止从较低级别构建同步器。
 *
 * <p>This class provides an efficient and scalable basis for
 * synchronization in part by specializing its range of use to
 * synchronizers that can rely on {@code int} state, acquire, and
 * release parameters, and an internal FIFO wait queue. When this does
 * not suffice, you can build synchronizers from a lower level using
 * {@link java.util.concurrent.atomic atomic} classes, your own custom
 * {@link java.util.Queue} classes, and {@link LockSupport} blocking
 * support.
 *
 * <h3>Usage Examples</h3>
 *
 * 这是一个不可重入的互斥锁定类，使用值0表示解锁状态，
 * 使用值1表示锁定状态。尽管不可重入锁并不严格要求记录当前所有者线程，
 * 但是无论如何，此类都这样做以使使用情况更易于监视。它还支持条件并公开一种检测方法：
 * <p>Here is a non-reentrant mutual exclusion lock class that uses
 * the value zero to represent the unlocked state, and one to
 * represent the locked state. While a non-reentrant lock
 * does not strictly require recording of the current owner
 * thread, this class does so anyway to make usage easier to monitor.
 * It also supports conditions and exposes
 * one of the instrumentation methods:
 *
 *  <pre> {@code
 * class Mutex implements Lock, java.io.Serializable {
 *
 *   // Our internal helper class
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     // Reports whether in locked state
 *     protected boolean isHeldExclusively() {
 *       return getState() == 1;
 *     }
 *
 *     // Acquires the lock if state is zero
 *     public boolean tryAcquire(int acquires) {
 *       assert acquires == 1; // Otherwise unused
 *       if (compareAndSetState(0, 1)) {
 *         setExclusiveOwnerThread(Thread.currentThread());
 *         return true;
 *       }
 *       return false;
 *     }
 *
 *     // Releases the lock by setting state to zero
 *     protected boolean tryRelease(int releases) {
 *       assert releases == 1; // Otherwise unused
 *       if (getState() == 0) throw new IllegalMonitorStateException();
 *       setExclusiveOwnerThread(null);
 *       setState(0);
 *       return true;
 *     }
 *
 *     // Provides a Condition
 *     Condition newCondition() { return new ConditionObject(); }
 *
 *     // Deserializes properly
 *     private void readObject(ObjectInputStream s)
 *         throws IOException, ClassNotFoundException {
 *       s.defaultReadObject();
 *       setState(0); // reset to unlocked state
 *     }
 *   }
 *
 *   // The sync object does all the hard work. We just forward to it.
 *   private final Sync sync = new Sync();
 *
 *   public void lock()                { sync.acquire(1); }
 *   public boolean tryLock()          { return sync.tryAcquire(1); }
 *   public void unlock()              { sync.release(1); }
 *   public Condition newCondition()   { return sync.newCondition(); }
 *   public boolean isLocked()         { return sync.isHeldExclusively(); }
 *   public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
 *   public void lockInterruptibly() throws InterruptedException {
 *     sync.acquireInterruptibly(1);
 *   }
 *   public boolean tryLock(long timeout, TimeUnit unit)
 *       throws InterruptedException {
 *     return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *   }
 * }}</pre>
 *
 * <p>Here is a latch class that is like a
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * except that it only requires a single {@code signal} to
 * fire. Because a latch is non-exclusive, it uses the {@code shared}
 * acquire and release methods.
 *
 *  <pre> {@code
 * class BooleanLatch {
 *
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     boolean isSignalled() { return getState() != 0; }
 *
 *     protected int tryAcquireShared(int ignore) {
 *       return isSignalled() ? 1 : -1;
 *     }
 *
 *     protected boolean tryReleaseShared(int ignore) {
 *       setState(1);
 *       return true;
 *     }
 *   }
 *
 *   private final Sync sync = new Sync();
 *   public boolean isSignalled() { return sync.isSignalled(); }
 *   public void signal()         { sync.releaseShared(1); }
 *   public void await() throws InterruptedException {
 *     sync.acquireSharedInterruptibly(1);
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
public abstract class AbstractQueuedSynchronizer
    extends AbstractOwnableSynchronizer
    implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() { }

    /**
     * 等待列队节点的子类
     * Wait queue node class.
     *
     * 等待队列是“ CLH”（Craig，Landin和Hagersten）锁定队列的变体。
     * CLH锁通常用于自旋锁。相反，我们将它们用于阻塞同步器，但是使用相同的基本策略，
     * 将有关线程的某些控制信息保存在其节点的前身中。
     * 每个节点中的“状态”字段将跟踪线程是否应阻塞。
     * 节点的前任释放时会发出信号。否则，队列的每个节点都充当一个特定通知样式的监视器，
     * 其中包含一个等待线程。虽然状态字段不控制是否授予线程锁等。
     * 线程可能会尝试获取它是否在队列中的第一位。但是先行并不能保证成功。它只赋予了抗辩的权利。
     * 因此，当前发布的竞争者线程可能需要重新等待。
     * <p>The wait queue is a variant of a "CLH" (Craig, Landin, and
     * Hagersten) lock queue. CLH locks are normally used for
     * spinlocks.  We instead use them for blocking synchronizers, but
     * use the same basic tactic of holding some of the control
     * information about a thread in the predecessor of its node.  A
     * "status" field in each node keeps track of whether a thread
     * should block.  A node is signalled when its predecessor
     * releases.  Each node of the queue otherwise serves as a
     * specific-notification-style monitor holding a single waiting
     * thread. The status field does NOT control whether threads are
     * granted locks etc though.  A thread may try to acquire if it is
     * first in the queue. But being first does not guarantee success;
     * it only gives the right to contend.  So the currently released
     * contender thread may need to rewait.
     *
     * <p>To enqueue into a CLH lock, you atomically splice it in as new
     * tail. To dequeue, you just set the head field.
     * <pre>
     *      +------+  prev +-----+       +-----+
     * head |      | <---- |     | <---- |     |  tail
     *      +------+       +-----+       +-----+
     * </pre>
     *
     * 插入到CLH队列中只需要对尾部执行一次原子操作，因此存在一个简单的原子分界点，
     * 即从未排队到排队。同样，出队仅涉及更新“头”。但是，节点需要花费更多的精力来确定其后继者是谁，
     * 部分原因是要处理由于超时和中断而可能导致的取消。
     * <p>Insertion into a CLH queue requires only a single atomic
     * operation on "tail", so there is a simple atomic point of
     * demarcation from unqueued to queued. Similarly, dequeuing
     * involves only updating the "head". However, it takes a bit
     * more work for nodes to determine who their successors are,
     * in part to deal with possible cancellation due to timeouts
     * and interrupts.
     *
     * “ prev”链接（在原始CLH锁中不使用）主要用于处理取消。
     * 如果取消某个节点，则其后继节点（通常）会重新链接到未取消的前任节点。
     * 有关自旋锁情况下类似机制的说明，请参见Scott和Scherer的论文，
     * 网址为http://www.cs.rochester.edu/u/scott/synchronization/
     * <p>The "prev" links (not used in original CLH locks), are mainly
     * needed to handle cancellation. If a node is cancelled, its
     * successor is (normally) relinked to a non-cancelled
     * predecessor. For explanation of similar mechanics in the case
     * of spin locks, see the papers by Scott and Scherer at
     * http://www.cs.rochester.edu/u/scott/synchronization/
     *
     * 我们还使用“下一个”链接来实现阻止机制。每个节点的线程ID保留在其自己的节点中，
     * 因此，前任通过遍历下一个链接以确定它是哪个线程，来通知下一个节点唤醒。
     * 确定后继者必须避免与新排队的节点竞争以设置其前任节点的“ next”字段。在必要时，
     * 可以通过在节点的后继者似乎为空时从原子更新的“尾部”向后检查来解决此问题。
     * （或者换句话说，下一个链接是一种优化，因此我们通常不需要向后扫描。）
     * <p>We also use "next" links to implement blocking mechanics.
     * The thread id for each node is kept in its own node, so a
     * predecessor signals the next node to wake up by traversing
     * next link to determine which thread it is.  Determination of
     * successor must avoid races with newly queued nodes to set
     * the "next" fields of their predecessors.  This is solved
     * when necessary by checking backwards from the atomically
     * updated "tail" when a node's successor appears to be null.
     * (Or, said differently, the next-links are an optimization
     * so that we don't usually need a backward scan.)
     *
     * 对消在基本算法中引入了一些保守性。因为我们必须轮询其他节点的取消，
     * 所以我们可能会忽略已取消的节点是在我们前面还是后面。
     * 处理这一问题的总是取消继任者的职务，允许他们稳定在一个新的前任上，
     * 除非我们能确定一个未被取消的前任来承担这一责任。
     * <p>Cancellation introduces some conservatism to the basic
     * algorithms.  Since we must poll for cancellation of other
     * nodes, we can miss noticing whether a cancelled node is
     * ahead or behind us. This is dealt with by always unparking
     * successors upon cancellation, allowing them to stabilize on
     * a new predecessor, unless we can identify an uncancelled
     * predecessor who will carry this responsibility.
     *
     * CLH队列需要一个虚拟头节点才能启动。但我们不在结构上创造它们，因为如果没有争论，
     * 那将是浪费精力。相反，在第一次争用时构造节点并设置头指针和尾指针。
     * <p>CLH queues need a dummy header node to get started. But
     * we don't create them on construction, because it would be wasted
     * effort if there is never contention. Instead, the node
     * is constructed and head and tail pointers are set upon first
     * contention.
     *
     * 等待条件的线程使用相同的节点，但使用其他链接。条件只需要链接简单（非并发）链接队列中的节点，
     * 因为它们只在独占持有时才被访问。等待时，节点被插入到条件队列中。一旦发出信号，
     * 节点就被转移到主队列。状态字段的特殊值用于标记节点所在的队列。
     * <p>Threads waiting on Conditions use the same nodes, but
     * use an additional link. Conditions only need to link nodes
     * in simple (non-concurrent) linked queues because they are
     * only accessed when exclusively held.  Upon await, a node is
     * inserted into a condition queue.  Upon signal, the node is
     * transferred to the main queue.  A special value of status
     * field is used to mark which queue a node is on.
     *
     * <p>Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
     * Scherer and Michael Scott, along with members of JSR-166
     * expert group, for helpful ideas, discussions, and critiques
     * on the design of this class.
     */
    static final class Node {
        /**
         * 表示节点正在共享模式下等待的标记（shared）
         * Marker to indicate a node is waiting in shared mode
         */
        static final Node SHARED = new Node();

        /**
         * 表示节点正在互斥模式下等待的标记（shared）
         * Marker to indicate a node is waiting in exclusive mode
         */
        static final Node EXCLUSIVE = null;

        /**
         * 状态等待至，表示线程已经被取消
         * waitStatus value to indicate thread has cancelled
         */
        static final int CANCELLED =  1;
        /**
         * waitStatus值，指示后续线程需要断开连接
         * waitStatus value to indicate successor's thread needs unparking
         */
        static final int SIGNAL    = -1;
        /**
         * 等待状态值用以表示线程在特定条件下处于等待
         * waitStatus value to indicate thread is waiting on condition
         */
        static final int CONDITION = -2;

        /**
         * 指示下一个acquireShared应无条件传播的waitStatus值
         * waitStatus value to indicate the next acquireShared should
         * unconditionally propagate
         */
        static final int PROPAGATE = -3;

        /**
         * 状态字段，可以包括如下值:
         * SIGNAL:       此节点的后续节点已（或将很快）被阻止（通过驻车），
         *               因此当前节点在释放或取消时必须断开其后续节点的连接。
         *               为了避免竞争，acquire方法必须首先指示它们需要一个信号，然后重试原子获取，
         *               然后在失败时阻塞。
         *   CANCELLED:  这个节点在超时或者中断的情况下被取消。节点从来不会离开这个状态。也别的，一个
         *               被取消的节点不会被阻塞。
         *   CONDITION:  此节点当前处于条件队列中。在传输之前，它不会用作同步队列节点，此时状态将设置为0。
         *              （此处使用此值与该字段的其他用途无关，只是一种简化机制。）
         *   PROPAGATE： releaseShared应传播到其他节点。这是在doReleaseShared中设置的（仅用于头节点），
         *               以确保传播继续进行，即使此后有其他操作介入。
         *   0:          以上都不是
         * Status field, taking on only the values:
         *   SIGNAL:     The successor of this node is (or will soon be)
         *               blocked (via park), so the current node must
         *               unpark its successor when it releases or
         *               cancels. To avoid races, acquire methods must
         *               first indicate they need a signal,
         *               then retry the atomic acquire, and then,
         *               on failure, block.
         *   CANCELLED:  This node is cancelled due to timeout or interrupt.
         *               Nodes never leave this state. In particular,
         *               a thread with cancelled node never again blocks.
         *   CONDITION:  This node is currently on a condition queue.
         *               It will not be used as a sync queue node
         *               until transferred, at which time the status
         *               will be set to 0. (Use of this value here has
         *               nothing to do with the other uses of the
         *               field, but simplifies mechanics.)
         *   PROPAGATE:  A releaseShared should be propagated to other
         *               nodes. This is set (for head node only) in
         *               doReleaseShared to ensure propagation
         *               continues, even if other operations have
         *               since intervened.
         *   0:          None of the above
         *
         * 数值的排列是为了简化使用。非负值表示节点不需要发出信号。所以，大多数代码不需要检查特定值，只需要检查符号。
         * The values are arranged numerically to simplify use.
         * Non-negative values mean that a node doesn't need to
         * signal. So, most code doesn't need to check for particular
         * values, just for sign.
         *
         * 对于正常同步节点，字段初始化为0，对于条件节点，字段初始化为条件。
         * 它使用CAS进行修改（或者在可能的情况下，使用无条件的volatile写操作）
         * The field is initialized to 0 for normal sync nodes, and
         * CONDITION for condition nodes.  It is modified using CAS
         * (or when possible, unconditional volatile writes).
         */
        volatile int waitStatus;

        /**
         * 指向当前节点/线程检查waitStatus所依赖的前置节点的链接。
         * 在排队期间分配，只有在排队时才清空（为了GC）。此外，当前置节点被取消，我们发现一个未被取消的一个短路，
         * 因为头节点从未被取消，所以总是存在的：由于成功获取，节点仅成为头部。
         * 一个被取消的线程永远不会成功获取，一个线程只会取消自己，而不是任何其他节点。
         * Link to predecessor node that current node/thread relies on
         * for checking waitStatus. Assigned during enqueuing, and nulled
         * out (for sake of GC) only upon dequeuing.  Also, upon
         * cancellation of a predecessor, we short-circuit while
         * finding a non-cancelled one, which will always exist
         * because the head node is never cancelled: A node becomes
         * head only as a result of successful acquire. A
         * cancelled thread never succeeds in acquiring, and a thread only
         * cancels itself, not any other node.
         */
        volatile Node prev;

        /**
         * 链接到当前节点/线程在释放时断开的后续节点。在排队期间分配，在绕过已取消的前置任务时进行调整，
         * 在出列时为空（为了GC）。enq操作在附加之后才分配前置任务的下一个字段，
         * 因此看到空的下一个字段并不一定意味着节点在队列的末尾。但是，如果下一个字段显示为空，
         * 我们可以从尾部扫描prev以进行双重检查。取消节点的下一个字段被设置为指向节点本身而不是空，
         * 以使isOnSyncQueue的工作更轻松。
         * Link to the successor node that the current node/thread
         * unparks upon release. Assigned during enqueuing, adjusted
         * when bypassing cancelled predecessors, and nulled out (for
         * sake of GC) when dequeued.  The enq operation does not
         * assign next field of a predecessor until after attachment,
         * so seeing a null next field does not necessarily mean that
         * node is at end of queue. However, if a next field appears
         * to be null, we can scan prev's from the tail to
         * double-check.  The next field of cancelled nodes is set to
         * point to the node itself instead of null, to make life
         * easier for isOnSyncQueue.
         */
        volatile Node next;

        /**
         * 使此节点排队的线程。在构造时初始化，使用后为空。
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.
         */
        volatile Thread thread;

        /**
         * 链接到下一个等待条件的节点，或共享的特殊值。因为条件队列只有在独占模式下保持时才被访问，
         * 所以我们只需要一个简单的链接队列来保持节点在等待条件时的状态。然后将它们转移到队列以重新获取。
         * 由于条件只能是互斥的，所以我们通过使用特殊值来表示共享模式来保存一个字段。
         * Link to next node waiting on condition, or the special
         * value SHARED.  Because condition queues are accessed only
         * when holding in exclusive mode, we just need a simple
         * linked queue to hold nodes while they are waiting on
         * conditions. They are then transferred to the queue to
         * re-acquire. And because conditions can only be exclusive,
         * we save a field by using special value to indicate shared
         * mode.
         */
        Node nextWaiter;

        /**
         * 如果节点在共享模式下处于等待的状态返回true
         * Returns true if node is waiting in shared mode.
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 返回上一个节点，如果为空，则抛出NullPointerException。前置任务不能为空时使用。
         * 空检查可以省略，但可以帮助VM。
         * Returns previous node, or throws NullPointerException if null.
         * Use when predecessor cannot be null.  The null check could
         * be elided, but is present to help the VM.
         *
         * @return the predecessor of this node
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        // Used to establish initial head or SHARED marker
        //用于建立初始头部或共享标记
        Node() {
        }

        // Used by addWaiter
        //由addWaiter使用
        Node(Thread thread, Node mode) {
            this.nextWaiter = mode;
            this.thread = thread;
        }

        // Used by Condition
        Node(Thread thread, int waitStatus) {
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /**
     * 等待队列的头，延迟初始化。除了初始化，它只通过方法setHead进行修改。注意：如果头存在，保证它的状态不被取消。
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only via method setHead.  Note:
     * If head exists, its waitStatus is guaranteed not to be
     * CANCELLED.
     */
    private transient volatile Node head;

    /**
     * 等待队列的尾部，延迟初始化。仅通过修改方法enq以添加新的等待节点。
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    private transient volatile Node tail;

    /**
     * The synchronization state.
     */
    private volatile int state;

    /**
     * 返回同步状态的当前值。此操作的内存语义为{@code volatile}read。
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a {@code volatile} read.
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * Sets the value of synchronization state.
     * This operation has memory semantics of a {@code volatile} write.
     * @param newState the new state value
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * 如果当前状态值等于预期值，原子地将同步状态设置为给定的更新值。此操作具有{@code volatile}读写的内存语义。
     * Atomically sets synchronization state to the given updated
     * value if the current state value equals the expected value.
     * This operation has memory semantics of a {@code volatile} read
     * and write.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual
     *         value was not equal to the expected value.
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // Queuing utilities

    /**
     * 旋转速度比使用定时驻车更快的纳秒数。一个粗略的估计就足以在非常短的超时时间内提高响应能力。
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices
     * to improve responsiveness with very short timeouts.
     */
    static final long spinForTimeoutThreshold = 1000L;

    /**
     * 将节点插入队列，必要时进行初始化。见上图。入队操作
     * Inserts node into queue, initializing if necessary. See picture above.
     *
     * 处理过程:
     * 1.当尾部节点为null,也就是此链表为空链表，程序会先创建一个新的空节点，并将尾部节点和头部节点
     *   赋值相等，继续下一步循环；
     * 2.通过compareAndSetTail CAS方法将node插入到队尾，
     * 3.ThreadB已经被包装成结点插入队尾了
     * @param node the node to insert
     * @return node's predecessor
     */
    private Node enq(final Node node) {
        //由于并发的存在，这里设计成为自旋操作

        for (;;) {
            //获取列队的尾部节点
            Node t = tail;
            //尾部节点为空说明此列表为空列表，需要初始化节点
            //初始化节点也就是将头部节点和尾部节点设置成当期那传入的这个节点
            // Must initialize
            if (t == null) {
                if (compareAndSetHead(new Node()))
                    tail = head;
            } else {
                //如果尾部节点不为null，将当前节点放入到链表尾部
                //及当前节点的前置节点设置为原先的尾部节点
                //这是当前尾部节点
                //保持双向列表，旧的尾部节点的下一节点指向当前节点
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * 为当前线程和给定模式创建和排队节点。添加到等待队列尾部。
     * Creates and enqueues node for current thread and given mode.
     *
     * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
     *             Node.EXCLUSIVE表示独占，Node.SHARED表示共享
     * @return the new node
     */
    private Node addWaiter(Node mode) {
        //Thread.currentThread()=>当前线程
        //mode: nextWait(下一个等待的节点)
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        //尝试enq的快速路径；失败时备份到完全enq

        //将当前节点放置到链表尾部
        //先尝试一次添加到尾部，如果添加成功，就不用走下面的enq方法
        Node pred = tail;
        if (pred != null) {
            node.prev = pred;
            //这里只做了node.pre = pred
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }

        //将节点插入队尾
        enq(node);
        return node;
    }

    /**
     * 将队列头设置为节点，从而取消排队。仅由调用获取方法。也会为了GC而清空未使用的字段抑制不必要的信号和遍历。
     * Sets head of queue to be node, thus dequeuing. Called only by
     * acquire methods.  Also nulls out unused fields for sake of GC
     * and to suppress unnecessary signals and traversals.
     *
     * @param node the node
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 唤醒节点的继承者，如果存在的话。
     * Wakes up node's successor, if one exists.
     *
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        /*
         * 如果状态是负数的（即可能需要信号），请尝试清除以预期发出信号。如果失败或通过等待线程更改状态，则可以。
         * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         * 节点等待的状态
         */
        int ws = node.waitStatus;
        //如果值为负数，需要设置等待状态为0

        //预置当前节点的状态为0，表示后续节点即将被唤醒
        if (ws < 0) {
            compareAndSetWaitStatus(node, ws, 0);
        }

        /*
         * 释放线程保留在后续线程中，该线程通常只是下一个节点。但是，如果已取消或明显为空，
         * 请从尾部向后移动以找到实际的未取消后继。
         * 寻找符合条件的后续节点
         * Thread to unpark is held in successor, which is normally
         * just the next node.  But if cancelled or apparently null,
         * traverse backwards from tail to find the actual
         * non-cancelled successor.
         */
        //此时会直接唤醒后继节点
        //但是如果后继节点处于1，CANDELLED状态的时候(说明被取消了),会从队尾开始，
        // 向前找到第一个未被取消的节点。
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            //向前移动
            //从后往前开始查找主要为了考虑并发入队(enq)的情况
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        //唤醒后续节点
        if (s != null)
            LockSupport.unpark(s.thread);
    }

    /**
     * 共享模式下的释放动作-表示后继信号并确保传播。
     * （注意：对于独占模式，如果需要信号，释放仅相当于调用head的unparkSuccessor。）
     * Release action for shared mode -- signals successor and ensures
     * propagation. (Note: For exclusive mode, release just amounts
     * to calling unparkSuccessor of head if it needs signal.)
     */
    private void doReleaseShared() {
        /*
         * 即使有其他正在进行的获取/发布，也要确保发布传播。如果需要信号，这以尝试取消headSuccessor的常规方式进行。
         * 但是，如果没有，则将状态设置为PROPAGATE，以确保释放后继续传播。
         * 此外，在执行此操作时，必须循环以防添加新节点。此外，与unparkSuccessor的其他用法不同，
         * 我们需要知道CAS重置状态是否失败，如果重新检查，则失败。
         * Ensure that a release propagates, even if there are other
         * in-progress acquires/releases.  This proceeds in the usual
         * way of trying to unparkSuccessor of head if it needs
         * signal. But if it does not, status is set to PROPAGATE to
         * ensure that upon release, propagation continues.
         * Additionally, we must loop in case a new node is added
         * while we are doing this. Also, unlike other uses of
         * unparkSuccessor, we need to know if CAS to reset status
         * fails, if so rechecking.
         */
        for (;;) {
            Node h = head;
            //头结点不为null并且头结点不为尾部节点，也就是这个链表大于等于2个节点
            //
            if (h != null && h != tail) {
                //获取当前节点的状态
                int ws = h.waitStatus;

                //后续节点需要断开连接（SIGNAL）
                if (ws == Node.SIGNAL) {
                    //CAS设置等待的状态值，如果不成功，则一直处于自旋状态
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    //唤醒后续节点
                    unparkSuccessor(h);
                }
                //没有任何状态
                else if (ws == 0 &&
                         !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
            //如果头部节点发生改变，则终止循环
            if (h == head)                   // loop if head changed
                break;
        }
    }

    /**
     * 设置队列头，并检查后继者是否可能在共享模式下等待，如果正在传播，则传播是否设置为传播> 0或PROPAGATE状态。
     * Sets head of queue, and checks if successor may be waiting
     * in shared mode, if so propagating if either propagate > 0 or
     * PROPAGATE status was set.
     *
     * @param node the node
     * @param propagate the return value from a tryAcquireShared
     *                  tryAcquireShared的返回值
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head; // Record old head for check below
        //退出自旋的节点变为头部节点
        setHead(node);
        /*
         * 如果发生以下情况，请尝试向下一个排队的节点发出信号：传播是由调用方指示的，
         * 还是由上一个操作记录的（作为setHead之前或之后的h.waitStatus）（注意：这使用waitStatus的符号检查），
         * 因为PROPAGATE状态可能转换为SIGNAL。 ）和下一个节点正在共享模式下等待，或者我们不知道，因为它看起来为空。
         * 这两种检查的保守性都可能导致不必要的唤醒，但是只有在有多个竞速获取/发布时，因此最需要现在或不久都会发出信号。
         * Try to signal next queued node if:
         *   Propagation was indicated by caller,
         *     or was recorded (as h.waitStatus either before
         *     or after setHead) by a previous operation
         *     (note: this uses sign-check of waitStatus because
         *      PROPAGATE status may transition to SIGNAL.)
         * and
         *   The next node is waiting in shared mode,
         *     or we don't know, because it appears null
         *
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         */
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }

    // Utilities for various versions of acquire

    /**
     * 取消正在进行的尝试获取锁的线程操作。
     * Cancels an ongoing attempt to acquire.
     *
     * @param node the node
     */
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        //如果节点不存在则忽略
        if (node == null)
            return;

        node.thread = null;

        // Skip cancelled predecessors
        //跳过取消的前任
        //这里直接掉过当前节点之前，素有已经取消的节点
        Node pred = node.prev;
        //CANCELLED:1
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        //predNext是要取消拼接的明显节点。如果没有，以下情况将失败，
        // 在这种情况下，我们输掉了比赛，而另一个取消或发出信号，因此不需要采取进一步的措施。
        // predNext is the apparent node to unsplice. CASes below will
        // fail if not, in which case, we lost race vs another cancel
        // or signal, so no further action is necessary.
        Node predNext = pred.next;

        // 可以在此处使用无条件写入代替CAS。完成这一基本步骤后，其他节点可以跳过我们。
        // 以前，我们不受其他线程的干扰。
        // Can use unconditional write instead of CAS here.
        // After this atomic step, other Nodes can skip past us.
        // Before, we are free of interference from other threads.
        node.waitStatus = Node.CANCELLED;

        // If we are the tail, remove ourselves.
        //如果当前节点为尾部节点需要移除自己
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            // 如果后置节点需要signal，请尝试设置pred的下一个链接，以便获得一个。否则唤醒它以传播。
            // 这里存在并发的操作
            // If successor needs signal, try to set pred's next-link
            // so it will get one. Otherwise wake it up to propagate.
            //waitStatus
            // Node.SIGNAL:后置节点需要断开连接
            int ws;
            //前置节点不为头部节点
            //前置节点的等待状态为SIGNAL 或者 前置节点状态为负数并且可以设置成SIGNAL
            //前置节点的线程对象不为空
            if (pred != head &&
                ((ws = pred.waitStatus) == Node.SIGNAL ||
                 (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                pred.thread != null) {
                Node next = node.next;
                //如果后置节点被空并且后置节点的等待状态小于0
                if (next != null && next.waitStatus <= 0)
                    //CAS设置前置节点的下一个节点
                    compareAndSetNext(pred, predNext, next);
            } else {
                //唤醒后置节点
                unparkSuccessor(node);
            }
            //
            node.next = node; // help GC
        }
    }

    /**
     * 检查并更新无法获取的节点的状态。如果线程应阻塞，则返回true。
     * 这是所有采集循环中的主要信号控制。要求pred == node.prev。
     * Checks and updates status for a node that failed to acquire.
     * Returns true if thread should block. This is the main signal
     * control in all acquire loops.  Requires that pred == node.prev.
     *
     * 判断是否要阻塞当前线程
     *
     * CLH的一个特点就是:将当前节点的状态保存在他的前驱中
     * 前驱状态是(-1等待唤醒),才会阻塞当前线程
     * 注意，对于独占功能，只使用了3种结点状态：
     * 结点状态	    值	    描述
     * CANCELLED	1	    取消。表示后驱结点被中断或超时，需要移出队列
     * SIGNAL	    -1	    发信号。表示后驱结点被阻塞了（当前结点在入队后、阻塞前，应确保将其prev结点类型改为SIGNAL，以便prev结点取消或释放时将当前结点唤醒。）
     * CONDITION	-2	    Condition专用。表示当前结点在Condition队列中，因为等待某个条件而被阻塞了
     *
     * @param pred node's predecessor holding status
     *             节点的前置保持的状态
     * @param node the node 节点
     * @return {@code true} if thread should block 如果线程处于阻塞状态则返回true
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        //前置节点的状态
        int ws = pred.waitStatus;
        //指示后续线程需要断开连接
        //即node需要断开
        //SINGLE:后续节点需要被唤醒(他说明当前节点的前驱将来会唤醒我，我可以安心的睡了。
        if (ws == Node.SIGNAL)
            /*
             * 该节点已经设置了状态，要求释放以发出信号，以便可以安全地停放。
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             */
            return true;
            //CANCELLED：线程已经被取消(1)
        //说明当前节点意外被中断或者取消，需要将其从等待列队移除
        if (ws > 0) {
            /*
             * 前置节点已取消。跳过前任并指示重试。
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             */
            do {
                //从node的前置节点的前置节点一直查找，直到找到第一个不是线程被取消的节点，
                //并且将当前找到的节点赋值给node的前置节点
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            //维护双向链表
            pred.next = node;
        } else {
            /*
             * PROPAGATE:指示下一个acquireShared应无条件传播的waitStatus值
             *
             * waitStatus必须为0或PROPAGATE。表示我们需要一个信号，但不要连接。
             * 调用需要重试以确保在连接前无法获取。
             * 对应独占模式来说，这里仅表示初始状态0
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             */
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * 一种方便的方法来中断当前线程。
     * Convenience method to interrupt current thread.
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * 链接的便捷方法，然后检查是否中断
     * Convenience method to park and then check if interrupted
     *
     * @return {@code true} if interrupted
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        //检查是否中断
        return Thread.interrupted();
    }

    /*
     * 各种获取方式，包括独占/共享和控制模式。每个都基本相同，但令人讨厌的不同。
     * 由于异常机制（包括确保在tryAcquire抛出异常时我们取消）和其他控件的相互作用，
     * 因此只有少量分解是可能的，至少在不影响性能的前提下。
     * Various flavors of acquire, varying in exclusive/shared and
     * control modes.  Each is mostly the same, but annoyingly
     * different.  Only a little bit of factoring is possible due to
     * interactions of exception mechanics (including ensuring that we
     * cancel if tryAcquire throws exception) and other control, at
     * least not without hurting performance too much.
     */

    /**
     * 以排他的不间断模式获取已在队列中的线程。用于条件等待方法以及获取。
     * Acquires in exclusive uninterruptible mode for thread already in
     * queue. Used by condition wait methods as well as acquire.
     *  从等待列队中获取队首线程，并尝试获取锁。如果获取不到，就要保证在前驱能唤醒自己的情况下（将
     *  前驱状态设置为Single）进入阻塞状态。
     *  注意:正常情况下，该方法会一直阻塞当前线程，除非获取到锁才返回。但是如果执行过程中，抛出异常
     *  (tryAcquire),那么会将当前节点移除，继续上抛异常。
     * @param node the node 节点
     * @param arg the acquire argument 获取的参数
     * @return {@code true} if interrupted while waiting
     *          当等待的的过程中，如果被打断则返回true
     */
    final boolean acquireQueued(final Node node, int arg) {
        //默认已经失败为true
        boolean failed = true;
        try {
            //是否已经被打断为false
            boolean interrupted = false;
            for (;;) {
                //获取前置节点
                final Node p = node.predecessor();
                //如果p为头部元素并且可以尝试改变，则将当前节点设置为头部元素
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    //头部节点的下一个节点为null,有助于GC的回收
                    p.next = null; // help GC
                    //这里指示并么有失败
                    failed = false;
                    //返回是否被打断
                    return interrupted;
                }
                //失败获取之后应该被关联并且关联并检查中断
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            //如果失败了，取消获取节点
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 在排他性可中断模式下获取。
     * 这里和{@link #acquireQueued(Node, int)}方法极为相似，只不过最后的判断后处理的逻辑不一样，
     * 本方法直接抛出异常，而acquireQueued则修改标识
     * Acquires in exclusive interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
        throws InterruptedException {
        //创建排他节点
        final Node node = addWaiter(Node.EXCLUSIVE);
        //失败默认为true
        boolean failed = true;
        try {
            for (;;) {
                //返回上一个节点
                final Node p = node.predecessor();
                //如果上一个节点时头部节点并且可以被设置，设置node为头部节点。
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    //此时将node的下一个节点设置为null,有助于GC回收
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                //在获取失败之后应该被关联
                //尝试获取锁后是否应该被阻塞
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                //取消获取节点
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in exclusive timed mode.
     * 以排他定时模式进行获取。
     * 和acquireQuqued方法类似，又是一个自旋操作，在超时前不断尝试获取锁，获取不到则阻塞（加上了等待时间的判断）。
     * 该方法内部，调用了LockSupport.parkNanos来超时阻塞线程：
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired 如果已经获取，则返回true.
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        //超时设置小于0，则返回false
        if (nanosTimeout <= 0L)
            return false;
        //死亡线:当前系统时间+超时时间
        final long deadline = System.nanoTime() + nanosTimeout;
        //将线程加入等待队列
        final Node node = addWaiter(Node.EXCLUSIVE);
        //失败表示默认为true
        //标识在等待时间内是否获取到锁
        boolean failed = true;
        try {
            for (;;) {
                //获取前置节点
                final Node p = node.predecessor();
                //是头部并且可以被获取
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                ///通过死亡线-当前系统的纳秒数，
                nanosTimeout = deadline - System.nanoTime();
                //如果纳秒为负数，则返回false
                //超过了截至等待时间点
                if (nanosTimeout <= 0L)
                    return false;
                //spinForTimeoutThreshold::旋转速度比使用定时驻车更快的纳秒数。
                // 一个粗略的估计就足以在非常短的超时时间内提高响应能力。
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)
                    //阻塞线程
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            //等待时间内没有获取到锁，则取消获取
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 以共享的不间断模式进行获取。
     * Acquires in shared uninterruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireShared(int arg) {
        //增加共享模式的节点  shared
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                //前置节点
                final Node p = node.predecessor();
                if (p == head) {
                    //尝试获取共享参数
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        //将当前节点和r设置到头部节点
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted)
                            //如果已经被打断，则需要自己中断连接
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 在共享可中断模式下获取。
     * Acquires in shared interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireSharedInterruptibly(int arg)
        throws InterruptedException {
        //包装成共享锁的节点
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {
                //自选阻塞线程并尝试获取锁
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    //大于0表示获取成功
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        //检查是否需要阻塞当前节点
                    parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 在共享定时模式下获取。
     * Acquires in shared timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    // Main exported methods

    /**
     * 尝试以独占模式进行获取。此方法应查询对象的状态是否允许以独占模式获取对象，如果允许则获取对象。
     * Attempts to acquire in exclusive mode. This method should query
     * if the state of the object permits it to be acquired in the
     * exclusive mode, and if so to acquire it.
     *
     * 始终由执行获取的线程调用此方法。如果此方法报告失败，则acquire方法可将线程排队（如果尚未排队），
     * 直到被其他线程释放释放为止。这可以用来实现方法{@link Lock＃tryLock（）}。
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread. This can be used
     * to implement method {@link Lock#tryLock()}.
     *
     * <p>The default
     * implementation throws {@link UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     *            获取参数。该值始终是传递给获取方法的值，或者是在条件等待输入时保存的值。
     *            否则该值将无法解释，并且可以代表您喜欢的任何内容。
     *
     * @return {@code true} if successful. Upon success, this object has
     *         been acquired.
     *         如果成功。成功后，便已获取该对象。
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     *         如果获取会使该同步器处于非法状态。必须以一致的方式抛出此异常，以使同步正常工作。
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试设置状态以反映排他模式下的发布。
     * Attempts to set the state to reflect a release in exclusive
     * mode.
     *
     * 始终由执行释放的线程调用此方法。
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     *            释放参数。该值始终是传递给释放方法的值，或者是输入条件等待时的当前状态值。
     *            否则该值将无法解释，并且可以代表您喜欢的任何内容。
     * @return {@code true} if this object is now in a fully released
     *         state, so that any waiting threads may attempt to acquire;
     *         and {@code false} otherwise.
     *         如果此对象现在处于完全释放状态，则任何等待线程都可以尝试获取；否则为{@code false}。
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     *         如果释放将使该同步器处于非法状态。必须以一致的方式抛出此异常，以使同步正常工作。
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试以共享模式进行获取。此方法应查询对象的状态是否允许以共享模式获取对象，如果允许则获取对象。
     * Attempts to acquire in shared mode. This method should query if
     * the state of the object permits it to be acquired in the shared
     * mode, and if so to acquire it.
     *
     * 始终由执行获取的线程调用此方法。如果此方法报告失败，则acquire方法可将线程排队（如果尚未排队），
     * 直到被其他线程释放释放为止。
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread.
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     *            获取参数。该值始终是传递给获取方法的值，或者是在条件等待输入时保存的值。
     *            否则该值将无法解释，并且可以代表您喜欢的任何内容。
     * @return a negative value on failure; zero if acquisition in shared
     *         mode succeeded but no subsequent shared-mode acquire can
     *         succeed; and a positive value if acquisition in shared
     *         mode succeeded and subsequent shared-mode acquires might
     *         also succeed, in which case a subsequent waiting thread
     *         must check availability. (Support for three different
     *         return values enables this method to be used in contexts
     *         where acquires only sometimes act exclusively.)  Upon
     *         success, this object has been acquired.
     *          失败情况下为负值；如果共享模式下的获取成功，但是后续共享模式下的获取无法成功，则为零；
     *         如果共享模式下的获取成功并且后续共享模式下的获取也可能成功，则为正值，在这种情况下，
     *         后续的等待线程必须检查可用性。 （对三个不同返回值的支持使该方法可以在仅有时进行获取的情况下使用。）
     *         成功后，就已经获取了此对象。
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     *         如果获取将使此同步器处于非法状态。必须以一致的方式引发此异常，同步才能正常工作。
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试设置状态以反映共享模式下的发布。
     * Attempts to set the state to reflect a release in shared mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *  此方法始终由执行释放的线程调用。
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     *            释放参数。此值始终是传递给release方法的值，或在进入条件wait时的当前状态值。
     *            该值在其他方面是不受欢迎的，可以表示任何您喜欢的内容。
     * @return {@code true} if this release of shared mode may permit a
     *         waiting acquire (shared or exclusive) to succeed; and
     *         {@code false} otherwise
     *         如果此共享模式的释放可能允许等待获取（共享或独占）成功，否则{@code false}
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     *         如果释放会使此同步器处于非法状态。必须以一致的方式引发此异常，同步才能正常工作。
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code true} if synchronization is held exclusively with
     * respect to the current (calling) thread.  This method is invoked
     * upon each call to a non-waiting {@link ConditionObject} method.
     * (Waiting methods instead invoke {@link #release}.)
     * 如果以独占方式保持与当前（调用）线程的同步，则返回{@code true}。
     * 每次调用非等待的{@link ConditionObject}方法时都会调用此方法。（等待方法改为调用{@link#release}。）
     *
     * 默认实现抛出{@link UnsupportedOperationException}。
     * 此方法仅在{@link ConditionObject}方法内部调用，因此如果不使用条件，则无需定义。
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}. This method is invoked
     * internally only within {@link ConditionObject} methods, so need
     * not be defined if conditions are not used.
     *
     * @return {@code true} if synchronization is held exclusively;
     *         {@code false} otherwise
     * @throws UnsupportedOperationException if conditions are not supported
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * 以独占模式获取，忽略中断。通过调用至少一次{@link#tryAcquire}来实现，成功时返回。
     * 否则线程将排队，可能会重复阻塞和解除阻塞，调用{@link#tryAcquire}直到成功。
     * 此方法可用于实现方法{@link Lock#Lock}。
     * Acquires in exclusive mode, ignoring interrupts.  Implemented
     * by invoking at least once {@link #tryAcquire},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquire} until success.  This method can be used
     * to implement method {@link Lock#lock}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     *            获取参数。这个值被传递到{@link#tryAcquire}，但在其他方面并不令人惊讶，
     *            可以表示任何您喜欢的内容。
     */
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
            //由于存在使用中的读锁，所以会调用acquireQueued并被加入等待队列，这个过程就是独占锁的请求过程
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            //不能尝试获取并且尝试入队，则自己中断程序
            //自宫
            selfInterrupt();
    }

    /**
     * 以独占模式获取，如果中断则中止。通过首先检查中断状态，然后至少调用一次{@link#tryAcquire}来实现
     * 成功时返回。否则线程将排队，可能重复阻塞和取消阻塞，调用{@link#tryAcquire}，直到成功或线程中断。
     * 此方法可用于实现方法{@link Lock#lockInterruptibly}。
     * Acquires in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * at least once {@link #tryAcquire}, returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking {@link #tryAcquire}
     * until success or the thread is interrupted.  This method can be
     * used to implement method {@link Lock#lockInterruptibly}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     *            获取参数。这个值被传递到{@link#tryAcquire}，但在其他方面并不令人惊讶，可以表示任何您喜欢的内容。
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        //如果线程中断标志为true,抛出异常
        if (Thread.interrupted())
            throw new InterruptedException();
        //否则尝试获取锁
        if (!tryAcquire(arg))
            //如果尝试获取锁调用失败，则调用doAcquireInterruptibly
            doAcquireInterruptibly(arg);
    }

    /**
     * 尝试以独占模式获取，如果中断则中止，如果给定超时已过则失败。通过首先检查中断状态，
     * 然后至少调用一次{@link#tryAcquire}，在成功时返回来实现。否则，线程将排队，可能会重复阻塞和解除阻塞，
     * 调用{@link#tryAcquire}，直到成功或线程中断或超时结束。
     * 此方法可用于实现方法{@link Lock#tryLock（long，TimeUnit）}。
     * Attempts to acquire in exclusive mode, aborting if interrupted,
     * and failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquire}, returning on success.  Otherwise, the thread is
     * queued, possibly repeatedly blocking and unblocking, invoking
     * {@link #tryAcquire} until success or the thread is interrupted
     * or the timeout elapses.  This method can be used to implement
     * method {@link Lock#tryLock(long, TimeUnit)}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     *            获取参数。这个值被传递到{@link#tryAcquire}，但在其他方面并不令人惊讶，
     *            可以表示任何您喜欢的内容。
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        //判断是否已经中断
        if (Thread.interrupted())
            throw new InterruptedException();
        //首先会尝试获取锁，如果失败则调用doAcquireNanos方法进行超时等待
        return tryAcquire(arg) ||
            doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 以独占模式释放。如果{@link#tryRelease}返回true，则通过取消阻止一个或多个线程来实现。
     * 此方法可用于实现方法{@link Lock#unlock}。
     * Releases in exclusive mode.  Implemented by unblocking one or
     * more threads if {@link #tryRelease} returns true.
     * This method can be used to implement method {@link Lock#unlock}.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryRelease} but is otherwise uninterpreted and
     *        can represent anything you like.
     *            这个值被传递到{@link#tryRelease}，但在其他方面并不令人惊讶，可以表示任何您喜欢的内容。
     * @return the value returned from {@link #tryRelease}
     */
    public final boolean release(int arg) {
        //尝试释放锁
        if (tryRelease(arg)) {
            //可以释放锁
            Node h = head;
            if (h != null && h.waitStatus != 0)
                //如果h不为空(即当前等待线程的链表不为空)，h的状态不为0，则尝试唤醒首节点
                unparkSuccessor(h);
            return true;
        }
        return false;
    }

    /**
     * 以共享模式获取，忽略中断。通过至少调用一次{@link#tryAcquireShared}来实现，成功后返回。
     * 否则线程将排队，可能会重复阻塞和解除阻塞，调用{@link#tryAcquireShared}直到成功。
     * Acquires in shared mode, ignoring interrupts.  Implemented by
     * first invoking at least once {@link #tryAcquireShared},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquireShared} until success.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }

    /**
     * Acquires in shared mode, aborting if interrupted.  Implemented
     * by first checking interrupt status, then invoking at least once
     * {@link #tryAcquireShared}, returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted.
     * @param arg the acquire argument.
     * This value is conveyed to {@link #tryAcquireShared} but is
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        //响应线程中断
        if (Thread.interrupted())
            throw new InterruptedException();
        //尝试获取锁，小于0表示获取失败
        /*
         *tryAcquireShared方法，该方法尝试获取锁，由AQS子类实现，其返回值的含义如下：
         * State	资源的定义
         * <0	    表示获取失败
         * 0	    表示获取成功
         * >0	    表示获取成功，且后继争用线程可能成功
         */
        if (tryAcquireShared(arg) < 0)
            //加入等待列队
            doAcquireSharedInterruptibly(arg);
    }

    /**
     * Attempts to acquire in shared mode, aborting if interrupted, and
     * failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquireShared}, returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted or the timeout elapses.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 ||
            doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * Releases in shared mode.  Implemented by unblocking one or more
     * threads if {@link #tryReleaseShared} returns true.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryReleaseShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @return the value returned from {@link #tryReleaseShared}
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    // Queue inspection methods

    /**
     * 查询是否有线程正在等待获取。请注意，由于中断和超时导致的取消可能随时发生，
     * 因此{@code true}返回不能保证任何其他线程将获得。
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations due to interrupts and timeouts may occur
     * at any time, a {@code true} return does not guarantee that any
     * other thread will ever acquire.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there may be other threads waiting to acquire
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is if an acquire method has ever blocked.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there has ever been contention
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * Returns the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued.
     *
     * <p>In this implementation, this operation normally returns in
     * constant time, but may iterate upon contention if other threads are
     * concurrently modifying the queue.
     *
     * @return the first (longest-waiting) thread in the queue, or
     *         {@code null} if no threads are currently queued
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails
     */
    private Thread fullGetFirstQueuedThread() {
        /*
         * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null) ||
            ((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null))
            return st;

        /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null)
                firstThread = tt;
            t = t.prev;
        }
        return firstThread;
    }

    /**
     * Returns true if the given thread is currently queued.
     *
     * <p>This implementation traverses the queue to determine
     * presence of the given thread.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is on the queue
     * @throws NullPointerException if the thread is null
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.thread == thread)
                return true;
        return false;
    }

    /**
     * Returns {@code true} if the apparent first queued thread, if one
     * exists, is waiting in exclusive mode.  If this method returns
     * {@code true}, and the current thread is attempting to acquire in
     * shared mode (that is, this method is invoked from {@link
     * #tryAcquireShared}) then it is guaranteed that the current thread
     * is not the first queued thread.  Used only as a heuristic in
     * ReentrantReadWriteLock.
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null &&
            (s = h.next)  != null &&
            !s.isShared()         &&
            s.thread != null;
    }

    /**
     * 查询是否存在线程比当前线程等待的时间更长
     * Queries whether any threads have been waiting to acquire longer
     * than the current thread.
     *
     * 这个方法的调用等用于但是可以更高效:
     * <p>An invocation of this method is equivalent to (but may be
     * more efficient than):
     *  <pre> {@code
     * getFirstQueuedThread() != Thread.currentThread() &&
     * hasQueuedThreads()}</pre>
     *
     * 请注意因为由于中断或者超时导致的取消可以在任何时刻发生，true的返回并不能保证另外一下线程将会比当前线程
     * 更先获取到。如此，有可能在这个方法返回false之后的其他线程可能争取到入栈的权利，由于这个列队还保持空的。
     * <p>Note that because cancellations due to interrupts and
     * timeouts may occur at any time, a {@code true} return does not
     * guarantee that some other thread will acquire before the current
     * thread.  Likewise, it is possible for another thread to win a
     * race to enqueue after this method has returned {@code false},
     * due to the queue being empty.
     *
     * <p>This method is designed to be used by a fair synchronizer to
     * avoid <a href="AbstractQueuedSynchronizer#barging">barging</a>.
     * Such a synchronizer's {@link #tryAcquire} method should return
     * {@code false}, and its {@link #tryAcquireShared} method should
     * return a negative value, if this method returns {@code true}
     * (unless this is a reentrant acquire).  For example, the {@code
     * tryAcquire} method for a fair, reentrant, exclusive mode
     * synchronizer might look like this:
     *
     *  <pre> {@code
     * protected boolean tryAcquire(int arg) {
     *   if (isHeldExclusively()) {
     *     // A reentrant acquire; increment hold count
     *     return true;
     *   } else if (hasQueuedPredecessors()) {
     *     return false;
     *   } else {
     *     // try to acquire normally
     *   }
     * }}</pre>
     *
     * @return {@code true} if there is a queued thread preceding the
     *         current thread, and {@code false} if the current thread
     *         is at the head of the queue or the queue is empty
     * @since 1.7
     */
    public final boolean hasQueuedPredecessors() {
        // The correctness of this depends on head being initialized
        // before tail and on head.next being accurate if the current
        // thread is first in queue.
        // Read fields in reverse initialization order
        Node t = tail;
        Node h = head;
        Node s;
        return h != t &&
            ((s = h.next) == null || s.thread != Thread.currentThread());
    }


    // Instrumentation and monitoring methods

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring system state, not for synchronization
     * control.
     *
     * @return the estimated number of threads waiting to acquire
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in exclusive mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to an exclusive acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in shared mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to a shared acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String {@code "State ="}
     * followed by the current value of {@link #getState}, and either
     * {@code "nonempty"} or {@code "empty"} depending on whether the
     * queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        int s = getState();
        String q  = hasQueuedThreads() ? "non" : "";
        return super.toString() +
            "[State = " + s + ", " + q + "empty queue]";
    }


    // Internal support methods for Conditions

    /**
     * Returns true if a node, always one that was initially placed on
     * a condition queue, is now waiting to reacquire on sync queue.
     * @param node the node
     * @return true if is reacquiring
     */
    final boolean isOnSyncQueue(Node node) {
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;
        if (node.next != null) // If has successor, it must be on queue
            return true;
        /*
         * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         */
        return findNodeFromTail(node);
    }

    /**
     * Returns true if node is on sync queue by searching backwards from tail.
     * Called only when needed by isOnSyncQueue.
     * @return true if present
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (;;) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }

    /**
     *
     * Transfers a node from a condition queue onto sync queue.
     * Returns true if successful.
     * @param node the node
     * @return true if successfully transferred (else the node was
     * cancelled before signal)
     */
    final boolean transferForSignal(Node node) {
        /*
         * If cannot change waitStatus, the node has been cancelled.
         */
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
            return false;

        /*
         * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently and harmlessly wrong).
         */
        Node p = enq(node);
        int ws = p.waitStatus;
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
            LockSupport.unpark(node.thread);
        return true;
    }

    /**
     * Transfers node, if necessary, to sync queue after a cancelled wait.
     * Returns true if thread was cancelled before being signalled.
     *
     * @param node the node
     * @return true if cancelled before the node was signalled
     */
    final boolean transferAfterCancelledWait(Node node) {
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        /*
         * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         */
        while (!isOnSyncQueue(node))
            Thread.yield();
        return false;
    }

    /**
     * 释放锁
     * Invokes release with current state value; returns saved state.
     * Cancels node and throws exception on failure.
     * @param node the condition node for this wait
     * @return previous sync state 返回释放前的同步状态值
     */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            //尝试释放锁，如果线程未持有锁，会抛出异常
            //同步状态值
            int savedState = getState();
            if (release(savedState)) {
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            //释放失败将当前节点置为CANCELLED，后续会从列队移除
            if (failed)
                node.waitStatus = Node.CANCELLED;
        }
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link
     * AbstractQueuedSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     *
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedSynchronizer}.
     *
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /**
         * First node of condition queue.
         * 条件列队首指针节点
         */
        private transient Node firstWaiter;
        /**
         * Last node of condition queue.
         * 条件列队尾指针节点
         */
        private transient Node lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() { }

        // Internal methods

        /**
         * Adds a new waiter to wait queue.
         * @return its new wait node
         */
        private Node addConditionWaiter() {
            Node t = lastWaiter;
            // If lastWaiter is cancelled, clean out.
            if (t != null && t.waitStatus != Node.CONDITION) {
                unlinkCancelledWaiters();
                t = lastWaiter;
            }
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (t == null)
                firstWaiter = node;
            else
                t.nextWaiter = node;
            lastWaiter = node;
            return node;
        }

        /**
         * 删除条件【列队】中的第一个CONDITION:-2节点
         * Removes and transfers nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(Node first) {
            do {
                //
                if ( (firstWaiter = first.nextWaiter) == null)
                    lastWaiter = null;
                first.nextWaiter = null;
                //删除完成后，transferForSignal方法会将CONDITON结点转换为初始结点，并插入【等待队列】
            } while (!transferForSignal(first) &&
                     (first = firstWaiter) != null);
        }

        /**
         * Removes and transfers all nodes.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * Unlinks cancelled waiter nodes from condition queue.
         * Called only while holding lock. This is called when
         * cancellation occurred during condition wait, and upon
         * insertion of a new waiter when lastWaiter is seen to have
         * been cancelled. This method is needed to avoid garbage
         * retention in the absence of signals. So even though it may
         * require a full traversal, it comes into play only when
         * timeouts or cancellations occur in the absence of
         * signals. It traverses all nodes rather than stopping at a
         * particular target to unlink all pointers to garbage nodes
         * without requiring many re-traversals during cancellation
         * storms.
         */
        private void unlinkCancelledWaiters() {
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;
                    if (next == null)
                        lastWaiter = trail;
                }
                else
                    trail = t;
                t = next;
            }
        }

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signal() {
            //如果线程未持有锁，抛出异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();

            //释放条件列队的队首节点
            Node first = firstWaiter;
            if (first != null)
                doSignal(first);
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signalAll() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignalAll(first);
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if (Thread.interrupted())
                    interrupted = true;
            }
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /** Mode meaning to reinterrupt on exit from wait */
        private static final int REINTERRUPT =  1;
        /** Mode meaning to throw InterruptedException on exit from wait */
        private static final int THROW_IE    = -1;

        /**
         * Checks for interrupt, returning THROW_IE if interrupted
         * before signalled, REINTERRUPT if after signalled, or
         * 0 if not interrupted.
         */
        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ?
                (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                0;
        }

        /**
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode)
            throws InterruptedException {
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            else if (interruptMode == REINTERRUPT)
                selfInterrupt();
        }

        /**
         * 先对线程中断做一次预判断，然后将线程包装成结点插入【条件队列】，插入完成后，条件队列的结构如下
         * Implements interruptible condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled or interrupted.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final void await() throws InterruptedException {
            //响应中断
            if (Thread.interrupted())
                throw new InterruptedException();
            //插入条件列队
            Node node = addConditionWaiter();
            //释放锁，返回释放前的同步状态
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            //判断当前节点是否在【等待列队】中
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null) // clean up if cancelled
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return deadline - System.nanoTime();
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean awaitUntil(Date deadline)
                throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit)
                throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
         *
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
         *
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
         *
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    /**
     * 设置为支持compareAndSet。我们需要在这里本地实现这一点：为了允许将来的增强，
     * 我们不能显式地将AtomicInteger子类化，否则它将是高效和有用的。
     * 因此，作为较少的evil，我们使用hotspot intrinsics API本机实现。当我们在这里的时候，
     * 我们对其他CASable字段也做同样的操作（这可以通过原子字段更新器来完成）。
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            //获取state在方法区的地址偏移量
            stateOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("next"));

        } catch (Exception ex) { throw new Error(ex); }
    }

    /**
     * CAS head field. Used only by enq.
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS tail field. Used only by enq.
     * @param expect preNode
     * @param  update nextNode
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS waitStatus field of a node.
     */
    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                                        expect, update);
    }

    /**
     * CAS next field of a node.
     */
    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
