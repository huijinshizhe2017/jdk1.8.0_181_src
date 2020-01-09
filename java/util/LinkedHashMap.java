/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.io.IOException;

/**
 * 结合先前的迭代,利用HashTable和linkedList并排序实现了Map接口。他与HashMap不同之处就是所有实例采用双向链表的形式运行。
 * 这个linkedList定义了一个可排序的迭代器，key被插入到map的可插入排序的排序器中。
 * 注意:如果一个key重新插入到map中的时候，插入排序器并不会受到影响。
 * (如果m.containsKey（k）在调用之前即将返回true，则调用m.put（k，v）时，会将key（k）重新插入map（m）。)
 *
 * <p>Hash table and linked list implementation of the <tt>Map</tt> interface,
 * with predictable iteration order.  This implementation differs from
 * <tt>HashMap</tt> in that it maintains a doubly-linked list running through
 * all of its entries.  This linked list defines the iteration ordering,
 * which is normally the order in which keys were inserted into the map
 * (<i>insertion-order</i>).  Note that insertion order is not affected
 * if a key is <i>re-inserted</i> into the map.  (A key <tt>k</tt> is
 * reinserted into a map <tt>m</tt> if <tt>m.put(k, v)</tt> is invoked when
 * <tt>m.containsKey(k)</tt> would return <tt>true</tt> immediately prior to
 * the invocation.)
 *
 * 此实现免除了{@link HashMap}（和{@link Hashtable}）提供的未指定的，通常混乱的顺序的客户，而不会增加与{@link TreeMap}相关的成本。
 * 它可用于生成以下内容的副本：与原始map的顺序相同的map，无论原始map的实现如何:
 * <p>This implementation spares its clients from the unspecified, generally
 * chaotic ordering provided by {@link HashMap} (and {@link Hashtable}),
 * without incurring the increased cost associated with {@link TreeMap}.  It
 * can be used to produce a copy of a map that has the same order as the
 * original, regardless of the original map's implementation:
 * <pre>
 *     void foo(Map m) {
 *         Map copy = new LinkedHashMap(m);
 *         ...
 *     }
 * </pre>
 * 如果模块在输入上获取映射，将其复制并随后返回结果（其顺序由副本的顺序确定），则此技术特别有用。 （客户通常喜欢按之前输入的顺序排序。）
 * This technique is particularly useful if a module takes a map on input,
 * copies it, and later returns results whose order is determined by that of
 * the copy.  (Clients generally appreciate having things returned in the same
 * order they were presented.)
 *
 * 提供了一种特殊的{@link #LinkedHashMap（int，float，boolean）构造函数}，以创建链接的哈希映射，
 * 其迭代顺序是其条目的最近访问顺序(从最近到最近)(访问顺序)。这种映射非常适合构建LRU缓存。
 * 调用{@code put}，{@code putIfAbsent}，{@code get}，{@code getOrDefault}，{@code compute}，
 * {@code computeIfAbsent}，{@code computeIfPresent}或{@code merge}方法导致对相应条目的访问（假设调用完成后该条目存在）。
 * 如果替换了值，则{@code replace}方法仅导致对条目的访问。 {@code putAll}方法为指定映射中的每个映射生成一个条目访问，
 * 其顺序为指定映射的条目集迭代器提供键-值映射。 没有其他方法可以生成条目访问权限。尤其是，对集合视图的操作不会影响原始map的迭代顺序。
 * <p>A special {@link #LinkedHashMap(int,float,boolean) constructor} is
 * provided to create a linked hash map whose order of iteration is the order
 * in which its entries were last accessed, from least-recently accessed to
 * most-recently (<i>access-order</i>).  This kind of map is well-suited to
 * building LRU caches.  Invoking the {@code put}, {@code putIfAbsent},
 * {@code get}, {@code getOrDefault}, {@code compute}, {@code computeIfAbsent},
 * {@code computeIfPresent}, or {@code merge} methods results
 * in an access to the corresponding entry (assuming it exists after the
 * invocation completes). The {@code replace} methods only result in an access
 * of the entry if the value is replaced.  The {@code putAll} method generates one
 * entry access for each mapping in the specified map, in the order that
 * key-value mappings are provided by the specified map's entry set iterator.
 * <i>No other methods generate entry accesses.</i>  In particular, operations
 * on collection-views do <i>not</i> affect the order of iteration of the
 * backing map.
 *
 * 可以重写{@link #removeEldestEntry（Map.Entry）}方法，以强加一个策略，以便在将新映射添加到map集合时自动删除陈旧的映射。
 * <p>The {@link #removeEldestEntry(Map.Entry)} method may be overridden to
 * impose a policy for removing stale mappings automatically when new mappings
 * are added to the map.
 *
 * 此类提供所有可选的Map操作，并允许空元素。像HashMap一样，它提供了基本功能（添加，包含和删除），并能提供恒定时间的性能，
 * 并假定哈希函数将元素正确地分布在存储桶中。由于维护链表的额外费用，性能可能会略低于HashMap，但有一个例外：
 * 无论其容量如何,对LinkedHashMap的集合视图进行迭代需要的时间与map的大小成正比。在HashMap上进行迭代可能会更昂贵，需要的时间与其容量成正比。
 * <p>This class provides all of the optional <tt>Map</tt> operations, and
 * permits null elements.  Like <tt>HashMap</tt>, it provides constant-time
 * performance for the basic operations (<tt>add</tt>, <tt>contains</tt> and
 * <tt>remove</tt>), assuming the hash function disperses elements
 * properly among the buckets.  Performance is likely to be just slightly
 * below that of <tt>HashMap</tt>, due to the added expense of maintaining the
 * linked list, with one exception: Iteration over the collection-views
 * of a <tt>LinkedHashMap</tt> requires time proportional to the <i>size</i>
 * of the map, regardless of its capacity.  Iteration over a <tt>HashMap</tt>
 * is likely to be more expensive, requiring time proportional to its
 * <i>capacity</i>.
 *
 * 链接的哈希映射具有两个影响其性能的参数：初始容量和负载因子。它们的定义与HashMap一样。
 * 但是请注意，与HashMap相比，此类为初始容量选择过高的值的惩罚不那么严重，因为此类的迭代时间不受容量的影响。
 * <p>A linked hash map has two parameters that affect its performance:
 * <i>initial capacity</i> and <i>load factor</i>.  They are defined precisely
 * as for <tt>HashMap</tt>.  Note, however, that the penalty for choosing an
 * excessively high value for initial capacity is less severe for this class
 * than for <tt>HashMap</tt>, as iteration times for this class are unaffected
 * by capacity.
 *
 * 请注意，此实现不是同步的。如果多个线程同时访问链接的哈希映射，并且至少一个线程在结构上修改了映射，则必须在外部进行同步。
 * 通常，通过在自然封装map的某个对象上进行同步来实现。
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access a linked hash map concurrently, and at least
 * one of the threads modifies the map structurally, it <em>must</em> be
 * synchronized externally.  This is typically accomplished by
 * synchronizing on some object that naturally encapsulates the map.
 *
 *如果不存在这样的对象，则应使用{@link Collections＃synchronizedMap Collections.synchronizedMap}方法“包装”map。
 * 最好在创建时完成此操作，以防止意外不同步地访问map。
 * If no such object exists, the map should be "wrapped" using the
 * {@link Collections#synchronizedMap Collections.synchronizedMap}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the map:<pre>
 *   Map m = Collections.synchronizedMap(new LinkedHashMap(...));</pre>
 *
 * 结构修改是添加或删除一个或多个映射的任何操作，或者在访问排序的链接哈希映射的情况下，会影响迭代顺序。
 * 在插入顺序链接的哈希表中，仅更改与映射中已包含的键关联的值不是结构上的修改。
 * 在按访问顺序排列的链接哈希图中，仅使用get查询该映射是结构上的修改。
 * A structural modification is any operation that adds or deletes one or more
 * mappings or, in the case of access-ordered linked hash maps, affects
 * iteration order.  In insertion-ordered linked hash maps, merely changing
 * the value associated with a key that is already contained in the map is not
 * a structural modification.  <strong>In access-ordered linked hash maps,
 * merely querying the map with <tt>get</tt> is a structural modification.
 * </strong>)
 *
 * 所有此类的所有collection视图方法返回的collection的iterator方法返回的迭代器都是快速失败的：
 * 如果在创建迭代器后的任何时间对结构进行结构修改，则除了通过迭代器自己的remove方法之外，都可以通过其他方式进行修改，
 * 迭代器将抛出{@link ConcurrentModificationException}。
 * 因此，面对并发修改，迭代器会快速干净地失败，而不会在未来的不确定时间内冒任意，不确定的行为的风险。
 * <p>The iterators returned by the <tt>iterator</tt> method of the collections
 * returned by all of this class's collection view methods are
 * <em>fail-fast</em>: if the map is structurally modified at any time after
 * the iterator is created, in any way except through the iterator's own
 * <tt>remove</tt> method, the iterator will throw a {@link
 * ConcurrentModificationException}.  Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the future.
 *
 * 请注意，迭代器的快速失败行为无法得到保证，因为通常来说，在存在不同步的并发修改的情况下，不可能做出任何严格的保证。
 * 快速失败的迭代器会尽最大努力抛出ConcurrentModificationException。
 * 因此，编写依赖于此异常的程序以确保其正确性是错误的：迭代器的快速失败行为应仅用于检测错误。
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw <tt>ConcurrentModificationException</tt> on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:   <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * 由此类的所有集合视图方法返回的集合的spliterator方法返回的分隔器为<a href="Spliterator.html#binding">后期绑定，失败快速，
 * 并另外报告{@link Spliterator＃ORDERED} 。
 * <p>The spliterators returned by the spliterator method of the collections
 * returned by all of this class's collection view methods are
 * <em><a href="Spliterator.html#binding">late-binding</a></em>,
 * <em>fail-fast</em>, and additionally report {@link Spliterator#ORDERED}.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * 由此类的所有集合视图方法返回的集合的spliterator方法返回的分离器是从相应集合的迭代器创建的。
 * @implNote
 * The spliterators returned by the spliterator method of the collections
 * returned by all of this class's collection view methods are created from
 * the iterators of the corresponding collections.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @author  Josh Bloch
 * @see     Object#hashCode()
 * @see     Collection
 * @see     Map
 * @see     HashMap
 * @see     TreeMap
 * @see     Hashtable
 * @since   1.4
 */
public class LinkedHashMap<K,V>
    extends HashMap<K,V>
    implements Map<K,V>
{

    /*
     * 实现说明。该类的先前版本在内部结构上有些不同。由于超类HashMap现在将树用于其某些节点，
     * 因此LinkedHashMap.Entry类现在被视为中间节点类，也可以转换为树形式。此类的名称LinkedHashMap.Entry在其当前上下文中以多种方式令人困惑，
     * 但是无法更改。否则，即使未将其导出到此程序包之外，也已知某些现有源代码在对removeEldestEntry的调用中依赖符号解析的特殊情况规则，
     * 该规则抑制了由于用法不明确引起的编译错误。因此，我们保留名称以保留未修改的可编译性。
     * Implementation note.  A previous version of this class was
     * internally structured a little differently. Because superclass
     * HashMap now uses trees for some of its nodes, class
     * LinkedHashMap.Entry is now treated as intermediary node class
     * that can also be converted to tree form. The name of this
     * class, LinkedHashMap.Entry, is confusing in several ways in its
     * current context, but cannot be changed.  Otherwise, even though
     * it is not exported outside this package, some existing source
     * code is known to have relied on a symbol resolution corner case
     * rule in calls to removeEldestEntry that suppressed compilation
     * errors due to ambiguous usages. So, we keep the name to
     * preserve unmodified compilability.
     *
     * 节点类的更改还需要使用两个字段（head，tail），而不是指向标头节点的指针，以维护双向链接的前/后列表。
     * 此类在访问，插入和删除时也曾使用过不同样式的回调方法。
     * The changes in node classes also require using two fields
     * (head, tail) rather than a pointer to a header node to maintain
     * the doubly-linked before/after list. This class also
     * previously used a different style of callback methods upon
     * access, insertion, and removal.
     */

    /**
     * HashMap.Node subclass for normal LinkedHashMap entries.
     */
    static class Entry<K,V> extends HashMap.Node<K,V> {
        Entry<K,V> before, after;
        Entry(int hash, K key, V value, Node<K,V> next) {
            super(hash, key, value, next);
        }
    }

    private static final long serialVersionUID = 3801124242820219131L;

    /**
     * 头部链表
     * The head (eldest) of the doubly linked list.
     */
    transient LinkedHashMap.Entry<K,V> head;

    /**
     * 尾部链表
     * The tail (youngest) of the doubly linked list.
     */
    transient LinkedHashMap.Entry<K,V> tail;

    /**
     * 访问顺序
     * 用于linkedHashMap的迭代排序方法:true->访问排序；false:插入排序
     * The iteration ordering method for this linked hash map: <tt>true</tt>
     * for access-order, <tt>false</tt> for insertion-order.
     *
     * @serial
     */
    final boolean accessOrder;

    // internal utilities

    // link at the end of list
    //在链表尾部插入数据
    private void linkNodeLast(LinkedHashMap.Entry<K,V> p) {
        //last最后一个元素
        LinkedHashMap.Entry<K,V> last = tail;
        tail = p;
        //last为null，即最后一个元素为空，则map集合为空。此时头结点和尾节点都为p
        if (last == null)
            head = p;
        else {
            //此处为双向链表，所以要设置p的前一个节点和之前旧的尾部节点的下一个节点
            p.before = last;
            last.after = p;
        }
    }

    // apply src's links to dst
    //将src复制到dst
    private void transferLinks(LinkedHashMap.Entry<K,V> src,
                               LinkedHashMap.Entry<K,V> dst) {
        //双向链表需要设置上一个节点和下一个节点
        LinkedHashMap.Entry<K,V> b = dst.before = src.before;
        LinkedHashMap.Entry<K,V> a = dst.after = src.after;
        //上一个节点为null,说明src为头结点
        if (b == null)
            head = dst;
        else
            //否则设置上一个节点的后置节点
            b.after = dst;
        //下一个节点为null,src为尾部节点
        if (a == null)
            tail = dst;
        else
            //下一个节点的前置节点复制dst
            a.before = dst;
    }

    // overrides of HashMap hook methods

    //重新HashMap的钩子方法，主要用于初始化LinkedHashMap,此处需要注意头结点和尾节点都为null
    void reinitialize() {
        super.reinitialize();
        head = tail = null;
    }

    /**
     *
     * @param hash Hash值
     * @param key
     * @param value
     * @param e 下一个元素
     * @return
     */
    Node<K,V> newNode(int hash, K key, V value, Node<K,V> e) {
        LinkedHashMap.Entry<K,V> p =
            new LinkedHashMap.Entry<K,V>(hash, key, value, e);
        linkNodeLast(p);
        return p;
    }

    /**
     * 替换某个元素
     * @param p
     * @param next
     * @return
     */
    Node<K,V> replacementNode(Node<K,V> p, Node<K,V> next) {
        LinkedHashMap.Entry<K,V> q = (LinkedHashMap.Entry<K,V>)p;
        LinkedHashMap.Entry<K,V> t =
            new LinkedHashMap.Entry<K,V>(q.hash, q.key, q.value, next);
        transferLinks(q, t);
        return t;
    }

    /**
     * 针对HashMap的红黑树结构新建树节点，添加都最后
     * @param hash
     * @param key
     * @param value
     * @param next
     * @return
     */
    TreeNode<K,V> newTreeNode(int hash, K key, V value, Node<K,V> next) {
        TreeNode<K,V> p = new TreeNode<K,V>(hash, key, value, next);
        linkNodeLast(p);
        return p;
    }

    /**
     * 替换treeNode节点
     * @param p
     * @param next
     * @return
     */
    TreeNode<K,V> replacementTreeNode(Node<K,V> p, Node<K,V> next) {
        LinkedHashMap.Entry<K,V> q = (LinkedHashMap.Entry<K,V>)p;
        TreeNode<K,V> t = new TreeNode<K,V>(q.hash, q.key, q.value, next);
        transferLinks(q, t);
        return t;
    }

    //移除当前节点
    // anode->enode->cnode
    // p = enode
    // b = anode
    // a = cnode
    // 将p的前后节点置位null
    // 如果
    void afterNodeRemoval(Node<K,V> e) { // unlink
        LinkedHashMap.Entry<K,V> p =
            (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
        p.before = p.after = null;
        //前置节点为null,则之前e为头结点，现在将e移除，则e的后置节点b为头结点
        if (b == null)
            head = a;
        else
            //将e的前置节点的后置节点设置为e的后置节点
            b.after = a;
        //双向链表设置同理
        if (a == null)
            tail = b;
        else
            a.before = b;
    }

    /**
     * 插入元素后置处理的方法
     * @param evict
     */
    void afterNodeInsertion(boolean evict) { // possibly remove eldest
        LinkedHashMap.Entry<K,V> first;
        if (evict && (first = head) != null && removeEldestEntry(first)) {
            K key = first.key;
            removeNode(hash(key), key, null, false, true);
        }
    }

    /**
     * 访问顺序排序，此时要借助accessOrder。决定访问顺序的节点。
     * 访问节点的后置处理方法。
     * @param e
     */
    void afterNodeAccess(Node<K,V> e) { // move node to last
        LinkedHashMap.Entry<K,V> last;
        //如果访问的元素不是尾部元素，则执行如下操作:即将当前访问的元素放置到最后一位。
        //如果是尾部元素，则不执行任何操作
        if (accessOrder && (last = tail) != e) {
            //移除当前节点
            LinkedHashMap.Entry<K,V> p = (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
            p.after = null;
            if (b == null)
                head = a;
            else
                b.after = a;
            if (a != null)
                a.before = b;
            else
                last = b;

            //将当前访问节点追加到最后一位
            //如果last为空，则p还是头部节点
            if (last == null)
                head = p;
            else {
                //双向链表追加到尾部
                p.before = last;
                last.after = p;
            }
            //此时更新尾部节点为p
            tail = p;
            ++modCount;
        }
    }

    /**
     * 序列化映射元素
     * @param s
     * @throws IOException
     */
    void internalWriteEntries(java.io.ObjectOutputStream s) throws IOException {
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
            s.writeObject(e.key);
            s.writeObject(e.value);
        }
    }

    /**
     * 通过制定的初始容量和加载因子构造一个空的插入排序{@linkplain #accessOrder}的LinkedHashMap实例
     * Constructs an empty insertion-ordered <tt>LinkedHashMap</tt> instance
     * with the specified initial capacity and load factor.
     *
     * @param  initialCapacity the initial capacity
     * @param  loadFactor      the load factor
     * @throws IllegalArgumentException if the initial capacity is negative
     *         or the load factor is nonpositive
     *         如果初始容量为负数或者加载因子为非正数则抛出异常
     */
    public LinkedHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        accessOrder = false;
    }

    /**
     * 通过制定的初始容量和默认的加载因子(0.75)构造一个空的插入排序{@linkplain #accessOrder}的LinkdedHashMap实例
     * Constructs an empty insertion-ordered <tt>LinkedHashMap</tt> instance
     * with the specified initial capacity and a default load factor (0.75).
     *
     * @param  initialCapacity the initial capacity
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public LinkedHashMap(int initialCapacity) {
        super(initialCapacity);
        accessOrder = false;
    }

    /**
     * 通过默认的初始化容量(16)和默认的加载因子(0.75)构造要一个空的插入排序的LinkedHashMap实例
     * Constructs an empty insertion-ordered <tt>LinkedHashMap</tt> instance
     * with the default initial capacity (16) and load factor (0.75).
     */
    public LinkedHashMap() {
        super();
        accessOrder = false;
    }

    /**
     * 构造一个插入顺序的LinkedHashMap实例，该实例具有与指定映射相同的映射。
     * 创建的LinkedHashMap实例具有默认的加载因子（0.75）和足以容纳指定映射中映射的初始容量。
     * Constructs an insertion-ordered <tt>LinkedHashMap</tt> instance with
     * the same mappings as the specified map.  The <tt>LinkedHashMap</tt>
     * instance is created with a default load factor (0.75) and an initial
     * capacity sufficient to hold the mappings in the specified map.
     *
     * @param  m the map whose mappings are to be placed in this map
     * @throws NullPointerException if the specified map is null
     */
    public LinkedHashMap(Map<? extends K, ? extends V> m) {
        super();
        accessOrder = false;
        putMapEntries(m, false);
    }

    /**
     * 构造一个空的LinkedHashMap实例，该实例具有指定的初始容量，负载因子和排序模式。
     * Constructs an empty <tt>LinkedHashMap</tt> instance with the
     * specified initial capacity, load factor and ordering mode.
     *
     * @param  initialCapacity the initial capacity
     * @param  loadFactor      the load factor
     * @param  accessOrder     the ordering mode - <tt>true</tt> for
     *         access-order, <tt>false</tt> for insertion-order
     * @throws IllegalArgumentException if the initial capacity is negative
     *         or the load factor is nonpositive
     */
    public LinkedHashMap(int initialCapacity,
                         float loadFactor,
                         boolean accessOrder) {
        super(initialCapacity, loadFactor);
        this.accessOrder = accessOrder;
    }


    /**
     * 如果这个map映射一个或者多个指定的value则返回true
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value.
     *
     * @param value value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     *         specified value
     */
    public boolean containsValue(Object value) {
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
            V v = e.value;
            if (v == value || (value != null && value.equals(v)))
                return true;
        }
        return false;
    }

    /**
     * 返回指定键所映射到的值；如果此映射不包含键的映射关系，则返回{@code null}。
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * 更正式地讲，如果此映射包含从键{@code k}到值{@code v}的映射，使得{@code(key == null？k == null：key.equals(k))}，
     * 然后此方法返回{@code v};否则返回{@code null}。 (最多可以有一个这样的映射。)
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code (key==null ? k==null :
     * key.equals(k))}, then this method returns {@code v}; otherwise
     * it returns {@code null}.  (There can be at most one such mapping.)
     *
     * 返回值{@code null}不一定表示该映射不包含该键的映射。映射也可能将键显式映射到{@code null}。
     * {@link #containsKey containsKey}操作可用于区分这两种情况。
     * <p>A return value of {@code null} does not <i>necessarily</i>
     * indicate that the map contains no mapping for the key; it's also
     * possible that the map explicitly maps the key to {@code null}.
     * The {@link #containsKey containsKey} operation may be used to
     * distinguish these two cases.
     */
    public V get(Object key) {
        Node<K,V> e;
        if ((e = getNode(hash(key), key)) == null)
            return null;
        if (accessOrder)
            afterNodeAccess(e);
        return e.value;
    }

    /**
     * 如果不存在key，则返回默认的值，如果是访问排序模式下，同时将e节点放置后链表尾部
     * {@inheritDoc}
     */
    public V getOrDefault(Object key, V defaultValue) {
       Node<K,V> e;
       if ((e = getNode(hash(key), key)) == null)
           return defaultValue;
       if (accessOrder)
           afterNodeAccess(e);
       return e.value;
   }

    /**
     * 清楚列表
     * {@inheritDoc}
     */
    public void clear() {
        super.clear();
        head = tail = null;
    }

    /**
     * 如果此映射应删除其最旧的条目，则返回true。
     * 在将新条目插入到映射后，由put和putAll调用此方法。每次添加新条目时，它为实施者提供了删除最旧条目的机会。
     * 如果映射表示一个缓存，这将很有用：它允许映射通过删除陈旧的条目来减少内存消耗。
     * Returns <tt>true</tt> if this map should remove its eldest entry.
     * This method is invoked by <tt>put</tt> and <tt>putAll</tt> after
     * inserting a new entry into the map.  It provides the implementor
     * with the opportunity to remove the eldest entry each time a new one
     * is added.  This is useful if the map represents a cache: it allows
     * the map to reduce memory consumption by deleting stale entries.
     *
     * 使用示例：此覆盖将使map最多可以容纳100个条目，然后每次添加新条目时都会删除最旧的条目，从而保持100个条目的稳定状态。
     *
     * private static final int MAX_ENTRIES = 100;
     * protected boolean removeEldestEntry(Map.Entry eldest) {
     *  return size() > MAX_ENTRIES;
     * }
     * <p>Sample use: this override will allow the map to grow up to 100
     * entries and then delete the eldest entry each time a new entry is
     * added, maintaining a steady state of 100 entries.
     * <pre>
     *     private static final int MAX_ENTRIES = 100;
     *
     *     protected boolean removeEldestEntry(Map.Entry eldest) {
     *        return size() &gt; MAX_ENTRIES;
     *     }
     * </pre>
     *
     * 此方法通常不以任何方式修改映射，而是允许映射按照其返回值的指示修改自身。
     * 允许此方法直接修改map，但如果这样做，则必须返回false（指示map不应尝试任何进一步的修改）。未指定从此方法修改映射后返回true的效果。
     * <p>This method typically does not modify the map in any way,
     * instead allowing the map to modify itself as directed by its
     * return value.  It <i>is</i> permitted for this method to modify
     * the map directly, but if it does so, it <i>must</i> return
     * <tt>false</tt> (indicating that the map should not attempt any
     * further modification).  The effects of returning <tt>true</tt>
     * after modifying the map from within this method are unspecified.
     *
     * 此实现仅返回false(因此，此映射的行为类似于普通映射-永远不会删除最老的元素)。
     * <p>This implementation merely returns <tt>false</tt> (so that this
     * map acts like a normal map - the eldest element is never removed).
     *
     * @param    eldest The least recently inserted entry in the map, or if
     *           this is an access-ordered map, the least recently accessed
     *           entry.  This is the entry that will be removed it this
     *           method returns <tt>true</tt>.  If the map was empty prior
     *           to the <tt>put</tt> or <tt>putAll</tt> invocation resulting
     *           in this invocation, this will be the entry that was just
     *           inserted; in other words, if the map contains a single
     *           entry, the eldest entry is also the newest.
     *           最近被插入到Map集合的实体，如果是按访问顺序排列的map，则为最近访问的条目。
     *           如果此方法返回true，则将删除该条目。如果在进行put或putAll调用之前该映射为空，则此映射将为仅仅插入的条目；
     *           否则，此映射为null。换句话说，如果map包含单个条目，则最旧的条目也是最新的。
     * @return   <tt>true</tt> if the eldest entry should be removed
     *           from the map; <tt>false</tt> if it should be retained.
     */
    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
        return false;
    }

    /**
     * 返回此映射中包含的键的{@link Set}视图。该集合由地图支持，因此对map的更改会反映在集合中，反之亦然。
     * 如果在对集合进行迭代时修改了映射（通过迭代器自己的remove操作除外），则迭代的结果不确定。
     * 该集合支持元素删除，该元素通过Iterator.remove，Set.remove，removeAll，retainAll和clear操作从映射中删除相应的映射。
     * 它不支持add或addAll操作。与{@code HashMap}相比，其{@link Spliterator}通常提供更快的顺序性能，但并行性能却差很多。
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or <tt>addAll</tt>
     * operations.
     * Its {@link Spliterator} typically provides faster sequential
     * performance but much poorer parallel performance than that of
     * {@code HashMap}.
     *
     * @return a set view of the keys contained in this map
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new LinkedKeySet();
            keySet = ks;
        }
        return ks;
    }

    final class LinkedKeySet extends AbstractSet<K> {
        public final int size()                 { return size; }
        public final void clear()               { LinkedHashMap.this.clear(); }
        public final Iterator<K> iterator() {
            return new LinkedKeyIterator();
        }
        public final boolean contains(Object o) { return containsKey(o); }
        public final boolean remove(Object key) {
            return removeNode(hash(key), key, null, false, true) != null;
        }
        public final Spliterator<K> spliterator()  {
            return Spliterators.spliterator(this, Spliterator.SIZED |
                                            Spliterator.ORDERED |
                                            Spliterator.DISTINCT);
        }
        public final void forEach(Consumer<? super K> action) {
            if (action == null)
                throw new NullPointerException();
            int mc = modCount;
            for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after)
                action.accept(e.key);
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    /**
     * 返回此映射中包含的值的{@link Collection}视图。集合由地图支持，因此对map的更改会反映在集合中，反之亦然。
     * 如果在对集合进行迭代时修改了映射（通过迭代器自己的remove操作除外），则迭代的结果是不确定的。
     * 集合支持元素删除，该元素通过Iterator.remove，Collection.remove，removeAll，retainAll和clear操作从映射中删除相应的映射。
     * 它不支持add或addAll操作。与{@code HashMap}相比，其{@link Spliterator}通常提供更快的顺序性能，但并行性能却差很多。
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own <tt>remove</tt> operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Collection.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt> and <tt>clear</tt> operations.  It does not
     * support the <tt>add</tt> or <tt>addAll</tt> operations.
     * Its {@link Spliterator} typically provides faster sequential
     * performance but much poorer parallel performance than that of
     * {@code HashMap}.
     *
     * @return a view of the values contained in this map
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new LinkedValues();
            values = vs;
        }
        return vs;
    }

    final class LinkedValues extends AbstractCollection<V> {
        public final int size()                 { return size; }
        public final void clear()               { LinkedHashMap.this.clear(); }
        public final Iterator<V> iterator() {
            return new LinkedValueIterator();
        }
        public final boolean contains(Object o) { return containsValue(o); }
        public final Spliterator<V> spliterator() {
            return Spliterators.spliterator(this, Spliterator.SIZED |
                                            Spliterator.ORDERED);
        }
        public final void forEach(Consumer<? super V> action) {
            if (action == null)
                throw new NullPointerException();
            int mc = modCount;
            for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after)
                action.accept(e.value);
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    /**
     * 返回此映射中包含的映射的{@link Set}视图。该集合由地图支持，因此对map的更改会反映在集合中，反之亦然。
     * 如果在进行集合迭代时修改了映射（除非通过迭代器自己的remove操作或通过迭代器返回的映射条目上的setValue操作），则迭代的结果是不确定的。
     * 该集合支持元素删除，该元素通过Iterator.remove，Set.remove，removeAll，retainAll和clear操作从映射中删除相应的映射。
     * 它不支持add或addAll操作。与{@code HashMap}相比，其{@link Spliterator}通常提供更快的顺序性能，但并行性能却差很多。
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation, or through the
     * <tt>setValue</tt> operation on a map entry returned by the
     * iterator) the results of the iteration are undefined.  The set
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt> and
     * <tt>clear</tt> operations.  It does not support the
     * <tt>add</tt> or <tt>addAll</tt> operations.
     * Its {@link Spliterator} typically provides faster sequential
     * performance but much poorer parallel performance than that of
     * {@code HashMap}.
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es;
        return (es = entrySet) == null ? (entrySet = new LinkedEntrySet()) : es;
    }

    final class LinkedEntrySet extends AbstractSet<Map.Entry<K,V>> {
        public final int size()                 { return size; }
        public final void clear()               { LinkedHashMap.this.clear(); }
        public final Iterator<Map.Entry<K,V>> iterator() {
            return new LinkedEntryIterator();
        }
        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            Object key = e.getKey();
            Node<K,V> candidate = getNode(hash(key), key);
            return candidate != null && candidate.equals(e);
        }
        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>) o;
                Object key = e.getKey();
                Object value = e.getValue();
                return removeNode(hash(key), key, value, true, true) != null;
            }
            return false;
        }
        public final Spliterator<Map.Entry<K,V>> spliterator() {
            return Spliterators.spliterator(this, Spliterator.SIZED |
                                            Spliterator.ORDERED |
                                            Spliterator.DISTINCT);
        }
        public final void forEach(Consumer<? super Map.Entry<K,V>> action) {
            if (action == null)
                throw new NullPointerException();
            int mc = modCount;
            for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after)
                action.accept(e);
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    // Map overrides

    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null)
            throw new NullPointerException();
        int mc = modCount;
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after)
            action.accept(e.key, e.value);
        if (modCount != mc)
            throw new ConcurrentModificationException();
    }

    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (function == null)
            throw new NullPointerException();
        int mc = modCount;
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after)
            e.value = function.apply(e.key, e.value);
        if (modCount != mc)
            throw new ConcurrentModificationException();
    }

    // Iterators

    abstract class LinkedHashIterator {
        LinkedHashMap.Entry<K,V> next;
        LinkedHashMap.Entry<K,V> current;
        int expectedModCount;

        LinkedHashIterator() {
            next = head;
            expectedModCount = modCount;
            current = null;
        }

        public final boolean hasNext() {
            return next != null;
        }

        final LinkedHashMap.Entry<K,V> nextNode() {
            LinkedHashMap.Entry<K,V> e = next;
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (e == null)
                throw new NoSuchElementException();
            current = e;
            next = e.after;
            return e;
        }

        public final void remove() {
            Node<K,V> p = current;
            if (p == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            current = null;
            K key = p.key;
            removeNode(hash(key), key, null, false, false);
            expectedModCount = modCount;
        }
    }

    final class LinkedKeyIterator extends LinkedHashIterator
        implements Iterator<K> {
        public final K next() { return nextNode().getKey(); }
    }

    final class LinkedValueIterator extends LinkedHashIterator
        implements Iterator<V> {
        public final V next() { return nextNode().value; }
    }

    final class LinkedEntryIterator extends LinkedHashIterator
        implements Iterator<Map.Entry<K,V>> {
        public final Map.Entry<K,V> next() { return nextNode(); }
    }


}
