package stud.g01.solver.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.PuzzleBoard;

/**
 * 曼哈顿距离启发式
 */
public class ManhattanPredictor implements Predictor {
    @Override
    public int heuristics(State state, State goal) {
        PuzzleBoard current = (PuzzleBoard) state;
        PuzzleBoard target = (PuzzleBoard) goal;
        int size = current.getSize();
        int distance = 0;

        int[] currentTiles = current.getPuzzleBoard();
        int[] goalTiles = target.getPuzzleBoard();

        for (int i = 0; i < currentTiles.length; i++) {
            int value = currentTiles[i];
            if (value != 0) {
                // 找到该值在目标状态中的位置
                int goalPos = findPosition(goalTiles, value);
                int currentRow = i / size;
                int currentCol = i % size;
                int goalRow = goalPos / size;
                int goalCol = goalPos % size;

                distance += Math.abs(currentRow - goalRow) + Math.abs(currentCol - goalCol);
            }
        }

        return distance;
    }

    private int findPosition(int[] tiles, int value) {
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == value) return i;
        }
        return -1;
    }
}