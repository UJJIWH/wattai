package stud.g01.runner;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.queue.PqFrontier;
import stud.g01.solver.SimpleBidirectionalIdAStar;
import stud.g01.solver.heuristic.ManhattanPredictor;
import stud.g01.heuristic.MisplacedPredictor;
import stud.g01.solver.pattern.*;
import stud.g01.solver.IdAStar;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 修复的NPuzzle问题供给器
 */
public class PuzzleFeeder extends EngineFeeder {

    // 全局缓存
    private final Map<String, Problem> problemCache;
    private final Map<HeuristicType, Predictor> predictorCache;
    private final Map<String, AbstractSearcher> searcherCache;

    // 性能统计
    private final AtomicInteger problemRequests;
    private final AtomicInteger predictorRequests;
    private final AtomicInteger searcherRequests;
    private final long startupTime;

    // 配置 - 改为非final
    private boolean enableCaching;
    private boolean enablePreloading;
    private boolean enablePerformanceLogging;

    public PuzzleFeeder() {
        this.problemCache = new ConcurrentHashMap<>();
        this.predictorCache = new ConcurrentHashMap<>();
        this.searcherCache = new ConcurrentHashMap<>();

        this.problemRequests = new AtomicInteger(0);
        this.predictorRequests = new AtomicInteger(0);
        this.searcherRequests = new AtomicInteger(0);
        this.startupTime = System.currentTimeMillis();

        // 默认配置
        this.enableCaching = true;
        this.enablePreloading = true;
        this.enablePerformanceLogging = true;

        System.out.println("初始化优化的PuzzleFeeder");
        System.out.println("缓存: " + (enableCaching ? "启用" : "禁用"));
        System.out.println("预加载: " + (enablePreloading ? "启用" : "禁用"));
        System.out.println("性能日志: " + (enablePerformanceLogging ? "启用" : "禁用"));

        // 预加载常用资源
        if (enablePreloading) {
            preloadCommonResources();
        }
    }

    @Override
    public ArrayList<Problem> getProblems(ArrayList<String> problemLines) {
        ArrayList<Problem> problems = new ArrayList<>();

        if (problemLines != null && !problemLines.isEmpty()) {
            for (String line : problemLines) {
                Problem problem = parseProblem(line);
                if (problem != null) {
                    problems.add(problem);
                }
            }
        }

        if (problems.isEmpty()) {
            problems.addAll(getDefaultProblems());
        }

        if (enablePerformanceLogging) {
            System.out.printf("加载问题完成: %,d 个问题\n", problems.size());
        }

        return problems;
    }

    /**
     * 从字符串行解析问题
     */
    private Problem parseProblem(String line) {
        problemRequests.incrementAndGet();

        String cacheKey = "problem_" + line.hashCode();
        if (enableCaching && problemCache.containsKey(cacheKey)) {
            return problemCache.get(cacheKey);
        }

        try {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                return null;
            }

            String[] parts = line.split("\\s+");

            if (parts.length >= 3) {
                int size = Integer.parseInt(parts[0].trim());

                // 优化数组长度验证
                int expectedLength = 2 * size * size + 1;
                if (parts.length < expectedLength) {
                    System.err.println("错误: 数组长度不匹配，期望 " + expectedLength + " 但得到 " + parts.length);
                    return null;
                }

                // 批量处理状态字符串
                String[] stateStrings = new String[2];
                StringBuilder sb = new StringBuilder();
                for (int s = 0; s < 2; s++) {
                    sb.setLength(0);
                    for (int i = 0; i < size * size; i++) {
                        int index = 1 + s * size * size + i;
                        if (index < parts.length) {
                            sb.append(parts[index]).append(",");
                        }
                    }
                    stateStrings[s] = sb.toString();
                }

                int[] initialTiles = parseTiles(stateStrings[0]);
                int[] goalTiles = parseTiles(stateStrings[1]);

                if (initialTiles.length != size * size || goalTiles.length != size * size) {
                    System.err.println("错误: 瓷砖数组长度不匹配尺寸 " + size);
                    return null;
                }

                PuzzleBoard initialState = new PuzzleBoard(size, initialTiles);
                PuzzleBoard goalState = new PuzzleBoard(size, goalTiles);

                NPuzzleProblem problem = new NPuzzleProblem(initialState, goalState, size);

                if (enableCaching) {
                    problemCache.put(cacheKey, problem);
                }

                return problem;
            } else {
                System.err.println("错误: 无效的行格式: " + line);
            }
        } catch (Exception e) {
            System.err.println("解析问题行失败: " + line);
            if (enablePerformanceLogging) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 解析拼图块数组
     */
    private int[] parseTiles(String tileStr) {
        tileStr = tileStr.replaceAll("[\\[\\]\\s]", "");
        String[] tileStrings = tileStr.split(",");
        int[] tiles = new int[tileStrings.length];

        for (int i = 0; i < tileStrings.length; i++) {
            try {
                tiles[i] = Integer.parseInt(tileStrings[i].trim());
            } catch (NumberFormatException e) {
                System.err.println("解析瓷砖失败: '" + tileStrings[i] + "'");
                tiles[i] = 0;
            }
        }
        return tiles;
    }

    /**
     * 获取默认问题集
     */
    private List<Problem> getDefaultProblems() {
        List<Problem> problems = new ArrayList<>();

        problems.add(create3x3Problem1());
        problems.add(create3x3Problem2());
        problems.add(create3x3Problem3());
        problems.add(create4x4Problem1());
        problems.add(create4x4Problem2());
        problems.add(createStage3Problem());

        if (enablePerformanceLogging) {
            System.out.println("使用默认问题集: " + problems.size() + " 个问题");
        }

        return problems;
    }

    // 3x3 拼图问题实例
    private Problem create3x3Problem1() {
        int[] initial = {1, 2, 3, 4, 0, 5, 7, 8, 6};
        int[] goal = {1, 2, 3, 4, 5, 6, 7, 8, 0};
        return createCachedProblem("3x3_1", initial, goal, 3);
    }

    private Problem create3x3Problem2() {
        int[] initial = {1, 2, 3, 4, 5, 6, 0, 7, 8};
        int[] goal = {1, 2, 3, 4, 5, 6, 7, 8, 0};
        return createCachedProblem("3x3_2", initial, goal, 3);
    }

    private Problem create3x3Problem3() {
        int[] initial = {0, 1, 3, 4, 2, 5, 7, 8, 6};
        int[] goal = {1, 2, 3, 4, 5, 6, 7, 8, 0};
        return createCachedProblem("3x3_3", initial, goal, 3);
    }

    // 4x4 拼图问题实例
    private Problem create4x4Problem1() {
        int[] initial = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 0, 15};
        int[] goal = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
        return createCachedProblem("4x4_1", initial, goal, 4);
    }

    private Problem create4x4Problem2() {
        int[] initial = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, 12, 13, 14, 11, 15};
        int[] goal = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
        return createCachedProblem("4x4_2", initial, goal, 4);
    }

    private Problem createStage3Problem() {
        int[] initial = {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 1, 2, 0};
        int[] goal = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
        return createCachedProblem("4x4_stage3", initial, goal, 4);
    }

    /**
     * 创建缓存的问题
     */
    private Problem createCachedProblem(String key, int[] initial, int[] goal, int size) {
        if (enableCaching && problemCache.containsKey(key)) {
            return problemCache.get(key);
        }

        PuzzleBoard initialState = new PuzzleBoard(size, initial);
        PuzzleBoard goalState = new PuzzleBoard(size, goal);
        NPuzzleProblem problem = new NPuzzleProblem(initialState, goalState, size);

        if (enableCaching) {
            problemCache.put(key, problem);
        }

        return problem;
    }

    @Override
    public Frontier getFrontier(EvaluationType type) {
        return new PqFrontier(core.solver.queue.Node.evaluator(type));
    }

    /**
     * 获取预测器方法
     */
    @Override
    public Predictor getPredictor(HeuristicType type) {
        predictorRequests.incrementAndGet();

        if (enableCaching && predictorCache.containsKey(type)) {
            return predictorCache.get(type);
        }

        Predictor predictor;
        long startTime = System.currentTimeMillis();

        try {
            switch (type) {
                case MANHATTAN:
                    predictor = new ManhattanPredictor();
                    break;
                case MISPLACED:
                    predictor = new MisplacedPredictor();
                    break;
                case PATTERN_78:
                    predictor = createPatternPredictor(type, "7-8", 4);
                    break;
                case PATTERN_663:
                    predictor = createPatternPredictor(type, "6-6-3", 4);
                    break;
                default:
                    System.out.println("未知启发式类型: " + type + "，使用曼哈顿距离替代");
                    predictor = new ManhattanPredictor();
            }

            if (enableCaching) {
                predictorCache.put(type, predictor);
            }

            long endTime = System.currentTimeMillis();
            if (enablePerformanceLogging) {
                System.out.printf("创建预测器 %s 耗时: %dms\n", type, endTime - startTime);
            }

            return predictor;

        } catch (Exception e) {
            System.out.println("创建预测器失败: " + type + ", 错误: " + e.getMessage());
            return new ManhattanPredictor();
        }
    }

    /**
     * 创建模式数据库预测器
     */
    private Predictor createPatternPredictor(HeuristicType type, String patternName, int size) {
        try {
            System.out.println("创建模式数据库预测器: " + patternName);

            PatternDatabase patternDatabase = PatternDatabaseBuilder.createByName(patternName, size);

            stud.g01.heuristic.PatternDatabasePredictor predictor =
                    new stud.g01.heuristic.PatternDatabasePredictor(patternDatabase);

            predictor.setEnableCache(true);
            predictor.setEnableStatistics(enablePerformanceLogging);
            predictor.setMaxCacheSize(50000);

            return predictor;

        } catch (Exception e) {
            System.out.println("创建模式数据库预测器失败: " + e.getMessage());
            System.out.println("回退到曼哈顿距离");
            return new ManhattanPredictor();
        }
    }

    /**
     * 获取搜索器方法 - 不重写父类final方法，创建新方法
     */
    public AbstractSearcher getCustomSearcher(String algorithmType, HeuristicType heuristicType) {
        searcherRequests.incrementAndGet();

        String cacheKey = algorithmType + "_" + heuristicType;
        if (enableCaching && searcherCache.containsKey(cacheKey)) {
            return searcherCache.get(cacheKey);
        }

        AbstractSearcher searcher;
        long startTime = System.currentTimeMillis();

        try {
            switch (algorithmType.toUpperCase()) {
                case "ASTAR":
                    searcher = getAStar(heuristicType);
                    break;
                case "IDASTAR":
                    searcher = getIdaStar(heuristicType);
                    break;
                case "BIDIRECTIONAL_IDASTAR":
                    searcher = getBidirectionalIdAStar(heuristicType);
                    break;
                case "OPTIMIZED_ASTAR":
                    searcher = getOptimizedAStar();
                    break;
                case "EMPTY_DISTANCE_ASTAR":
                    searcher = getEmptyDistanceAStar();
                    break;
                case "DIJKSTRA":
                    searcher = getDijkstra();
                    break;
                default:
                    System.out.println("未知算法类型: " + algorithmType + "，使用默认A*");
                    searcher = getAStar(heuristicType);
            }

            if (enableCaching) {
                searcherCache.put(cacheKey, searcher);
            }

            long endTime = System.currentTimeMillis();
            if (enablePerformanceLogging) {
                System.out.printf("创建搜索器 %s 耗时: %dms\n", cacheKey, endTime - startTime);
            }

            return searcher;

        } catch (Exception e) {
            System.out.println("创建搜索器失败: " + algorithmType + ", 错误: " + e.getMessage());
            return getAStar(heuristicType);
        }
    }

    /**
     * 重写父类的空位距离A*方法
     */
    @Override
    public AbstractSearcher getEmptyDistanceAStar() {
        Predictor predictor = new stud.g01.heuristic.EmptyDistancePredictor();
        Frontier frontier = getFrontier(EvaluationType.FULL);
        return new BestFirstSearcher(frontier, predictor);
    }

    /**
     * 双向IDA*
     */
    public AbstractSearcher getBidirectionalIdAStar(HeuristicType type) {
        Predictor predictor = getPredictor(type);
        Frontier frontier = getFrontier(EvaluationType.FULL);
        return new SimpleBidirectionalIdAStar(frontier, predictor);
    }

    /**
     * 获取指定难度和规模的NPuzzle问题
     */
    public Problem getNPuzzleProblem(int size, int difficulty) {
        String key = "custom_" + size + "_" + difficulty;
        if (enableCaching && problemCache.containsKey(key)) {
            return problemCache.get(key);
        }

        Problem problem;
        if (size == 3) {
            switch (difficulty) {
                case 1: problem = create3x3Problem1(); break;
                case 2: problem = create3x3Problem2(); break;
                case 3: problem = create3x3Problem3(); break;
                default: problem = create3x3Problem1();
            }
        } else if (size == 4) {
            switch (difficulty) {
                case 1: problem = create4x4Problem1(); break;
                case 2: problem = create4x4Problem2(); break;
                case 3: problem = createStage3Problem(); break;
                default: problem = create4x4Problem1();
            }
        } else {
            throw new IllegalArgumentException("不支持的拼图尺寸: " + size);
        }

        if (enableCaching) {
            problemCache.put(key, problem);
        }

        return problem;
    }

    /**
     * 根据阶段获取推荐的算法配置
     */
    public List<AlgorithmConfig> getRecommendedConfigs(int stage) {
        List<AlgorithmConfig> configs = new ArrayList<>();

        switch (stage) {
            case 1:
                configs.add(new AlgorithmConfig("ASTAR", HeuristicType.MISPLACED));
                configs.add(new AlgorithmConfig("ASTAR", HeuristicType.MANHATTAN));
                configs.add(new AlgorithmConfig("IDASTAR", HeuristicType.MANHATTAN));
                break;

            case 2:
                configs.add(new AlgorithmConfig("ASTAR", HeuristicType.MANHATTAN));
                configs.add(new AlgorithmConfig("IDASTAR", HeuristicType.MANHATTAN));
                configs.add(new AlgorithmConfig("OPTIMIZED_ASTAR", HeuristicType.MANHATTAN));
                break;

            case 3:
                configs.add(new AlgorithmConfig("ASTAR", HeuristicType.PATTERN_78));
                configs.add(new AlgorithmConfig("ASTAR", HeuristicType.PATTERN_663));
                configs.add(new AlgorithmConfig("IDASTAR", HeuristicType.PATTERN_78));
                configs.add(new AlgorithmConfig("ASTAR", HeuristicType.MANHATTAN));
                break;
        }

        if (enablePerformanceLogging) {
            System.out.printf("阶段 %d 推荐配置: %d 种\n", stage, configs.size());
        }

        return configs;
    }

    /**
     * 算法配置类
     */
    public static class AlgorithmConfig {
        public final String algorithm;
        public final HeuristicType heuristic;

        public AlgorithmConfig(String algorithm, HeuristicType heuristic) {
            this.algorithm = algorithm;
            this.heuristic = heuristic;
        }

        @Override
        public String toString() {
            return algorithm + " with " + heuristic;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            AlgorithmConfig that = (AlgorithmConfig) obj;
            return algorithm.equals(that.algorithm) && heuristic == that.heuristic;
        }

        @Override
        public int hashCode() {
            return Objects.hash(algorithm, heuristic);
        }
    }

    /**
     * 预加载常用资源
     */
    private void preloadCommonResources() {
        System.out.println("预加载常用资源...");

        Thread preloadThread = new Thread(() -> {
            try {
                getPredictor(HeuristicType.MANHATTAN);
                getPredictor(HeuristicType.MISPLACED);

                getCustomSearcher("ASTAR", HeuristicType.MANHATTAN);
                getCustomSearcher("IDASTAR", HeuristicType.MANHATTAN);

                preloadPatternDatabases();

                System.out.println("资源预加载完成");
            } catch (Exception e) {
                System.out.println("预加载失败: " + e.getMessage());
            }
        });

        preloadThread.setDaemon(true);
        preloadThread.start();
    }

    /**
     * 预加载模式数据库
     */
    private void preloadPatternDatabases() {
        System.out.println("预加载模式数据库...");

        Thread patternPreloadThread = new Thread(() -> {
            try {
                getPredictor(HeuristicType.PATTERN_78);

                Thread.sleep(1000);
                getPredictor(HeuristicType.PATTERN_663);

                System.out.println("模式数据库预加载完成");
            } catch (Exception e) {
                System.out.println("模式数据库预加载失败: " + e.getMessage());
            }
        });

        patternPreloadThread.setDaemon(true);
        patternPreloadThread.start();
    }

    /**
     * 运行模式数据库构建性能测试
     */
    public void runPatternDatabasePerformanceTest() {
        System.out.println("=== 模式数据库构建性能测试 ===");

        System.out.println("\n--- 测试7-8分区 ---");
        int[] pattern78 = {1, 2, 3, 4, 5, 6, 7};
        compareBuildMethods(pattern78, 4);

        System.out.println("\n--- 测试6-6-3分区(第一组) ---");
        int[] pattern663_1 = {1, 2, 3, 4, 5, 6};
        compareBuildMethods(pattern663_1, 4);
    }

    /**
     * 比较构建方法
     */
    private void compareBuildMethods(int[] patternTiles, int size) {
        System.out.println("模式: " + java.util.Arrays.toString(patternTiles));
        System.out.println("尺寸: " + size + "x" + size);

        // 测试普通BFS
        System.out.println("\n--- 普通BFS构建 ---");
        EfficientPatternDatabase dbBFS = new EfficientPatternDatabase(patternTiles, size);
        long startTime = System.currentTimeMillis();
        dbBFS.precompute();
        long bfsTime = System.currentTimeMillis() - startTime;
        int bfsSize = dbBFS.getSize();

        // 测试双向BFS
        System.out.println("\n--- 双向BFS构建 ---");
        EfficientPatternDatabase dbBiBFS = new EfficientPatternDatabase(patternTiles, size);
        startTime = System.currentTimeMillis();
        dbBiBFS.precomputeWithBiBFS();
        long bibfsTime = System.currentTimeMillis() - startTime;
        int bibfsSize = dbBiBFS.getSize();

        System.out.println("\n=== 性能比较结果 ===");
        System.out.printf("普通BFS - 时间: %dms, 状态数: %d\n", bfsTime, bfsSize);
        System.out.printf("双向BFS - 时间: %dms, 状态数: %d\n", bibfsTime, bibfsSize);

        if (bibfsTime > 0) {
            double speedup = (double) bfsTime / bibfsTime;
            System.out.printf("速度比: %.2f (%.2fx %s)\n",
                    speedup, speedup, speedup > 1 ? "更快" : "更慢");
        }

        System.out.println("\n=== 结果验证 ===");
        System.out.println("状态数差异: " + Math.abs(bfsSize - bibfsSize));
        if (bfsSize == bibfsSize) {
            System.out.println("? 两种方法构建的数据库大小相同");
        } else {
            System.out.println("? 两种方法构建的数据库大小不同");
        }
    }

    /**
     * 设置是否启用缓存
     */
    public void setEnableCaching(boolean enableCaching) {
        this.enableCaching = enableCaching;
        System.out.println("缓存: " + (enableCaching ? "启用" : "禁用"));

        if (!enableCaching) {
            clearCaches();
        }
    }

    /**
     * 设置是否启用预加载
     */
    public void setEnablePreloading(boolean enablePreloading) {
        this.enablePreloading = enablePreloading;
        System.out.println("预加载: " + (enablePreloading ? "启用" : "禁用"));
    }

    /**
     * 设置是否启用性能日志
     */
    public void setEnablePerformanceLogging(boolean enablePerformanceLogging) {
        this.enablePerformanceLogging = enablePerformanceLogging;
        System.out.println("性能日志: " + (enablePerformanceLogging ? "启用" : "禁用"));
    }

    /**
     * 清空所有缓存
     */
    public void clearCaches() {
        problemCache.clear();
        predictorCache.clear();
        searcherCache.clear();
        System.out.println("所有缓存已清空");
    }

    /**
     * 获取性能统计
     */
    public void printPerformanceStatistics() {
        long uptime = System.currentTimeMillis() - startupTime;

        System.out.println("\n=== PuzzleFeeder 性能统计 ===");
        System.out.printf("运行时间: %.2f 分钟\n", uptime / 60000.0);
        System.out.printf("问题请求: %,d\n", problemRequests.get());
        System.out.printf("预测器请求: %,d\n", predictorRequests.get());
        System.out.printf("搜索器请求: %,d\n", searcherRequests.get());
        System.out.printf("缓存问题数: %,d\n", problemCache.size());
        System.out.printf("缓存预测器数: %,d\n", predictorCache.size());
        System.out.printf("缓存搜索器数: %,d\n", searcherCache.size());

        double estimatedHitRate = (problemCache.size() + predictorCache.size() + searcherCache.size()) * 100.0
                / (problemRequests.get() + predictorRequests.get() + searcherRequests.get());
        System.out.printf("估算缓存命中率: %.1f%%\n", Math.min(estimatedHitRate, 100));
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        clearCaches();
        System.out.println("PuzzleFeeder 资源清理完成");
    }
}