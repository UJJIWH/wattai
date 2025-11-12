package stud.g01.solver;

import core.problem.Problem;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.util.*;

/**
 * 简化的双向IDA*实现
 * 同时从起点和终点搜索，在中间相遇
 */
public class SimpleBidirectionalIdAStar extends AbstractSearcher {
    private final Predictor predictor;

    public SimpleBidirectionalIdAStar(Frontier frontier, Predictor predictor) {
        super(frontier);
        this.predictor = predictor;
    }

    @Override
    public Deque<Node> search(Problem problem) {
        if (!problem.solvable()) {
            return null;
        }

        // 清理统计信息
        nodesExpanded = 0;
        nodesGenerated = 0;
        explored.clear();

        System.out.println("开始简化双向IDA*搜索...");

        NPuzzleProblem npuzzle = (NPuzzleProblem) problem;

        // 前向搜索（从初始状态到目标状态）
        Node forwardRoot = problem.root(predictor);

        // 后向搜索（从目标状态到初始状态）
        Node backwardRoot = new Node(npuzzle.getGoal(), null, null, 0);
        backwardRoot.setHeuristic(predictor.heuristics(npuzzle.getGoal(), forwardRoot.getState()));

        // 使用两个搜索同时进行
        Map<Long, Node> forwardVisited = new HashMap<>();
        Map<Long, Node> backwardVisited = new HashMap<>();

        int threshold = Math.max(forwardRoot.getHeuristic(), backwardRoot.getHeuristic());

        System.out.println("初始阈值: " + threshold);

        long startTime = System.currentTimeMillis();
        final int TIME_LIMIT_MS = 60000;

        while (threshold < 100) { // 设置合理的阈值上限
            System.out.println("当前阈值: " + threshold);

            // 前向深度受限搜索
            int forwardResult = bidirectionalDepthLimitedSearch(
                    problem, forwardRoot, threshold, 0, forwardVisited, true, backwardVisited);

            // 如果找到相遇点
            if (forwardResult == -1) {
                System.out.println("在前向搜索中找到相遇点");
                return reconstructPath(forwardVisited, backwardVisited);
            }

            // 后向深度受限搜索
            int backwardResult = bidirectionalDepthLimitedSearch(
                    problem, backwardRoot, threshold, 0, backwardVisited, false, forwardVisited);

            if (backwardResult == -1) {
                System.out.println("在后向搜索中找到相遇点");
                return reconstructPath(forwardVisited, backwardVisited);
            }

            // 更新阈值
            threshold = Math.min(forwardResult, backwardResult);

            // 检查超时
            if ((System.currentTimeMillis() - startTime) > TIME_LIMIT_MS) {
                System.out.println("超时（60秒），停止搜索");
                break;
            }
        }

        System.out.println("达到阈值上限，使用标准IDA*");
        // 如果双向搜索失败，回退到标准IDA*
        IdAStar fallback = new IdAStar(frontier, predictor);
        return fallback.search(problem);
    }

    private int bidirectionalDepthLimitedSearch(Problem problem, Node node,
                                                int threshold, int depth,
                                                Map<Long, Node> visited,
                                                boolean isForward,
                                                Map<Long, Node> otherVisited) {
        int f = node.getPathCost() + node.getHeuristic();

        if (f > threshold) {
            return f;
        }

        // 检查是否在另一方向的搜索中访问过（相遇条件）
        long stateKey = ((PuzzleBoard) node.getState()).getCompressedState();
        if (otherVisited.containsKey(stateKey)) {
            // 找到相遇点
            visited.put(stateKey, node);
            return -1;
        }

        visited.put(stateKey, node);
        nodesExpanded++;

        int minExceed = Integer.MAX_VALUE;

        // 生成子节点
        for (Node child : problem.childNodes(node)) {
            // 计算启发式值
            int heuristic;
            if (isForward) {
                heuristic = predictor.heuristics(child.getState(), problem.getGoal());
            } else {
                // 反向搜索时，目标是初始状态
                heuristic = predictor.heuristics(child.getState(),
                        ((NPuzzleProblem) problem).root().getState());
            }
            child.setHeuristic(heuristic);

            nodesGenerated++;

            // 跳过已经在当前方向访问过的状态
            long childKey = ((PuzzleBoard) child.getState()).getCompressedState();
            if (visited.containsKey(childKey)) {
                continue;
            }

            int result = bidirectionalDepthLimitedSearch(problem, child, threshold,
                    depth + 1, visited, isForward, otherVisited);

            if (result == -1) {
                return -1;
            }

            if (result < minExceed) {
                minExceed = result;
            }
        }

        return minExceed;
    }

    private Deque<Node> reconstructPath(Map<Long, Node> forwardVisited,
                                        Map<Long, Node> backwardVisited) {
        // 找到相遇的状态
        for (Map.Entry<Long, Node> entry : forwardVisited.entrySet()) {
            if (backwardVisited.containsKey(entry.getKey())) {
                Node meetingNode = entry.getValue();
                Node backwardNode = backwardVisited.get(entry.getKey());

                // 构建完整路径：前向路径 + 反向路径（反转）
                Deque<Node> path = new ArrayDeque<>();

                // 前向部分
                Node current = meetingNode;
                while (current != null) {
                    path.addFirst(current);
                    current = current.getParent();
                }

                // 后向部分（跳过相遇点）
                current = backwardNode.getParent();
                while (current != null) {
                    path.addLast(current);
                    current = current.getParent();
                }

                return path;
            }
        }

        return null;
    }
}