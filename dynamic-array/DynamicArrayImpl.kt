import kotlinx.atomicfu.*

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val _size = atomic(0)

    override fun get(index: Int): E {
        if (index < size) {
            return core.value.array[index].value!!.value
        }
        else throw IllegalArgumentException()
    }

    private fun move(curCore: Core<E>, curCapacity: Int) {
        curCore.next.compareAndSet(null, Core(2 * curCapacity))
        val nextCore = curCore.next.value
        for (i in 0 until curCapacity) {
            while (true) {
                val curElement = curCore.array[i]
                val curValue = curElement.value ?: break
                if (!(curValue is Frozen || curValue is Moved)) {
                    curElement.compareAndSet(curValue, Frozen(curValue.value))
                }
                if (curValue is Frozen) {
                    nextCore!!.array[i].compareAndSet(null, Element(curValue.value))
                    curElement.compareAndSet(curValue, Moved(curValue.value))
                }
                if (curValue is Moved) {
                    break
                }
            }
        }
        core.compareAndSet(curCore, curCore.next.value!!)
    }

    override fun put(index: Int, element: E) {
        if (index < size) {
            while (true) {
                val curCore = core.value
                val curElement = curCore.array[index]
                val curValue = curElement.value
                val curCapacity = curCore.array.size
                if (curValue == null) {
                    if (curElement.compareAndSet(null, Element(element))) {
                        break
                    }
                } else {
                    if (!(curValue is Frozen<E> || curValue is Moved)) {
                        if (curElement.compareAndSet(curValue, Element(element))) {
                            break
                        }
                    } else {  // if there is concurrent move, help
                        move(curCore, curCapacity)
                    }
                }
            }
        }
        else throw IllegalArgumentException()
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            val curSize = size
            val curCapacity = curCore.array.size
            if (curSize < curCapacity) {
                if (curCore.array[curSize].compareAndSet(null, Element(element))) {
                    _size.compareAndSet(curSize, curSize + 1)
                    break
                }
                _size.compareAndSet(curSize, curSize + 1) // если между 67 и 68 строчкой поток завис, то CAS на 67 строке всегда будет фейлится и pushBack не выполнится => надо помогать доставлять size
            } else {
                move(curCore, curCapacity)
            }
        }
    }

    override val size: Int get() {
        return _size.value
    }
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<Element<E>>(capacity)
    val next = atomic<Core<E>?>(null)
}

private open class Element<E>(val value: E)
private class Frozen<E>(value: E) : Element<E>(value)
private class Moved<E>(value: E) : Element<E>(value)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME