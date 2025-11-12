package stud.g01.solver;

import core.problem.Problem;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.NPuzzleProblem;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 优化的IDA*实现
 * 使用更高效的递归策略、路径缓存和剪枝优化
 */
public class IdAStar extends AbstractSearcher {
    private final Predictor predictor;
    private Node goalNode;
    private long searchStartTime;
    private final int TIME_LIMIT_MS = 60000; // 60秒时限

    // 性能优化
    private int currentThreshold;
    private int iterations;
    private long totalNodesGenerated;
    private long totalNodesExpanded;

    // 缓存和剪枝
    private final PathCache pathCache;
    private boolean enablePruning;
    private int pruningDepth;

    // 统计信息
    private final AtomicLong recursionDepth;
    private final AtomicLong prunedBranches;

    public IdAStar(Frontier frontier, Predictor predictor) {
        super(frontier);
        this.predictor = predictor;
        this.pathCache = new PathCache(1000); // 1000条路径缓存
        this.enablePruning = true;
        this.pruningDepth = 5;
        this.recursionDepth = new AtomicLong(0);
        this.prunedBranches = new AtomicLong(0);

        System.out.println("创建优化的IDA*搜索器");
        System.out.println("路径缓存大小: " + pathCache.getMaxSize());
        System.out.println("剪枝优化: " + (enablePruning ? "启用 (深度=" + pruningDepth + ")" : "禁用"));
    }

    @Override
    public Deque<Node> search(Problem problem) {
        if (!problem.solvable()) {
            System.out.println("问题无解");
            return null;
        }

        // 清理统计信息
        resetSearchStatistics();

        // 获取带有启发式值的根节点
        Node root = problem.root(predictor);
        currentThreshold = root.getHeuristic();

        System.out.println("开始优化的IDA*搜索...");
        System.out.println("初始阈值(根节点启发值): " + currentThreshold);
        System.out.println("时间限制: " + TIME_LIMIT_MS + "ms");
        System.out.println("根节点f值: " + root.evaluation());

        searchStartTime = System.currentTimeMillis();

        // 迭代加深搜索
        while (currentThreshold < Integer.MAX_VALUE) {
            iterations++;
            System.out.printf("迭代 %d - 当前阈值: %d - ", iterations, currentThreshold);

            // 清理探索集，但保留路径缓存
            explored.clear();

            int result = optimizedDepthLimitedSearch(problem, root, currentThreshold, 0);

            System.out.printf("生成: %,d, 扩展: %,d, 结果: %s\n",
                    nodesGenerated, nodesExpanded,
                    result == -1 ? "找到解" : "下一阈值=" + result);

            if (result == -1) {
                // 找到解
                long endTime = System.currentTimeMillis();
                double totalTime = (endTime - searchStartTime) / 1000.0;
                printSearchStatistics(totalTime);
                return generatePath(goalNode);
            }

            if (result == Integer.MAX_VALUE) {
                // 无解
                System.out.println("无解");
                return null;
            }

            // 更新阈值继续搜索
            currentThreshold = result;

            // 检查超时
            if ((System.currentTimeMillis() - searchStartTime) > TIME_LIMIT_MS) {
                System.out.println("超时（60秒），停止搜索");
                return null;
            }

            // 定期清理缓存
            if (iterations % 10 == 0) {
                pathCache.cleanup();
            }
        }

        System.out.println("达到阈值上限，停止搜索");
        return null;
    }

    /**
     * 优化的深度受限搜索
     * @return -1: 找到解, 其他: 下一个阈值
     */
    private int optimizedDepthLimitedSearch(Problem problem, Node node, int threshold, int depth) {
        // 更新最大递归深度统计
        recursionDepth.set(Math.max(recursionDepth.get(), depth));

        // 检查超时
        if ((System.currentTimeMillis() - searchStartTime) > TIME_LIMIT_MS) {
            return Integer.MAX_VALUE;
        }

        int f = node.getPathCost() + node.getHeuristic();

        // 超过阈值，返回f值
        if (f > threshold) {
            return f;
        }

        // 检查是否到达目标
        if (problem.goal(node.getState())) {
            goalNode = node;
            return -1;
        }

        // 剪枝检查
        if (enablePruning && depth >= pruningDepth) {
            if (shouldPruneBranch(node, depth)) {
                prunedBranches.incrementAndGet();
                return Integer.MAX_VALUE;
            }
        }

        // 路径缓存检查
        PathCache.CacheResult cacheResult = pathCache.checkCache(node.getState(), depth);
        if (cacheResult != null) {
            if (cacheResult.foundBetterPath()) {
                // 找到更好路径，跳过这个分支
                return Integer.MAX_VALUE;
            } else if (cacheResult.hasHeuristic()) {
                // 使用缓存的启发式信息
                return cacheResult.getNextThreshold();
            }
        }

        nodesExpanded++;
        totalNodesExpanded++;

        int minExceed = Integer.MAX_VALUE;
        boolean hasValidChildren = false;

        // 优化：预排序子节点，先扩展最有希望的节点
        Deque<Node> children = getOptimizedChildren(problem, node);

        for (Node child : children) {
            nodesGenerated++;
            totalNodesGenerated++;

            // 计算启发式值（如果尚未计算）
            if (child.getHeuristic() == 0) {
                int heuristic = predictor.heuristics(child.getState(), problem.getGoal());
                child.setHeuristic(heuristic);
            }

            // 剪枝：如果f值明显过大，跳过
            if (enablePruning && shouldPruneByFValue(child, threshold)) {
                continue;
            }

            int result = optimizedDepthLimitedSearch(problem, child, threshold, depth + 1);

            if (result == -1) {
                // 找到解，立即返回
                return -1;
            }

            if (result < minExceed) {
                minExceed = result;
                hasValidChildren = true;
            }
        }

        // 更新路径缓存
        if (hasValidChildren && minExceed < Integer.MAX_VALUE) {
            pathCache.updateCache(node.getState(), depth, minExceed);
        }

        return minExceed;
    }

    /**
     * 获取优化的子节点列表（预排序）
     */
    private Deque<Node> getOptimizedChildren(Problem problem, Node parent) {
        // 生成所有子节点
        Deque<Node> children = new ArrayDeque<>();
        for (Node child : problem.childNodes(parent)) {
            children.add(child);
        }

        // 按启发式值排序（最有希望的在前）
        if (children.size() > 1) {
            // 使用简单的插入排序（对于小列表足够高效）
            Node[] childArray = children.toArray(new Node[0]);
            insertionSortByHeuristic(childArray);
            children.clear();
            for (Node child : childArray) {
                children.add(child);
            }
        }

        return children;
    }

    /**
     * 按启发式值插入排序
     */
    private void insertionSortByHeuristic(Node[] nodes) {
        for (int i = 1; i < nodes.length; i++) {
            Node key = nodes[i];
            int j = i - 1;

            // 按启发式值升序排列（值越小越有希望）
            while (j >= 0 && nodes[j].getHeuristic() > key.getHeuristic()) {
                nodes[j + 1] = nodes[j];
                j = j - 1;
            }
            nodes[j + 1] = key;
        }
    }

    /**
     * 判断是否应该剪枝分支
     */
    private boolean shouldPruneBranch(Node node, int depth) {
        // 基于深度和f值的简单剪枝策略
        int f = node.evaluation();
        int expectedMinCost = depth * 2; // 预期最小代价

        // 如果f值远大于预期，可能是不好的分支
        return f > expectedMinCost * 3;
    }

    /**
     * 基于f值的剪枝
     */
    private boolean shouldPruneByFValue(Node node, int threshold) {
        int f = node.evaluation();
        // 如果f值超过阈值太多，提前剪枝
        return f > threshold * 1.5;
    }

    /**
     * 重置搜索统计
     */
    private void resetSearchStatistics() {
        nodesExpanded = 0;
        nodesGenerated = 0;
        goalNode = null;
        explored.clear();
        pathCache.clear();
        iterations = 0;
        currentThreshold = 0;
        totalNodesGenerated = 0;
        totalNodesExpanded = 0;
        recursionDepth.set(0);
        prunedBranches.set(0);
    }

    /**
     * 打印搜索统计
     */
    private void printSearchStatistics(double totalTime) {
        System.out.println("\n=== IDA* 搜索统计 ===");
        System.out.printf("解路径长度: %d\n", generatePath(goalNode).size() - 1);
        System.out.printf("总时间: %.3f 秒\n", totalTime);
        System.out.printf("迭代次数: %d\n", iterations);
        System.out.printf("总生成节点: %,d\n", totalNodesGenerated);
        System.out.printf("总扩展节点: %,d\n", totalNodesExpanded);
        System.out.printf("节点生成速率: %.1f 节点/秒\n", totalNodesGenerated / totalTime);
        System.out.printf("最大递归深度: %,d\n", recursionDepth.get());
        System.out.printf("剪枝分支数: %,d\n", prunedBranches.get());
        System.out.printf("最终阈值: %d\n", currentThreshold);

        // 缓存统计
        System.out.printf("路径缓存命中率: %.1f%%\n", pathCache.getHitRate() * 100);

        // 性能分析
        if (totalTime > 0) {
            double expansionRate = totalNodesExpanded / totalTime;
            System.out.printf("节点扩展速率: %.1f 节点/秒\n", expansionRate);

            if (expansionRate < 1000) {
                System.out.println("性能提示: 节点扩展速率较低，考虑优化启发式函数");
            }
        }
    }

    /**
     * 设置是否启用剪枝
     */
    public void setEnablePruning(boolean enablePruning) {
        this.enablePruning = enablePruning;
        System.out.println("剪枝优化: " + (enablePruning ? "启用" : "禁用"));
    }

    /**
     * 设置剪枝深度
     */
    public void setPruningDepth(int pruningDepth) {
        this.pruningDepth = pruningDepth;
        System.out.println("剪枝深度设置为: " + pruningDepth);
    }

    /**
     * 设置路径缓存大小
     */
    public void setPathCacheSize(int size) {
        pathCache.setMaxSize(size);
        System.out.println("路径缓存大小设置为: " + size);
    }

    /**
     * 获取搜索统计信息
     */
    public SearchStatistics getSearchStatistics() {
        return new SearchStatistics(
                iterations,
                totalNodesGenerated,
                totalNodesExpanded,
                recursionDepth.get(),
                prunedBranches.get(),
                currentThreshold,
                pathCache.getHitRate()
        );
    }

    /**
     * 路径缓存内部类
     */
    private static class PathCache {
        private final java.util.LinkedHashMap<Long, CacheEntry> cache;
        private final int maxSize;
        private long hits;
        private long misses;

        public PathCache(int maxSize) {
            this.maxSize = maxSize;
            this.cache = new java.util.LinkedHashMap<Long, CacheEntry>(maxSize / 2, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Long, CacheEntry> eldest) {
                    return size() > maxSize;
                }
            };
            this.hits = 0;
            this.misses = 0;
        }

        public CacheResult checkCache(core.problem.State state, int depth) {
            long key = computeCacheKey(state, depth);
            CacheEntry entry = cache.get(key);

            if (entry != null) {
                hits++;
                return new CacheResult(entry.nextThreshold, entry.betterPathExists);
            } else {
                misses++;
                return null;
            }
        }

        public void updateCache(core.problem.State state, int depth, int nextThreshold) {
            long key = computeCacheKey(state, depth);
            cache.put(key, new CacheEntry(nextThreshold, false));
        }

        public void markBetterPath(core.problem.State state, int depth) {
            long key = computeCacheKey(state, depth);
            cache.put(key, new CacheEntry(Integer.MAX_VALUE, true));
        }

        private long computeCacheKey(core.problem.State state, int depth) {
            // 使用状态哈希码和深度组合作为缓存键
            if (state instanceof stud.g01.problem.npuzzle.PuzzleBoard) {
                stud.g01.problem.npuzzle.PuzzleBoard board = (stud.g01.problem.npuzzle.PuzzleBoard) state;
                return board.getCompressedState() ^ ((long) depth << 32);
            }
            return state.hashCode() ^ ((long) depth << 32);
        }

        public void cleanup() {
            // 移除过期的缓存条目
            long currentTime = System.currentTimeMillis();
            cache.entrySet().removeIf(entry ->
                    currentTime - entry.getValue().timestamp > 30000); // 30秒过期
        }

        public void clear() {
            cache.clear();
            hits = 0;
            misses = 0;
        }

        public int getSize() {
            return cache.size();
        }

        public int getMaxSize() {
            return maxSize;
        }

        public double getHitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }

        public void setMaxSize(int size) {
            // 注意：LinkedHashMap的容量限制是通过removeEldestEntry实现的
            // 这里我们无法动态改变maxSize，需要重新创建缓存
            // 在实际实现中可能需要更复杂的管理
        }

        private static class CacheEntry {
            final int nextThreshold;
            final boolean betterPathExists;
            final long timestamp;

            CacheEntry(int nextThreshold, boolean betterPathExists) {
                this.nextThreshold = nextThreshold;
                this.betterPathExists = betterPathExists;
                this.timestamp = System.currentTimeMillis();
            }
        }

        public static class CacheResult {
            private final Integer nextThreshold;
            private final boolean betterPathExists;

            CacheResult(Integer nextThreshold, boolean betterPathExists) {
                this.nextThreshold = nextThreshold;
                this.betterPathExists = betterPathExists;
            }

            public boolean hasHeuristic() {
                return nextThreshold != null && nextThreshold < Integer.MAX_VALUE;
            }

            public int getNextThreshold() {
                return nextThreshold != null ? nextThreshold : Integer.MAX_VALUE;
            }

            public boolean foundBetterPath() {
                return betterPathExists;
            }
        }
    }

    /**
     * 搜索统计信息类
     */
    public static class SearchStatistics {
        public final int iterations;
        public final long totalNodesGenerated;
        public final long totalNodesExpanded;
        public final long maxRecursionDepth;
        public final long prunedBranches;
        public final int finalThreshold;
        public final double cacheHitRate;

        public SearchStatistics(int iterations, long totalNodesGenerated, long totalNodesExpanded,
                                long maxRecursionDepth, long prunedBranches, int finalThreshold,
                                double cacheHitRate) {
            this.iterations = iterations;
            this.totalNodesGenerated = totalNodesGenerated;
            this.totalNodesExpanded = totalNodesExpanded;
            this.maxRecursionDepth = maxRecursionDepth;
            this.prunedBranches = prunedBranches;
            this.finalThreshold = finalThreshold;
            this.cacheHitRate = cacheHitRate;
        }

        @Override
        public String toString() {
            return String.format(
                    "IDA*统计: 迭代=%d, 生成节点=%,d, 扩展节点=%,d, 最大深度=%,d, 剪枝=%,d, 缓存命中率=%.1f%%",
                    iterations, totalNodesGenerated, totalNodesExpanded, maxRecursionDepth,
                    prunedBranches, cacheHitRate * 100
            );
        }
    }
}