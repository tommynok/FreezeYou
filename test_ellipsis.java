import java.util.Formatter;

public class test_ellipsis {
    public static void main(String[] args) {
        try {
            // using exact hex to avoid encoding issues during compilation
            String formatStr = "\u0412\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435 %1$s\u2026";
            String result = String.format(formatStr, "test");
            System.out.println("Result: " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
