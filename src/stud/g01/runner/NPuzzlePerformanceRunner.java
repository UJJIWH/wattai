package stud.g01.runner;

import core.problem.Problem;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 修复的NPuzzle性能测试运行器
 */
public class NPuzzlePerformanceRunner {

    private final PuzzleFeeder feeder;
    private final PerformanceMetrics globalMetrics;
    private final Map<String, TestResult> testResults;

    // 改为非final变量
    private boolean enableDetailedLogging;
    private int warmupRounds;

    // 性能监控
    private long totalTestTime;
    private int totalTestsRun;
    private int successfulTests;

    public NPuzzlePerformanceRunner(PuzzleFeeder feeder) {
        this.feeder = feeder;
        this.globalMetrics = new PerformanceMetrics();
        this.testResults = new ConcurrentHashMap<>();

        // 改为直接赋值，非final
        this.enableDetailedLogging = true;
        this.warmupRounds = 2;

        this.totalTestTime = 0;
        this.totalTestsRun = 0;
        this.successfulTests = 0;

        System.out.println("初始化NPuzzle性能测试运行器");
        System.out.println("详细日志: " + (enableDetailedLogging ? "启用" : "禁用"));
        System.out.println("预热轮数: " + warmupRounds);
    }

    /**
     * 运行阶段性能测试
     */
    public void runStageTest(int stage) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("=== NPuzzle 性能测试 - 阶段 " + stage + " ===");
        System.out.println("=".repeat(60));

        long stageStartTime = System.currentTimeMillis();

        // 获取测试问题
        List<Problem> testProblems = getTestProblems(stage);
        // 获取推荐的算法配置
        List<PuzzleFeeder.AlgorithmConfig> configs = feeder.getRecommendedConfigs(stage);

        System.out.printf("测试配置: %d 个问题, %d 种算法配置\n",
                testProblems.size(), configs.size());

        // 预热阶段（可选）
        if (warmupRounds > 0) {
            runWarmupPhase(testProblems, configs);
        }

        // 运行所有配置的测试
        AtomicInteger configIndex = new AtomicInteger(1);
        for (PuzzleFeeder.AlgorithmConfig config : configs) {
            System.out.printf("\n--- 测试配置 %d/%d: %s ---\n",
                    configIndex.getAndIncrement(), configs.size(), config);
            testConfiguration(config, testProblems, stage);
        }

        long stageEndTime = System.currentTimeMillis();
        double stageDuration = (stageEndTime - stageStartTime) / 1000.0;

        System.out.println("\n" + "=".repeat(60));
        System.out.printf("=== 阶段 %d 测试完成 - 总耗时: %.2f 秒 ===\n", stage, stageDuration);
        System.out.println("=".repeat(60));

        // 生成阶段报告
        generateStageReport(stage);
    }

    /**
     * 预热阶段 - 提前加载资源和初始化
     */
    private void runWarmupPhase(List<Problem> testProblems, List<PuzzleFeeder.AlgorithmConfig> configs) {
        System.out.println("\n执行预热阶段...");

        for (int round = 1; round <= warmupRounds; round++) {
            System.out.printf("预热轮次 %d/%d\n", round, warmupRounds);

            for (PuzzleFeeder.AlgorithmConfig config : configs.subList(0, Math.min(2, configs.size()))) {
                Problem sampleProblem = testProblems.get(0);
                if (sampleProblem.solvable()) {
                    // 运行测试但不记录结果
                    Map<String, Object> results = feeder.runAlgorithmTest(
                            sampleProblem, config.algorithm, config.heuristic);

                    if (enableDetailedLogging) {
                        Boolean success = (Boolean) results.get("success");
                        long timeMs = (Long) results.get("timeMs");
                        System.out.printf("  预热 %s: %s (%.2fs)\n",
                                config, success ? "成功" : "失败", timeMs / 1000.0);
                    }
                }
            }
        }

        System.out.println("预热阶段完成");
    }

    private List<Problem> getTestProblems(int stage) {
        List<Problem> problems = new ArrayList<>();

        switch (stage) {
            case 1:
                // 第一阶段：3x3标准问题
                problems.add(feeder.getNPuzzleProblem(3, 1));
                problems.add(feeder.getNPuzzleProblem(3, 2));
                problems.add(feeder.getNPuzzleProblem(3, 3));
                break;
            case 2:
                // 第二阶段：4x4标准问题
                problems.add(feeder.getNPuzzleProblem(4, 1));
                problems.add(feeder.getNPuzzleProblem(4, 2));
                problems.add(feeder.getNPuzzleProblem(4, 3));
                break;
            case 3:
                // 第三阶段：复杂4x4问题
                problems.add(feeder.getNPuzzleProblem(4, 2));
                problems.add(feeder.getNPuzzleProblem(4, 3));
                break;
        }

        // 验证问题可解性
        List<Problem> solvableProblems = new ArrayList<>();
        for (Problem problem : problems) {
            if (problem.solvable()) {
                solvableProblems.add(problem);
            } else {
                System.out.println("跳过无解问题");
            }
        }

        System.out.printf("阶段 %d: %d/%d 个问题有解\n",
                stage, solvableProblems.size(), problems.size());

        return solvableProblems;
    }

    private void testConfiguration(PuzzleFeeder.AlgorithmConfig config,
                                   List<Problem> problems, int stage) {
        int problemIndex = 1;

        for (Problem problem : problems) {
            if (enableDetailedLogging) {
                System.out.printf("问题 %d/%d: ", problemIndex, problems.size());
            }

            // 显示问题信息
            if (problem instanceof NPuzzleProblem) {
                NPuzzleProblem npuzzle = (NPuzzleProblem) problem;
                if (npuzzle.root().getState() instanceof PuzzleBoard) {
                    PuzzleBoard initialState = (PuzzleBoard) npuzzle.root().getState();
                    if (enableDetailedLogging) {
                        System.out.printf("尺寸: %dx%d, ", initialState.getSize(), initialState.getSize());
                    }
                }
            }

            if (!problem.solvable()) {
                if (enableDetailedLogging) {
                    System.out.println("无解，跳过");
                }
                problemIndex++;
                continue;
            }

            // 运行算法测试
            long testStartTime = System.currentTimeMillis();
            Map<String, Object> results = feeder.runAlgorithmTest(
                    problem, config.algorithm, config.heuristic);
            long testEndTime = System.currentTimeMillis();

            totalTestTime += (testEndTime - testStartTime);
            totalTestsRun++;

            // 记录结果
            TestResult testResult = recordTestResult(config, problem, results,
                    testEndTime - testStartTime, stage);

            // 输出结果
            printTestResults(results, stage, testResult);

            problemIndex++;
        }

        // 配置级别的统计
        printConfigurationSummary(config, problems.size());
    }

    /**
     * 记录测试结果
     */
    private TestResult recordTestResult(PuzzleFeeder.AlgorithmConfig config, Problem problem,
                                        Map<String, Object> results, long testTime, int stage) {
        String testKey = config.algorithm + "_" + config.heuristic + "_stage" + stage;

        boolean success = (Boolean) results.get("success");
        long timeMs = (Long) results.get("timeMs");
        int pathLength = (Integer) results.get("pathLength");
        int nodesGenerated = (Integer) results.get("nodesGenerated");
        int nodesExpanded = (Integer) results.get("nodesExpanded");

        TestResult testResult = testResults.computeIfAbsent(testKey,
                k -> new TestResult(config.algorithm, config.heuristic, stage));

        testResult.addTestResult(success, timeMs, pathLength, nodesGenerated, nodesExpanded);

        if (success) {
            successfulTests++;
        }

        // 更新全局指标
        globalMetrics.recordTest(success, timeMs, pathLength, nodesGenerated, nodesExpanded);

        return testResult;
    }

    private void printTestResults(Map<String, Object> results, int stage, TestResult testResult) {
        boolean success = (Boolean) results.get("success");
        long timeMs = (Long) results.get("timeMs");
        int pathLength = (Integer) results.get("pathLength");
        int nodesGenerated = (Integer) results.get("nodesGenerated");
        int nodesExpanded = (Integer) results.get("nodesExpanded");

        if (success) {
            System.out.printf("成功 - 解长度: %d, 时间: %.3fs, 生成节点: %,d, 扩展节点: %,d",
                    pathLength, timeMs / 1000.0, nodesGenerated, nodesExpanded);

            // 检查时限要求
            checkStageRequirement(stage, timeMs / 1000.0, pathLength, testResult);
        } else {
            System.out.printf("失败 - 时间: %.3fs, 生成节点: %,d, 扩展节点: %,d",
                    timeMs / 1000.0, nodesGenerated, nodesExpanded);
        }

        System.out.println();
    }

    private void checkStageRequirement(int stage, double time, int pathLength, TestResult testResult) {
        boolean timePass = false;
        String requirement = "";

        switch (stage) {
            case 1:
                timePass = time <= 1.0;
                requirement = "1秒";
                break;
            case 2:
                timePass = time <= 5.0;
                requirement = "5秒";
                break;
            case 3:
                timePass = time <= 60.0;
                requirement = "60秒";
                break;
        }

        if (timePass) {
            System.out.printf("  ? 满足阶段 %d 时限要求 (%s)", stage, requirement);
            testResult.incrementRequirementsMet();
        } else {
            System.out.printf("  ? 不满足阶段 %d 时限要求 (%s)", stage, requirement);
        }
    }

    /**
     * 打印配置级别的统计摘要
     */
    private void printConfigurationSummary(PuzzleFeeder.AlgorithmConfig config, int totalProblems) {
        String testKey = config.algorithm + "_" + config.heuristic;
        TestResult result = testResults.get(testKey);

        if (result != null && result.getTotalTests() > 0) {
            System.out.printf("配置统计: 成功率 %.1f%%, 平均时间 %.2fs, 平均解长度 %.1f\n",
                    result.getSuccessRate() * 100,
                    result.getAverageTime() / 1000.0,
                    result.getAveragePathLength());
        }
    }

    /**
     * 批量性能比较测试
     */
    public void runComparativeTest(Problem problem) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("=== 算法比较测试 ===");
        System.out.println("=".repeat(60));

        List<PuzzleFeeder.AlgorithmConfig> allConfigs = new ArrayList<>();
        for (int stage = 1; stage <= 3; stage++) {
            allConfigs.addAll(feeder.getRecommendedConfigs(stage));
        }

        // 去重
        Map<String, PuzzleFeeder.AlgorithmConfig> uniqueConfigs = new LinkedHashMap<>();
        for (PuzzleFeeder.AlgorithmConfig config : allConfigs) {
            String key = config.algorithm + "_" + config.heuristic;
            uniqueConfigs.putIfAbsent(key, config);
        }

        System.out.println("测试问题:");
        if (problem.root().getState() instanceof PuzzleBoard) {
            PuzzleBoard initialState = (PuzzleBoard) problem.root().getState();
            System.out.println("尺寸: " + initialState.getSize() + "x" + initialState.getSize());
        }

        if (enableDetailedLogging) {
            problem.root().getState().draw();
        }

        if (!problem.solvable()) {
            System.out.println("问题无解，跳过比较测试");
            return;
        }

        System.out.printf("比较 %d 种算法配置:\n", uniqueConfigs.size());

        // 运行所有配置并收集结果
        List<ComparativeResult> comparativeResults = new ArrayList<>();

        for (PuzzleFeeder.AlgorithmConfig config : uniqueConfigs.values()) {
            System.out.printf("\n测试: %-40s", config);

            long startTime = System.currentTimeMillis();
            Map<String, Object> results = feeder.runAlgorithmTest(problem, config.algorithm, config.heuristic);
            long endTime = System.currentTimeMillis();

            ComparativeResult compResult = createComparativeResult(config, results, endTime - startTime);
            comparativeResults.add(compResult);

            printComparativeResults(compResult);
        }

        // 生成比较报告
        generateComparativeReport(comparativeResults, problem);
    }

    private ComparativeResult createComparativeResult(PuzzleFeeder.AlgorithmConfig config,
                                                      Map<String, Object> results, long testTime) {
        boolean success = (Boolean) results.get("success");
        long timeMs = (Long) results.get("timeMs");
        int pathLength = (Integer) results.get("pathLength");
        int nodesGenerated = (Integer) results.get("nodesGenerated");
        int nodesExpanded = (Integer) results.get("nodesExpanded");

        return new ComparativeResult(config, success, timeMs, pathLength,
                nodesGenerated, nodesExpanded, testTime);
    }

    private void printComparativeResults(ComparativeResult result) {
        if (result.success) {
            System.out.printf("? 长度=%d, 时间=%.3fs, 生成=%,d, 扩展=%,d, 效率=%.1f\n",
                    result.pathLength, result.timeMs / 1000.0,
                    result.nodesGenerated, result.nodesExpanded,
                    result.getEfficiencyScore());
        } else {
            System.out.printf("? 未找到解 (%.3fs)\n", result.timeMs / 1000.0);
        }
    }

    /**
     * 运行特定问题的详细测试
     */
    public void runDetailedTest(Problem problem, String algorithm, HeuristicType heuristic) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("=== 详细性能测试 ===");
        System.out.printf("算法: %s, 启发式: %s\n", algorithm, heuristic);
        System.out.println("=".repeat(60));

        // 显示问题信息
        if (problem.root().getState() instanceof PuzzleBoard) {
            PuzzleBoard initialState = (PuzzleBoard) problem.root().getState();
            System.out.println("问题尺寸: " + initialState.getSize() + "x" + initialState.getSize());
        }

        System.out.println("初始状态:");
        problem.root().getState().draw();

        if (!problem.solvable()) {
            System.out.println("问题无解");
            return;
        }

        // 运行测试
        long startTime = System.currentTimeMillis();
        Map<String, Object> results = feeder.runAlgorithmTest(problem, algorithm, heuristic);
        long endTime = System.currentTimeMillis();

        long overheadTime = endTime - startTime - (Long) results.get("timeMs");

        // 详细输出
        boolean success = (Boolean) results.get("success");
        long timeMs = (Long) results.get("timeMs");
        int pathLength = (Integer) results.get("pathLength");
        int nodesGenerated = (Integer) results.get("nodesGenerated");
        int nodesExpanded = (Integer) results.get("nodesExpanded");
        AbstractSearcher searcher = (AbstractSearcher) results.get("searcher");

        System.out.println("\n" + "-".repeat(40));
        System.out.println("=== 详细测试结果 ===");

        if (success) {
            System.out.printf("? 成功找到解\n");
            System.out.printf("解路径长度: %d 步\n", pathLength);
            System.out.printf("执行时间: %.3f 秒 (开销: %.3f 秒)\n",
                    timeMs / 1000.0, overheadTime / 1000.0);
            System.out.printf("生成节点数: %,d\n", nodesGenerated);
            System.out.printf("扩展节点数: %,d\n", nodesExpanded);
            System.out.printf("节点生成速率: %.1f 节点/秒\n",
                    nodesGenerated / (timeMs / 1000.0));
            System.out.printf("节点扩展速率: %.1f 节点/秒\n",
                    nodesExpanded / (timeMs / 1000.0));
            System.out.printf("分支因子: %.2f\n",
                    (double) nodesGenerated / nodesExpanded);

            // 内存使用估算
            long estimatedMemory = nodesGenerated * 100L;
            System.out.printf("估算内存使用: %.1f MB\n", estimatedMemory / (1024.0 * 1024.0));

            // 显示解路径（如果不太长）
            if (pathLength <= 25) {
                System.out.println("\n解路径:");
                Deque<Node> path = searcher.search(problem);
                if (path != null) {
                    problem.showSolution(path);
                }
            } else {
                System.out.printf("\n解路径太长 (%d 步)，跳过显示\n", pathLength);
            }

            // 性能分析
            analyzePerformance(nodesGenerated, nodesExpanded, timeMs, pathLength);

        } else {
            System.out.println("? 未找到解");
            System.out.printf("搜索时间: %.3f 秒\n", timeMs / 1000.0);
            System.out.printf("生成节点: %,d\n", nodesGenerated);
            System.out.printf("扩展节点: %,d\n", nodesExpanded);
        }

        System.out.println("-".repeat(40));
    }

    /**
     * 性能分析
     */
    private void analyzePerformance(int nodesGenerated, int nodesExpanded, long timeMs, int pathLength) {
        System.out.println("\n性能分析:");

        double timeSeconds = timeMs / 1000.0;
        double nodesPerSecond = nodesExpanded / timeSeconds;

        System.out.printf("节点扩展速率: %.1f 节点/秒\n", nodesPerSecond);

        if (nodesPerSecond < 1000) {
            System.out.println("??  扩展速率较低，可能启发式函数不够有效");
        } else if (nodesPerSecond > 10000) {
            System.out.println("? 扩展速率良好");
        }

        double efficiency = (double) pathLength / nodesExpanded;
        System.out.printf("搜索效率: %.6f (解长度/扩展节点)\n", efficiency);

        if (efficiency < 0.001) {
            System.out.println("??  搜索效率较低，可能启发式函数不够准确");
        }
    }

    /**
     * 生成阶段报告
     */
    private void generateStageReport(int stage) {
        System.out.println("\n" + "=".repeat(60));
        System.out.printf("=== 阶段 %d 性能报告 ===\n", stage);
        System.out.println("=".repeat(60));

        System.out.printf("总测试数: %d\n", totalTestsRun);
        System.out.printf("成功测试: %d (%.1f%%)\n",
                successfulTests, (successfulTests * 100.0 / totalTestsRun));
        System.out.printf("总测试时间: %.2f 秒\n", totalTestTime / 1000.0);

        // 显示最佳配置
        printBestConfigurations(stage);

        // 全局统计
        globalMetrics.printSummary();
    }

    /**
     * 显示最佳配置
     */
    private void printBestConfigurations(int stage) {
        System.out.println("\n最佳配置排名:");

        List<TestResult> stageResults = new ArrayList<>();
        for (TestResult result : testResults.values()) {
            if (result.stage == stage && result.getTotalTests() > 0) {
                stageResults.add(result);
            }
        }

        // 按成功率排序
        stageResults.sort((r1, r2) -> Double.compare(r2.getSuccessRate(), r1.getSuccessRate()));

        for (int i = 0; i < Math.min(5, stageResults.size()); i++) {
            TestResult result = stageResults.get(i);
            System.out.printf("%d. %s - 成功率: %.1f%%, 平均时间: %.2fs\n",
                    i + 1, result.getConfigDescription(),
                    result.getSuccessRate() * 100,
                    result.getAverageTime() / 1000.0);
        }
    }

    /**
     * 生成比较报告
     */
    private void generateComparativeReport(List<ComparativeResult> results, Problem problem) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("=== 算法比较报告 ===");
        System.out.println("=".repeat(60));

        // 过滤成功的测试
        List<ComparativeResult> successfulResults = new ArrayList<>();
        for (ComparativeResult result : results) {
            if (result.success) {
                successfulResults.add(result);
            }
        }

        if (successfulResults.isEmpty()) {
            System.out.println("没有成功的测试结果");
            return;
        }

        // 按效率排序
        successfulResults.sort((r1, r2) -> Double.compare(r2.getEfficiencyScore(), r1.getEfficiencyScore()));

        System.out.println("效率排名 (综合考虑时间、节点数和解长度):");
        for (int i = 0; i < successfulResults.size(); i++) {
            ComparativeResult result = successfulResults.get(i);
            System.out.printf("%d. %-40s - 效率: %.2f, 时间: %.3fs, 节点: %,d\n",
                    i + 1, result.config.toString(),
                    result.getEfficiencyScore(),
                    result.timeMs / 1000.0,
                    result.nodesExpanded);
        }

        // 找出最佳配置
        ComparativeResult bestResult = successfulResults.get(0);
        System.out.printf("\n推荐配置: %s\n", bestResult.config.toString());
        System.out.printf("理由: 最高效率得分 (%.2f), 合理的时间 (%.3fs)\n",
                bestResult.getEfficiencyScore(), bestResult.timeMs / 1000.0);
    }

    /**
     * 设置是否启用详细日志
     */
    public void setEnableDetailedLogging(boolean enableDetailedLogging) {
        this.enableDetailedLogging = enableDetailedLogging;
        System.out.println("详细日志: " + (enableDetailedLogging ? "启用" : "禁用"));
    }

    /**
     * 设置预热轮数
     */
    public void setWarmupRounds(int warmupRounds) {
        this.warmupRounds = warmupRounds;
        System.out.println("预热轮数设置为: " + warmupRounds);
    }

    /**
     * 获取测试统计
     */
    public void printTestStatistics() {
        System.out.println("\n=== 测试运行器统计 ===");
        System.out.printf("总测试运行数: %,d\n", totalTestsRun);
        System.out.printf("成功测试数: %,d (%.1f%%)\n",
                successfulTests, (successfulTests * 100.0 / totalTestsRun));
        System.out.printf("总测试时间: %.2f 小时\n", totalTestTime / (1000.0 * 3600));
        System.out.printf("测试配置数: %,d\n", testResults.size());

        if (totalTestsRun > 0) {
            System.out.printf("平均测试时间: %.2f 秒\n", (totalTestTime / 1000.0) / totalTestsRun);
        }
    }

    /**
     * 清空测试结果
     */
    public void clearResults() {
        testResults.clear();
        totalTestTime = 0;
        totalTestsRun = 0;
        successfulTests = 0;
        System.out.println("测试结果已清空");
    }

    /**
     * 测试结果类
     */
    private static class TestResult {
        final String algorithm;
        final HeuristicType heuristic;
        final int stage;

        int totalTests;
        int successfulTests;
        int requirementsMet;
        long totalTimeMs;
        int totalPathLength;
        long totalNodesGenerated;
        long totalNodesExpanded;

        TestResult(String algorithm, HeuristicType heuristic, int stage) {
            this.algorithm = algorithm;
            this.heuristic = heuristic;
            this.stage = stage;
        }

        void addTestResult(boolean success, long timeMs, int pathLength,
                           int nodesGenerated, int nodesExpanded) {
            totalTests++;
            if (success) {
                successfulTests++;
            }
            totalTimeMs += timeMs;
            totalPathLength += pathLength;
            totalNodesGenerated += nodesGenerated;
            totalNodesExpanded += nodesExpanded;
        }

        void incrementRequirementsMet() {
            requirementsMet++;
        }

        double getSuccessRate() {
            return totalTests > 0 ? (double) successfulTests / totalTests : 0.0;
        }

        double getAverageTime() {
            return totalTests > 0 ? (double) totalTimeMs / totalTests : 0.0;
        }

        double getAveragePathLength() {
            return totalTests > 0 ? (double) totalPathLength / totalTests : 0.0;
        }

        int getTotalTests() {
            return totalTests;
        }

        String getConfigDescription() {
            return algorithm + " + " + heuristic;
        }
    }

    /**
     * 比较结果类
     */
    private static class ComparativeResult {
        final PuzzleFeeder.AlgorithmConfig config;
        final boolean success;
        final long timeMs;
        final int pathLength;
        final int nodesGenerated;
        final int nodesExpanded;
        final long overheadTime;

        ComparativeResult(PuzzleFeeder.AlgorithmConfig config, boolean success,
                          long timeMs, int pathLength, int nodesGenerated,
                          int nodesExpanded, long overheadTime) {
            this.config = config;
            this.success = success;
            this.timeMs = timeMs;
            this.pathLength = pathLength;
            this.nodesGenerated = nodesGenerated;
            this.nodesExpanded = nodesExpanded;
            this.overheadTime = overheadTime;
        }

        double getEfficiencyScore() {
            if (!success || timeMs == 0) return 0.0;

            double timeScore = 1.0 / (timeMs / 1000.0);
            double nodeEfficiency = (double) pathLength / nodesExpanded;
            double memoryEfficiency = 1.0 / (nodesGenerated / 1000.0);

            return timeScore * 0.5 + nodeEfficiency * 0.3 + memoryEfficiency * 0.2;
        }
    }

    /**
     * 性能指标类
     */
    private static class PerformanceMetrics {
        private int totalTests;
        private int successfulTests;
        private long totalTimeMs;
        private int totalPathLength;
        private long totalNodesGenerated;
        private long totalNodesExpanded;

        void recordTest(boolean success, long timeMs, int pathLength,
                        int nodesGenerated, int nodesExpanded) {
            totalTests++;
            if (success) {
                successfulTests++;
                totalTimeMs += timeMs;
                totalPathLength += pathLength;
                totalNodesGenerated += nodesGenerated;
                totalNodesExpanded += nodesExpanded;
            }
        }

        void printSummary() {
            if (successfulTests == 0) return;

            System.out.println("\n全局性能指标:");
            System.out.printf("平均解时间: %.3f 秒\n", (double) totalTimeMs / successfulTests / 1000.0);
            System.out.printf("平均解长度: %.1f 步\n", (double) totalPathLength / successfulTests);
            System.out.printf("平均生成节点: %,d\n", totalNodesGenerated / successfulTests);
            System.out.printf("平均扩展节点: %,d\n", totalNodesExpanded / successfulTests);
            System.out.printf("平均节点扩展速率: %.1f 节点/秒\n",
                    (double) totalNodesExpanded / (totalTimeMs / 1000.0));
        }
    }
}