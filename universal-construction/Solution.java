/**
 * @author Shik Alexey
 */
public class Solution implements AtomicCounter {
    private final Node root = new Node();
    private final ThreadLocal<Node> last = ThreadLocal.withInitial(() -> root);

    public int getAndAdd(int x) {
        int old;
        while (true) {
            old = last.get().value;
            Node newNode = new Node(old + x);
            last.set(last.get().next.decide(newNode));
            if (newNode.equals(last.get())) {
                break;
            }
        }
        return old;
    }


    private static class Node {

        Node() {
            this(0);
        }

        Node(int value) {
            this.value = value;
            this.next = new Consensus<>();
        }

        final int value;
        final Consensus<Node> next;
    }
}
