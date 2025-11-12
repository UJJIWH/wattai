package stud.g01.queue;

import core.problem.State;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Map;

/**
 * 优化的优先队列Frontier实现
 * 使用HashMap快速查找，PriorityQueue维护顺序
 */
public class PqFrontier implements Frontier {
    private final PriorityQueue<Node> priorityQueue;
    private final Map<State, Node> stateMap;
    private final Comparator<Node> evaluator;

    public PqFrontier(Comparator<Node> evaluator) {
        this.evaluator = evaluator;
        this.priorityQueue = new PriorityQueue<>(evaluator);
        this.stateMap = new HashMap<>();
    }

    @Override
    public Node poll() {
        Node node = priorityQueue.poll();
        if (node != null) {
            stateMap.remove(node.getState());
        }
        return node;
    }

    @Override
    public void clear() {
        priorityQueue.clear();
        stateMap.clear();
    }

    @Override
    public int size() {
        return priorityQueue.size();
    }

    @Override
    public boolean isEmpty() {
        return priorityQueue.isEmpty();
    }

    @Override
    public boolean contains(Node node) {
        return stateMap.containsKey(node.getState());
    }

    @Override
    public boolean offer(Node node) {
        State state = node.getState();
        Node existing = stateMap.get(state);

        if (existing == null) {
            // 新状态，直接插入
            boolean added = priorityQueue.offer(node);
            if (added) {
                stateMap.put(state, node);
            }
            return added;
        } else {
            // 已存在相同状态，选择更好的节点
            if (evaluator.compare(node, existing) < 0) {
                // 新节点更好，替换旧节点
                boolean removed = priorityQueue.remove(existing);
                if (removed) {
                    stateMap.remove(state);
                    boolean added = priorityQueue.offer(node);
                    if (added) {
                        stateMap.put(state, node);
                    }
                    return added;
                }
            }
            return false;
        }
    }
}