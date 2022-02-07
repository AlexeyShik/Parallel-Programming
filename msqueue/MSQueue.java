package msqueue;

import kotlinx.atomicfu.AtomicRef;

public class MSQueue implements Queue {
    private final AtomicRef<Node> head;
    private final AtomicRef<Node> tail;

    public MSQueue() {
        final Node dummy = new Node(0);
        this.head = new AtomicRef<>(dummy);
        this.tail = new AtomicRef<>(dummy);
    }

    @Override
    public void enqueue(int x) {
        final Node newTail = new Node(x);
        while (true) {
            final Node curTail = tail.getValue();
            final AtomicRef<Node> next = curTail.next;
            if (next.getValue() == null) {
                if (tail.getValue().next.compareAndSet(null, newTail)) {
                    break;
                }
            } else {
                tail.compareAndSet(curTail, next.getValue());
            }
        }
    }

    @Override
    public int dequeue() {
        while (true) {
            final Node curHead = head.getValue();
            final Node curTail = tail.getValue();
            final Node headNext = curHead.next.getValue();
            final Node tailNext = curTail.next.getValue();
            if (headNext == null)
                return Integer.MIN_VALUE;
            if (curHead == curTail && tailNext != null) {
                tail.compareAndSet(curTail, tailNext);
            } else if (head.compareAndSet(curHead, headNext)) {
                return headNext.value;
            }
        }
    }

    @Override
    public int peek() {
        final Node next = head.getValue().next.getValue();
        if (next == null) {
            return Integer.MIN_VALUE;
        }
        return next.value;
    }

    private static class Node {
        final int value;
        AtomicRef<Node> next;

        Node(int value) {
            this.value = value;
            this.next = new AtomicRef<>(null);
        }
    }
}