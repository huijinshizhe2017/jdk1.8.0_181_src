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
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link java.util.Map} providing thread safety and atomicity
 * guarantees.
 * 一个提供线程安全和原子性保证性的Map集合
 *
 * <p>Memory consistency effects: As with other concurrent
 * collections, actions in a thread prior to placing an object into a
 * {@code ConcurrentMap} as a key or value
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions subsequent to the access or removal of that object from
 * the {@code ConcurrentMap} in another thread.
 * 内存一致性影响：与其他并发集合一样，在将对象作为键或值放入{@code ConcurrentMap}中之前，
 * 线程中的操作happen-before在另一个线程中从{@code ConcurrentMap}访问或删除该对象之后执行的操作。
 *
 *
 * <p>This interface is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 * 这个接口是一个Java集合框架的一个成员
 *
 * @since 1.5
 * @author Doug Lea
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public interface ConcurrentMap<K, V> extends Map<K, V> {

    /**
     * {@inheritDoc}
     *
     * 此实现假定ConcurrentMap的value不能包含null值，并且{@code get()}明确返回null意味着没有键。
     * 支持空值的实现必须覆盖此默认实现。
     *
     * @implNote This implementation assumes that the ConcurrentMap cannot
     * contain null values and {@code get()} returning null unambiguously means
     * the key is absent. Implementations which support null values
     * <strong>must</strong> override this default implementation.
     *
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    default V getOrDefault(Object key, V defaultValue) {
        V v;
        return ((v = get(key)) != null) ? v : defaultValue;
    }

   /**
     * {@inheritDoc}
     *
     * @implSpec The default implementation is equivalent to, for this
    *  默认实现等效于此
     * {@code map}:
     * <pre> {@code
     * for ((Map.Entry<K, V> entry : map.entrySet())
     *     action.accept(entry.getKey(), entry.getValue());
     * }</pre>
     *
    *  默认实现假定{@code getKey()}或{@code getValue()}抛出的
    *  {@code IllegalStateException}表示该条目已被删除并且无法处理。后续操作继续进行。
     * @implNote The default implementation assumes that
     * {@code IllegalStateException} thrown by {@code getKey()} or
     * {@code getValue()} indicates that the entry has been removed and cannot
     * be processed. Operation continues for subsequent entries.
     *
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    default void forEach(BiConsumer<? super K, ? super V> action) {
        //判空抛异常
        Objects.requireNonNull(action);
        for (Map.Entry<K, V> entry : entrySet()) {
            K k;
            V v;
            try {
                k = entry.getKey();
                v = entry.getValue();
            } catch(IllegalStateException ise) {
                // this usually means the entry is no longer in the map.
                //抛出异常通常意味着这个实体对象不再这个map存在
                continue;
            }
            action.accept(k, v);
        }
    }

    /**
     * 如果指定的键尚未与值关联，则将其与给定值关联。
     * 这个方法等同于:
     * if (!map.containsKey(key)){
     *     return map.put(key, value);
     * }else{
     *     return map.get(key);
     * }
     *
     * If the specified key is not already associated
     * with a value, associate it with the given value.
     * This is equivalent to
     *  <pre> {@code
     * if (!map.containsKey(key))
     *   return map.put(key, value);
     * else
     *   return map.get(key);
     * }</pre>
     *
     * 除了动作是原子执行的。
     * except that the action is performed atomically.
     *
     * 此实现有意地重新提取了{@code Map}中提供的不适当的默认值。
     * @implNote This implementation intentionally re-abstracts the
     * inappropriate default provided in {@code Map}.
     *
     * @param key key with which the specified value is to be associated
     *            与指定值关联的key
     * @param value value to be associated with the specified key
     *              与指定键关联的值
     * @return the previous value associated with the specified key, or
     *         {@code null} if there was no mapping for the key.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with the key,
     *         if the implementation supports null values.)
     *         与指定键相关联的先前值；如果键没有映射，则为{@code null}。
     *         (如果实现支持空值，则返回{@code null}也可以表明映射先前将{@code null}与该键相关联。)
     * @throws UnsupportedOperationException if the {@code put} operation
     *         is not supported by this map
     *         如果此map不支持{@code put}操作
     * @throws ClassCastException if the class of the specified key or value
     *         prevents it from being stored in this map
     *         如果指定键或值的类阻止其存储在此映射中
     * @throws NullPointerException if the specified key or value is null,
     *         and this map does not permit null keys or values
     *         如果指定的键或值是null，并且此映射不允许空键或值
     * @throws IllegalArgumentException if some property of the specified key
     *         or value prevents it from being stored in this map
     *         如果指定键或值的某些属性阻止将其存储在此映射中
     *
     */
     V putIfAbsent(K key, V value);

    /**
     * 仅当当前映射到给定值时，才删除键的条目。
     * 这相当于：
     *  if (map.containsKey(key) && Objects.equals(map.get(key), value)) {
     *    map.remove(key);
     *    return true;
     *  }else{
     *    return false;
     *  }
     *
     * Removes the entry for a key only if currently mapped to a given value.
     * This is equivalent to
     *  <pre> {@code
     * if (map.containsKey(key) && Objects.equals(map.get(key), value)) {
     *   map.remove(key);
     *   return true;
     * } else
     *   return false;
     * }</pre>
     *
     * except that the action is performed atomically.
     * 除了动作是原子执行的。
     *
     * @implNote This implementation intentionally re-abstracts the
     * inappropriate default provided in {@code Map}.
     * 此实现有意地重新提取了{@code Map}中提供的不适当的默认值。
     *
     * @param key key with which the specified value is associated
     *            与指定值关联的键
     * @param value value expected to be associated with the specified key
     *              预期与指定键关联的值
     * @return {@code true} if the value was removed 如果该值被删除
     * @throws UnsupportedOperationException if the {@code remove} operation
     *         is not supported by this map
     *         如果此地图不支持{@code remove}操作
     * @throws ClassCastException if the key or value is of an inappropriate
     *         type for this map
     *         如果键或值的类型不适用于此Map
     *         (<a href="../Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified key or value is null,
     *         and this map does not permit null keys or values
     *         (<a href="../Collection.html#optional-restrictions">optional</a>)
     *         如果指定的键或值是null，并且此映射不允许空键或值
     */
    boolean remove(Object key, Object value);

    /**
     * Replaces the entry for a key only if currently mapped to a given value.
     * 仅当当前映射到给定值时才替换键的条目。
     * 这相当于：
     * if (map.containsKey(key) && Objects.equals(map.get(key), oldValue)) {
     *   map.put(key, newValue);
     *   return true;
     * } else {
     *   return false;
     * }
     *
     *
     * This is equivalent to
     *  <pre> {@code
     * if (map.containsKey(key) && Objects.equals(map.get(key), oldValue)) {
     *   map.put(key, newValue);
     *   return true;
     * } else
     *   return false;
     * }</pre>
     *
     * except that the action is performed atomically.
     * 除了动作是原子执行的。
     *
     * @implNote This implementation intentionally re-abstracts the
     * inappropriate default provided in {@code Map}.
     * 此实现有意地重新提取了{@code Map}中提供的不适当的默认值。
     *
     * @param key key with which the specified value is associated
     *            与指定值关联的键
     * @param oldValue value expected to be associated with the specified key
     *                 预期与指定键关联的值
     * @param newValue value to be associated with the specified key 与指定键关联的值
     * @return {@code true} if the value was replaced
     * @throws UnsupportedOperationException if the {@code put} operation
     *         is not supported by this map
     *         如果此地图不支持{@code put}操作
     * @throws ClassCastException if the class of a specified key or value
     *         prevents it from being stored in this map
     *         如果指定键或值的类阻止其存储在此映射中
     * @throws NullPointerException if a specified key or value is null,
     *         and this map does not permit null keys or values
     *         如果指定的键或值为null，并且此映射不允许空键或值
     * @throws IllegalArgumentException if some property of a specified key
     *         or value prevents it from being stored in this map
     *         如果指定键或值的某些属性阻止将其存储在此映射中
     */
    boolean replace(K key, V oldValue, V newValue);

    /**
     * Replaces the entry for a key only if currently mapped to some value.
     * This is equivalent to
     *  <pre> {@code
     * if (map.containsKey(key)) {
     *   return map.put(key, value);
     * } else
     *   return null;
     * }</pre>
     *
     * except that the action is performed atomically.
     *
     * @implNote This implementation intentionally re-abstracts the
     * inappropriate default provided in {@code Map}.
     *
     * @param key key with which the specified value is associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     *         {@code null} if there was no mapping for the key.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with the key,
     *         if the implementation supports null values.)
     * @throws UnsupportedOperationException if the {@code put} operation
     *         is not supported by this map
     * @throws ClassCastException if the class of the specified key or value
     *         prevents it from being stored in this map
     * @throws NullPointerException if the specified key or value is null,
     *         and this map does not permit null keys or values
     * @throws IllegalArgumentException if some property of the specified key
     *         or value prevents it from being stored in this map
     */
    V replace(K key, V value);

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * <p>The default implementation is equivalent to, for this {@code map}:
     * <pre> {@code
     * for ((Map.Entry<K, V> entry : map.entrySet())
     *     do {
     *        K k = entry.getKey();
     *        V v = entry.getValue();
     *     } while(!replace(k, v, function.apply(k, v)));
     * }</pre>
     *
     * The default implementation may retry these steps when multiple
     * threads attempt updates including potentially calling the function
     * repeatedly for a given key.
     *
     * <p>This implementation assumes that the ConcurrentMap cannot contain null
     * values and {@code get()} returning null unambiguously means the key is
     * absent. Implementations which support null values <strong>must</strong>
     * override this default implementation.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.8
     */
    @Override
    default void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        forEach((k,v) -> {
            while(!replace(k, v, function.apply(k, v))) {
                // v changed or k is gone
                if ( (v = get(k)) == null) {
                    // k is no longer in the map.
                    break;
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * The default implementation is equivalent to the following steps for this
     * {@code map}, then returning the current value or {@code null} if now
     * absent:
     *
     * <pre> {@code
     * if (map.get(key) == null) {
     *     V newValue = mappingFunction.apply(key);
     *     if (newValue != null)
     *         return map.putIfAbsent(key, newValue);
     * }
     * }</pre>
     *
     * The default implementation may retry these steps when multiple
     * threads attempt updates including potentially calling the mapping
     * function multiple times.
     *
     * <p>This implementation assumes that the ConcurrentMap cannot contain null
     * values and {@code get()} returning null unambiguously means the key is
     * absent. Implementations which support null values <strong>must</strong>
     * override this default implementation.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    default V computeIfAbsent(K key,
            Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        V v, newValue;
        return ((v = get(key)) == null &&
                (newValue = mappingFunction.apply(key)) != null &&
                (v = putIfAbsent(key, newValue)) == null) ? newValue : v;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * The default implementation is equivalent to performing the following
     * steps for this {@code map}, then returning the current value or
     * {@code null} if now absent. :
     *
     * <pre> {@code
     * if (map.get(key) != null) {
     *     V oldValue = map.get(key);
     *     V newValue = remappingFunction.apply(key, oldValue);
     *     if (newValue != null)
     *         map.replace(key, oldValue, newValue);
     *     else
     *         map.remove(key, oldValue);
     * }
     * }</pre>
     *
     * The default implementation may retry these steps when multiple threads
     * attempt updates including potentially calling the remapping function
     * multiple times.
     *
     * <p>This implementation assumes that the ConcurrentMap cannot contain null
     * values and {@code get()} returning null unambiguously means the key is
     * absent. Implementations which support null values <strong>must</strong>
     * override this default implementation.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    default V computeIfPresent(K key,
            BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue;
        while((oldValue = get(key)) != null) {
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                if (replace(key, oldValue, newValue))
                    return newValue;
            } else if (remove(key, oldValue))
               return null;
        }
        return oldValue;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * 默认实现等效于对此{@code映射}执行以下步骤，然后返回当前值；如果不存在，则返回{@code null}：
     * The default implementation is equivalent to performing the following
     * steps for this {@code map}, then returning the current value or
     * {@code null} if absent:
     *
     * <pre> {@code
     * V oldValue = map.get(key);
     * V newValue = remappingFunction.apply(key, oldValue);
     * if (oldValue != null ) {
     *    if (newValue != null)
     *       map.replace(key, oldValue, newValue);
     *    else
     *       map.remove(key, oldValue);
     * } else {
     *    if (newValue != null)
     *       map.putIfAbsent(key, newValue);
     *    else
     *       return null;
     * }
     * }</pre>
     *
     * 当多个线程尝试进行更新（包括可能多次调用重新映射函数）时，默认实现可以重试这些步骤。
     * The default implementation may retry these steps when multiple
     * threads attempt updates including potentially calling the remapping
     * function multiple times.
     *
     * 此实现假定ConcurrentMap不能包含null值，并且{@code get()}明确返回null意味着没有键。
     * 支持空值的实现必须覆盖此默认实现。
     * <p>This implementation assumes that the ConcurrentMap cannot contain null
     * values and {@code get()} returning null unambiguously means the key is
     * absent. Implementations which support null values <strong>must</strong>
     * override this default implementation.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    default V compute(K key,
            BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        //通过key在Map中获取旧值
        V oldValue = get(key);
        //这是涉及到CAS(Compare and Swap)的的一些操作
        //
        for(;;) {
            //通过Lamda表达式获取新值
            V newValue = remappingFunction.apply(key, oldValue);
            //新值为空
            if (newValue == null) {
                // delete mapping
                //删除映射
                if (oldValue != null || containsKey(key)) {
                    // something to remove
                    //移除就的Key-value
                    if (remove(key, oldValue)) {
                        // removed the old value as expected
                        return null;
                    }

                    // some other value replaced old value. try again.
                    //再次尝试
                    oldValue = get(key);
                } else {
                    // nothing to do. Leave things as they were.
                    //不去做任何事
                    return null;
                }
            } else {
                // add or replace old mapping
                //增加或者替换旧的映射
                if (oldValue != null) {
                    // replace
                    if (replace(key, oldValue, newValue)) {
                        // replaced as expected.
                        //期望的值替换
                        return newValue;
                    }

                    // some other value replaced old value. try again.
                    oldValue = get(key);
                } else {
                    // add (replace if oldValue was null)
                    if ((oldValue = putIfAbsent(key, newValue)) == null) {
                        // replaced
                        return newValue;
                    }

                    // some other value replaced old value. try again.
                }
            }
        }
    }


    /**
     * {@inheritDoc}
     *
     * 默认实现等效于对此{@code map}执行以下步骤，然后返回当前值或{@code null}如果不存在：
     * @implSpec
     * The default implementation is equivalent to performing the following
     * steps for this {@code map}, then returning the current value or
     * {@code null} if absent:
     *
     * <pre> {@code
     * V oldValue = map.get(key);
     * V newValue = (oldValue == null) ? value :
     *              remappingFunction.apply(oldValue, value);
     * if (newValue == null)
     *     map.remove(key);
     * else
     *     map.put(key, newValue);
     * }</pre>
     *
     * 当多个线程尝试进行更新（包括可能多次调用重新映射函数）时，默认实现可以重试这些步骤。
     * <p>The default implementation may retry these steps when multiple
     * threads attempt updates including potentially calling the remapping
     * function multiple times.
     *
     * 此实现假定ConcurrentMap不能包含null值，并且{@code get()}明确返回null意味着没有键。
     * 支持空值的实现必须覆盖此默认实现。
     * <p>This implementation assumes that the ConcurrentMap cannot contain null
     * values and {@code get()} returning null unambiguously means the key is
     * absent. Implementations which support null values <strong>must</strong>
     * override this default implementation.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @since 1.8
     */
    @Override
    default V merge(K key, V value,
            BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        //判空抛异常
        Objects.requireNonNull(remappingFunction);
        Objects.requireNonNull(value);
        V oldValue = get(key);
        for (;;) {
            //如果oldValue不为空
            if (oldValue != null) {
                //将旧的值映射到新值上，这里的合并是需要通过Lamda表达式传递合并方式
                V newValue = remappingFunction.apply(oldValue, value);
                //如果新值不为null,需要将旧值替换为新值
                if (newValue != null) {
                    //map.containsKey(key) && Objects.equals(map.get(key), oldValue)
                    if (replace(key, oldValue, newValue))
                        return newValue;
                    //新值为空，则需要移除就的值
                } else if (remove(key, oldValue)) {
                    return null;
                }
                //获取到旧值
                oldValue = get(key);
                //如果旧值为空，
            } else {
                if ((oldValue = putIfAbsent(key, value)) == null) {
                    return value;
                }
            }
        }
    }
}
