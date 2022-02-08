import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SynchronousQueueMS<E> : SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy: Node<E> = Dummy(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override suspend fun send(element: E) {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            val nextTail = curTail.next.value
            if (curHead == curTail && nextTail != null) { // msqueue helping
                tail.compareAndSet(curTail, nextTail)
                continue
            }
            if (curHead == curTail || curTail !is Receiver) { // no receivers waiting, add task
                val result = suspendCoroutine<Any> sc@ { cont ->
                    if (curTail.next.compareAndSet(null, Sender(element, cont))) {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                    } else {
                        cont.resume(RETRY)
                        return@sc
                    }
                }
                if (result != RETRY) return
            } else { // try to rendezvous with receiver
                val nextHead = curHead.next.value!!
                if (head.compareAndSet(curHead, nextHead)) {
                    (nextHead as Receiver).continuation.resume(element!!)
                    return
                }
            }
        }
    }

    override suspend fun receive(): E {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            val nextTail = curTail.next.value
            if (curHead == curTail && nextTail != null) { // msqueue helping
                tail.compareAndSet(curTail, nextTail)
                continue
            }
            if (curHead == curTail || curTail !is Sender) { // no senders waiting, add task
                val result = suspendCoroutine<Any> sc@ { cont ->
                    if (curTail.next.compareAndSet(null, Receiver(null, cont))) {
                        tail.compareAndSet(curTail, curTail.next.value!!)
                    } else {
                        cont.resume(RETRY)
                        return@sc
                    }
                }
                if (result != RETRY) return result as E
            } else { // try to rendezvous with sender
                val nextHead = curHead.next.value!!
                if (head.compareAndSet(curHead, nextHead)) {
                    (nextHead as Sender).continuation.resume(Unit)
                    return nextHead.value!!
                }
            }
        }
    }

    private open class Node<E> internal constructor(val value: E?) {
        val next:AtomicRef<Node<E>?> = atomic(null)
    }
    private class Sender<E>(value: E?, val continuation: Continuation<Any>) : Node<E>(value)
    private class Receiver<E>(value: E?, val continuation: Continuation<Any>) : Node<E>(value)
    private class Dummy<E>(value: E?) : Node<E>(value)
    private class Retry
    private val RETRY = Retry()
}
