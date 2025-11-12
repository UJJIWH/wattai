package stud.g01.solver.pattern;

/**
 * 模式数据库构建性能比较器
 */
public class PatternDatabaseComparator {

    public static void compareBuildMethods(int[] patternTiles, int size) {
        System.out.println("=== 模式数据库构建性能比较 ===");
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

        // 验证结果一致性
        System.out.println("\n=== 结果验证 ===");
        System.out.println("状态数差异: " + Math.abs(bfsSize - bibfsSize));
        if (bfsSize == bibfsSize) {
            System.out.println("? 两种方法构建的数据库大小相同");
        } else {
            System.out.println("? 两种方法构建的数据库大小不同");
        }
    }
}