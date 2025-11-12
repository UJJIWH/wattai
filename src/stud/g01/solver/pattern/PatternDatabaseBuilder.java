package stud.g01.solver.pattern;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 优化的模式数据库构建器
 * 支持缓存、预加载和智能构建策略
 */
public class PatternDatabaseBuilder {

    // 全局缓存，避免重复构建相同的模式数据库
    private static final Map<String, PatternDatabase> globalCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_TIMEOUT_MS = 30 * 60 * 1000; // 30分钟缓存超时

    // 预定义的分区配置
    private static final int[] PATTERN_78_GROUP1 = {1, 2, 3, 4, 5, 6, 7};
    private static final int[] PATTERN_78_GROUP2 = {8, 9, 10, 11, 12, 13, 14, 15};

    private static final int[] PATTERN_663_GROUP1 = {1, 2, 3, 4, 5, 6};
    private static final int[] PATTERN_663_GROUP2 = {7, 8, 9, 10, 11, 12};
    private static final int[] PATTERN_663_GROUP3 = {13, 14, 15};

    private static final int[] PATTERN_CORNER = {1, 3, 7, 9};
    private static final int[] PATTERN_EDGE = {2, 4, 6, 8};

    /**
     * 修复的6-6-3分区创建方法 - 使用缓存和优化构建
     */
    public static PatternDatabase createSixSixThreePartition(int size) {
        String cacheKey = "6-6-3_" + size;
        return getOrCreateDatabase(cacheKey, () -> {
            if (size != 4) {
                throw new IllegalArgumentException("6-6-3 partition is designed for 4x4 puzzles");
            }

            try {
                System.out.println("创建优化的6-6-3分区模式数据库...");

                // 验证分区正确性
                validatePartition(PATTERN_663_GROUP1, PATTERN_663_GROUP2, PATTERN_663_GROUP3, size);

                // 使用优化的构建器创建子数据库
                EfficientPatternDatabase db1 = createOptimizedDatabase(PATTERN_663_GROUP1, size);
                EfficientPatternDatabase db2 = createOptimizedDatabase(PATTERN_663_GROUP2, size);
                EfficientPatternDatabase db3 = createOptimizedDatabase(PATTERN_663_GROUP3, size);

                System.out.println("6-6-3分区模式数据库创建成功");
                System.out.println("分区1: " + Arrays.toString(PATTERN_663_GROUP1) + " (预期状态: ~" + estimateStateCount(PATTERN_663_GROUP1, size) + ")");
                System.out.println("分区2: " + Arrays.toString(PATTERN_663_GROUP2) + " (预期状态: ~" + estimateStateCount(PATTERN_663_GROUP2, size) + ")");
                System.out.println("分区3: " + Arrays.toString(PATTERN_663_GROUP3) + " (预期状态: ~" + estimateStateCount(PATTERN_663_GROUP3, size) + ")");

                DisjointPatternDatabase disjointDB = new DisjointPatternDatabase(new PatternDatabase[]{db1, db2, db3});
                disjointDB.setParallelComputation(true); // 启用并行计算

                return disjointDB;
            } catch (Exception e) {
                System.out.println("创建6-6-3分区模式数据库失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Failed to create 6-6-3 partition database", e);
            }
        });
    }

    /**
     * 修复的7-8分区创建方法 - 使用缓存和优化构建
     */
    public static PatternDatabase createSevenEightPartition(int size) {
        String cacheKey = "7-8_" + size;
        return getOrCreateDatabase(cacheKey, () -> {
            if (size != 4) {
                throw new IllegalArgumentException("7-8 partition is designed for 4x4 puzzles");
            }

            try {
                System.out.println("创建优化的7-8分区模式数据库...");

                // 验证分区正确性
                validatePartition(PATTERN_78_GROUP1, PATTERN_78_GROUP2, size);

                // 使用优化的构建器创建子数据库
                EfficientPatternDatabase db1 = createOptimizedDatabase(PATTERN_78_GROUP1, size);
                EfficientPatternDatabase db2 = createOptimizedDatabase(PATTERN_78_GROUP2, size);

                System.out.println("7-8分区模式数据库创建成功");
                System.out.println("分区1: " + Arrays.toString(PATTERN_78_GROUP1) + " (预期状态: ~" + estimateStateCount(PATTERN_78_GROUP1, size) + ")");
                System.out.println("分区2: " + Arrays.toString(PATTERN_78_GROUP2) + " (预期状态: ~" + estimateStateCount(PATTERN_78_GROUP2, size) + ")");

                DisjointPatternDatabase disjointDB = new DisjointPatternDatabase(new PatternDatabase[]{db1, db2});
                disjointDB.setParallelComputation(true); // 启用并行计算

                return disjointDB;
            } catch (Exception e) {
                System.out.println("创建7-8分区模式数据库失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Failed to create 7-8 partition database", e);
            }
        });
    }

    /**
     * 创建优化的单个模式数据库
     */
    private static EfficientPatternDatabase createOptimizedDatabase(int[] patternTiles, int size) {
        String cacheKey = Arrays.toString(patternTiles) + "_" + size;

        PatternDatabase cached = globalCache.get(cacheKey);
        if (cached instanceof EfficientPatternDatabase) {
            System.out.println("使用缓存的模式数据库: " + Arrays.toString(patternTiles));
            return (EfficientPatternDatabase) cached;
        }

        EfficientPatternDatabase db = new EfficientPatternDatabase(patternTiles, size);

        // 尝试从文件加载
        String filename = generateFilename(patternTiles, size);
        if (java.nio.file.Files.exists(java.nio.file.Paths.get(filename))) {
            System.out.println("从文件加载模式数据库: " + filename);
            db.loadFromFile(filename);
        } else {
            System.out.println("文件不存在，使用双向BFS构建: " + Arrays.toString(patternTiles));
            db.precomputeWithBiBFS();

            // 异步保存到文件
            new Thread(() -> {
                try {
                    db.saveToFile(filename);
                } catch (Exception e) {
                    System.out.println("异步保存失败: " + e.getMessage());
                }
            }).start();
        }

        // 缓存数据库
        globalCache.put(cacheKey, db);
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        return db;
    }

    /**
     * 生成文件名
     */
    private static String generateFilename(int[] patternTiles, int size) {
        String patternStr = Arrays.toString(patternTiles)
                .replaceAll("[\\[\\]\\s,]", "");
        return "pattern_db_" + patternStr + "_" + size + "x" + size + ".ser";
    }

    /**
     * 验证分区正确性
     */
    private static void validatePartition(int[]... groups) {
        System.out.println("验证分区正确性...");

        // 检查所有分区是否覆盖了1-15的所有数字且没有重叠
        boolean[] covered = new boolean[16]; // 索引1-15

        for (int[] group : groups) {
            for (int tile : group) {
                if (tile < 1 || tile > 15) {
                    throw new IllegalArgumentException("无效的瓷砖编号: " + tile);
                }
                if (covered[tile]) {
                    throw new IllegalArgumentException("瓷砖重复: " + tile);
                }
                covered[tile] = true;
            }
        }

        // 检查是否覆盖了所有1-15的瓷砖
        for (int i = 1; i <= 15; i++) {
            if (!covered[i]) {
                throw new IllegalArgumentException("瓷砖缺失: " + i);
            }
        }

        System.out.println("分区验证通过");
    }

    /**
     * 验证分区正确性（重载版本）
     */
    private static void validatePartition(int[] group1, int[] group2, int size) {
        validatePartition(new int[][]{group1, group2});
    }

    /**
     * 验证分区正确性（重载版本）
     */
    private static void validatePartition(int[] group1, int[] group2, int[] group3, int size) {
        validatePartition(new int[][]{group1, group2, group3});
    }

    /**
     * 保留原有的小模式创建方法 - 优化版本
     */
    public static PatternDatabase createCornerPattern(int size) {
        String cacheKey = "corner_" + size;
        return getOrCreateDatabase(cacheKey, () -> {
            if (size != 3) {
                throw new IllegalArgumentException("Corner pattern is designed for 3x3 puzzles");
            }
            EfficientPatternDatabase db = createOptimizedDatabase(PATTERN_CORNER, size);
            System.out.println("创建角模式数据库: " + Arrays.toString(PATTERN_CORNER));
            return db;
        });
    }

    public static PatternDatabase createEdgePattern(int size) {
        String cacheKey = "edge_" + size;
        return getOrCreateDatabase(cacheKey, () -> {
            if (size != 3) {
                throw new IllegalArgumentException("Edge pattern is designed for 3x3 puzzles");
            }
            EfficientPatternDatabase db = createOptimizedDatabase(PATTERN_EDGE, size);
            System.out.println("创建边模式数据库: " + Arrays.toString(PATTERN_EDGE));
            return db;
        });
    }

    /**
     * 根据名称创建模式数据库 - 优化版本
     */
    public static PatternDatabase createByName(String name, int size) {
        String cacheKey = name + "_" + size;
        return getOrCreateDatabase(cacheKey, () -> {
            switch (name.toUpperCase()) {
                case "7-8":
                    return createSevenEightPartition(size);
                case "6-6-3":
                    return createSixSixThreePartition(size);
                case "CORNER":
                    return createCornerPattern(size);
                case "EDGE":
                    return createEdgePattern(size);
                default:
                    throw new IllegalArgumentException("未知的模式数据库: " + name);
            }
        });
    }

    /**
     * 获取或创建数据库（带缓存）
     */
    private static PatternDatabase getOrCreateDatabase(String cacheKey, DatabaseFactory factory) {
        // 检查缓存
        PatternDatabase cached = globalCache.get(cacheKey);
        if (cached != null) {
            Long timestamp = cacheTimestamps.get(cacheKey);
            if (timestamp != null && (System.currentTimeMillis() - timestamp) < CACHE_TIMEOUT_MS) {
                System.out.println("使用缓存的数据库: " + cacheKey);
                return cached;
            } else {
                // 缓存过期，清除
                globalCache.remove(cacheKey);
                cacheTimestamps.remove(cacheKey);
            }
        }

        // 创建新数据库
        PatternDatabase database = factory.create();
        globalCache.put(cacheKey, database);
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        return database;
    }

    /**
     * 数据库工厂接口
     */
    @FunctionalInterface
    private interface DatabaseFactory {
        PatternDatabase create();
    }

    /**
     * 获取可用的模式数据库类型 - 优化版本
     */
    public static String[] getAvailablePatterns(int size) {
        if (size == 3) {
            return new String[]{"CORNER", "EDGE"};
        } else if (size == 4) {
            return new String[]{"7-8", "6-6-3"};
        } else {
            return new String[0];
        }
    }

    /**
     * 估算状态数量
     */
    private static int estimateStateCount(int[] patternTiles, int size) {
        int n = patternTiles.length;
        int totalPositions = size * size;

        // 组合数 C(totalPositions, n) * n! 的近似值
        long combinations = 1;
        for (int i = 0; i < n; i++) {
            combinations *= (totalPositions - i);
        }
        for (int i = 1; i <= n; i++) {
            combinations /= i;
        }

        combinations *= factorial(n);

        // 实际可达状态大约是理论值的60-80%
        return (int) (combinations * 0.7);
    }

    /**
     * 阶乘计算（优化的查表版本）
     */
    private static long factorial(int n) {
        if (n < 0 || n > 20) return 1;

        final long[] FACTORIALS = {
                1L, 1L, 2L, 6L, 24L, 120L, 720L, 5040L, 40320L, 362880L,
                3628800L, 39916800L, 479001600L, 6227020800L, 87178291200L,
                1307674368000L, 20922789888000L, 355687428096000L,
                6402373705728000L, 121645100408832000L, 2432902008176640000L
        };

        return FACTORIALS[n];
    }

    /**
     * 预加载常用模式数据库
     */
    public static void preloadCommonDatabases() {
        System.out.println("预加载常用模式数据库...");

        Thread preloadThread = new Thread(() -> {
            try {
                // 预加载4x4的常用模式
                createSevenEightPartition(4);
                createSixSixThreePartition(4);

                // 预加载3x3的常用模式
                createCornerPattern(3);
                createEdgePattern(3);

                System.out.println("常用模式数据库预加载完成");
            } catch (Exception e) {
                System.out.println("预加载失败: " + e.getMessage());
            }
        });

        preloadThread.setDaemon(true);
        preloadThread.start();
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        int size = globalCache.size();
        globalCache.clear();
        cacheTimestamps.clear();
        System.out.println("清除缓存，释放 " + size + " 个数据库");
    }

    /**
     * 获取缓存统计信息
     */
    public static void printCacheStatistics() {
        System.out.println("=== 模式数据库缓存统计 ===");
        System.out.println("缓存数据库数量: " + globalCache.size());

        long totalMemory = 0;
        for (Map.Entry<String, PatternDatabase> entry : globalCache.entrySet()) {
            PatternDatabase db = entry.getValue();
            totalMemory += db.getSize() * 16L; // 估算内存使用
            System.out.printf("  %s: %,d 状态\n", entry.getKey(), db.getSize());
        }

        System.out.printf("估算内存使用: %,d KB\n", totalMemory / 1024);

        // 显示缓存时间信息
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : cacheTimestamps.entrySet()) {
            long age = (now - entry.getValue()) / 1000;
            System.out.printf("  %s: 缓存 %d 秒\n", entry.getKey(), age);
        }
    }

    /**
     * 维护缓存 - 清除过期项目
     */
    public static void cleanupCache() {
        long now = System.currentTimeMillis();
        int removed = 0;

        for (Map.Entry<String, Long> entry : cacheTimestamps.entrySet()) {
            if ((now - entry.getValue()) > CACHE_TIMEOUT_MS) {
                String key = entry.getKey();
                globalCache.remove(key);
                cacheTimestamps.remove(key);
                removed++;
            }
        }

        if (removed > 0) {
            System.out.println("缓存维护: 清除 " + removed + " 个过期项目");
        }

        // 建议垃圾回收
        System.gc();
    }

    /**
     * 创建自定义分区模式数据库
     */
    public static PatternDatabase createCustomPartition(int[][] groups, int size) {
        if (groups == null || groups.length == 0) {
            throw new IllegalArgumentException("分区组不能为空");
        }

        String cacheKey = "custom_" + Arrays.deepHashCode(groups) + "_" + size;
        return getOrCreateDatabase(cacheKey, () -> {
            System.out.println("创建自定义分区模式数据库...");

            // 验证分区
            validatePartition(groups);

            // 创建子数据库
            PatternDatabase[] subDatabases = new PatternDatabase[groups.length];
            for (int i = 0; i < groups.length; i++) {
                System.out.printf("创建子数据库 %d/%d: %s\n",
                        i + 1, groups.length, Arrays.toString(groups[i]));
                subDatabases[i] = createOptimizedDatabase(groups[i], size);
            }

            DisjointPatternDatabase disjointDB = new DisjointPatternDatabase(subDatabases);
            disjointDB.setParallelComputation(groups.length > 1);

            System.out.println("自定义分区模式数据库创建成功");
            return disjointDB;
        });
    }

    static {
        // 注册关闭钩子，在JVM关闭时清理资源
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("清理模式数据库资源...");
            cleanupCache();

            // 关闭所有DisjointPatternDatabase的线程池
            for (PatternDatabase db : globalCache.values()) {
                if (db instanceof DisjointPatternDatabase) {
                    ((DisjointPatternDatabase) db).shutdown();
                }
            }
        }));

        // 启动缓存维护线程
        Thread cacheMaintenanceThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5 * 60 * 1000); // 每5分钟运行一次
                    cleanupCache();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        cacheMaintenanceThread.setDaemon(true);
        cacheMaintenanceThread.start();
    }
}