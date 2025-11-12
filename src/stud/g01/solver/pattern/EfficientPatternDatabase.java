package stud.g01.solver.pattern;

import stud.g01.problem.npuzzle.PuzzleBoard;
import core.problem.State;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 修复的高效模式数据库实现
 */
public class EfficientPatternDatabase extends PatternDatabase {
    private final int[] goalPositions;
    private final int patternSize;

    // 使用更高效的数据结构
    private Map<Long, Integer> optimizedDatabase;
    private static final int MAX_STATES = 2000000;

    // 性能监控
    private long bfsTime;
    private long bibfsTime;

    public EfficientPatternDatabase(int[] patternTiles, int size) {
        super(patternTiles, size);
        this.patternSize = patternTiles.length;
        this.goalPositions = calculateGoalPositions();
        this.optimizedDatabase = new HashMap<>(calculateInitialCapacity(patternTiles.length));
        this.bfsTime = 0;
        this.bibfsTime = 0;
    }

    @Override
    public void precompute() {
        System.out.println("开始构建高效模式数据库，模式瓷砖: " + Arrays.toString(patternTiles));
        long startTime = System.currentTimeMillis();

        optimizedDatabase.clear();
        buildWithImprovedBFS();

        long endTime = System.currentTimeMillis();
        this.buildTime = endTime - startTime;
        this.bfsTime = this.buildTime;

        System.out.printf("数据库构建完成，耗时: %.2fs, 大小: %,d, 状态处理速率: %.1f 状态/秒\n",
                buildTime / 1000.0, optimizedDatabase.size(),
                statesProcessed * 1000.0 / buildTime);
        isLoaded = true;
    }

    @Override
    public void precomputeWithBiBFS() {
        System.out.println("使用优化的双向BFS构建模式数据库...");
        long startTime = System.currentTimeMillis();

        optimizedDatabase.clear();
        buildWithOptimizedBiBFS();

        long endTime = System.currentTimeMillis();
        this.buildTime = endTime - startTime;
        this.bibfsTime = this.buildTime;

        System.out.printf("双向BFS构建完成，耗时: %.2fs, 大小: %,d, 状态处理速率: %.1f 状态/秒\n",
                buildTime / 1000.0, optimizedDatabase.size(),
                statesProcessed * 1000.0 / buildTime);
        isLoaded = true;
    }

    @Override
    public int getHeuristic(State state) {
        if (!isLoaded) {
            precomputeWithBiBFS();
        }

        if (!(state instanceof PuzzleBoard)) {
            return 0;
        }

        PuzzleBoard board = (PuzzleBoard) state;
        AbstractState abstractState = createAbstractState(board);
        Integer heuristic = optimizedDatabase.get(abstractState.key);

        if (heuristic != null) {
            return heuristic;
        } else {
            return calculateManhattanFallback(board);
        }
    }

    @Override
    public int getSize() {
        return optimizedDatabase.size();
    }

    @Override
    public void clear() {
        optimizedDatabase.clear();
        super.clear();
    }

    @Override
    public void saveToFile(String filename) {
        // 将优化数据库内容复制到父类数据库用于保存
        database.putAll(optimizedDatabase);
        super.saveToFile(filename);
        database.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadFromFile(String filename) {
        try {
            super.loadFromFile(filename);
            optimizedDatabase.putAll(database);
            database.clear();
            isLoaded = true;
        } catch (Exception e) {
            System.out.println("加载失败，将重新构建: " + e.getMessage());
            precomputeWithBiBFS();
        }
    }

    /**
     * 改进的BFS构建方法
     */
    private void buildWithImprovedBFS() {
        Queue<AbstractState> queue = new ArrayDeque<>(10000);
        Set<Long> visited = new HashSet<>(10000);

        AbstractState goalState = createGoalAbstractState();
        queue.offer(goalState);
        visited.add(goalState.key);
        optimizedDatabase.put(goalState.key, 0);

        int nodesProcessed = 0;
        int lastReport = 0;

        while (!queue.isEmpty() && nodesProcessed < MAX_STATES) {
            AbstractState current = queue.poll();
            nodesProcessed++;
            statesProcessed++;
            int currentDistance = optimizedDatabase.get(current.key);

            for (AbstractState neighbor : generateOptimizedNeighbors(current)) {
                if (!visited.contains(neighbor.key)) {
                    visited.add(neighbor.key);
                    optimizedDatabase.put(neighbor.key, currentDistance + 1);
                    queue.offer(neighbor);
                }
            }

            if (nodesProcessed - lastReport >= 10000 || nodesProcessed % 50000 == 0) {
                double progress = (double) nodesProcessed / MAX_STATES * 100;
                System.out.printf("BFS进度: %,d/%,d (%.1f%%), 数据库大小: %,d\n",
                        nodesProcessed, MAX_STATES, progress, optimizedDatabase.size());
                lastReport = nodesProcessed;

                if (nodesProcessed % 100000 == 0) {
                    optimizeMemoryUsage();
                }
            }
        }

        System.out.printf("BFS完成，处理节点: %,d, 最终数据库大小: %,d\n",
                nodesProcessed, optimizedDatabase.size());
    }

    /**
     * 优化的双向BFS构建方法
     */
    private void buildWithOptimizedBiBFS() {
        System.out.println("开始优化的双向BFS构建...");
        long startTime = System.currentTimeMillis();

        Map<Long, Integer> forwardDistances = new HashMap<>(50000);
        Map<Long, Integer> backwardDistances = new HashMap<>(50000);
        Set<Long> forwardVisited = new HashSet<>(50000);
        Set<Long> backwardVisited = new HashSet<>(50000);

        Queue<AbstractState> forwardQueue = new ArrayDeque<>(10000);
        Queue<AbstractState> backwardQueue = new ArrayDeque<>(10000);

        AbstractState goalState = createGoalAbstractState();
        forwardQueue.offer(goalState);
        forwardVisited.add(goalState.key);
        forwardDistances.put(goalState.key, 0);

        initializeOptimizedBackwardSearch(backwardQueue, backwardDistances, backwardVisited);

        int totalProcessed = 0;
        boolean foundMeeting = false;
        AbstractState meetingState = null;
        int meetingDistance = 0;

        while ((!forwardQueue.isEmpty() || !backwardQueue.isEmpty()) &&
                totalProcessed < MAX_STATES && !foundMeeting) {

            if (!forwardQueue.isEmpty()) {
                int forwardSize = forwardQueue.size();
                for (int i = 0; i < forwardSize && !foundMeeting; i++) {
                    AbstractState current = forwardQueue.poll();
                    totalProcessed++;
                    statesProcessed++;
                    int currentDistance = forwardDistances.get(current.key);

                    for (AbstractState neighbor : generateOptimizedNeighbors(current)) {
                        if (!forwardVisited.contains(neighbor.key)) {
                            forwardVisited.add(neighbor.key);
                            forwardDistances.put(neighbor.key, currentDistance + 1);
                            forwardQueue.offer(neighbor);

                            if (backwardVisited.contains(neighbor.key)) {
                                meetingState = neighbor;
                                meetingDistance = currentDistance + 1 + backwardDistances.get(neighbor.key);
                                foundMeeting = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (!foundMeeting && !backwardQueue.isEmpty()) {
                int backwardSize = backwardQueue.size();
                for (int i = 0; i < backwardSize && !foundMeeting; i++) {
                    AbstractState current = backwardQueue.poll();
                    totalProcessed++;
                    statesProcessed++;
                    int currentDistance = backwardDistances.get(current.key);

                    for (AbstractState neighbor : generateOptimizedNeighbors(current)) {
                        if (!backwardVisited.contains(neighbor.key)) {
                            backwardVisited.add(neighbor.key);
                            backwardDistances.put(neighbor.key, currentDistance + 1);
                            backwardQueue.offer(neighbor);

                            if (forwardVisited.contains(neighbor.key)) {
                                meetingState = neighbor;
                                meetingDistance = currentDistance + 1 + forwardDistances.get(neighbor.key);
                                foundMeeting = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (totalProcessed % 50000 == 0) {
                System.out.printf("双向BFS进度: %,d/%,d, 前向: %,d, 后向: %,d\n",
                        totalProcessed, MAX_STATES, forwardVisited.size(), backwardVisited.size());
                optimizeMemoryUsage();
            }
        }

        mergeOptimizedDatabases(forwardDistances, backwardDistances, foundMeeting, meetingState, meetingDistance);

        System.out.printf("双向BFS完成，处理节点: %,d, 最终数据库大小: %,d\n",
                totalProcessed, optimizedDatabase.size());

        if (foundMeeting) {
            System.out.printf("在节点 %,d 处相遇，相遇距离: %d\n",
                    meetingState.key, meetingDistance);
        }
    }

    /**
     * 初始化优化的后向搜索
     */
    private void initializeOptimizedBackwardSearch(Queue<AbstractState> queue,
                                                   Map<Long, Integer> distances,
                                                   Set<Long> visited) {
        List<AbstractState> startStates = generateStrategicStartStates(5);

        for (AbstractState state : startStates) {
            queue.offer(state);
            visited.add(state.key);
            distances.put(state.key, 0);
        }

        System.out.println("初始化后向搜索，使用 " + startStates.size() + " 个战略起始状态");
    }

    /**
     * 生成战略起始状态
     */
    private List<AbstractState> generateStrategicStartStates(int count) {
        List<AbstractState> states = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            int[] positions = new int[patternSize];

            switch (i % 3) {
                case 0:
                    for (int j = 0; j < patternSize; j++) {
                        positions[j] = j;
                    }
                    break;
                case 1:
                    for (int j = 0; j < patternSize; j++) {
                        positions[j] = size * size - 1 - j;
                    }
                    break;
                case 2:
                    for (int j = 0; j < patternSize; j++) {
                        positions[j] = (j * size + j) % (size * size);
                    }
                    break;
            }

            int blankPos;
            do {
                blankPos = random.nextInt(size * size);
            } while (contains(positions, blankPos));

            states.add(new AbstractState(positions, blankPos));
        }

        return states;
    }

    private boolean contains(int[] array, int value) {
        for (int item : array) {
            if (item == value) return true;
        }
        return false;
    }

    /**
     * 合并优化的数据库
     */
    private void mergeOptimizedDatabases(Map<Long, Integer> forward,
                                         Map<Long, Integer> backward,
                                         boolean foundMeeting,
                                         AbstractState meetingState,
                                         int meetingDistance) {
        optimizedDatabase.putAll(forward);

        for (Map.Entry<Long, Integer> entry : backward.entrySet()) {
            long key = entry.getKey();
            int backwardDist = entry.getValue();
            int forwardDist = forward.getOrDefault(key, Integer.MAX_VALUE);

            optimizedDatabase.put(key, Math.min(forwardDist, backwardDist));
        }

        if (foundMeeting && meetingState != null) {
            optimizedDatabase.put(meetingState.key, meetingDistance);
        }
    }

    /**
     * 生成优化的邻居状态
     */
    private List<AbstractState> generateOptimizedNeighbors(AbstractState state) {
        List<AbstractState> neighbors = new ArrayList<>(4);
        int blankPos = state.blankPos;
        int blankRow = blankPos / size;
        int blankCol = blankPos % size;

        final int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int newRow = blankRow + dir[0];
            int newCol = blankCol + dir[1];

            if (newRow >= 0 && newRow < size && newCol >= 0 && newCol < size) {
                int newBlankPos = newRow * size + newCol;
                int[] newPositions = state.positions.clone();

                boolean isPatternTileMoved = false;
                for (int i = 0; i < patternSize; i++) {
                    if (state.positions[i] == newBlankPos) {
                        newPositions[i] = blankPos;
                        isPatternTileMoved = true;
                        break;
                    }
                }

                if (isPatternTileMoved) {
                    neighbors.add(new AbstractState(newPositions, newBlankPos));
                }
            }
        }
        return neighbors;
    }

    /**
     * 优化的抽象状态内部类
     */
    private class AbstractState {
        final int[] positions;
        final int blankPos;
        final long key;

        AbstractState(int[] positions, int blankPos) {
            this.positions = positions.clone();
            this.blankPos = blankPos;
            this.key = computeOptimizedKey(positions, blankPos);
        }

        private long computeOptimizedKey(int[] positions, int blankPos) {
            long computedKey = blankPos;
            for (int pos : positions) {
                computedKey = (computedKey << 6) | (pos & 0x3F);
            }
            return computedKey;
        }
    }

    /**
     * 从具体状态创建抽象状态
     */
    private AbstractState createAbstractState(PuzzleBoard board) {
        int[] tiles = board.getPuzzleBoard();
        int[] positions = new int[patternSize];

        for (int i = 0; i < patternSize; i++) {
            positions[i] = findPosition(tiles, patternTiles[i]);
        }

        return new AbstractState(positions, board.getZeroPos());
    }

    /**
     * 创建目标抽象状态
     */
    private AbstractState createGoalAbstractState() {
        return new AbstractState(goalPositions, size * size - 1);
    }

    /**
     * 计算目标位置
     */
    private int[] calculateGoalPositions() {
        int[] positions = new int[patternSize];
        for (int i = 0; i < patternSize; i++) {
            positions[i] = patternTiles[i] - 1;
        }
        return positions;
    }

    /**
     * 内存使用优化
     */
    private void optimizeMemoryUsage() {
        // HashMap没有trimToSize方法，跳过
        System.gc();
    }

    /**
     * 性能比较信息
     */
    public void printComparison() {
        if (bfsTime > 0 && bibfsTime > 0) {
            double speedup = (double) bfsTime / bibfsTime;
            System.out.printf("性能比较: 双向BFS比普通BFS快 %.2f 倍\n", speedup);
        }
    }

    /**
     * 计算初始容量（修复方法）
     */
    @Override
    protected int calculateInitialCapacity(int patternLength) {
        int estimatedStates = 10000;
        return (int) (estimatedStates / 0.75f) + 1;
    }
}