package boss;

import static utils.Bot.sendMessageByTime;
import static utils.Constant.CHROME_DRIVER;
import static utils.Constant.WAIT;
import static utils.JobUtils.formatDuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.AiConfig;
import ai.AiFilter;
import ai.AiService;
import utils.Job;
import utils.JobUtils;
import utils.SeleniumUtil;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * Boss直聘自动投递
 */
public class Boss {
    static final int noJobMaxPages = 10; // 无岗位最大页数
    private static final Logger log = LoggerFactory.getLogger(Boss.class);
    static Integer page = 1;
    static String homeUrl = "https://www.zhipin.com";
    static String baseUrl = "https://www.zhipin.com/web/geek/job?";
    static Set<String> blackCompanies;
    static Set<String> blackRecruiters;
    static Set<String> blackJobs;
    static List<Job> resultList = new ArrayList<>();
    static List<String> deadStatus = List.of("半年前活跃", "4月内活跃");
    static List<String> activeStatus = List.of("刚刚活跃", "今日活跃");
    static String dataPath = "./src/main/java/boss/data.json";
    static String cookiePath = "./src/main/java/boss/cookie.json";
    static int noJobPages;
    static int lastSize;
    static Date startDate;
    static BossConfig config = BossConfig.init();
    static int maxPages = 10;

    public static void main(String[] args) {
        loadData(dataPath);
        SeleniumUtil.initDriver();
        startDate = new Date();
        login();
        config.getCityCode().forEach(Boss::postJobByCity);
        log.info(resultList.isEmpty() ? "未发起新的聊天..." : "新发起聊天公司如下:\n{}", resultList.stream().map(Object::toString).collect(Collectors.joining("\n")));
        printResult();
    }

    private static void printResult() {
        String message = String.format("\nBoss投递完成，共发起%d个聊天，用时%s", resultList.size(), formatDuration(startDate, new Date()));
        log.info(message);
        sendMessageByTime(message);
        saveData(dataPath);
        resultList.clear();
        CHROME_DRIVER.close();
        CHROME_DRIVER.quit();
    }

    private static void postJobByCity(String cityCode) {
        String searchUrl = getSearchUrl(cityCode);
        WebDriverWait wait = new WebDriverWait(CHROME_DRIVER, 40);
        for (String keyword : config.getKeywords()) {
            int page = 1;
            int noJobPages = 0;
            int lastSize = -1;
            String url = searchUrl + "&page=" + page + "&query=" + keyword;
            log.info("开始投递第一页，页面url：{}", url);
            CHROME_DRIVER.get(url);

            while (true) {
                log.info("投递【{}】关键词第【{}】页", keyword, page);
                // 检查是否找到岗位元素
                if (isJobsPresent(wait)) {
                    log.info("当前页面已找到岗位，开始进行投递...");
                    // 进行投递操作
                    Integer resultSize = resumeSubmission(keyword);
                    if (resultSize == -1) {
                        log.info("今日沟通人数已达上限，请明天再试");
                        return;
                    }
                    if (resultSize == -2) {
                        log.info("出现异常访问，请手动过验证后再继续投递...");
                        return;
                    }
                    if (resultSize == -3) {
                        log.info("没有岗位了，换个关键词再试试...");
                        return;
                    }

                    noJobPages = 0;
                } else {
                    noJobPages++;
                    if (noJobPages >= noJobMaxPages) {
                        log.info("【{}】关键词已经连续【{}】页无岗位，结束该关键词的投递...", keyword, noJobPages);
                        break;
                    } else {
                        log.info("【{}】第【{}】页无岗位,目前已连续【{}】页无新岗位...", keyword, page, noJobPages);
                    }
                }

                if (page >= maxPages) {
                    log.info("关键词【{}】已投递{}页，结束该关键词投递", keyword, maxPages);
                    break;
                }

                int pageResult = clickNextPage(page, wait);
                if (pageResult == 0) {
                    log.info("【{}】关键词已投递至末页，结束该关键词的投递...", keyword);
                    break;
                }
                page++;
                log.info("准备投递下一页，页码{}", page);
                url = searchUrl + "&page=" + page + "&query=" + keyword;
                log.info("加载新页面url{}", url);
                CHROME_DRIVER.get(url);
                log.info("等待页面加载完成");

                // 确保页面加载完成
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@class='search-job-result']")));
            }
        }
    }

    private static boolean isJobsPresent(WebDriverWait wait) {
        try {
            // 判断页面是否存在岗位的元素
            WebElement jobList = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='search-job-result']/ul[@class='job-list-box']")));
            List<WebElement> jobCards = jobList.findElements(By.className("job-card-wrapper"));
            return !jobCards.isEmpty();
        } catch (Exception e) {
            log.error("未能找到岗位元素,即将跳转下一页{}", e.getMessage());
            return false;
        }
    }

    private static int clickNextPage(int currentPage, WebDriverWait wait) {
        try {
            WebElement nextButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a//i[@class='ui-icon-arrow-right']")));
            if (nextButton.isEnabled()) {
                nextButton.click();
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@class='job-list-wrapper']")));
                return 1;
            } else {
                return 0;
            }
        } catch (Exception e) {
            log.error("点击下一页按钮异常>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>", e);
            String currentUrl = CHROME_DRIVER.getCurrentUrl();
            log.debug("当前页面url>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>" + currentUrl);
            int nextPage = currentPage + 1;
            String newUrl = currentUrl.replaceAll("page=" + currentPage, "page=" + nextPage).replaceAll("&query=[^&]*", "");
            log.debug("新的页面url>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>" + newUrl);
            CHROME_DRIVER.get(newUrl);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@class='job-list-wrapper']")));
            return -1;
        }
    }

    private static String getSearchUrl(String cityCode) {
        return baseUrl + JobUtils.appendParam("city", cityCode) +
                JobUtils.appendParam("jobType", config.getJobType()) +
                JobUtils.appendParam("salary", config.getSalary()) +
                JobUtils.appendListParam("experience", config.getExperience()) +
                JobUtils.appendListParam("degree", config.getDegree()) +
                JobUtils.appendListParam("scale", config.getScale()) +
                JobUtils.appendListParam("industry", config.getIndustry())+
                JobUtils.appendListParam("stage", config.getStage());
    }

    private static void saveData(String path) {
        try {
            updateListData();
            Map<String, Set<String>> data = new HashMap<>();
            data.put("blackCompanies", blackCompanies);
            data.put("blackRecruiters", blackRecruiters);
            data.put("blackJobs", blackJobs);
            String json = customJsonFormat(data);
            Files.write(Paths.get(path), json.getBytes());
        } catch (IOException e) {
            log.error("保存【{}】数据失败！", path);
        }
    }

    private static void updateListData() {
        CHROME_DRIVER.get("https://www.zhipin.com/web/geek/chat");
        SeleniumUtil.getWait(3);

        JavascriptExecutor js = CHROME_DRIVER;
        boolean shouldBreak = false;
        while (!shouldBreak) {
            try {
                WebElement bottom = CHROME_DRIVER.findElement(By.xpath("//div[@class='finished']"));
                if ("没有更多了".equals(bottom.getText())) {
                    shouldBreak = true;
                }
            } catch (Exception ignore) {
            }
            List<WebElement> items = CHROME_DRIVER.findElements(By.xpath("//li[@role='listitem']"));
            for (int i = 0; i < items.size(); i++) {
                try {
                    WebElement companyElement = CHROME_DRIVER.findElements(By.xpath("//span[@class='name-box']//span[2]")).get(i);
                    String companyName = companyElement.getText();
                    WebElement messageElement = CHROME_DRIVER.findElements(By.xpath("//span[@class='last-msg-text']")).get(i);
                    String message = messageElement.getText();
                    boolean match = message.contains("不") || message.contains("感谢") || message.contains("但") || message.contains("遗憾") || message.contains("需要本") || message.contains("对不");
                    boolean nomatch = message.contains("不是") || message.contains("不生");
                    if (match && !nomatch) {
                        log.info("黑名单公司：【{}】，信息：【{}】", companyName, message);
                        if (blackCompanies.stream().anyMatch(companyName::contains)) {
                            continue;
                        }
                        companyName = companyName.replaceAll("\\.{3}", "");
                        if (companyName.matches(".*(\\p{IsHan}{2,}|[a-zA-Z]{4,}).*")) {
                            blackCompanies.add(companyName);
                        }
                    }
                } catch (Exception e) {
                    log.error("寻找黑名单公司异常...");
                }
            }
            WebElement element;
            try {
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(text(), '滚动加载更多')]")));
                element = CHROME_DRIVER.findElement(By.xpath("//div[contains(text(), '滚动加载更多')]"));
            } catch (Exception e) {
                log.info("没找到滚动条...");
                break;
            }

            if (element != null) {
                try {
                    js.executeScript("arguments[0].scrollIntoView();", element);
                } catch (Exception e) {
                    log.error("滚动到元素出错", e);
                }
            } else {
                try {
                    js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                } catch (Exception e) {
                    log.error("滚动到页面底部出错", e);
                }
            }
        }
        log.info("黑名单公司数量：{}", blackCompanies.size());
    }


    private static String customJsonFormat(Map<String, Set<String>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (Map.Entry<String, Set<String>> entry : data.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": [\n");
            sb.append(entry.getValue().stream().map(s -> "        \"" + s + "\"").collect(Collectors.joining(",\n")));

            sb.append("\n    ],\n");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("\n}");
        return sb.toString();
    }

    private static void loadData(String path) {
        try {
            String json = new String(Files.readAllBytes(Paths.get(path)));
            parseJson(json);
        } catch (IOException e) {
            log.error("读取【{}】数据失败！", path);
        }
    }

    private static void parseJson(String json) {
        JSONObject jsonObject = new JSONObject(json);
        blackCompanies = jsonObject.getJSONArray("blackCompanies").toList().stream().map(Object::toString).collect(Collectors.toSet());
        blackRecruiters = jsonObject.getJSONArray("blackRecruiters").toList().stream().map(Object::toString).collect(Collectors.toSet());
        blackJobs = jsonObject.getJSONArray("blackJobs").toList().stream().map(Object::toString).collect(Collectors.toSet());
    }

    @SneakyThrows
    private static Integer resumeSubmission(String url, String keyword) {
        CHROME_DRIVER.get(url + "&query=" + keyword);
        try {
            WAIT.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='job-title clearfix']")));
            SeleniumUtil.simulateRandomUserBehavior(config.getFakeUserAction());
        } catch (Exception e) {
            Optional<WebElement> jobEmpty = SeleniumUtil.findElement("//div[@class='job-empty-wrapper']", "没有找到\"相关职位搜索不到\"的tag");
            if (jobEmpty.isPresent()) {
                return -3;
            }
        }
        List<WebElement> jobCards = CHROME_DRIVER.findElements(By.cssSelector("li.job-card-wrapper"));
        List<Job> jobs = new ArrayList<>();
        for (WebElement jobCard : jobCards) {
            WebElement infoPublic = jobCard.findElement(By.cssSelector("div.info-public"));
            String recruiterText = infoPublic.getText();
            String recruiterName = infoPublic.findElement(By.cssSelector("em")).getText();
            String salary = jobCard.findElement(By.cssSelector("span.salary")).getText();
            if (blackRecruiters.stream().anyMatch(recruiterName::contains)) {
                // 排除黑名单招聘人员
                continue;
            }
            String jobName = jobCard.findElement(By.cssSelector("div.job-title span.job-name")).getText();
            if (blackJobs.stream().anyMatch(jobName::contains) || !isTargetJob(keyword, jobName)) {
                // 排除黑名单岗位
                continue;
            }
            String companyName = jobCard.findElement(By.cssSelector("div.company-info h3.company-name")).getText();
            if (blackCompanies.stream().anyMatch(companyName::contains)) {
                // 排除黑名单公司
                continue;
            }
            if (isSalaryNotExpected(salary)) {
                // 过滤薪资
                log.info("已过滤:【{}】公司【{}】岗位薪资【{}】不符合投递要求", companyName, jobName, salary);
                noJobPages = 0;
                continue;
            }
            Job job = new Job();
            job.setRecruiter(recruiterText.replace(recruiterName, "") + ":" + recruiterName);
            job.setHref(jobCard.findElement(By.cssSelector("a")).getAttribute("href"));
            job.setJobName(jobName);
            job.setJobArea(jobCard.findElement(By.cssSelector("div.job-title span.job-area")).getText());
            job.setSalary(jobCard.findElement(By.cssSelector("div.job-info span.salary")).getText());
            List<WebElement> tagElements = jobCard.findElements(By.cssSelector("div.job-info ul.tag-list li"));
            StringBuilder tag = new StringBuilder();
            for (WebElement tagElement : tagElements) {
                tag.append(tagElement.getText()).append("·");
            }
            job.setCompanyTag(tag.substring(0, tag.length() - 1));
            jobs.add(job);
        }

        for (Job job : jobs) {
            // 打开新的标签页
            JavascriptExecutor jse = CHROME_DRIVER;
            jse.executeScript("window.open(arguments[0], '_blank')", job.getHref());
            // 切换到新的标签页
            ArrayList<String> tabs = new ArrayList<>(CHROME_DRIVER.getWindowHandles());
            CHROME_DRIVER.switchTo().window(tabs.get(tabs.size() - 1));
            // 模拟随机用户行为
            SeleniumUtil.simulateRandomUserBehavior(config.getFakeUserAction());
            try {
                // 等待聊天按钮出现
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='btn btn-startchat']")));
            } catch (Exception e) {
                Optional<WebElement> element = SeleniumUtil.findElement("//div[@class='error-content']", "");
                if (element.isPresent() && element.get().getText().contains("异常访问")) {
                    return -2;
                }
            }
            //过滤不符合期望薪资的岗位
            if (isSalaryNotExpected()) {
                closeWindow(tabs);
                SeleniumUtil.sleep(1);
                SeleniumUtil.simulateRandomUserBehavior(config.getFakeUserAction()); // 加入模拟行为
                continue;
            }
            //过滤不活跃HR
            if (isDeadHR()) {
                closeWindow(tabs);
                SeleniumUtil.sleep(1);
                SeleniumUtil.simulateRandomUserBehavior(config.getFakeUserAction());  // 加入模拟行为
                continue;
            }
            simulateWait();
            SeleniumUtil.simulateRandomUserBehavior(config.getFakeUserAction());
            WebElement btn = CHROME_DRIVER.findElement(By.cssSelector("[class*='btn btn-startchat']"));
            WebElement activeTime = null;
            WebElement bossOnlineTag = null;
            try {
                activeTime = CHROME_DRIVER.findElement(By.cssSelector("[class*='boss-active-time']"));
            } catch (Exception e) {
                log.info("没有找到Boss的活跃度");
            }
            try {
                bossOnlineTag = CHROME_DRIVER.findElement(By.cssSelector("[class*='boss-online-tag']"));
            } catch (Exception e) {
                log.info("没有找到Boss的在线状态");
            }
            // 判断Boss是否为半年前活跃
            if (activeTime != null && activeTime.getText().equals("半年前活跃")) {
                SeleniumUtil.sleep(1);
                CHROME_DRIVER.close();
                CHROME_DRIVER.switchTo().window(tabs.get(0));
                continue;
            }else if (bossOnlineTag == null) {
                SeleniumUtil.sleep(1);
                CHROME_DRIVER.close();
                CHROME_DRIVER.switchTo().window(tabs.get(0));
                continue;
            }
            AiFilter filterResult = null;
            if (config.getEnableAI()) {
                //AI检测岗位是否匹配
String jd = CHROME_DRIVER.findElement(By.xpath("//div[@class='job-sec-text']")).getText();
if (!AiFilterUtil.shouldSayHi(jd)) {
    log.info("❌ 匹配度不足，自动跳过该岗位");
    closeWindow(tabs);
    continue;
}

// 👇👇👇 插入 GPT 嵌入匹配逻辑
List<Double> resumeVec = EmbeddingUtil.getEmbedding(ResumeUtil.load());
List<Double> jdVec = EmbeddingUtil.getEmbedding(jd);
double matchScore = CosineSimilarity.calculate(resumeVec, jdVec);
if (matchScore < 0.85) {
    log.info("Boss岗位匹配度为 {}，低于0.85，跳过投递", matchScore);
    closeWindow(tabs);
    continue;
}
// 👆👆👆

filterResult = checkJob(keyword, job.getJobName(), jd);  // 原逻辑

            }


            if ("立即沟通".equals(btn.getText())) {
                String waitTime = config.getWaitTime();
                int sleepTime = 10; // 默认等待10秒

                if (waitTime != null) {
                    try {
                        sleepTime = Integer.parseInt(waitTime);
                    } catch (NumberFormatException e) {
                        log.error("等待时间转换异常！！");
                    }
                }

                SeleniumUtil.sleep(sleepTime);

                AiFilter filterResult = null;
                if (config.getEnableAI()) {
                    // AI检测岗位是否匹配
                    String jd = CHROME_DRIVER.findElement(By.xpath("//div[@class='job-sec-text']")).getText();
                    filterResult = checkJob(keyword, job.getJobName(), jd);
                }
                btn.click();
                if (isLimit()) {
                    SeleniumUtil.sleep(1);
                    return -1;
                }
                try {
                    WebElement input;
                    List<WebElement> elements = CHROME_DRIVER.findElements(By.xpath("//textarea[@class='input-area']"));
                    if (elements.isEmpty()) {
                        // 元素不存在的处理逻辑
                        input = WAIT.until(ExpectedConditions.presenceOfElementLocated((By.xpath("//div[@id='chat-input']"))));
                    } else {
                        // 元素存在的处理逻辑
                        input = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//textarea[@class='input-area']")));
                    }
                    WebElement input = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@id='chat-input']")));
                    SeleniumUtil.simulateRandomUserBehavior(config.getFakeUserAction());;
                    input.click();
                    SeleniumUtil.sleep(1);
                    WebElement element = CHROME_DRIVER.findElement(By.xpath("//div[@class='dialog-container']"));
                    if ("不匹配".equals(element.getText())) {
                        CHROME_DRIVER.close();
                        CHROME_DRIVER.switchTo().window(tabs.get(0));
                        continue;
                    }

                    }
                    send.click();
                    SeleniumUtil.sleep(3);
                    WebElement recruiterNameElement = CHROME_DRIVER.findElement(By.xpath("//div[@class='name-content']/span[@class='name-text']"));
                    WebElement recruiterTitleElement = CHROME_DRIVER.findElement(By.xpath("//div[@class='base-info']/span[@class='base-title']"));
                    String recruiter = recruiterNameElement.getText() + " " + recruiterTitleElement.getText();

                    WebElement companyElement = null;
                    try {
                        // 通过定位父元素后获取第二个 span 元素，获取公司名
                        companyElement = CHROME_DRIVER.findElement(By.xpath("//div[@class='base-info']/span[1]"));
                    } catch (Exception e) {
                        log.info("获取公司名异常！");
                    }
                    String company = null;
                    if (companyElement != null) {
                        company = companyElement.getText();
                        job.setCompanyName(company);
                    }
                    WebElement positionNameElement = CHROME_DRIVER.findElement(By.xpath("//div[@class='left-content']/span[@class='position-name']"));
                    WebElement salaryElement = CHROME_DRIVER.findElement(By.xpath("//div[@class='left-content']/span[@class='salary']"));
                    WebElement cityElement = CHROME_DRIVER.findElement(By.xpath("//div[@class='left-content']/span[@class='city']"));
                    String position = positionNameElement.getText() + " " + salaryElement.getText() + " " + cityElement.getText();
                    company = company == null ? "未知公司: " + job.getHref() : company;
                    Boolean imgResume = sendResume(company);
                    SeleniumUtil.sleep(2);
                    log.info("正在投递【{}】公司，【{}】职位，招聘官:【{}】{}", company, position, recruiter, imgResume ? "发送图片简历成功！" : "");
                    resultList.add(job);
                    noJobPages = 0;
                } catch (Exception e) {
                    log.error("发送消息失败:{}", e.getMessage(), e);
                }
            }
            closeWindow(tabs);
        }
        return resultList.size();
    }

    public static boolean isValidString(String str) {
        return str != null && !str.isEmpty();
    }

    public static Boolean sendResume(String company) {
        // 如果 config.getSendImgResume() 为 true，再去找图片
        if (!config.getSendImgResume()) {
            return false;
        }

        try {
            // 从类路径加载 resume.jpg
            URL resourceUrl = Boss.class.getResource("/resume.jpg");
            if (resourceUrl == null) {
                log.error("在类路径下未找到 resume.jpg 文件！");
                return false;
            }

            // 将 URL 转为 File 对象
            File imageFile = new File(resourceUrl.toURI());
            log.info("简历图片路径：{}", imageFile.getAbsolutePath());

            if (!imageFile.exists()) {
                log.error("简历图片不存在！: {}", imageFile.getAbsolutePath());
                return false;
            }

            // 使用 XPath 定位 <input type="file"> 元素
            WebElement fileInput = CHROME_DRIVER.findElement(By.xpath("//div[@aria-label='发送图片']//input[@type='file']"));

            // 上传图片
            fileInput.sendKeys(imageFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("发送简历图片时出错：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查岗位薪资是否符合预期
     *
     * @return boolean
     * true 不符合预期
     * false 符合预期
     * 期望的最低薪资如果比岗位最高薪资还小，则不符合（薪资给的太少）
     * 期望的最高薪资如果比岗位最低薪资还小，则不符合(要求太高满足不了)
     */
    private static boolean isSalaryNotExpected(String salary) {
        try {
            // 1. 如果没有期望薪资范围，直接返回 false，表示“薪资并非不符合预期”
            List<Integer> expectedSalary = config.getExpectedSalary();
            if (!hasExpectedSalary(expectedSalary)) {
                return false;
            }

            // 2. 清理薪资文本（比如去掉 "·15薪"）
            salary = removeYearBonusText(salary);

            // 3. 如果薪资格式不符合预期（如缺少 "K" / "k"），直接返回 true，表示“薪资不符合预期”
            if (!isSalaryInExpectedFormat(salary)) {
                return true;
            }

            // 4. 进一步清理薪资文本，比如去除 "K"、"k"、"·" 等
            salary = cleanSalaryText(salary);

            // 5. 判断是 "月薪" 还是 "日薪"
            String jobType = detectJobType(salary);
            salary = removeDayUnitIfNeeded(salary); // 如果是按天，则去除 "元/天"

            // 6. 解析薪资范围并检查是否超出预期
            Integer[] jobSalaryRange = parseSalaryRange(salary);
            return isSalaryOutOfRange(jobSalaryRange,
                    getMinimumSalary(expectedSalary),
                    getMaximumSalary(expectedSalary),
                    jobType);

        } catch (Exception e) {
            log.error("岗位薪资获取异常！{}", e.getMessage(), e);
            // 出错时，您可根据业务需求决定返回 true 或 false
            // 这里假设出错时无法判断，视为不满足预期 => 返回 true
            return true;
        }
    }

    /**
     * 是否存在有效的期望薪资范围
     */
    private static boolean hasExpectedSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty();
    }

    /**
     * 去掉年终奖信息，如 "·15薪"、"·13薪"。
     */
    private static String removeYearBonusText(String salary) {
        if (salary.contains("薪")) {
            // 使用正则去除 "·任意数字薪"
            return salary.replaceAll("·\\d+薪", "");
        }
        return salary;
    }

    /**
     * 判断是否是按天计薪，如发现 "元/天" 则认为是日薪
     */
    private static String detectJobType(String salary) {
        if (salary.contains("元/天")) {
            return "day";
        }
        return "mouth";
    }

    /**
     * 如果是日薪，则去除 "元/天"
     */
    private static String removeDayUnitIfNeeded(String salary) {
        if (salary.contains("元/天")) {
            return salary.replaceAll("元/天", "");
        }
        return salary;
    }

    private static Integer getMinimumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty() ? expectedSalary.getFirst() : null;
    }

    private static Integer getMaximumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && expectedSalary.size() > 1 ? expectedSalary.get(1) : null;
    }

    private static boolean isSalaryInExpectedFormat(String salaryText) {
        return salaryText.contains("K") || salaryText.contains("k");
    }

    private static String cleanSalaryText(String salaryText) {
        salaryText = salaryText.replace("K", "").replace("k", "");
        int dotIndex = salaryText.indexOf('·');
        if (dotIndex != -1) {
            salaryText = salaryText.substring(0, dotIndex);
        }
        return salaryText;
    }

    private static boolean isSalaryOutOfRange(Integer[] jobSalary, Integer miniSalary, Integer maxSalary, String jobType) {
        if (jobSalary == null) {
            return true;
        }
        if (miniSalary == null) {
            return false;
        }
        if (Objects.equals("day", jobType)) {
            // 期望薪资转为平均每日的工资
            maxSalary = BigDecimal.valueOf(maxSalary).multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
            miniSalary = BigDecimal.valueOf(miniSalary).multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
        }
        // 如果职位薪资下限低于期望的最低薪资，返回不符合
        if (jobSalary[1] < miniSalary) {
            return true;
        }
        // 如果职位薪资上限高于期望的最高薪资，返回不符合
        return maxSalary != null && jobSalary[0] > maxSalary;
    }

    private static void RandomWait() {
        SeleniumUtil.sleep(JobUtils.getRandomNumberInRange(1, 3));
    }

    private static void simulateWait() {
        for (int i = 0; i < 3; i++) {
            ACTIONS.sendKeys(" ").perform();
            SeleniumUtil.sleep(1);
        }
        ACTIONS.keyDown(Keys.CONTROL)
                .sendKeys(Keys.HOME)
                .keyUp(Keys.CONTROL)
                .perform();
        SeleniumUtil.sleep(1);
    }


    private static boolean isDeadHR() {
        if (!config.getFilterDeadHR()) {
            return false;
        }
        try {
            // 尝试获取 HR 的活跃时间
            String activeTimeText = CHROME_DRIVER.findElement(By.xpath("//span[@class='boss-active-time']")).getText();
            log.info("{}：{}", getCompanyAndHR(), activeTimeText);
            // 如果 HR 活跃状态符合预期，则返回 true
            return !activeStatus.contains(activeTimeText);
        } catch (Exception e) {
            log.info("没有找到【{}】的活跃状态, 默认此岗位将会投递...", getCompanyAndHR());
            return false;
        }
    }

    private static boolean isJobNotMatch() {
        String text = CHROME_DRIVER.findElement(By.xpath("//div[@class='job-sec-text']")).getText();

        if (text.contains("25") && !text.contains("26")) {
            log.info("岗位要求25届，跳过");
            return true;
        }

        if (text.contains("985") || text.contains("211")) {
            log.info("岗位要求92爷，跳过");
            return true;
        }

        if (text.contains("安卓") || text.contains("android") || text.contains("Android") || text.contains("客户端")) {
            log.info("岗位要求安卓，跳过");
            return true;
        }

        return false;
    }

    private static void closeWindow(ArrayList<String> tabs) {
        SeleniumUtil.sleep(1);
        CHROME_DRIVER.close();
        CHROME_DRIVER.switchTo().window(tabs.getFirst());
    }

    private static AiFilter checkJob(String keyword, String jobName, String jd) {
        AiConfig aiConfig = AiConfig.init();
        String requestMessage = String.format(aiConfig.getPrompt(), aiConfig.getIntroduce(), keyword, jobName, jd,  String.join("。", config.getSayHi()));
        String result = AiService.sendRequest(requestMessage);
        return result.contains("false") ? new AiFilter(false) : new AiFilter(true, result);
    }

    private static boolean isTargetJob(String keyword, String jobName) {
        boolean keywordIsAI = false;
        for (String target : new String[]{"大模型", "AI"}) {
            if (keyword.contains(target)) {
                keywordIsAI = true;
                break;
            }
        }

        boolean jobIsDesign = false;
        for (String designOrVision : new String[]{"设计", "视觉", "产品", "运营"}) {
            if (jobName.contains(designOrVision)) {
                jobIsDesign = true;
                break;
            }
        }

        boolean jobIsAI = false;
        for (String target : new String[]{"AI", "人工智能", "大模型", "生成"}) {
            if (jobName.contains(target)) {
                jobIsAI = true;
                break;
            }
        }

        if (keywordIsAI) {
            if (jobIsDesign) {
                return false;
            } else if (!jobIsAI) {
                return true;
            }
        }
        return true;
    }

    private static Integer[] parseSalaryRange(String salaryText) {
        try {
            return Arrays.stream(salaryText.split("-")).map(s -> s.replaceAll("[^0-9]", ""))  // 去除非数字字符
                    .map(Integer::parseInt)               // 转换为Integer
                    .toArray(Integer[]::new);             // 转换为Integer数组
        } catch (Exception e) {
            log.error("薪资解析异常！{}", e.getMessage(), e);
        }
        return null;
    }

    private static boolean isLimit() {
        try {
            SeleniumUtil.sleep(1);
            String text = CHROME_DRIVER.findElement(By.className("dialog-con")).getText();
            return text.contains("已达上限");
        } catch (Exception e) {
            return false;
        }
    }

    @SneakyThrows
    private static void login() {
        log.info("打开Boss直聘网站中...");
        CHROME_DRIVER.get(homeUrl);
        if (SeleniumUtil.isCookieValid(cookiePath)) {
            SeleniumUtil.loadCookie(cookiePath);
            CHROME_DRIVER.navigate().refresh();
            SeleniumUtil.sleep(2);
        }
        if (isLoginRequired()) {
            log.error("cookie失效，尝试扫码登录...");
            scanLogin();
        }
    }


    private static boolean isLoginRequired() {
        try {
            String text = CHROME_DRIVER.findElement(By.className("btns")).getText();
            return text != null && text.contains("登录");
        } catch (Exception e) {
            try {
                CHROME_DRIVER.findElement(By.xpath("//h1")).getText();
                CHROME_DRIVER.findElement(By.xpath("//a[@ka='403_login']")).click();
                return true;
            } catch (Exception ex) {
                log.info("没有出现403访问异常");
            }
            log.info("cookie有效，已登录...");
            return false;
        }
    }

    @SneakyThrows
    private static void scanLogin() {
        // 访问登录页面
        CHROME_DRIVER.get(homeUrl + "/web/user/?ka=header-login");
        SeleniumUtil.sleep(3);

        // 1. 如果已经登录，则直接返回
        try {
            String text = CHROME_DRIVER.findElement(By.xpath("//li[@class='nav-figure']")).getText();
            if (!Objects.equals(text, "登录")) {
                log.info("已经登录，直接开始投递...");
                return;
            }
        } catch (Exception ignored) {
        }

        log.info("等待登录...");

        // 2. 定位二维码登录的切换按钮
        WebElement app = WAIT.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//div[@class='btn-sign-switch ewm-switch']")));

        // 3. 登录逻辑
        boolean login = false;

        // 4. 记录开始时间，用于判断10分钟超时
        long startTime = System.currentTimeMillis();
        final long TIMEOUT = 10 * 60 * 1000;  // 10分钟

        // 5. 用于监听用户是否在控制台回车
        Scanner scanner = new Scanner(System.in);

        while (!login) {
            // 如果已经超过10分钟，退出程序
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= TIMEOUT) {
                log.error("超过10分钟未完成登录，程序退出...");
                System.exit(1);
            }

            try {
                // 尝试点击二维码按钮并等待页面出现已登录的元素
                app.click();
                WAIT.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//*[@id=\"header\"]/div[1]/div[1]/a")));
                WAIT.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//*[@id=\"wrap\"]/div[2]/div[1]/div/div[1]/a[2]")));

                // 如果上述元素都能找到，说明登录成功
                login = true;
                log.info("登录成功！保存cookie...");
            } catch (Exception e) {
                // 登录失败
                log.error("登录失败，等待用户操作或者 2 秒后重试...");

                // 每次登录失败后，等待2秒，同时检查用户是否按了回车
                boolean userInput = waitForUserInputOrTimeout(scanner);
                if (userInput) {
                    log.info("检测到用户输入，继续尝试登录...");
                }
            }
        }

        // 登录成功后，保存Cookie
        SeleniumUtil.saveCookie(cookiePath);
    }

    /**
     * 在指定的毫秒数内等待用户输入回车；若在等待时间内用户按回车则返回 true，否则返回 false。
     *
     * @param scanner 用于读取控制台输入
     * @return 用户是否在指定时间内按回车
     */
    private static boolean waitForUserInputOrTimeout(Scanner scanner) {
        long end = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < end) {
            try {
                // 判断输入流中是否有可用字节
                if (System.in.available() > 0) {
                    // 读取一行（用户输入）
                    scanner.nextLine();
                    return true;
                }
            } catch (IOException e) {
                // 读取输入流异常，直接忽略
            }

            // 小睡一下，避免 CPU 空转
            SeleniumUtil.sleep(1);
        }
        return false;
    }


}

