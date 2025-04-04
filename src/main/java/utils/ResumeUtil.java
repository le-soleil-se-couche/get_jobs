package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ResumeUtil {
    public static String load() {
        try {
            return Files.readString(Paths.get("src/main/resources/resume.txt"));
        } catch (IOException e) {
            return "我是来自哥伦比亚大学的硕士应届毕业生，背景包括金融工程、数据科学、风控建模等方向，具备丰富的项目实践与编程能力，致力于在金融科技领域发展。";
        }
    }
}
