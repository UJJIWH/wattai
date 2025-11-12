package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.solver.queue.Node;

import java.util.Deque;

public class NPuzzleProblem extends Problem {
    public NPuzzleProblem(State initialState, State goal) {
        super(initialState, goal);
    }

    public NPuzzleProblem(State initialState, State goal, int size) {
        super(initialState, goal, size);
    }

    public State getGoal() {
        return goal;
    }
    @Override
    public boolean solvable() {
        return isSolvable((PuzzleBoard) initialState,(PuzzleBoard) goal);
    }

    private boolean isSolvable(PuzzleBoard start, PuzzleBoard goal) {
        int startInversions = countInversions(start.getPuzzleBoard());
        int goalInversions = countInversions(goal.getPuzzleBoard());
        int size = start.getSize();

        if (size % 2 == 1) {
            // 奇数维度：逆序数奇偶性相同则有解
            return (startInversions % 2) == (goalInversions % 2);
        } else {
            // 偶数维度：考虑空白行位置
            int startBlankRow = start.getZeroPos() / size;
            int goalBlankRow = goal.getZeroPos() / size;
            int rowDifference = Math.abs(startBlankRow - goalBlankRow);

            return (startInversions % 2) == ((goalInversions + rowDifference) % 2);
        }
    }

    /**
     * 使用归并排序计算逆序数 - O(n log n) 复杂度
     * @param board 拼图块数组
     * @return 逆序数
     */
    private int countInversions(int[] board) {
        // 创建一个不包含0的数组用于计算逆序数
        int[] nonZeroBoard = new int[board.length - 1];
        int index = 0;
        for (int tile : board) {
            if (tile != 0) {
                nonZeroBoard[index++] = tile;
            }
        }

        return mergeSortAndCount(nonZeroBoard, 0, nonZeroBoard.length - 1);
    }

    /**
     * 使用归并排序计算逆序数
     * @param arr 数组
     * @param left 左边界
     * @param right 右边界
     * @return 逆序数
     */
    private int mergeSortAndCount(int[] arr, int left, int right) {
        int count = 0;
        if (left < right) {
            int mid = left + (right - left) / 2;

            // 左半部分的逆序数
            count += mergeSortAndCount(arr, left, mid);
            // 右半部分的逆序数
            count += mergeSortAndCount(arr, mid + 1, right);
            // 合并时的逆序数
            count += mergeAndCount(arr, left, mid, right);
        }
        return count;
    }

    /**
     * 合并两个已排序的数组并计算逆序数
     * @param arr 数组
     * @param left 左边界
     * @param mid 中间位置
     * @param right 右边界
     * @return 合并过程中的逆序数
     */
    private int mergeAndCount(int[] arr, int left, int mid, int right) {
        // 左右子数组
        int[] leftArr = new int[mid - left + 1];
        int[] rightArr = new int[right - mid];

        System.arraycopy(arr, left, leftArr, 0, leftArr.length);
        System.arraycopy(arr, mid + 1, rightArr, 0, rightArr.length);

        int i = 0, j = 0, k = left;
        int swaps = 0;

        while (i < leftArr.length && j < rightArr.length) {
            if (leftArr[i] <= rightArr[j]) {
                arr[k++] = leftArr[i++];
            } else {
                arr[k++] = rightArr[j++];
                // 当左子数组的当前元素大于右子数组的当前元素时，
                // 左子数组中从当前元素到末尾的所有元素都会与右子数组的当前元素形成逆序对
                swaps += (mid + 1) - (left + i);
            }
        }

        // 复制剩余元素
        while (i < leftArr.length) {
            arr[k++] = leftArr[i++];
        }
        while (j < rightArr.length) {
            arr[k++] = rightArr[j++];
        }

        return swaps;
    }

    @Override
    public int stepCost(State state, Action action) {
        return 1;
    }

    @Override
    public boolean applicable(State state, Action action) {
        if (!(state instanceof PuzzleBoard) || !(action instanceof PuzzleAction)) {
            return false;
        }

        PuzzleBoard board = (PuzzleBoard) state;
        PuzzleAction puzzleAction = (PuzzleAction) action;
        int blankPos = board.getZeroPos();
        int row = blankPos / board.getSize();
        int col = blankPos % board.getSize();

        // 检查移动是否在边界内
        PDirection direction = puzzleAction.getDirection();
        switch (direction) {
            case UP: return row > 0;
            case DOWN: return row < board.getSize() - 1;
            case LEFT: return col > 0;
            case RIGHT: return col < board.getSize() - 1;
            default: return false;
        }
    }

    @Override
    public void showSolution(Deque<Node> path) {
        System.out.println("=== NPuzzle Solution ===");
        System.out.println("Total steps: " + (path.size() - 1));
        System.out.println();

        int step = 0;
        for (Node node : path) {
            System.out.println("Step " + step + ":");
            if (node.getAction() != null) {
                System.out.println("Action: " + node.getAction());
            }
            node.getState().draw();
            step++;
        }

        System.out.println("Solution completed!");
    }
}
