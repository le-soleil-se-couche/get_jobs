package ai;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 记录每条 JD 匹配分数和岗位详情的日志工具类
 */
public class LogRecorder {
    private static final String LOG_PATH = "./ai_match_log.csv";

    public static void recordMatch(String platform, String jobTitle, String company, String jd, double score) {
        try (FileWriter writer = new FileWriter(LOG_PATH, true)) {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String cleanedJd = jd.replaceAll("[\\r\\n\\t]", " ").replace(",", "，");
            writer.write(String.format("%s,%s,%s,%s,%.4f,%s\n", 
                                       time, platform, jobTitle, company, score, cleanedJd));
        } catch (IOException e) {
            System.err.println("匹配日志写入失败：" + e.getMessage());
        }
    }
}
