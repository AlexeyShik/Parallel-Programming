package dijkstra

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.locks.ReentrantLock
import kotlin.Comparator
import kotlin.concurrent.thread

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> o1!!.distance.compareTo(o2!!.distance) }

class MultiQueue(workers: Int) {

    private val random: Random = Random()
    private val nQueues: Int = 2 * workers
    private val locks: Array<ReentrantLock> = Array(nQueues) { ReentrantLock() }
    private val queues: Array<PriorityQueue<Node>> = Array(nQueues) { PriorityQueue(workers, NODE_DISTANCE_COMPARATOR) }

    fun add(node: Node) {
        while (true) {
            val i: Int = random.nextInt(nQueues)
            if (locks[i].tryLock()) {
                try {
                    queues[i].add(node)
                } finally {
                    locks[i].unlock()
                }
                return
            }
        }
    }

    fun poll(): Node? {
        while (true) {
            val i: Int = random.nextInt(nQueues)
            val j: Int = random.nextInt(nQueues)
            if (i == j) {
                continue
            }
            var result: Node? = null
            if (locks[i].tryLock()) {
                try {
                    if (locks[j].tryLock()) {
                        try {
                            val min1: Node? = queues[i].peek()
                            val min2: Node? = queues[j].peek()
                            if (min1 != null && min2 != null) {
                                if (min1.distance < min2.distance) {
                                    result = min1
                                    queues[i].poll()
                                } else {
                                    result = min2
                                    queues[j].poll()
                                }
                            } else if (min1 != null) {
                                result = min1
                                queues[i].poll()
                            } else if (min2 != null) {
                                result = min2
                                queues[j].poll()
                            }
                        } finally {
                            locks[j].unlock()
                        }
                    }
                } finally {
                    locks[i].unlock()
                }
            }
            return result
        }
    }
}

class AtomicIntWrapper {

    private val intValue: AtomicInt = atomic(1)

    fun inc() {
        intValue.incrementAndGet()
    }

    fun dec() {
        intValue.decrementAndGet()
    }

    fun get(): Int {
        return intValue.value
    }
}

fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    start.distance = 0
    val queue = MultiQueue(workers)
    queue.add(start)
    val activeNodes = AtomicIntWrapper()
    val onFinish = Phaser(workers + 1)
    repeat(workers) {
        thread {
            while (true) {
                val cur: Node = synchronized(queue) { queue.poll() } ?: if (activeNodes.get() == 0) break else continue
                for (e in cur.outgoingEdges) {
                    while (true) {
                        val distance = e.to.distance
                        val newDistance = cur.distance + e.weight
                        if (distance > newDistance) {
                            if (e.to.casDistance(distance, newDistance)) {
                                queue.add(e.to)
                                activeNodes.inc()
                            }
                        } else {
                            break
                        }
                    }
                }
                activeNodes.dec()
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}






























/*

makeCASN(Descriptor desc, int i) {
    if (i == n || i < 0) {
        rollback(desc) // операция завершится тут
        return
    }

    if (desc.outcome.value == i - 1) {
        // i - 1 итераций прошло успешно, делаем i-ю
        val obsI = desc.vars[i].value

        if (obsI == desc || obsI == desc.exp[i] && CAS(&desc.vars[i], decs.exp[i], desc)) {
            // мы увидели exp и успели поменять или мы увидели desc, его до нас поставила эта же операция

            if (CAS(&desc.outcome, i - 1, i)) {
                // мы привели операцию на ячейке к консенсусу, идем дальше
                makeCASN(desc, i + 1)
            } else {
                // другой поток привел к консенсусу, догоняем его
                makeCASN(desc, desc.outcome)
            }
        } else {
            // увидели не exp значение и до нас никто не сделал этот шаг

            if (CAS(&desc.outcome, i - 1, -i)) {
                // мы привели операцию к консенсусу
                rollback(desc)
            } else {
                // другой поток убежал далеко с помощью, догоняем его
                makeCASN(desc, desc.outcome)
            }
        }
    } else {
        // другой поток убежал далеко с помощью, догоняем его
        makeCASN(desc, desc.outcome)
    }
}

n   => прошли все итерации, CASN успешен, откатываем в upd всех
<0  => где-то был не expected CAS, операция не успешна, откатываем на exp
rollback(Descriptor desc) {
    // идем по всем индексам от i или -i до 1 и выставляем соответствующие значения
}


































makeCASN(Descriptor desc, int i) {
    if (i == n || i < 0) {
        rollback(desc) // операция завершится тут
        return
    }

    if (desc.outcome.value == i - 1) {
        // i - 1 итераций прошло успешно, делаем i-ю
        var obsI = desc.vars[i].value
        while (!CAS(&desc.vars[i], obsI, Descriptor'(desc)) {
            obsI = desc.vars[i].value
            if (obsI instanceof Descriptor) help(obsI as Descriptor)
        }

        if (obsI == desc || obsI == desc.exp[i] && CAS(&desc.vars[i], desc', desc)) {
            // мы увидели exp и успели поменять или мы увидели desc, его до нас поставила эта же операция

            if (CAS(&desc.outcome, i - 1, i)) {
                // мы привели операцию на ячейке к консенсусу, идем дальше
                makeCASN(desc, i + 1)
            } else {
                // другой поток привел к консенсусу, откат выполнится в рекурсивной функции
                makeCASN(desc, desc.outcome)
            }
        } else {
            // увидели не exp значение и до нас никто не сделал этот шаг

            if (CAS(&desc.outcome, i - 1, -i)) {
                // мы привели операцию к консенсусу
                rollback(desc)
            } else {
                // другой поток убежал далеко с помощью, догоняем его
                makeCASN(desc, desc.outcome)
            }
        }
    } else {
        // другой поток убежал далеко с помощью, догоняем его
        makeCASN(desc, desc.outcome)
    }
}


help(Descriptor desc) = makeCASN(desc, desc.outcome)



















 */














