import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestAllStrings {
    public static void main(String[] args) throws Exception {
        File resDir = new File("app/src/main/res");
        File[] dirs = resDir.listFiles();
        Pattern p = Pattern.compile("<string name=\"restoring_app\">([^<]*)</string>");
        
        for (File dir : dirs) {
            if (dir.isDirectory() && dir.getName().startsWith("values")) {
                File stringsFile = new File(dir, "strings.xml");
                if (stringsFile.exists()) {
                    String content = new String(Files.readAllBytes(stringsFile.toPath()), "UTF-8");
                    Matcher m = p.matcher(content);
                    if (m.find()) {
                        String raw = m.group(1);
                        // Convert XML entities? (no need if simple)
                        try {
                            String.format(raw, "pkg");
                        } catch (Exception e) {
                            System.out.println("CRASH IN " + dir.getName() + ": " + raw);
                        }
                    }
                }
            }
        }
        System.out.println("Done.");
    }
}
