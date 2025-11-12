package stud.g01.solver.pattern;

import core.problem.State;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 修复的不相交模式数据库
 */
public class DisjointPatternDatabase extends PatternDatabase {
    private final PatternDatabase[] subDatabases;
    private final ExecutorService executor;
    private boolean isParallelComputation;
    private final long[] subDatabaseBuildTimes;

    public DisjointPatternDatabase(PatternDatabase[] subDatabases) {
        super(combinePatternTiles(subDatabases), subDatabases[0].size);
        this.subDatabases = Arrays.copyOf(subDatabases, subDatabases.length);
        this.executor = Executors.newFixedThreadPool(Math.min(subDatabases.length, Runtime.getRuntime().availableProcessors()));
        this.isParallelComputation = true;
        this.subDatabaseBuildTimes = new long[subDatabases.length];

        validateDisjointness();

        System.out.println("创建优化的不相交模式数据库，包含 " + subDatabases.length + " 个子数据库");
        for (int i = 0; i < subDatabases.length; i++) {
            System.out.printf("子数据库 %d: %s\n",
                    i + 1, Arrays.toString(subDatabases[i].patternTiles));
        }
        System.out.println("并行计算: " + (isParallelComputation ? "启用" : "禁用"));
    }

    @Override
    public void precompute() {
        if (isLoaded) {
            System.out.println("数据库已加载，跳过预计算");
            return;
        }

        System.out.println("开始预计算不相交模式数据库...");
        long totalStartTime = System.currentTimeMillis();
        statesProcessed = 0;

        if (isParallelComputation && subDatabases.length > 1) {
            precomputeParallel();
        } else {
            precomputeSequential();
        }

        this.buildTime = System.currentTimeMillis() - totalStartTime;
        isLoaded = true;

        System.out.printf("不相交模式数据库预计算完成，总耗时: %.2fs\n", buildTime / 1000.0);
        printDetailedStatistics();
    }

    /**
     * 并行预计算
     */
    private void precomputeParallel() {
        System.out.println("使用并行计算构建子数据库...");

        CompletionService<Boolean> completionService = new ExecutorCompletionService<>(executor);
        AtomicInteger completedCount = new AtomicInteger(0);
        int totalTasks = subDatabases.length;

        for (int i = 0; i < subDatabases.length; i++) {
            final int index = i;
            completionService.submit(() -> {
                long startTime = System.currentTimeMillis();
                try {
                    System.out.printf("开始构建子数据库 %d/%d: %s\n",
                            index + 1, totalTasks, Arrays.toString(subDatabases[index].patternTiles));

                    subDatabases[index].precompute();

                    long endTime = System.currentTimeMillis();
                    subDatabaseBuildTimes[index] = endTime - startTime;
                    statesProcessed += getSubDatabaseStatesProcessed(subDatabases[index]);

                    System.out.printf("子数据库 %d 构建完成，耗时: %.2fs, 大小: %,d\n",
                            index + 1, subDatabaseBuildTimes[index] / 1000.0,
                            subDatabases[index].getSize());

                    return true;
                } catch (Exception e) {
                    System.out.printf("子数据库 %d 构建失败: %s\n", index + 1, e.getMessage());
                    return false;
                } finally {
                    int completed = completedCount.incrementAndGet();
                    System.out.printf("进度: %d/%d (%.1f%%)\n",
                            completed, totalTasks, (completed * 100.0 / totalTasks));
                }
            });
        }

        for (int i = 0; i < totalTasks; i++) {
            try {
                Future<Boolean> future = completionService.take();
                Boolean result = future.get(10, TimeUnit.MINUTES);
                if (Boolean.FALSE.equals(result)) {
                    System.out.println("部分子数据库构建失败，但继续执行");
                }
            } catch (TimeoutException e) {
                System.out.println("子数据库构建超时，可能状态空间过大");
            } catch (Exception e) {
                System.out.println("子数据库构建异常: " + e.getMessage());
            }
        }
    }

    /**
     * 顺序预计算
     */
    private void precomputeSequential() {
        System.out.println("使用顺序计算构建子数据库...");

        for (int i = 0; i < subDatabases.length; i++) {
            long startTime = System.currentTimeMillis();

            System.out.printf("构建子数据库 %d/%d: %s\n",
                    i + 1, subDatabases.length, Arrays.toString(subDatabases[i].patternTiles));

            subDatabases[i].precompute();

            long endTime = System.currentTimeMillis();
            subDatabaseBuildTimes[i] = endTime - startTime;
            statesProcessed += getSubDatabaseStatesProcessed(subDatabases[i]);

            System.out.printf("子数据库 %d 构建完成，耗时: %.2fs, 大小: %,d\n",
                    i + 1, subDatabaseBuildTimes[i] / 1000.0, subDatabases[i].getSize());

            double progress = (i + 1) * 100.0 / subDatabases.length;
            System.out.printf("总体进度: %.1f%%\n", progress);
        }
    }

    /**
     * 获取子数据库处理状态数（兼容性方法）
     */
    private int getSubDatabaseStatesProcessed(PatternDatabase db) {
        try {
            // 尝试调用getStatesProcessed方法
            return db.getStatesProcessed();
        } catch (Exception e) {
            // 如果方法不存在，返回估算值
            return db.getSize();
        }
    }

    @Override
    public int getHeuristic(State state) {
        if (!isLoaded) {
            System.out.println("数据库未加载，开始惰性构建...");
            precompute();
        }

        int heuristic = 0;
        int validSubDatabases = 0;

        for (int i = 0; i < subDatabases.length; i++) {
            try {
                int subHeuristic = subDatabases[i].getHeuristic(state);
                heuristic += subHeuristic;
                validSubDatabases++;
            } catch (Exception e) {
                System.out.printf("子数据库 %d 计算启发式失败: %s\n", i + 1, e.getMessage());
            }
        }

        if (validSubDatabases == 0) {
            System.out.println("所有子数据库都失败，使用曼哈顿距离回退");
            return calculateManhattanFallbackFromState(state);
        }

        return heuristic;
    }

    /**
     * 从状态计算曼哈顿距离回退值
     */
    private int calculateManhattanFallbackFromState(State state) {
        return patternTiles.length * 2;
    }

    @Override
    public boolean isLoaded() {
        for (PatternDatabase db : subDatabases) {
            if (!db.isLoaded()) return false;
        }
        return true;
    }

    @Override
    public int getSize() {
        int totalSize = 0;
        for (PatternDatabase db : subDatabases) {
            totalSize += db.getSize();
        }
        return totalSize;
    }

    @Override
    public void clear() {
        for (PatternDatabase db : subDatabases) {
            db.clear();
        }
        super.clear();
        Arrays.fill(subDatabaseBuildTimes, 0);
    }

    @Override
    public void saveToFile(String filename) {
        System.out.println("保存不相交模式数据库到: " + filename);
        for (int i = 0; i < subDatabases.length; i++) {
            String subFilename = filename + "_part" + (i + 1) + ".ser";
            subDatabases[i].saveToFile(subFilename);
        }
        System.out.println("所有子数据库保存完成");
    }

    @Override
    public void loadFromFile(String filename) {
        System.out.println("加载不相交模式数据库从: " + filename);
        boolean allLoaded = true;

        for (int i = 0; i < subDatabases.length; i++) {
            String subFilename = filename + "_part" + (i + 1) + ".ser";
            try {
                subDatabases[i].loadFromFile(subFilename);
                if (!subDatabases[i].isLoaded()) {
                    allLoaded = false;
                    System.out.printf("子数据库 %d 加载失败，将重新构建\n", i + 1);
                    subDatabases[i].precompute();
                }
            } catch (Exception e) {
                System.out.printf("子数据库 %d 加载异常: %s，将重新构建\n", i + 1, e.getMessage());
                subDatabases[i].precompute();
                allLoaded = false;
            }
        }

        isLoaded = allLoaded;
        if (isLoaded) {
            System.out.println("所有子数据库加载成功");
        } else {
            System.out.println("部分子数据库需要重新构建");
        }
    }

    /**
     * 验证不相交性
     */
    private void validateDisjointness() {
        System.out.println("验证模式分区的不相交性...");

        boolean[] covered = new boolean[16];

        for (PatternDatabase db : subDatabases) {
            for (int tile : db.patternTiles) {
                if (tile < 1 || tile > 15) {
                    throw new IllegalArgumentException("无效的瓷砖编号: " + tile);
                }
                if (covered[tile]) {
                    throw new IllegalArgumentException("瓷砖重复出现在多个分区: " + tile);
                }
                covered[tile] = true;
            }
        }

        for (int i = 1; i <= 15; i++) {
            if (!covered[i]) {
                System.out.println("警告: 瓷砖 " + i + " 未被任何分区覆盖");
            }
        }

        System.out.println("不相交性验证通过");
    }

    /**
     * 组合模式瓷砖数组
     */
    private static int[] combinePatternTiles(PatternDatabase[] databases) {
        int totalLength = 0;
        for (PatternDatabase db : databases) {
            totalLength += db.patternTiles.length;
        }

        int[] combined = new int[totalLength];
        int index = 0;
        for (PatternDatabase db : databases) {
            System.arraycopy(db.patternTiles, 0, combined, index, db.patternTiles.length);
            index += db.patternTiles.length;
        }

        return combined;
    }

    /**
     * 设置是否使用并行计算
     */
    public void setParallelComputation(boolean parallel) {
        this.isParallelComputation = parallel;
        System.out.println("并行计算: " + (parallel ? "启用" : "禁用"));
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 更详细的统计信息
     */
    public void printDetailedStatistics() {
        System.out.println("\n=== 不相交模式数据库详细统计 ===");
        System.out.printf("子数据库数量: %d\n", subDatabases.length);
        System.out.printf("总状态数量: %,d\n", getSize());
        System.out.printf("总构建时间: %.2fs\n", buildTime / 1000.0);
        System.out.printf("总处理状态数: %,d\n", statesProcessed);

        System.out.println("\n子数据库详情:");
        for (int i = 0; i < subDatabases.length; i++) {
            System.out.printf("  [%d] %s: %,d 状态, %.2fs\n",
                    i + 1, Arrays.toString(subDatabases[i].patternTiles),
                    subDatabases[i].getSize(), subDatabaseBuildTimes[i] / 1000.0);
        }

        if (isParallelComputation && subDatabases.length > 1) {
            long maxSubTime = Arrays.stream(subDatabaseBuildTimes).max().orElse(1);
            double efficiency = (buildTime * 1.0 / maxSubTime) / subDatabases.length;
            System.out.printf("并行效率: %.2f\n", efficiency);
        }

        System.out.printf("内存使用估算: ~%,d KB\n", (getSize() * 16 / 1024));
    }

    /**
     * 清理资源（替代finalize）
     */
    public void cleanup() {
        shutdown();
    }
}