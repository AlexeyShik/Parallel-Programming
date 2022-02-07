package stack;

import kotlinx.atomicfu.AtomicIntArray;
import kotlinx.atomicfu.AtomicRef;

import java.util.concurrent.ThreadLocalRandom;

public class StackImpl implements Stack {

    private static final int EMPTY = Integer.MIN_VALUE + 1;
    private static final int DONE = Integer.MIN_VALUE + 2;

    private static class Node {

        final AtomicRef<Node> next;
        final int value;

        Node(int value, Node next) {
            this.next = new AtomicRef<>(next);
            this.value = value;
        }
    }

    private static final int ELIMINATION_ARRAY_SIZE = 10;
    private static final int N_ITERATIONS = 5;
    private static final int RANGE = 1;
    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

    private final AtomicRef<Node> head = new AtomicRef<>(null);
    private final AtomicIntArray elimination = new AtomicIntArray(ELIMINATION_ARRAY_SIZE);

    {
        for (int i = 0; i < ELIMINATION_ARRAY_SIZE; ++i) {
            elimination.get(i).setValue(EMPTY);
        }
    }

    private boolean tryEliminationPush(int index, int x) {
        for (int i = 0; i < N_ITERATIONS; ++i) {
            //  waiting for pop queries
            if (elimination.get(index).compareAndSet(DONE, EMPTY)) {
                return true;
            }
        }

        if (elimination.get(index).compareAndSet(x, EMPTY)) {
            return false;
        } else {
            //  is 100% DONE
            elimination.get(index).compareAndSet(DONE, EMPTY);
            return true;
        }

    }

    @Override
    public void push(int x) {
        if (x != DONE && x != EMPTY) {
            final int index = RANDOM.nextInt(ELIMINATION_ARRAY_SIZE);
            for (int i = Math.max(0, index - RANGE); i <= Math.min(index + RANGE, ELIMINATION_ARRAY_SIZE); ++i) {
                if (elimination.get(index).compareAndSet(EMPTY, x)) {
                    if (tryEliminationPush(index, x)) {
                        return;
                    }
                    break;
                }
            }
        }
        //  push in real stack
        while (true) {
            final Node curHead = head.getValue();
            if (head.compareAndSet(curHead, new Node(x, curHead))) {
                return;
            }
        }
    }

    @Override
    public int pop() {
        final int index = RANDOM.nextInt(ELIMINATION_ARRAY_SIZE);
        for (int i = Math.max(0, index - RANGE); i <= Math.min(index + RANGE, ELIMINATION_ARRAY_SIZE); ++i) {
            int x = elimination.get(index).getValue();

            if (x != EMPTY && x != DONE && elimination.get(index).compareAndSet(x, DONE)) {
                return x;
            }
        }
        //  pop from real stack
        while (true) {
            final Node curHead = head.getValue();

            if (curHead == null) {
                return Integer.MIN_VALUE;
            }

            if (head.compareAndSet(curHead, curHead.next.getValue())) {
                return curHead.value;
            }
        }
    }
}
