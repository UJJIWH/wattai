package stud.g01.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.solver.pattern.PatternDatabase;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 修复的模式数据库预测器
 */
public class PatternDatabasePredictor implements Predictor {
    private final PatternDatabase patternDatabase;

    // 启发式计算缓存
    private final Map<Long, Integer> heuristicCache;
    private int maxCacheSize; // 改为非final

    // 性能统计
    private final AtomicLong totalCalculations;
    private final AtomicLong cacheHits;
    private final AtomicLong totalCalculationTime;
    private final AtomicLong lastCalculationTime;

    // 配置
    private boolean enableCache;
    private boolean enableStatistics;
    private int cacheHitThreshold;

    public PatternDatabasePredictor(PatternDatabase patternDatabase) {
        this.patternDatabase = patternDatabase;

        // 缓存配置
        this.maxCacheSize = 100000;
        this.heuristicCache = new ConcurrentHashMap<>(maxCacheSize / 2);

        // 统计初始化
        this.totalCalculations = new AtomicLong(0);
        this.cacheHits = new AtomicLong(0);
        this.totalCalculationTime = new AtomicLong(0);
        this.lastCalculationTime = new AtomicLong(0);

        // 默认配置
        this.enableCache = true;
        this.enableStatistics = true;
        this.cacheHitThreshold = 1000;

        System.out.println("创建模式数据库预测器");
        System.out.println("模式数据库: " + patternDatabase.getClass().getSimpleName());
        System.out.println("缓存大小: " + maxCacheSize);
        System.out.println("统计监控: " + (enableStatistics ? "启用" : "禁用"));
    }

    @Override
    public int heuristics(State state, State goal) {
        if (!(state instanceof PuzzleBoard)) {
            throw new IllegalArgumentException("PatternDatabasePredictor only works for PuzzleBoard states");
        }

        long startTime = System.nanoTime();
        int heuristicValue = 0;

        try {
            PuzzleBoard board = (PuzzleBoard) state;

            // 生成缓存键
            long cacheKey = board.getCompressedState();

            // 尝试从缓存获取
            if (enableCache) {
                Integer cachedHeuristic = heuristicCache.get(cacheKey);
                if (cachedHeuristic != null) {
                    cacheHits.incrementAndGet();
                    heuristicValue = cachedHeuristic;

                    if (enableStatistics) {
                        updateStatistics(startTime, true);
                    }
                    return heuristicValue;
                }
            }

            // 缓存未命中，计算启发式值
            heuristicValue = patternDatabase.getHeuristic(state);

            // 存入缓存
            if (enableCache && heuristicCache.size() < maxCacheSize) {
                heuristicCache.put(cacheKey, heuristicValue);
            }

            if (enableStatistics) {
                updateStatistics(startTime, false);
            }

            return heuristicValue;

        } catch (Exception e) {
            System.out.println("模式数据库启发式计算失败: " + e.getMessage());
            // 回退到曼哈顿距离
            return calculateFallbackHeuristic((PuzzleBoard) state, goal);
        }
    }

    /**
     * 更新性能统计
     */
    private void updateStatistics(long startTime, boolean cacheHit) {
        long endTime = System.nanoTime();
        long calculationTime = endTime - startTime;

        totalCalculations.incrementAndGet();
        totalCalculationTime.addAndGet(calculationTime);
        lastCalculationTime.set(calculationTime);

        // 定期输出统计信息
        long calculations = totalCalculations.get();
        if (calculations % cacheHitThreshold == 0) {
            printStatistics();
        }
    }

    /**
     * 计算回退启发式（曼哈顿距离）
     */
    private int calculateFallbackHeuristic(PuzzleBoard board, State goal) {
        if (!(goal instanceof PuzzleBoard)) {
            return 0;
        }

        PuzzleBoard target = (PuzzleBoard) goal;
        int size = board.getSize();
        int distance = 0;

        int[] currentTiles = board.getPuzzleBoard();
        int[] goalTiles = target.getPuzzleBoard();

        for (int i = 0; i < currentTiles.length; i++) {
            int value = currentTiles[i];
            if (value != 0) {
                // 找到该值在目标状态中的位置
                int goalPos = findPosition(goalTiles, value);
                if (goalPos != -1) {
                    int currentRow = i / size;
                    int currentCol = i % size;
                    int goalRow = goalPos / size;
                    int goalCol = goalPos % size;
                    distance += Math.abs(currentRow - goalRow) + Math.abs(currentCol - goalCol);
                }
            }
        }

        return distance;
    }

    /**
     * 查找位置
     */
    private int findPosition(int[] tiles, int value) {
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == value) return i;
        }
        return -1;
    }

    /**
     * 获取模式数据库
     */
    public PatternDatabase getPatternDatabase() {
        return patternDatabase;
    }

    /**
     * 启用或禁用缓存
     */
    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
        if (!enableCache) {
            clearCache();
        }
        System.out.println("启发式缓存: " + (enableCache ? "启用" : "禁用"));
    }

    /**
     * 启用或禁用统计
     */
    public void setEnableStatistics(boolean enableStatistics) {
        this.enableStatistics = enableStatistics;
        System.out.println("统计监控: " + (enableStatistics ? "启用" : "禁用"));
    }

    /**
     * 设置缓存大小
     */
    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
        // 如果当前缓存超过新大小，清理部分缓存
        if (heuristicCache.size() > maxCacheSize) {
            heuristicCache.clear();
        }
        System.out.println("缓存大小设置为: " + maxCacheSize);
    }

    /**
     * 设置统计输出阈值
     */
    public void setCacheHitThreshold(int threshold) {
        this.cacheHitThreshold = threshold;
        System.out.println("统计输出阈值: " + threshold);
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        int size = heuristicCache.size();
        heuristicCache.clear();
        System.out.println("清空启发式缓存，释放 " + size + " 条记录");
    }

    /**
     * 获取缓存命中率
     */
    public double getCacheHitRate() {
        long total = totalCalculations.get();
        long hits = cacheHits.get();
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * 获取平均计算时间（纳秒）
     */
    public double getAverageCalculationTime() {
        long total = totalCalculations.get();
        long time = totalCalculationTime.get();
        return total > 0 ? (double) time / total : 0.0;
    }

    /**
     * 获取最后计算时间（纳秒）
     */
    public long getLastCalculationTime() {
        return lastCalculationTime.get();
    }

    /**
     * 获取总计算次数
     */
    public long getTotalCalculations() {
        return totalCalculations.get();
    }

    /**
     * 获取缓存命中次数
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * 获取当前缓存大小
     */
    public int getCurrentCacheSize() {
        return heuristicCache.size();
    }

    /**
     * 打印详细统计信息
     */
    public void printStatistics() {
        if (!enableStatistics) {
            System.out.println("统计监控已禁用");
            return;
        }

        long total = totalCalculations.get();
        long hits = cacheHits.get();
        double hitRate = getCacheHitRate();
        double avgTime = getAverageCalculationTime();

        System.out.println("\n=== 模式数据库预测器统计 ===");
        System.out.printf("总计算次数: %,d\n", total);
        System.out.printf("缓存命中次数: %,d\n", hits);
        System.out.printf("缓存命中率: %.2f%%\n", hitRate * 100);
        System.out.printf("平均计算时间: %.2f μs\n", avgTime / 1000.0);
        System.out.printf("最后计算时间: %.2f μs\n", lastCalculationTime.get() / 1000.0);
        System.out.printf("当前缓存大小: %,d / %,d\n", heuristicCache.size(), maxCacheSize);
        System.out.printf("缓存使用率: %.1f%%\n",
                heuristicCache.size() * 100.0 / maxCacheSize);

        // 模式数据库统计
        PatternDatabase db = getPatternDatabase();
        System.out.println("\n模式数据库信息:");
        System.out.printf("数据库类型: %s\n", db.getClass().getSimpleName());
        System.out.printf("数据库大小: %,d\n", db.getSize());

        // 使用try-catch来兼容可能不存在的方法
        try {
            long buildTime = db.getBuildTime();
            System.out.printf("数据库构建时间: %.2f s\n", buildTime / 1000.0);
        } catch (Exception e) {
            // 方法不存在，跳过
        }

        try {
            int statesProcessed = db.getStatesProcessed();
            System.out.printf("数据库状态数: %,d\n", statesProcessed);
        } catch (Exception e) {
            // 方法不存在，跳过
        }
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        totalCalculations.set(0);
        cacheHits.set(0);
        totalCalculationTime.set(0);
        lastCalculationTime.set(0);
        System.out.println("统计信息已重置");
    }

    /**
     * 预热缓存
     */
    public void warmupCache(PuzzleBoard[] commonStates) {
        if (!enableCache) {
            System.out.println("缓存已禁用，跳过预热");
            return;
        }

        System.out.println("开始预热启发式缓存...");
        long startTime = System.currentTimeMillis();
        int warmed = 0;

        for (PuzzleBoard state : commonStates) {
            if (heuristicCache.size() >= maxCacheSize * 0.8) {
                System.out.println("缓存接近上限，停止预热");
                break;
            }

            try {
                int heuristic = patternDatabase.getHeuristic(state);
                long cacheKey = state.getCompressedState();
                heuristicCache.put(cacheKey, heuristic);
                warmed++;
            } catch (Exception e) {
                System.out.println("预热状态失败: " + e.getMessage());
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.printf("缓存预热完成: %,d 个状态, 耗时: %.2f s\n",
                warmed, (endTime - startTime) / 1000.0);
    }

    /**
     * 优化缓存性能
     */
    public void optimizeCache() {
        // ConcurrentHashMap没有trimToSize方法，跳过
        System.gc();
        System.out.println("缓存优化完成");
    }

    /**
     * 检查缓存健康状态
     */
    public boolean isCacheHealthy() {
        double hitRate = getCacheHitRate();
        int cacheSize = heuristicCache.size();

        // 健康标准：命中率 > 10% 且缓存大小不超过上限的90%
        boolean healthy = (hitRate > 0.1) && (cacheSize <= maxCacheSize * 0.9);

        if (!healthy && enableStatistics) {
            System.out.println("缓存健康状态警告:");
            System.out.printf("  命中率: %.1f%% (期望 > 10%%)\n", hitRate * 100);
            System.out.printf("  缓存大小: %,d/%d (期望 < 90%%)\n", cacheSize, maxCacheSize);
        }

        return healthy;
    }

    /**
     * 获取性能建议
     */
    public String getPerformanceAdvice() {
        double hitRate = getCacheHitRate();
        double avgTime = getAverageCalculationTime();
        int cacheSize = heuristicCache.size();

        StringBuilder advice = new StringBuilder();

        if (hitRate < 0.1) {
            advice.append("缓存命中率较低，考虑禁用缓存以减少内存开销。");
        } else if (hitRate > 0.5) {
            advice.append("缓存命中率良好，可以适当增加缓存大小。");
        }

        if (avgTime > 1000000) { // 1ms
            advice.append("平均计算时间较长，建议检查模式数据库性能。");
        }

        if (cacheSize >= maxCacheSize * 0.9) {
            advice.append("缓存接近上限，建议清理或增加缓存大小。");
        }

        return advice.length() > 0 ? advice.toString() : "性能状态良好";
    }

    /**
     * 清理资源（替代finalize）
     */
    public void cleanup() {
        if (enableStatistics) {
            printStatistics();
        }
        clearCache();
    }
}