package linked_list_set;

import kotlinx.atomicfu.AtomicRef;

public class SetImpl implements Set {

    private interface SetNode {}

    private static class Node implements SetNode {

        protected AtomicRef<SetNode> next;
        protected int x;

        Node(int x, SetNode next) {
            this.next = new AtomicRef<>(next);
            this.x = x;
        }

    }


    private static class Removed implements SetNode {

        private final Node node;

        Removed(Node node) {
            this.node = node;
        }
    }

    private static class Window {
        Node cur;
        Node next;
    }

    private final Node head = new Node(Integer.MIN_VALUE, new Node(Integer.MAX_VALUE, null));

    /**
     * Returns the {@link Window}, where cur.x < x <= next.x
     */
    private Window findWindow(int x) {
        while (true) {
            Window window = new Window();
            window.cur = head;
            window.next = (Node) window.cur.next.getValue();
            boolean success = true;

            while (window.next.x < x) {
                SetNode nextNext = window.next.next.getValue();

                if (nextNext instanceof Removed) {
                    Node realNextNext = ((Removed) nextNext).node;

                    if (!window.cur.next.compareAndSet(window.next, realNextNext)) {
                        success = false;
                        break;
                    }
                    window.next = realNextNext;
                } else {
                    window.cur = window.next;
                    window.next = (Node) nextNext;
                }
            }

            if (success) {
                return window;
            }
        }
    }

    @Override
    public boolean add(int x) {
        while (true) {
            Window window = findWindow(x);
            SetNode nextNext = window.next.next.getValue();

            if (nextNext instanceof Node && window.next.x == x) {
                return false;
            }

            if (window.cur.next.compareAndSet(window.next, new Node(x, window.next))) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(int x) {
        while (true) {
            Window window = findWindow(x);
            if (window.next.x != x) {
                return false;
            }

            SetNode nextNext = window.next.next.getValue();
            if (nextNext instanceof Removed) {
                return false;
            }

            if (window.next.next.compareAndSet(nextNext, new Removed((Node) nextNext))) {
                window.cur.next.compareAndSet(window.next, nextNext);
                return true;
            }
        }
    }

    @Override
    public boolean contains(int x) {
        Window window = findWindow(x);
        return window.next.next.getValue() instanceof Node && window.next.x == x;
    }
}