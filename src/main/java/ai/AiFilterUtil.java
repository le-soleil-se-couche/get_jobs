public static boolean shouldSayHi(String platform, String jobTitle, String company, String jd) {
    try {
        List<Double> resumeVec = EmbeddingUtil.getEmbedding(ResumeUtil.load());
        List<Double> jdVec = EmbeddingUtil.getEmbedding(jd);
        double matchScore = CosineSimilarity.calculate(resumeVec, jdVec);

        // ✅ 自动记录更多详细信息
        LogRecorder.recordMatch(platform, jobTitle, company, jd, matchScore);

        return matchScore >= 0.85;
    } catch (Exception e) {
        System.err.println("计算匹配度失败：" + e.getMessage());
        return true; // 默认投递，避免误杀
    }
}
