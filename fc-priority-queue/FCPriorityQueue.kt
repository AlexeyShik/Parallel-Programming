import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.PriorityQueue
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcLock = atomic(false)
    private val arraySize = 8
    private val fcArray = atomicArrayOfNulls<Task>(arraySize)
    private val random = ThreadLocalRandom.current()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return processTask(PollTask())
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return processTask(PeekTask())
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        processTask(AddTask(element))
    }

    private fun processTask(task : Task): E? {
        var i = random.nextInt(0, arraySize)
        while (true) {
            if (fcArray[i].compareAndSet(null, task)) {
                break
            }
            i = (i + 1) % arraySize
        }
        while (true) {
            if (fcLock.compareAndSet(false, true)) {
                for (j in 0 until arraySize) {
                    val curTask = fcArray[j].getAndSet(null) // only this thread works here, don't need CAS
                    if (curTask != null) {
                        curTask.process()
                        curTask.ready.getAndSet(true)
                    }
                }
                fcLock.getAndSet(false)
                break
            } else if (task.ready.value) {
                break
            }
        }
        return task.result
    }

    abstract inner class Task {
        abstract fun process()
        var result: E? = null
        val ready: AtomicBoolean = atomic(false)
    }
    inner class PollTask : Task() {
        override fun process() {
            result = q.poll()
        }
    }
    inner class PeekTask : Task() {
        override fun process() {
            result = q.peek()
        }
    }
    inner class AddTask(private val element : E) : Task() {
        override fun process() {
            q.add(element)
        }
    }
}