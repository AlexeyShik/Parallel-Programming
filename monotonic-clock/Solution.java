import org.jetbrains.annotations.NotNull;

/**
 * В теле класса решения разрешено использовать только финальные переменные типа RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 *
 * @author Shik Alexey
 */
public class Solution implements MonotonicClock {
    private final RegularInt cl1 = new RegularInt(0);
    private final RegularInt cl2 = new RegularInt(0);
    private final RegularInt cl3 = new RegularInt(0);

    private final RegularInt cr1 = new RegularInt(0);
    private final RegularInt cr2 = new RegularInt(0);
    private final RegularInt cr3 = new RegularInt(0);

    @Override
    public void write(@NotNull Time time) {
        // write right-to-left
        cr1.setValue(time.getD1());
        cr2.setValue(time.getD2());
        cr3.setValue(time.getD3());
        // write left-to-right
        cl3.setValue(time.getD3());
        cl2.setValue(time.getD2());
        cl1.setValue(time.getD1());
    }

    @NotNull
    @Override
    public Time read() {
        // read right-to-left
        int l1 = cl1.getValue();
        int l2 = cl2.getValue();
        // read left-to-right
        int r3 = cr3.getValue();
        int r2 = cr2.getValue();
        int r1 = cr1.getValue();
        // combining to result
        int ans1;
        int ans2 = 0;
        int ans3 = 0;
        if (l1 == r1) {
            ans1 = l1;
            if (l2 == r2) {
                ans2 = l2;
                ans3 = r3;
            } else {
                ans2 = r2;
            }
        } else {
            ans1 = r1;
        }
        return new Time(ans1, ans2, ans3);
    }
}
