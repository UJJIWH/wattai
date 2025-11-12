package stud.g01.solver.pattern;

import core.problem.State;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 修复的模式数据库基类
 */
public abstract class PatternDatabase {
    protected final Map<Long, Integer> database;
    protected final int[] patternTiles;
    protected final int size;
    protected final int patternSize;
    protected boolean isLoaded;

    // 性能统计
    protected long buildTime;
    protected int statesProcessed;

    public PatternDatabase(int[] patternTiles, int size) {
        this.database = new HashMap<>(calculateInitialCapacity(patternTiles.length));
        this.patternTiles = Arrays.copyOf(patternTiles, patternTiles.length);
        this.size = size;
        this.patternSize = patternTiles.length;
        this.isLoaded = false;
        this.buildTime = 0;
        this.statesProcessed = 0;

        System.out.println("模式数据库初始化:");
        System.out.println("模式瓷砖: " + Arrays.toString(patternTiles));
        System.out.println("拼图尺寸: " + size + "x" + size);
        System.out.println("预期状态数: ~" + estimateStateCount());
    }

    /**
     * 预计算模式数据库 - 标准方法
     */
    public abstract void precompute();

    /**
     * 使用双向BFS预计算模式数据库 - 可选方法
     */
    public void precomputeWithBiBFS() {
        System.out.println("双向BFS未实现，使用标准预计算");
        precompute();
    }

    /**
     * 获取状态的启发式值
     */
    public abstract int getHeuristic(State state);

    /**
     * 优化的模式键值生成
     */
    protected long generatePatternKey(PuzzleBoard board) {
        int[] tiles = board.getPuzzleBoard();
        long key = 0;

        for (int tile : patternTiles) {
            int position = findPosition(tiles, tile);
            if (position == -1) {
                position = 63;
            }
            key = (key << 6) | (position & 0x3F);
        }
        return key;
    }

    /**
     * 优化的位置查找
     */
    protected int findPosition(int[] tiles, int value) {
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == value) return i;
        }
        return -1;
    }

    /**
     * 数据库是否已加载
     */
    public boolean isLoaded() {
        return isLoaded;
    }

    /**
     * 获取数据库大小
     */
    public int getSize() {
        return database.size();
    }

    /**
     * 获取构建时间
     */
    public long getBuildTime() {
        return buildTime;
    }

    /**
     * 获取处理状态数
     */
    public int getStatesProcessed() {
        return statesProcessed;
    }

    /**
     * 清空数据库
     */
    public void clear() {
        database.clear();
        isLoaded = false;
        buildTime = 0;
        statesProcessed = 0;
    }

    /**
     * 计算回退启发式值（优化的曼哈顿距离）
     */
    protected int calculateManhattanFallback(PuzzleBoard board) {
        int[] tiles = board.getPuzzleBoard();
        int distance = 0;

        for (int tile : patternTiles) {
            int currentPos = findPosition(tiles, tile);
            int goalPos = tile - 1;

            if (currentPos >= 0 && goalPos >= 0) {
                int currentRow = currentPos / size;
                int currentCol = currentPos % size;
                int goalRow = goalPos / size;
                int goalCol = goalPos % size;
                distance += Math.abs(currentRow - goalRow) + Math.abs(currentCol - goalCol);
            }
        }
        return distance;
    }

    /**
     * 保存到文件
     */
    public void saveToFile(String filename) {
        long startTime = System.currentTimeMillis();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(database);
            long endTime = System.currentTimeMillis();
            System.out.println("数据库已保存到: " + filename + " (" + (endTime - startTime) + "ms)");
        } catch (IOException e) {
            System.out.println("保存数据库失败: " + e.getMessage());
        }
    }

    /**
     * 从文件加载
     */
    @SuppressWarnings("unchecked")
    public void loadFromFile(String filename) {
        long startTime = System.currentTimeMillis();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
            database.clear();
            database.putAll((Map<Long, Integer>) ois.readObject());
            isLoaded = true;
            long endTime = System.currentTimeMillis();
            System.out.println("数据库已从文件加载: " + filename + " (" + (endTime - startTime) + "ms)");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("加载数据库失败: " + e.getMessage() + "，将重新构建");
            precompute();
        }
    }

    /**
     * 估算初始HashMap容量以减少扩容
     */
    protected int calculateInitialCapacity(int patternLength) {
        int estimatedStates = estimateStateCount();
        return (int) (estimatedStates / 0.75f) + 1;
    }

    /**
     * 估算状态数量
     */
    private int estimateStateCount() {
        int totalPositions = size * size;

        long combinations = 1;
        for (int i = 0; i < patternSize; i++) {
            combinations *= (totalPositions - i);
        }
        for (int i = 1; i <= patternSize; i++) {
            combinations /= i;
        }

        combinations *= factorial(patternSize);

        return (int) (combinations * 0.7);
    }

    /**
     * 阶乘计算
     */
    private long factorial(int n) {
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
     * 内存优化
     */
    public void optimizeMemory() {
        if (database instanceof HashMap) {
            // HashMap没有trimToSize方法，跳过
        }
    }

    /**
     * 性能统计信息
     */
    public void printStatistics() {
        System.out.println("=== 模式数据库统计 ===");
        System.out.println("模式: " + Arrays.toString(patternTiles));
        System.out.println("状态数量: " + database.size());
        System.out.println("构建时间: " + buildTime + "ms");
        System.out.println("处理状态数: " + statesProcessed);
        System.out.println("平均状态处理时间: " +
                (statesProcessed > 0 ? (buildTime * 1.0 / statesProcessed) : 0) + "ms/状态");
        System.out.println("内存使用: ~" + (database.size() * 16 / 1024) + "KB");
    }
}

/**
 * 模式状态内部类
 */
class PatternState {
    private final int[] patternPositions;
    private final int blankPos;
    private final int size;
    private final long key;

    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public PatternState(int[] patternPositions, int blankPos, int size) {
        this.patternPositions = patternPositions.clone();
        this.blankPos = blankPos;
        this.size = size;
        this.key = computeKey();
    }

    private long computeKey() {
        long computedKey = blankPos;
        for (int pos : patternPositions) {
            computedKey = (computedKey << 6) | (pos & 0x3F);
        }
        return computedKey;
    }

    public long getKey() {
        return key;
    }

    public PatternState[] generateNeighbors() {
        PatternState[] neighbors = new PatternState[4];
        int count = 0;

        int blankRow = blankPos / size;
        int blankCol = blankPos % size;

        for (int[] dir : DIRECTIONS) {
            int newRow = blankRow + dir[0];
            int newCol = blankCol + dir[1];

            if (newRow >= 0 && newRow < size && newCol >= 0 && newCol < size) {
                int newBlankPos = newRow * size + newCol;

                int movedTileIndex = -1;
                for (int i = 0; i < patternPositions.length; i++) {
                    if (patternPositions[i] == newBlankPos) {
                        movedTileIndex = i;
                        break;
                    }
                }

                if (movedTileIndex != -1) {
                    int[] newPositions = patternPositions.clone();
                    newPositions[movedTileIndex] = blankPos;
                    neighbors[count++] = new PatternState(newPositions, newBlankPos, size);
                }
            }
        }

        if (count < neighbors.length) {
            PatternState[] result = new PatternState[count];
            System.arraycopy(neighbors, 0, result, 0, count);
            return result;
        }

        return neighbors;
    }

    public int getBlankPos() {
        return blankPos;
    }

    public int[] getPatternPositions() {
        return patternPositions.clone();
    }
}