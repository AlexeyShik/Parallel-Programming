import kotlinx.atomicfu.*

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 */
class IntIntHashMap {
    private val core = atomic(Core(INITIAL_CAPACITY))

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(core.value.getInternal(key))
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key   a positive key.
     * @param value a positive value.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
     * [Integer.MAX_VALUE] which is reserved.
     */
    fun put(key: Int, value: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        return toValue(putAndRehashWhileNeeded(key, value))
    }

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key a positive key.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    fun remove(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        while (true) {
            val curCore = core.value
            val oldValue = curCore.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            core.compareAndSet(curCore, curCore.rehash())
        }
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map: AtomicIntArray = AtomicIntArray(2 * capacity)
        val shift: Int
        val next: AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            while (true) {
                var probes = 0
                var curKey = map[index].value
                while (curKey != key) { // optimize for successful lookup
                    if (curKey == NULL_KEY) return NULL_VALUE // not found -- no value
                    if (++probes >= MAX_PROBES) return NULL_VALUE
                    if (index == 0) index = map.size
                    index -= 2
                    curKey = map[index].value
                }
                // found key -- return value
                val curValue = map[index + 1].value
                return when {
                    curValue >= 0 -> curValue
                    curValue == DONE_VALUE -> next.value!!.getInternal(key)
                    else -> {
                        // help rehash
                        val updValue = curValue.xor(HIGH_BIT)
                        val nextCore = next.value
                        assert(nextCore != null)
                        assert(isValue(updValue))
                        nextCore?.makeMove(curKey, updValue)
                        val result = nextCore?.getInternal(key) as Int
                        map[index + 1].compareAndSet(curValue, DONE_VALUE)
                        return result
                    }
                }
            }
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) {
                val curKey = map[index].value
                val curValue = map[index + 1].value

                if (key == curKey) {
                    // found key -- update value
                    return when {
                        curValue >= 0 -> if (map[index + 1].compareAndSet(curValue, value)) curValue else continue
                        curValue == DONE_VALUE -> next.value!!.putInternal(key, value)
                        else -> {
                            // help rehash
                            val updValue = curValue.xor(HIGH_BIT)
                            val nextCore = next.value
                            assert(nextCore != null)
                            assert(isValue(updValue))
                            nextCore?.makeMove(curKey, updValue)
                            val result = nextCore?.putInternal(key, value) as Int
                            map[index + 1].compareAndSet(curValue, DONE_VALUE)
                            result
                        }
                    }
                } else if (curKey == NULL_KEY) {
                    // not found -- claim this slot
                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot

                    if (map[index].compareAndSet(NULL_KEY, key)) {
                        if (map[index + 1].compareAndSet(curValue, value))  return curValue else continue
                    } else continue
                }

                if (++probes >= MAX_PROBES) return if (value == DEL_VALUE) NULL_VALUE else NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
            }
        }

        fun rehash(): Core {
            next.compareAndSet(null, Core(map.size))
            val newCore = next.value!!
            var index = 0
            while (index < map.size) {
                while (true) {
                    val curKey = map[index].value
                    val curValue = map[index + 1].value

                    if (isValue(curValue)) {
                        val updValue = curValue.or(HIGH_BIT)
                        if (map[index + 1].compareAndSet(curValue, updValue)) {
                            assert(isValue(curValue))
                            newCore.makeMove(curKey, curValue)
                            if (map[index + 1].compareAndSet(updValue, DONE_VALUE)) break
                        }
                    } else if (curValue == 0 || curValue == DEL_VALUE) {
                        if (map[index + 1].compareAndSet(curValue, DONE_VALUE)) break
                    } else if (curValue == DONE_VALUE) {
                        break
                    } else if (curValue < 0) {
                        val updValue = curValue.xor(HIGH_BIT)
                        assert(isValue(updValue))
                        newCore.makeMove(curKey, updValue)
                        if (map[index + 1].compareAndSet(curValue, DONE_VALUE)) break
                    }
                }

                index += 2
            }

            return newCore
        }

        fun makeMove(key: Int, value: Int) {
            var index = index(key)
            var probes = 0
            while (true) {
                val curKey = map[index].value

                if (curKey == NULL_KEY) {
                    if (map[index].compareAndSet(NULL_KEY, key)) {
                        if (map[index + 1].compareAndSet(NULL_VALUE, value)) {
                            return
                        }
                    }
                    continue
                } else if (curKey == key) {
                    map[index + 1].compareAndSet(NULL_VALUE, value)
                    return
                }

                if (++probes >= MAX_PROBES) return
                if (index == 0) index = map.size
                index -= 2
            }
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int {
            return (key * MAGIC ushr shift) * 2
        }
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val DONE_VALUE = Int.MIN_VALUE // mark for moved value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed
private const val HIGH_BIT: Int = 1.shl(31) // 1 << 31 to encode frozen values (V')

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0