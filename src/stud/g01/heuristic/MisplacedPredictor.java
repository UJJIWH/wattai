package stud.g01.heuristic;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.PuzzleBoard;

/**
 * ´íÎ»Æ´Í¼¿éÊýÁ¿Æô·¢Ê½
 */
public class MisplacedPredictor implements Predictor {
    @Override
    public int heuristics(State state, State goal) {
        PuzzleBoard current = (PuzzleBoard) state;
        PuzzleBoard target = (PuzzleBoard) goal;
        int[] currentTiles = current.getPuzzleBoard();
        int[] goalTiles = target.getPuzzleBoard();
        int misplaced = 0;

        for (int i = 0; i < currentTiles.length; i++) {
            // ºöÂÔ¿Õ°×¿é
            if (currentTiles[i] != 0 && currentTiles[i] != goalTiles[i]) {
                misplaced++;
            }
        }

        return misplaced;
    }
}