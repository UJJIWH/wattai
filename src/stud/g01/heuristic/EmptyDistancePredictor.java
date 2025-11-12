package stud.g01.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.PuzzleBoard;

/**
 * 空位距离启发式
 * 只考虑必须移动到空位的约束
 */
public class EmptyDistancePredictor implements Predictor {
    @Override
    public int heuristics(State state, State goal) {
        PuzzleBoard current = (PuzzleBoard) state;
        PuzzleBoard target = (PuzzleBoard) goal;
        int size = current.getSize();

        // 找到当前状态和目标状态的空位位置
        int currentZeroPos = current.getZeroPos();
        int goalZeroPos = target.getZeroPos();

        // 计算空位的曼哈顿距离
        int currentRow = currentZeroPos / size;
        int currentCol = currentZeroPos % size;
        int goalRow = goalZeroPos / size;
        int goalCol = goalZeroPos % size;

        return Math.abs(currentRow - goalRow) + Math.abs(currentCol - goalCol);
    }
}