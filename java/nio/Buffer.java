/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.nio;

import java.util.Spliterator;

/**
 * 特定原始类型的数据的容器。
 * A container for data of a specific primitive type.
 *
 * 缓冲区是特定原始类型元素的线性有限序列。除了其内容之外，缓冲区的基本属性还包括其容量，限制和位置：
 * <p> A buffer is a linear, finite sequence of elements of a specific
 * primitive type.  Aside from its content, the essential properties of a
 * buffer are its capacity, limit, and position: </p>
 *
 * <blockquote>
 *   一个缓冲区的容量是包含元素的数量。一个缓冲区的容量从来不会是负数，并且不会改变。
 *   <p> A buffer's <i>capacity</i> is the number of elements it contains.  The
 *   capacity of a buffer is never negative and never changes.  </p>
 *
 *   缓冲区的限制是第一个不能读或者写的元素的索引。缓冲区的限制从来不会是负数，也不会比他的容量大。
 *   <p> A buffer's <i>limit</i> is the index of the first element that should
 *   not be read or written.  A buffer's limit is never negative and is never
 *   greater than its capacity.  </p>
 *
 *   缓冲区的位置是下一个可读可写的元素的索引。缓存区的位置从来不会是负数，并且也不会超过他的限制。
 *   <p> A buffer's <i>position</i> is the index of the next element to be
 *   read or written.  A buffer's position is never negative and is never
 *   greater than its limit.  </p>
 *
 * </blockquote>
 *
 * 对于每个非布尔基元类型，该类都有一个子类。
 * <p> There is one subclass of this class for each non-boolean primitive type.
 *
 * 传输数据
 * <h2> Transferring data </h2>
 *
 * 每个类的子类都定义了两种类型的get和put操作：
 * <p> Each subclass of this class defines two categories of <i>get</i> and
 * <i>put</i> operations: </p>
 *
 * <blockquote>
 *
 *   相对操作开始在当前位置读取或写入一个或多个元素，然后通过传输数据的个数移动当前位置。
 *   如果请求的传输超出限制，则相对get操作会抛出{@link BufferUnderflowException}错误，
 *  并且相对的put操作会抛出一个{@link BufferOverflowException}错误;无论哪种情况，都不会传输数据。
 *   <p> <i>Relative</i> operations read or write one or more elements starting
 *   at the current position and then increment the position by the number of
 *   elements transferred.  If the requested transfer exceeds the limit then a
 *   relative <i>get</i> operation throws a {@link BufferUnderflowException}
 *   and a relative <i>put</i> operation throws a {@link
 *   BufferOverflowException}; in either case, no data is transferred.  </p>
 *
 *   绝对操作采用显式元素索引，而不采用影响位置。如果索引的参数超出limit限制，
 *   绝对get和put操作将会抛出{@link IndexOutOfBoundsException}
 *   <p> <i>Absolute</i> operations take an explicit element index and do not
 *   affect the position.  Absolute <i>get</i> and <i>put</i> operations throw
 *   an {@link IndexOutOfBoundsException} if the index argument exceeds the
 *   limit.  </p>
 *
 * </blockquote>
 *
 * 当然，也可以通过始终相对于当前位置的适当通道的I/O操作将数据移入或移出缓冲区。
 * <p> Data may also, of course, be transferred in to or out of a buffer by the
 * I/O operations of an appropriate channel, which are always relative to the
 * current position.
 *
 *
 * 标记和重置
 * <h2> Marking and resetting </h2>
 *
 * 当{@link #reset reset}方法被调用的时候，缓冲区的标记索引将会重置他的位置。标记不会总是会被定义，但是当她被定义了，他从来不会
 * 是负数并且比position大。如果定义了标记，则在将位置或限制调整为小于标记的值时将其丢弃。
 * 如果未定义标记，则调用{@link #reset reset}方法会引发{@link InvalidMarkException}。
 * <p> A buffer's <i>mark</i> is the index to which its position will be reset
 * when the {@link #reset reset} method is invoked.  The mark is not always
 * defined, but when it is defined it is never negative and is never greater
 * than the position.  If the mark is defined then it is discarded when the
 * position or the limit is adjusted to a value smaller than the mark.  If the
 * mark is not defined then invoking the {@link #reset reset} method causes an
 * {@link InvalidMarkException} to be thrown.
 *
 *
 * 不变量
 * <h2> Invariants </h2>
 *
 * 对于标记，位置，限制和容量值，以下不变量成立:0<=mark<=position<=limit<=capacity
 * <p> The following invariant holds for the mark, position, limit, and
 * capacity values:
 * <blockquote>
 *     <tt>0</tt> <tt>&lt;=</tt>
 *     <i>mark</i> <tt>&lt;=</tt>
 *     <i>position</i> <tt>&lt;=</tt>
 *     <i>limit</i> <tt>&lt;=</tt>
 *     <i>capacity</i>
 * </blockquote>
 *
 * 一个新创建的缓冲区总是有一个初始的0位置和未定义的缓冲区。初始限制可以为零，也可以是其他一些值，
 * 具体取决于缓冲区的类型及其构造方式。新分配的缓冲区的每个元素都初始化为零。
 * <p> A newly-created buffer always has a position of zero and a mark that is
 * undefined.  The initial limit may be zero, or it may be some other value
 * that depends upon the type of the buffer and the manner in which it is
 * constructed.  Each element of a newly-allocated buffer is initialized
 * to zero.
 *
 * 清除，翻转和倒带
 * <h2> Clearing, flipping, and rewinding </h2>
 * 除了访问位置，极限和容量值以及标记和重置的方法之外，此类还定义了以下对缓冲区的操作:
 * <p> In addition to methods for accessing the position, limit, and capacity
 * values and for marking and resetting, this class also defines the following
 * operations upon buffers:
 *
 * <ul>
 *   清除操作{@link #clear}使缓冲区为新的通道读取或相对的put操作序列做好准备：它将限制设置为容量，并将位置设置为零。
 *   clear()方法用于写模式，其作用为情况Buffer中的内容，所谓清空是指写上限与Buffer的真实容量相同，
 *   即limit==capacity,同时将当前写位置置为最前端下标为0处。
 *
 *   <li><p> {@link #clear} makes a buffer ready for a new sequence of
 *   channel-read or relative <i>put</i> operations: It sets the limit to the
 *   capacity and the position to zero.  </p></li>
 *
 *   翻转操作{@link #flip}使缓冲区准备好进行新的通道写入或相对的get操作序列：它将限制设置为当前位置，然后将该位置设置为零。
 *   flip()函数的作用是将写模式转变为读模式，即将写模式下的Buffer中内容的最后位置变为读模式下的limit位置，
 *   作为读越界位置，同时将当前读位置置为0，表示转换后重头开始读，同时再消除写模式下的mark标记。
 *   <li><p> {@link #flip} makes a buffer ready for a new sequence of
 *   channel-write or relative <i>get</i> operations: It sets the limit to the
 *   current position and then sets the position to zero.  </p></li>
 *
 *   倒带操作{@link #rewind}使缓冲区准备好重新读取它已经包含的数据：保留限制不变，并将位置设置为零。
 *   rewind()在读写模式下都可用，它单纯的将当前位置置0，同时取消mark标记，仅此而已；
 *   也就是说写模式下limit仍保持与Buffer容量相同，只是重头写而已；读模式下limit仍然与rewind()调用之前相同，
 *   也就是为flip()调用之前写模式下的position的最后位置，flip()调用后此位置变为了读模式的limit位置，即越界位置，
 *   <li><p> {@link #rewind} makes a buffer ready for re-reading the data that
 *   it already contains: It leaves the limit unchanged and sets the position
 *   to zero.  </p></li>
 *
 * </ul>
 *
 * 只读缓冲区
 * <h2> Read-only buffers </h2>
 * 每一个缓冲区是可读的，但是不一定是可写的。
 * 每个缓冲区类的可变方法都指定为可选操作，当对只读缓冲区调用时，它们将引发{@link ReadOnlyBufferException}。
 * 只读缓冲区不允许更改其内容，但其标记，位置和限制值是可变的。
 * 缓冲区是否为只读可以通过调用其{@link #isReadOnly isReadOnly}方法来确定。
 * <p> Every buffer is readable, but not every buffer is writable.  The
 * mutation methods of each buffer class are specified as <i>optional
 * operations</i> that will throw a {@link ReadOnlyBufferException} when
 * invoked upon a read-only buffer.  A read-only buffer does not allow its
 * content to be changed, but its mark, position, and limit values are mutable.
 * Whether or not a buffer is read-only may be determined by invoking its
 * {@link #isReadOnly isReadOnly} method.
 *
 * 线程安全
 * <h2> Thread safety </h2>
 *
 * 缓冲区不能安全地供多个并发线程使用。如果一个缓冲区将由多个线程使用，则应通过适当的同步来控制对该缓冲区的访问。
 * <p> Buffers are not safe for use by multiple concurrent threads.  If a
 * buffer is to be used by more than one thread then access to the buffer
 * should be controlled by appropriate synchronization.
 *
 * 调用链
 * <h2> Invocation chaining </h2>
 *
 * 此类中没有其他要返回值的方法被指定为返回在其上调用它们的缓冲区。这使得方法调用可以链接在一起；例如，语句序列:
 * b.flip();
 * b.position(23);
 * b.limit(42);
 * 可以被一行链式语句替换：b.flip().position(23).limit(42);
 *
 * <p> Methods in this class that do not otherwise have a value to return are
 * specified to return the buffer upon which they are invoked.  This allows
 * method invocations to be chained; for example, the sequence of statements
 *
 * <blockquote><pre>
 * b.flip();
 * b.position(23);
 * b.limit(42);</pre></blockquote>
 *
 * can be replaced by the single, more compact statement
 *
 * <blockquote><pre>
 * b.flip().position(23).limit(42);</pre></blockquote>
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public abstract class Buffer {

    /**
     * 遍历和拆分元素的分割迭代器的特性保留在缓冲区中。
     * The characteristics of Spliterators that traverse and split elements
     * maintained in Buffers.
     */
    static final int SPLITERATOR_CHARACTERISTICS =
        Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.ORDERED;

    // Invariants: mark <= position <= limit <= capacity
    private int mark = -1;
    private int position = 0;
    private int limit;
    private int capacity;

    // Used only by direct buffers
    //使用直接缓存
    // NOTE: hoisted here for speed in JNI GetDirectBufferAddress
    //注意：这里为了提高速度而悬挂在JNI GetDirectBufferAddress中
    long address;

    // Creates a new buffer with the given mark, position, limit, and capacity,
    //通过给定的标记、位置、限制和容量创建一个新的缓存
    // after checking invariants.
    //
    Buffer(int mark, int pos, int lim, int cap) {       // package-private
        //容量不能小于0
        if (cap < 0)
            throw new IllegalArgumentException("Negative capacity: " + cap);
        this.capacity = cap;
        //校验limit变量，并赋值
        //1.此处主要检查limit的返回不能小于0和大于capital。
        //2.如果位置大于limit，则位置赋值等于limit
        //3.如果mark大于limit,将mark赋值为-1.
        limit(lim);
        //1.位置范围:0<= position<=limit
        //2.如果position大于limit,将mark赋值为-1.
        position(pos);
        if (mark >= 0) {
            if (mark > pos)
                throw new IllegalArgumentException("mark > position: ("
                                                   + mark + " > " + pos + ")");
            this.mark = mark;
        }
    }

    /**
     * 返回缓冲区的容量
     * Returns this buffer's capacity.
     *
     * 当前对象缓冲区的容量。
     * @return  The capacity of this buffer
     */
    public final int capacity() {
        return capacity;
    }

    /**
     * 返回缓冲区的位置
     * Returns this buffer's position.
     *
     * 当前对象缓冲区的位置。
     * @return  The position of this buffer
     */
    public final int position() {
        return position;
    }

    /**
     * 设置缓冲区的位置。如果标记被定义并且大于新的位置，他讲不可用
     * Sets this buffer's position.  If the mark is defined and larger than the
     * new position then it is discarded.
     *
     * @param  newPosition
     *         The new position value; must be non-negative
     *         and no larger than the current limit
     *         一个新的位置值。必须是非负数并且大于limit.
     *
     * @return  This buffer 当前缓冲区
     *
     * @throws  IllegalArgumentException
     *          If the preconditions on <tt>newPosition</tt> do not hold
     *          如果新位置的前提条件不被满足的时候抛出此异常。
     */
    public final Buffer position(int newPosition) {
        if ((newPosition > limit) || (newPosition < 0))
            throw new IllegalArgumentException();
        position = newPosition;
        if (mark > position) mark = -1;
        return this;
    }

    /**
     * 返回缓冲区的限制
     * Returns this buffer's limit.
     *
     * @return  The limit of this buffer
     *           当前对象缓冲区的位置。
     */
    public final int limit() {
        return limit;
    }

    /**
     * 设置缓冲区的limit.如果position大于新的limit，他将设置一个新的limit.如果标记被定义并且大于新的limit，则失效。
     * Sets this buffer's limit.  If the position is larger than the new limit
     * then it is set to the new limit.  If the mark is defined and larger than
     * the new limit then it is discarded.
     *
     * @param  newLimit
     *         The new limit value; must be non-negative
     *         and no larger than this buffer's capacity
     *         新的limit值；必须是一个非负数或者不小于缓冲区的容量。
     *
     * @return  This buffer当前缓冲区
     *
     * @throws  IllegalArgumentException
     *          If the preconditions on <tt>newLimit</tt> do not hold
     *          如果新的限制值的前置条件不满足。
     */
    public final Buffer limit(int newLimit) {
        //新的limit大于容量或者小于0
        if ((newLimit > capacity) || (newLimit < 0))
            throw new IllegalArgumentException();
        limit = newLimit;
        if (position > limit) position = limit;
        if (mark > limit) mark = -1;
        return this;
    }

    /**
     * 在当前设置当前缓冲区的标记
     * Sets this buffer's mark at its position.
     *
     * @return  This buffer
     */
    public final Buffer mark() {
        mark = position;
        return this;
    }

    /**
     * 重新设置当前缓冲区的位置到先前定义的标记位置
     * Resets this buffer's position to the previously-marked position.
     *
     * 既不改变又不失效标记值则可以调用此方法。
     * <p> Invoking this method neither changes nor discards the mark's
     * value. </p>
     *
     * @return  This buffer
     *
     * @throws  InvalidMarkException
     *          If the mark has not been set
     *          如果标记还没有被设置则抛出异常
     */
    public final Buffer reset() {
        int m = mark;
        if (m < 0)
            throw new InvalidMarkException();
        position = m;
        return this;
    }

    /**
     * 清除缓冲区。位置被设置为0，limit被设置为容量大小，标志变为不可用(-1)
     * Clears this buffer.  The position is set to zero, the limit is set to
     * the capacity, and the mark is discarded.
     *
     * 在使用可读序列或者put操作填充这个缓冲区之前调用此方法。例如:
     * buf.clear()
     * in.read(buf)
     * <p> Invoke this method before using a sequence of channel-read or
     * <i>put</i> operations to fill this buffer.  For example:
     *
     * <blockquote><pre>
     * buf.clear();     // Prepare buffer for reading
     * in.read(buf);    // Read data</pre></blockquote>
     * 这个方法通常情况下不会清楚缓冲区的数据，他经常会被使用在合适的场景中。
     * <p> This method does not actually erase the data in the buffer, but it
     * is named as if it did because it will most often be used in situations
     * in which that might as well be the case. </p>
     *
     * @return  This buffer
     */
    public final Buffer clear() {
        position = 0;
        limit = capacity;
        mark = -1;
        return this;
    }

    /**
     * 反转缓冲区。限制值倍设置为当前的位置，并且位置设置为0.如果标记已经被定义他将失效(-1)。
     * Flips this buffer.  The limit is set to the current position and then
     * the position is set to zero.  If the mark is defined then it is
     * discarded.
     *
     * 在可读管道或者put操作的序列之后，调用这个方式是为了可写操作或者相关get方法操作准备。例如:
     *
     * <p> After a sequence of channel-read or <i>put</i> operations, invoke
     * this method to prepare for a sequence of channel-write or relative
     * <i>get</i> operations.  For example:
     *
     * <blockquote><pre>
     * buf.put(magic);    // Prepend header
     * in.read(buf);      // Read data into rest of buffer
     * buf.flip();        // Flip buffer
     * out.write(buf);    // Write header + data to channel</pre></blockquote>
     *
     * 当从一个地方到另外一个地方传输数据的时候，这个方法经常对于 {@link java.nio.ByteBuffer#compact compact}方法连着使用
     * <p> This method is often used in conjunction with the {@link
     * java.nio.ByteBuffer#compact compact} method when transferring data from
     * one place to another.  </p>
     *
     * @return  This buffer
     */
    public final Buffer flip() {
        limit = position;
        position = 0;
        mark = -1;
        return this;
    }

    /**
     * 回放这个缓冲区。位置被设置为0，标记变为不可用。
     * Rewinds this buffer.  The position is set to zero and the mark is
     * discarded.
     *
     * 在通道写入或获取操作序列之前调用此方法，前提是已适当设置了限制。例如：
     * out.write(buf);//写剩下的数据
     * buf.rewind();//倒带缓冲区
     * buf.get(array);//复制数据到数组中
     *
     * <p> Invoke this method before a sequence of channel-write or <i>get</i>
     * operations, assuming that the limit has already been set
     * appropriately.  For example:
     *
     * <blockquote><pre>
     * out.write(buf);    // Write remaining data
     * buf.rewind();      // Rewind buffer
     * buf.get(array);    // Copy data into array</pre></blockquote>
     *
     * @return  This buffer
     */
    public final Buffer rewind() {
        position = 0;
        mark = -1;
        return this;
    }

    /**
     * 返回当前位置和limit之前元素的数量，即可用数量。
     * Returns the number of elements between the current position and the
     * limit.
     *
     * @return  The number of elements remaining in this buffer
     *           当前缓冲区剩余元素的数量
     */
    public final int remaining() {
        return limit - position;
    }

    /**
     * 指示在当前位置到limit是否还有任何的元素
     * Tells whether there are any elements between the current position and
     * the limit.
     *
     * @return  <tt>true</tt> if, and only if, there is at least one element
     *          remaining in this buffer
     *          当且仅当为true的时候，在这个缓冲区至少还有一个可用的元素。
     */
    public final boolean hasRemaining() {
        return position < limit;
    }

    /**
     * 指示缓冲区是否可读
     * Tells whether or not this buffer is read-only.
     *
     * @return  <tt>true</tt> if, and only if, this buffer is read-only
     *           当且仅当为true的时候，在这个缓冲区可读。
     */
    public abstract boolean isReadOnly();

    /**
     * 指示此缓冲区是否由可访问数组支持
     * Tells whether or not this buffer is backed by an accessible
     * array.
     *
     * 如果这个方法返回true,那么{@link #array() array}和{@link #arrayOffset() arrayOffset}被调用是安全的。
     * <p> If this method returns <tt>true</tt> then the {@link #array() array}
     * and {@link #arrayOffset() arrayOffset} methods may safely be invoked.
     * </p>
     *
     * @return  <tt>true</tt> if, and only if, this buffer
     *          is backed by an array and is not read-only
     *          当且仅当为true的时候，这个缓冲区由数组支持，并且不是只读的
     *
     * @since 1.6
     */
    public abstract boolean hasArray();

    /**
     * 返回支持此缓冲区的数组（可选操作）
     * Returns the array that backs this
     * buffer&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * 此方法旨在使数组支持的缓冲区更有效地传递给本机代码。具体的子类为这个方法提供了更强类型的返回值。
     * <p> This method is intended to allow array-backed buffers to be
     * passed to native code more efficiently. Concrete subclasses
     * provide more strongly-typed return values for this method.
     *
     * 修改此缓冲区的内容将导致修改返回数组的内容，反之亦然。
     * <p> Modifications to this buffer's content will cause the returned
     * array's content to be modified, and vice versa.
     *
     * 在调用此方法之前调用{@link#hasrarray hasrarray}方法，以确保此缓冲区具有可访问的后备数组。
     * <p> Invoke the {@link #hasArray hasArray} method before invoking this
     * method in order to ensure that this buffer has an accessible backing
     * array.  </p>
     *
     * @return  The array that backs this buffer
     *           支持此缓冲区的数组
     *
     * @throws  ReadOnlyBufferException
     *          If this buffer is backed by an array but is read-only
     *          如果此缓冲区由数组支持但为只读
     *
     * @throws  UnsupportedOperationException
     *          If this buffer is not backed by an accessible array
     *          如果此缓冲区没有可访问数组的支持
     *
     * @since 1.6
     */
    public abstract Object array();

    /**
     * 返回缓冲区的第一个元素在该缓冲区的后备数组中的偏移量；（可选操作）。
     * Returns the offset within this buffer's backing array of the first
     * element of the buffer&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * 如果此缓冲区由数组支持，则缓冲区位置p对应于数组索引p；+arrayOffset（）。
     * <p> If this buffer is backed by an array then buffer position <i>p</i>
     * corresponds to array index <i>p</i>&nbsp;+&nbsp;<tt>arrayOffset()</tt>.
     *
     * 在调用此方法之前调用{@link#hasrarray hasrarray}方法，以确保此缓冲区具有可访问的后备数组。
     * <p> Invoke the {@link #hasArray hasArray} method before invoking this
     * method in order to ensure that this buffer has an accessible backing
     * array.  </p>
     *
     * @return  The offset within this buffer's array
     *          of the first element of the buffer
     *          此缓冲区数组中缓冲区第一个元素的偏移量
     *
     * @throws  ReadOnlyBufferException
     *          If this buffer is backed by an array but is read-only
     *          如果此缓冲区由数组支持但为只读
     *
     * @throws  UnsupportedOperationException
     *          If this buffer is not backed by an accessible array
     *          如果此缓冲区没有可访问数组的支持
     *
     * @since 1.6
     */
    public abstract int arrayOffset();

    /**
     * 指示这个缓冲区是否是直接内容
     * Tells whether or not this buffer is
     * <a href="ByteBuffer.html#direct"><i>direct</i></a>.
     *
     * @return  <tt>true</tt> if, and only if, this buffer is direct
     *           当且仅当为true的时候，缓冲区是直接缓冲区。
     *
     * @since 1.6
     */
    public abstract boolean isDirect();


    // -- Package-private methods for bounds checking, etc. --

    /**
     * 对照限制检查当前位置，如果不小于限制，则抛出{@link BufferUnderflowException}，然后递增该位置。
     * Checks the current position against the limit, throwing a {@link
     * BufferUnderflowException} if it is not smaller than the limit, and then
     * increments the position.
     *
     * @return  The current position value, before it is incremented
     */
    final int nextGetIndex() {                          // package-private
        if (position >= limit)
            throw new BufferUnderflowException();
        return position++;
    }

    final int nextGetIndex(int nb) {                    // package-private
        if (limit - position < nb)
            throw new BufferUnderflowException();
        int p = position;
        position += nb;
        return p;
    }

    /**
     * 对照限制检查当前位置，如果不小于限制，则抛出{@link BufferOverflowException}，然后递增该位置
     * Checks the current position against the limit, throwing a {@link
     * BufferOverflowException} if it is not smaller than the limit, and then
     * increments the position.
     *
     * @return  The current position value, before it is incremented 在他增加之前，返回当前位置值
     */
    final int nextPutIndex() {                          // package-private
        if (position >= limit)
            throw new BufferOverflowException();
        return position++;
    }

    //下一个设置的索引
    final int nextPutIndex(int nb) {                    // package-private
        if (limit - position < nb)
            throw new BufferOverflowException();
        int p = position;
        position += nb;
        return p;
    }

    /**
     * 对照限制检查给定索引，如果不小于限制或小于零，则抛出{@link indexoutfoundsexception}。
     * Checks the given index against the limit, throwing an {@link
     * IndexOutOfBoundsException} if it is not smaller than the limit
     * or is smaller than zero.
     */
    final int checkIndex(int i) {                       // package-private
        if ((i < 0) || (i >= limit))
            throw new IndexOutOfBoundsException();
        return i;
    }

    //检查索引
    final int checkIndex(int i, int nb) {               // package-private
        if ((i < 0) || (nb > limit - i))
            throw new IndexOutOfBoundsException();
        return i;
    }

    //返回标记的数值
    final int markValue() {                             // package-private
        return mark;
    }

    //package方法。删节
    final void truncate() {                             // package-private
        mark = -1;
        position = 0;
        limit = 0;
        capacity = 0;
    }

    //使标记处于不可用
    final void discardMark() {                          // package-private
        mark = -1;
    }

    //监测边界
    static void checkBounds(int off, int len, int size) { // package-private
        if ((off | len | (off + len) | (size - (off + len))) < 0)
            throw new IndexOutOfBoundsException();
    }

}
