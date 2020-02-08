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

package java.util.concurrent.atomic;
import java.io.Serializable;

/**
 * 根据Oracle官方文档的介绍，LongAdder在高并发的场景下会比它的前辈————AtomicLong 具有更好的性能，
 * 代价是消耗更多的内存空间：
 *
 * 在并发量较低的环境下，线程冲突的概率比较小，自旋的次数不会很多。
 * 但是，高并发环境下，N个线程同时进行自旋操作，会出现大量失败并不断自旋的情况，
 * 此时AtomicLong的自旋会成为瓶颈。
 * 这就是LongAdder引入的初衷——解决高并发环境下AtomicLong的自旋瓶颈问题。
 *
 * 我们知道，AtomicLong中有个内部变量value保存着实际的long值，所有的操作都是针对该变量进行。
 * 也就是说，高并发环境下，value变量其实是一个热点，也就是N个线程竞争一个热点。
 *
 * LongAdder的基本思路就是分散热点，将value值分散到一个数组中，不同线程会命中到数组的不同槽中，
 * 各个线程只对自己槽中的那个值进行CAS操作，这样热点就被分散了，冲突的概率就小很多。
 * 如果要获取真正的long值，只要将各个槽中的变量值累加返回。
 *
 * addAndGet、decrementAndGet除了单纯的做自增自减外，还可以立即获取增减后的值，
 * 而LongAdder则需要做同步控制才能精确获取增减后的值。如果业务需求需要精确的控制计数，
 * 做计数比较，AtomicLong也更合适。
 *
 * 另外，从空间方面考虑，LongAdder其实是一种“空间换时间”的思想，从这一点来讲AtomicLong更适合。
 * 当然，如果你一定要跟我杠现代主机的内存对于这点消耗根本不算什么，那我也办法。
 *
 * 总之，低并发、一般的业务场景下AtomicLong是足够了。如果并发量很多，存在大量写多读少的情况，
 * 那LongAdder可能更合适。适合的才是最好的，如果真出现了需要考虑到底用AtomicLong好
 * 还是LongAdder的业务场景，那么这样的讨论是没有意义的，因为这种情况下要么进行性能测试，
 * 以准确评估在当前业务场景下两者的性能，要么换个思路寻求其它解决方案。
 * One or more variables that together maintain an initially zero
 * {@code long} sum.  When updates (method {@link #add}) are contended
 * across threads, the set of variables may grow dynamically to reduce
 * contention. Method {@link #sum} (or, equivalently, {@link
 * #longValue}) returns the current total combined across the
 * variables maintaining the sum.
 *
 * <p>This class is usually preferable to {@link AtomicLong} when
 * multiple threads update a common sum that is used for purposes such
 * as collecting statistics, not for fine-grained synchronization
 * control.  Under low update contention, the two classes have similar
 * characteristics. But under high contention, expected throughput of
 * this class is significantly higher, at the expense of higher space
 * consumption.
 *
 * <p>LongAdders can be used with a {@link
 * java.util.concurrent.ConcurrentHashMap} to maintain a scalable
 * frequency map (a form of histogram or multiset). For example, to
 * add a count to a {@code ConcurrentHashMap<String,LongAdder> freqs},
 * initializing if not already present, you can use {@code
 * freqs.computeIfAbsent(k -> new LongAdder()).increment();}
 *
 * <p>This class extends {@link Number}, but does <em>not</em> define
 * methods such as {@code equals}, {@code hashCode} and {@code
 * compareTo} because instances are expected to be mutated, and so are
 * not useful as collection keys.
 *
 * @since 1.8
 * @author Doug Lea
 */
public class LongAdder extends Striped64 implements Serializable {
    private static final long serialVersionUID = 7249069246863182397L;

    /**
     * Creates a new adder with initial sum of zero.
     */
    public LongAdder() {
    }

    /**
     * Adds the given value.
     *
     * @param x the value to add
     */
    public void add(long x) {
        Cell[] as; long b, v; int m; Cell a;
        //初始时Cell[]为null，base为0。
        // 所以ThreadA会调用casBase方法（定义在Striped64中），
        // 因为没有并发，CAS操作成功将base变为10
        //这个方法体会先再次判断Cell[]槽数组有没初始化过，如果初始化过了，
        // 以后所有的CAS操作都只针对槽中的Cell；否则，进入longAccumulate方法。
        if ((as = cells) != null || !casBase(b = base, b + x)) {
            boolean uncontended = true;
            if (as == null || (m = as.length - 1) < 0 ||
                (a = as[getProbe() & m]) == null ||
                !(uncontended = a.cas(v = a.value, v + x)))
                longAccumulate(x, null, uncontended);
        }
    }

    /**
     * Equivalent to {@code add(1)}.
     */
    public void increment() {
        add(1L);
    }

    /**
     * Equivalent to {@code add(-1)}.
     */
    public void decrement() {
        add(-1L);
    }

    /**
     * Returns the current sum.  The returned value is <em>NOT</em> an
     * atomic snapshot; invocation in the absence of concurrent
     * updates returns an accurate result, but concurrent updates that
     * occur while the sum is being calculated might not be
     * incorporated.
     *
     * @return the sum
     */
    public long sum() {
        Cell[] as = cells; Cell a;
        long sum = base;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    sum += a.value;
            }
        }
        return sum;
    }

    /**
     * Resets variables maintaining the sum to zero.  This method may
     * be a useful alternative to creating a new adder, but is only
     * effective if there are no concurrent updates.  Because this
     * method is intrinsically racy, it should only be used when it is
     * known that no threads are concurrently updating.
     */
    public void reset() {
        Cell[] as = cells; Cell a;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    a.value = 0L;
            }
        }
    }

    /**
     * Equivalent in effect to {@link #sum} followed by {@link
     * #reset}. This method may apply for example during quiescent
     * points between multithreaded computations.  If there are
     * updates concurrent with this method, the returned value is
     * <em>not</em> guaranteed to be the final value occurring before
     * the reset.
     *
     * @return the sum
     */
    public long sumThenReset() {
        Cell[] as = cells; Cell a;
        long sum = base;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null) {
                    sum += a.value;
                    a.value = 0L;
                }
            }
        }
        return sum;
    }

    /**
     * Returns the String representation of the {@link #sum}.
     * @return the String representation of the {@link #sum}
     */
    public String toString() {
        return Long.toString(sum());
    }

    /**
     * Equivalent to {@link #sum}.
     *
     * @return the sum
     */
    public long longValue() {
        return sum();
    }

    /**
     * Returns the {@link #sum} as an {@code int} after a narrowing
     * primitive conversion.
     */
    public int intValue() {
        return (int)sum();
    }

    /**
     * Returns the {@link #sum} as a {@code float}
     * after a widening primitive conversion.
     */
    public float floatValue() {
        return (float)sum();
    }

    /**
     * Returns the {@link #sum} as a {@code double} after a widening
     * primitive conversion.
     */
    public double doubleValue() {
        return (double)sum();
    }

    /**
     * Serialization proxy, used to avoid reference to the non-public
     * Striped64 superclass in serialized forms.
     * @serial include
     */
    private static class SerializationProxy implements Serializable {
        private static final long serialVersionUID = 7249069246863182397L;

        /**
         * The current value returned by sum().
         * @serial
         */
        private final long value;

        SerializationProxy(LongAdder a) {
            value = a.sum();
        }

        /**
         * Return a {@code LongAdder} object with initial state
         * held by this proxy.
         *
         * @return a {@code LongAdder} object with initial state
         * held by this proxy.
         */
        private Object readResolve() {
            LongAdder a = new LongAdder();
            a.base = value;
            return a;
        }
    }

    /**
     * Returns a
     * <a href="../../../../serialized-form.html#java.util.concurrent.atomic.LongAdder.SerializationProxy">
     * SerializationProxy</a>
     * representing the state of this instance.
     *
     * @return a {@link SerializationProxy}
     * representing the state of this instance
     */
    private Object writeReplace() {
        return new SerializationProxy(this);
    }

    /**
     * @param s the stream
     * @throws java.io.InvalidObjectException always
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

}
