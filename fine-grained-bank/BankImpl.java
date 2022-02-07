import java.util.concurrent.locks.ReentrantLock;

/**
 * Bank implementation.
 *
 * @author Shik Alexey
 */
public class BankImpl implements Bank {
    /**
     * An array of accounts by index.
     */
    private final Account[] accounts;

    /**
     * Creates new bank instance.
     * @param n the number of accounts (numbered from 0 to n-1).
     */
    public BankImpl(int n) {
        accounts = new Account[n];
        for (int i = 0; i < n; i++) {
            accounts[i] = new Account();
        }
    }

    @Override
    public int getNumberOfAccounts() {
        return accounts.length;
    }

    @Override
    public long getAmount(int index) {
        Account account = accounts[index];
        account.lock.lock();
        long amount = account.amount;
        account.lock.unlock();
        return amount;
    }

    @Override
    public long getTotalAmount() {
        long sum = 0;
        for (Account account : accounts)
            account.lock.lock();
        for (Account account : accounts)
            sum += account.amount;
        for (Account account : accounts)
            account.lock.unlock();
        return sum;
    }

    @Override
    public long deposit(int index, long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Invalid amount: " + amount);
        Account account = accounts[index];
        account.lock.lock();
        if (amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT) {
            account.lock.unlock();
            throw new IllegalStateException("Overflow");
        }
        long result = account.amount += amount;
        account.lock.unlock();
        return result;
    }

    @Override
    public long withdraw(int index, long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Invalid amount: " + amount);
        Account account = accounts[index];
        account.lock.lock();
        if (account.amount - amount < 0) {
            account.lock.unlock();
            throw new IllegalStateException("Underflow");
        }
        long result = account.amount -= amount;
        account.lock.unlock();
        return result;
    }

    private static void unlock(Account a, Account b, boolean lower) {
        if (lower) {
            b.lock.unlock();
            a.lock.unlock();
        } else {
            a.lock.unlock();
            b.lock.unlock();
        }
    }

    @Override
    public void transfer(int fromIndex, int toIndex, long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Invalid amount: " + amount);
        if (fromIndex == toIndex)
            throw new IllegalArgumentException("fromIndex == toIndex");
        Account from = accounts[fromIndex];
        Account to = accounts[toIndex];
        if (fromIndex <= toIndex) {
            from.lock.lock();
            to.lock.lock();
        } else {
            to.lock.lock();
            from.lock.lock();
        }
        if (amount > from.amount) {
            unlock(from, to, fromIndex <= toIndex);
            throw new IllegalStateException("Underflow");
        } else if (amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT) {
            unlock(from, to, fromIndex <= toIndex);
            throw new IllegalStateException("Overflow");
        }
        from.amount -= amount;
        to.amount += amount;
        unlock(from, to, fromIndex <= toIndex);
    }

    /**
     * Private account data structure.
     */
    static class Account {

        private final ReentrantLock lock = new ReentrantLock();

        /**
         * Amount of funds in this account.
         */
        private long amount;
    }
}
