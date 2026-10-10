public class test_format3 {
    public static void main(String[] args) {
        try {
            String formatStr = "Restoring %1$s\u2026";
            System.out.println(String.format(formatStr, "com.example"));
            String formatStr2 = "\u0412\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u0438\u0435 %1$s\u2026";
            System.out.println(String.format(formatStr2, ""));
            System.out.println("No crash");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
