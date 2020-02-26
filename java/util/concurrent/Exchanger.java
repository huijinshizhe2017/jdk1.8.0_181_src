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
 * Written by Doug Lea, Bill Scherer, and Michael Scott with
 * assistance from members of JCP JSR-166 Expert Group and released to
 * the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * 交换器，类似于双向栅栏，用于线程之间的配对和数据交换；其内部根据并发情况有“单槽交换”和“多槽交换”之分.
 *
 * Thread1  -----> Object1 ------> Thread2
 *          <----- Object2 <------
 *
 * Thread1线程到达栅栏后，会首先观察有没其它线程已经到达栅栏，如果没有就会等待，如果已经有其它线程（Thread2）已经到达了，
 * 就会以成对的方式交换各自携带的信息，因此Exchanger非常适合用于两个线程之间的数据交换。
 *
 * Exchanger有两种数据交换的方式，当并发量低的时候，内部采用“单槽位交换”；并发量高的时候会采用“多槽位交换”。
 *
 *
 * A synchronization point at which threads can pair and swap elements
 * within pairs.  Each thread presents some object on entry to the
 * {@link #exchange exchange} method, matches with a partner thread,
 * and receives its partner's object on return.  An Exchanger may be
 * viewed as a bidirectional form of a {@link SynchronousQueue}.
 * Exchangers may be useful in applications such as genetic algorithms
 * and pipeline designs.
 *
 * <p><b>Sample Usage:</b>
 * Here are the highlights of a class that uses an {@code Exchanger}
 * to swap buffers between threads so that the thread filling the
 * buffer gets a freshly emptied one when it needs it, handing off the
 * filled one to the thread emptying the buffer.
 *  <pre> {@code
 * class FillAndEmpty {
 *   Exchanger<DataBuffer> exchanger = new Exchanger<DataBuffer>();
 *   DataBuffer initialEmptyBuffer = ... a made-up type
 *   DataBuffer initialFullBuffer = ...
 *
 *   class FillingLoop implements Runnable {
 *     public void run() {
 *       DataBuffer currentBuffer = initialEmptyBuffer;
 *       try {
 *         while (currentBuffer != null) {
 *           addToBuffer(currentBuffer);
 *           if (currentBuffer.isFull())
 *             currentBuffer = exchanger.exchange(currentBuffer);
 *         }
 *       } catch (InterruptedException ex) { ... handle ... }
 *     }
 *   }
 *
 *   class EmptyingLoop implements Runnable {
 *     public void run() {
 *       DataBuffer currentBuffer = initialFullBuffer;
 *       try {
 *         while (currentBuffer != null) {
 *           takeFromBuffer(currentBuffer);
 *           if (currentBuffer.isEmpty())
 *             currentBuffer = exchanger.exchange(currentBuffer);
 *         }
 *       } catch (InterruptedException ex) { ... handle ...}
 *     }
 *   }
 *
 *   void start() {
 *     new Thread(new FillingLoop()).start();
 *     new Thread(new EmptyingLoop()).start();
 *   }
 * }}</pre>
 *
 * <p>Memory consistency effects: For each pair of threads that
 * successfully exchange objects via an {@code Exchanger}, actions
 * prior to the {@code exchange()} in each thread
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * those subsequent to a return from the corresponding {@code exchange()}
 * in the other thread.
 *
 * @since 1.5
 * @author Doug Lea and Bill Scherer and Michael Scott
 * @param <V> The type of objects that may be exchanged
 */
public class Exchanger<V> {

    /*
     * Overview: The core algorithm is, for an exchange "slot",
     * and a participant (caller) with an item:
     *
     * for (;;) {
     *   if (slot is empty) {                       // offer
     *     place item in a Node;
     *     if (can CAS slot from empty to node) {
     *       wait for release;
     *       return matching item in node;
     *     }
     *   }
     *   else if (can CAS slot from node to empty) { // release
     *     get the item in node;
     *     set matching item in node;
     *     release waiting thread;
     *   }
     *   // else retry on CAS failure
     * }
     *
     * This is among the simplest forms of a "dual data structure" --
     * see Scott and Scherer's DISC 04 paper and
     * http://www.cs.rochester.edu/research/synchronization/pseudocode/duals.html
     *
     * This works great in principle. But in practice, like many
     * algorithms centered on atomic updates to a single location, it
     * scales horribly when there are more than a few participants
     * using the same Exchanger. So the implementation instead uses a
     * form of elimination arena, that spreads out this contention by
     * arranging that some threads typically use different slots,
     * while still ensuring that eventually, any two parties will be
     * able to exchange items. That is, we cannot completely partition
     * across threads, but instead give threads arena indices that
     * will on average grow under contention and shrink under lack of
     * contention. We approach this by defining the Nodes that we need
     * anyway as ThreadLocals, and include in them per-thread index
     * and related bookkeeping state. (We can safely reuse per-thread
     * nodes rather than creating them fresh each time because slots
     * alternate between pointing to a node vs null, so cannot
     * encounter ABA problems. However, we do need some care in
     * resetting them between uses.)
     *
     * Implementing an effective arena requires allocating a bunch of
     * space, so we only do so upon detecting contention (except on
     * uniprocessors, where they wouldn't help, so aren't used).
     * Otherwise, exchanges use the single-slot slotExchange method.
     * On contention, not only must the slots be in different
     * locations, but the locations must not encounter memory
     * contention due to being on the same cache line (or more
     * generally, the same coherence unit).  Because, as of this
     * writing, there is no way to determine cacheline size, we define
     * a value that is enough for common platforms.  Additionally,
     * extra care elsewhere is taken to avoid other false/unintended
     * sharing and to enhance locality, including adding padding (via
     * sun.misc.Contended) to Nodes, embedding "bound" as an Exchanger
     * field, and reworking some park/unpark mechanics compared to
     * LockSupport versions.
     *
     * The arena starts out with only one used slot. We expand the
     * effective arena size by tracking collisions; i.e., failed CASes
     * while trying to exchange. By nature of the above algorithm, the
     * only kinds of collision that reliably indicate contention are
     * when two attempted releases collide -- one of two attempted
     * offers can legitimately fail to CAS without indicating
     * contention by more than one other thread. (Note: it is possible
     * but not worthwhile to more precisely detect contention by
     * reading slot values after CAS failures.)  When a thread has
     * collided at each slot within the current arena bound, it tries
     * to expand the arena size by one. We track collisions within
     * bounds by using a version (sequence) number on the "bound"
     * field, and conservatively reset collision counts when a
     * participant notices that bound has been updated (in either
     * direction).
     *
     * The effective arena size is reduced (when there is more than
     * one slot) by giving up on waiting after a while and trying to
     * decrement the arena size on expiration. The value of "a while"
     * is an empirical matter.  We implement by piggybacking on the
     * use of spin->yield->block that is essential for reasonable
     * waiting performance anyway -- in a busy exchanger, offers are
     * usually almost immediately released, in which case context
     * switching on multiprocessors is extremely slow/wasteful.  Arena
     * waits just omit the blocking part, and instead cancel. The spin
     * count is empirically chosen to be a value that avoids blocking
     * 99% of the time under maximum sustained exchange rates on a
     * range of test machines. Spins and yields entail some limited
     * randomness (using a cheap xorshift) to avoid regular patterns
     * that can induce unproductive grow/shrink cycles. (Using a
     * pseudorandom also helps regularize spin cycle duration by
     * making branches unpredictable.)  Also, during an offer, a
     * waiter can "know" that it will be released when its slot has
     * changed, but cannot yet proceed until match is set.  In the
     * mean time it cannot cancel the offer, so instead spins/yields.
     * Note: It is possible to avoid this secondary check by changing
     * the linearization point to be a CAS of the match field (as done
     * in one case in the Scott & Scherer DISC paper), which also
     * increases asynchrony a bit, at the expense of poorer collision
     * detection and inability to always reuse per-thread nodes. So
     * the current scheme is typically a better tradeoff.
     *
     * On collisions, indices traverse the arena cyclically in reverse
     * order, restarting at the maximum index (which will tend to be
     * sparsest) when bounds change. (On expirations, indices instead
     * are halved until reaching 0.) It is possible (and has been
     * tried) to use randomized, prime-value-stepped, or double-hash
     * style traversal instead of simple cyclic traversal to reduce
     * bunching.  But empirically, whatever benefits these may have
     * don't overcome their added overhead: We are managing operations
     * that occur very quickly unless there is sustained contention,
     * so simpler/faster control policies work better than more
     * accurate but slower ones.
     *
     * Because we use expiration for arena size control, we cannot
     * throw TimeoutExceptions in the timed version of the public
     * exchange method until the arena size has shrunken to zero (or
     * the arena isn't enabled). This may delay response to timeout
     * but is still within spec.
     *
     * Essentially all of the implementation is in methods
     * slotExchange and arenaExchange. These have similar overall
     * structure, but differ in too many details to combine. The
     * slotExchange method uses the single Exchanger field "slot"
     * rather than arena array elements. However, it still needs
     * minimal collision detection to trigger arena construction.
     * (The messiest part is making sure interrupt status and
     * InterruptedExceptions come out right during transitions when
     * both methods may be called. This is done by using null return
     * as a sentinel to recheck interrupt status.)
     *
     * As is too common in this sort of code, methods are monolithic
     * because most of the logic relies on reads of fields that are
     * maintained as local variables so can't be nicely factored --
     * mainly, here, bulky spin->yield->block/cancel code), and
     * heavily dependent on intrinsics (Unsafe) to use inlined
     * embedded CAS and related memory access operations (that tend
     * not to be as readily inlined by dynamic compilers when they are
     * hidden behind other methods that would more nicely name and
     * encapsulate the intended effects). This includes the use of
     * putOrderedX to clear fields of the per-thread Nodes between
     * uses. Note that field Node.item is not declared as volatile
     * even though it is read by releasing threads, because they only
     * do so after CAS operations that must precede access, and all
     * uses by the owning thread are otherwise acceptably ordered by
     * other operations. (Because the actual points of atomicity are
     * slot CASes, it would also be legal for the write to Node.match
     * in a release to be weaker than a full volatile write. However,
     * this is not done because it could allow further postponement of
     * the write, delaying progress.)
     */

    /**
     * The byte distance (as a shift value) between any two used slots
     * in the arena.  1 << ASHIFT should be at least cacheline size.
     */
    private static final int ASHIFT = 7;

    /**
     * The maximum supported arena index. The maximum allocatable
     * arena size is MMASK + 1. Must be a power of two minus one, less
     * than (1<<(31-ASHIFT)). The cap of 255 (0xff) more than suffices
     * for the expected scaling limits of the main algorithms.
     */
    private static final int MMASK = 0xff;

    /**
     * Unit for sequence/version bits of bound field. Each successful
     * change to the bound also adds SEQ.
     */
    private static final int SEQ = MMASK + 1;

    /** The number of CPUs, for sizing and spin control */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * The maximum slot index of the arena: The number of slots that
     * can in principle hold all threads without contention, or at
     * most the maximum indexable value.
     */
    static final int FULL = (NCPU >= (MMASK << 1)) ? MMASK : NCPU >>> 1;

    /**
     * The bound for spins while waiting for a match. The actual
     * number of iterations will on average be about twice this value
     * due to randomization. Note: Spinning is disabled when NCPU==1.
     */
    private static final int SPINS = 1 << 10;

    /**
     * Value representing null arguments/returns from public
     * methods. Needed because the API originally didn't disallow null
     * arguments, which it should have.
     */
    private static final Object NULL_ITEM = new Object();

    /**
     * Sentinel value returned by internal exchange methods upon
     * timeout, to avoid need for separate timed versions of these
     * methods.
     */
    private static final Object TIMED_OUT = new Object();

    /**
     * Node节点持有部分交换对象。加上其他每线程簿记。通过@sun.misc.Contended。旨在减少内存争用。
     * Nodes hold partially exchanged data, plus other per-thread
     * bookkeeping. Padded via @sun.misc.Contended to reduce memory
     * contention.
     */
    @sun.misc.Contended static final class Node {
        /**
         * Arena index
         * 多槽数组的索引
         */
        int index;
        /**
         * Last recorded value of Exchanger.bound
         * 交换边界最后记录的值
         */
        int bound;

        /**
         * Number of CAS failures at current bound
         * 在当前边界中CAS失败的次数
         */
        int collides;

        /*++++++++++++++++++++++++++++以上字段多槽交换使用++++++++++++++++++++++++++++++++++*/
        /**
         * Pseudo-random for spins
         * 线程的伪随机数，用于自旋优化
         */
        int hash;

        /**
         * This thread's current item
         * 当前线程携带的数据
         */
        Object item;
        /**
         * Item provided by releasing thread
         * 配对线程携带的数据。后到达的线程会将自身携带的值设置到配对线程的该字段上。
         */
        volatile Object match;
        /**
         * Set to this thread when parked, else null
         * 此节点的阻塞线程（先到达并阻塞的线程会设置该值为自身）
         */
        volatile Thread parked;
    }

    /**
     * The corresponding thread local class
     */
    static final class Participant extends ThreadLocal<Node> {
        public Node initialValue() { return new Node(); }
    }

    /**
     * Per-thread state
     * 构造时，内部创建了一个Participant对象，
     * Participant是Exchanger的一个内部类，本质就是一个ThreadLocal，用来保存线程本地变量Node：
     */
    private final Participant participant;

    /**
     * 多槽交换数组
     * Elimination array; null until enabled (within slotExchange).
     * Element accesses use emulation of volatile gets and CAS.
     */
    private volatile Node[] arena;

    /**
     * 单槽交换点
     * Slot used until contention detected.
     *
     */
    private volatile Node slot;

    /**
     * The index of the largest valid arena position, OR'ed with SEQ
     * number in high bits, incremented on each update.  The initial
     * update from 0 to SEQ is used to ensure that the arena array is
     * constructed only once.
     */
    private volatile int bound;

    /**
     * Exchange function when arenas enabled. See above for explanation.
     *
     * 多槽交换方法arenaExchange的整体流程和slotExchange类似，
     * 主要区别在于它会根据当前线程的数据携带结点Node中的index字段计算出命中的槽位。
     *
     * 如果槽位被占用，说明已经有线程先到了，之后的处理和slotExchange一样；
     *
     * 如果槽位有效且为null，说明当前线程是先到的，就占用槽位，然后按照：
     * spin->yield->block这种锁升级的顺序进行优化的等待，等不到配对线程就会进入阻塞。
     *
     * 另外，由于arenaExchange利用了槽数组，所以涉及到槽数组的扩容和缩减问题，读者可以自己去研读源码。
     *
     * 其次，在定位arena数组的有效槽位时，需要考虑缓存行的影响。由于高速缓存与内存之间是以缓存行为单位交换数据的，
     * 根据局部性原理，相邻地址空间的数据会被加载到高速缓存的同一个数据块上（缓存行），
     * 而数组是连续的（逻辑，涉及到虚拟内存）内存地址空间，因此，多个slot会被加载到同一个缓存行上，
     * 当一个slot改变时，会导致这个slot所在的缓存行上所有的数据（包括其他的slot）无效，
     * 需要从内存重新加载，影响性能。
     *
     * @param item the (non-null) item to exchange
     * @param timed true if the wait is timed
     * @param ns if timed, the maximum wait time, else 0L
     * @return the other thread's item; or null if interrupted; or
     * TIMED_OUT if timed and timed out
     *         其它配对线程的数据; 如果被中断返回null, 如果超时返回TIMED_OUT(一个Obejct对象)
     */
    private final Object arenaExchange(Object item, boolean timed, long ns) {
        Node[] a = arena;
        // 当前线程携带的交换结点
        Node p = participant.get();
        // 当前线程的arena索引
        for (int i = p.index;;) {                      // access slot at i
            int b, m, c; long j;                       // j is raw array offset
            // 从arena数组中选出偏移地址为(i << ASHIFT) + ABASE的元素, 即真正可用的Node
            Node q = (Node)U.getObjectVolatile(a, j = (i << ASHIFT) + ABASE);
            // CASE1: 槽不为空，说明已经有线程到达并在等待了
            if (q != null && U.compareAndSwapObject(a, j, q, null)) {
                // 获取已经到达的线程所携带的值
                Object v = q.item;                     // release
                // 把当前线程携带的值交换给已经到达的线程
                q.match = item;
                // q.parked指向已经到达的线程
                Thread w = q.parked;
                if (w != null)
                    // 唤醒已经到达的线程
                    U.unpark(w);
                return v;
            }
            // CASE2: 有效槽位位置且槽位为空
            else if (i <= (m = (b = bound) & MMASK) && q == null) {
                p.item = item;                         // offer
                if (U.compareAndSwapObject(a, j, null, p)) {
                    long end = (timed && m == 0) ? System.nanoTime() + ns : 0L;
                    Thread t = Thread.currentThread(); // wait
                    // 自旋等待一段时间,看看有没其它配对线程到达该槽位
                    for (int h = p.hash, spins = SPINS;;) {
                        Object v = p.match;
                        // 有配对线程到达了该槽位
                        if (v != null) {
                            U.putOrderedObject(p, MATCH, null);
                            p.item = null;             // clear for next use
                            p.hash = h;
                            // 返回配对线程交换过来的值
                            return v;
                        }
                        else if (spins > 0) {
                            h ^= h << 1; h ^= h >>> 3; h ^= h << 10; // xorshift
                            if (h == 0)                // initialize hash
                                h = SPINS | (int)t.getId();
                            else if (h < 0 &&          // approx 50% true
                                     (--spins & ((SPINS >>> 1) - 1)) == 0)
                                // 每一次等待有两次让出CPU的时机
                                Thread.yield();        // two yields per wait
                        }
                        // 优化操作:配对线程已经到达, 但是还未完全准备好, 所以需要再自旋等待一会儿
                        else if (U.getObjectVolatile(a, j) != p)
                            spins = SPINS;       // releaser hasn't set match yet
                        else if (!t.isInterrupted() && m == 0 &&
                                 (!timed ||
                                  (ns = end - System.nanoTime()) > 0L)) {
                            // 等不到配对线程了, 阻塞当前线程
                            U.putObject(t, BLOCKER, this); // emulate LockSupport
                            // 在结点引用当前线程，以便配对线程到达后唤醒我
                            p.parked = t;              // minimize window
                            if (U.getObjectVolatile(a, j) == p)
                                U.park(false, ns);
                            p.parked = null;
                            // 尝试缩减arena槽数组的大小
                            U.putObject(t, BLOCKER, null);
                        }
                        else if (U.getObjectVolatile(a, j) == p &&
                                 U.compareAndSwapObject(a, j, p, null)) {
                            if (m != 0)                // try to shrink
                                U.compareAndSwapInt(this, BOUND, b, b + SEQ - 1);
                            p.item = null;
                            p.hash = h;
                            i = p.index >>>= 1;        // descend
                            if (Thread.interrupted())
                                return null;
                            if (timed && m == 0 && ns <= 0L)
                                return TIMED_OUT;
                            break;                     // expired; restart
                        }
                    }
                }
                // 占用槽位失败
                else
                    p.item = null;                     // clear offer
            }
            // CASE3: 无效槽位位置, 需要扩容
            else {
                if (p.bound != b) {                    // stale; reset
                    p.bound = b;
                    p.collides = 0;
                    i = (i != m || m == 0) ? m : m - 1;
                }
                else if ((c = p.collides) < m || m == FULL ||
                         !U.compareAndSwapInt(this, BOUND, b, b + SEQ + 1)) {
                    p.collides = c + 1;
                    i = (i == 0) ? m : i - 1;          // cyclically traverse
                }
                else
                    i = m + 1;                         // grow
                p.index = i;
            }
        }
    }

    /**
     * 在启用竞技场之前一直使用交换功能。请参阅上面的说明。
     * Exchange function used until arenas enabled. See above for explanation.
     * 首先到达的线程：
     *
     * 如果当前线程是首个到达的线程，会将slot字段指向自身的Node结点，表示槽位被占用；
     * 然后，线程会自旋一段时间，如果经过一段时间的自旋还是等不到配对线程到达，就会进入阻塞。（这里之所以不直接阻塞，而是自旋，是出于线程上下文切换开销的考虑，属于一种优化手段）
     * 稍后到达的配对线程：
     * 如果当前线程（配对线程）不是首个到达的线程，则到达时槽（slot）已经被占用，此时slot指向首个到达线程自身的Node结点。配对线程会将slot置空，并取Node中的item作为交换得到的数据返回，另外，配对线程会把自身携带的数据存入Node的match字段中，并唤醒Node.parked所指向的线程（也就是先到达的线程）。
     *
     * 首先到达的线程被唤醒：
     * 线程被唤醒后，由于match不为空（存放了配对线程携带过来的数据），所以会退出自旋，然后将match对应的值返回。
     *
     * 这样，线程A和线程B就实现了数据交换，整个过程都没有用到同步操作。
     *
     * @param item the item to exchange
     * @param timed true if the wait is timed
     * @param ns if timed, the maximum wait time, else 0L
     * @return the other thread's item; or null if either the arena
     * was enabled or the thread was interrupted before completion; or
     * TIMED_OUT if timed and timed out
     */
    private final Object slotExchange(Object item, boolean timed, long ns) {
        //当前线程携带的交换点
        Node p = participant.get();
        Thread t = Thread.currentThread();
        //preserve interrupt status so caller can recheck
        //线程中断检查
        if (t.isInterrupted())
            return null;

        //自旋操作
        for (Node q;;) {
            //slot!= null,说明已经有线程先到并占用了slot
            if ((q = slot) != null) {
                if (U.compareAndSwapObject(this, SLOT, q, null)) {
                    //获取交换值
                    Object v = q.item;
                    //设置交换值
                    q.match = item;
                    Thread w = q.parked;
                    //唤醒在此槽位等待的线程
                    if (w != null)
                        U.unpark(w);
                    //交换成功，返回结果
                    return v;
                }
                // create arena on contention, but continue until slot null
                //CPU核数多于1，且bound为0时创建arena数组，并将bound设置为SEQ大小
                //也就是说，如果在单槽交换中，同时出现了多个配对线程竞争修改slot槽位，
                // 导致某个线程CAS修改slot失败时，就会初始化arena多槽数组，
                // 后续所有的交换都会走arenaExchange：
                if (NCPU > 1 && bound == 0 &&
                    U.compareAndSwapInt(this, BOUND, 0, SEQ))
                    arena = new Node[(FULL + 2) << ASHIFT];
            }
            //// slot == null && arena != null,返回null,执行多槽操作
            else if (arena != null)
                // 单槽交换中途出现了初始化arena的操作，需要重新直接路由到多槽交换(arenaExchange)
                return null; // caller must reroute to arenaExchange
            else {
                // 当前线程先到, 则占用此slot
                p.item = item;
                // 将slot槽占用
                if (U.compareAndSwapObject(this, SLOT, null, p))
                    break;
                // CAS操作失败, 继续下一次自旋
                p.item = null;
            }
        }

        // await release
        // 执行到这, 说明当前线程先到达, 且已经占用了slot槽, 需要等待配对线程到达
        int h = p.hash;
        long end = timed ? System.nanoTime() + ns : 0L;
        // 自旋次数, 与CPU核数有关
        int spins = (NCPU > 1) ? SPINS : 1;
        Object v;
        // p.match == null表示配对的线程还未到达
        while ((v = p.match) == null) {
            // 优化操作:自旋过程中随机释放CPU
            if (spins > 0) {
                h ^= h << 1; h ^= h >>> 3; h ^= h << 10;
                if (h == 0)
                    h = SPINS | (int)t.getId();
                else if (h < 0 && (--spins & ((SPINS >>> 1) - 1)) == 0)
                    Thread.yield();
            }
            // 优化操作:配对线程已经到达, 但是还未完全准备好, 所以需要再自旋等待一会儿
            else if (slot != p)
                spins = SPINS;
            //已经自旋很久了, 还是等不到配对, 此时才阻塞当前线程
            else if (!t.isInterrupted() && arena == null &&
                     (!timed || (ns = end - System.nanoTime()) > 0L)) {
                U.putObject(t, BLOCKER, this);
                p.parked = t;
                if (slot == p)
                    // 阻塞当前线程
                    U.park(false, ns);
                p.parked = null;
                U.putObject(t, BLOCKER, null);
            }
            // 超时或其他（取消）, 给其他线程腾出slot
            else if (U.compareAndSwapObject(this, SLOT, p, null)) {
                v = timed && ns <= 0L && !t.isInterrupted() ? TIMED_OUT : null;
                break;
            }
        }
        U.putOrderedObject(p, MATCH, null);
        p.item = null;
        p.hash = h;
        return v;
    }

    /**
     * Creates a new Exchanger.
     */
    public Exchanger() {
        participant = new Participant();
    }

    /**
     * Waits for another thread to arrive at this exchange point (unless
     * the current thread is {@linkplain Thread#interrupt interrupted}),
     * and then transfers the given object to it, receiving its object
     * in return.
     *
     * <p>If another thread is already waiting at the exchange point then
     * it is resumed for thread scheduling purposes and receives the object
     * passed in by the current thread.  The current thread returns immediately,
     * receiving the object passed to the exchange by that other thread.
     *
     * <p>If no other thread is already waiting at the exchange then the
     * current thread is disabled for thread scheduling purposes and lies
     * dormant until one of two things happens:
     * <ul>
     * <li>Some other thread enters the exchange; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * for the exchange,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @param x the object to exchange
     * @return the object provided by the other thread
     * @throws InterruptedException if the current thread was
     *         interrupted while waiting
     */
    @SuppressWarnings("unchecked")
    public V exchange(V x) throws InterruptedException {
        Object v;
        //这里判NULL,这是一个新的Object对象
        Object item = (x == null) ? NULL_ITEM : x; // translate null args
        /*
         * 这里的判断用来决定数据交换方式:
         * 1.单槽交换(slotExchange):当多槽交换数组为空(arena==null),进行单槽交换；
         * 2.多槽交换(arenaExchange):当多槽交换数组不为空或者单槽交换失败的时候，进行多槽交换。
         *
         * if ((arena != null ||(v = slotExchange(item, false, 0L)) == null) &&
         *     ((Thread.interrupted() || (v = arenaExchange(item, false, 0L)) == null)))
         *
         * 这个判断包含有两部的判断:
         * 1.(arena != null ||(v = slotExchange(item, false, 0L)) == null)
         * 1.1 如果多槽数据(arena)不为空,arena != null为真,即"||"前面为真，则不执行slotExchange...
         * 1.2 如果多槽数据(arena)为空,arena != null为假,即"||"前面为假，执行slotExchange执行
         * 1.3 如果slotExchange执行失败，即v == null,"&&"前部的判断语句为真，"&&"后面的语句执行；
         * 1.4 如果slotExchange执行成功，即v != null；当前判断为false，则整个if判断为false.所以不执行抛出异常的代码；
         *
         * 也就是说，此处判断的结果有两种情况:
         * 1.此处判断为true,则说明多槽数组不为空或者单槽执行失败，此种情况需要执行下面的多槽交换；
         * 2.此处判断为false,即"||"前后都为false,即多槽数组为空且者单槽执行成功，则跳出if判断
         *
         * 2.(Thread.interrupted() || (v = arenaExchange(item, false, 0L)) == null))
         * 2.1 如果线程被中断，也就是if判断为true,则抛出异常；
         * 2.2 多槽交换(arenaExchange)执行成功，即v != null,需要跳出if判断；执行失败，v == null,则抛出异常
         */
        if ((arena != null ||
             (v = slotExchange(item, false, 0L)) == null) &&
            ((Thread.interrupted() || // disambiguates null return
              (v = arenaExchange(item, false, 0L)) == null)))
            throw new InterruptedException();
        return (v == NULL_ITEM) ? null : (V)v;
    }

    /**
     * Waits for another thread to arrive at this exchange point (unless
     * the current thread is {@linkplain Thread#interrupt interrupted} or
     * the specified waiting time elapses), and then transfers the given
     * object to it, receiving its object in return.
     *
     * <p>If another thread is already waiting at the exchange point then
     * it is resumed for thread scheduling purposes and receives the object
     * passed in by the current thread.  The current thread returns immediately,
     * receiving the object passed to the exchange by that other thread.
     *
     * <p>If no other thread is already waiting at the exchange then the
     * current thread is disabled for thread scheduling purposes and lies
     * dormant until one of three things happens:
     * <ul>
     * <li>Some other thread enters the exchange; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * for the exchange,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then {@link
     * TimeoutException} is thrown.  If the time is less than or equal
     * to zero, the method will not wait at all.
     *
     * @param x the object to exchange
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return the object provided by the other thread
     * @throws InterruptedException if the current thread was
     *         interrupted while waiting
     * @throws TimeoutException if the specified waiting time elapses
     *         before another thread enters the exchange
     */
    @SuppressWarnings("unchecked")
    public V exchange(V x, long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException {
        Object v;
        Object item = (x == null) ? NULL_ITEM : x;
        long ns = unit.toNanos(timeout);
        if ((arena != null ||
             (v = slotExchange(item, true, ns)) == null) &&
            ((Thread.interrupted() ||
              (v = arenaExchange(item, true, ns)) == null)))
            throw new InterruptedException();
        if (v == TIMED_OUT)
            throw new TimeoutException();
        return (v == NULL_ITEM) ? null : (V)v;
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long BOUND;
    private static final long SLOT;
    private static final long MATCH;
    private static final long BLOCKER;
    private static final int ABASE;
    static {
        int s;
        try {
            U = sun.misc.Unsafe.getUnsafe();
            Class<?> ek = Exchanger.class;
            Class<?> nk = Node.class;
            Class<?> ak = Node[].class;
            Class<?> tk = Thread.class;
            BOUND = U.objectFieldOffset
                (ek.getDeclaredField("bound"));
            SLOT = U.objectFieldOffset
                (ek.getDeclaredField("slot"));
            MATCH = U.objectFieldOffset
                (nk.getDeclaredField("match"));
            BLOCKER = U.objectFieldOffset
                (tk.getDeclaredField("parkBlocker"));
            s = U.arrayIndexScale(ak);
            // ABASE absorbs padding in front of element 0
            ABASE = U.arrayBaseOffset(ak) + (1 << ASHIFT);

        } catch (Exception e) {
            throw new Error(e);
        }
        if ((s & (s-1)) != 0 || s > (1 << ASHIFT))
            throw new Error("Unsupported array scale");
    }

}
