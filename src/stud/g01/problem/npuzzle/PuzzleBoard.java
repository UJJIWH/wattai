package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.State;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class PuzzleBoard extends State {
    private final int[] puzzleBoard;
    private final int size;         //问题规模
    private final int zeroPos;
    private final long compressedState; // 添加压缩状态缓存

    public PuzzleBoard(int size, int[] puzzleBoard) {
        if (puzzleBoard == null) {
            throw new IllegalArgumentException("Puzzle board cannot be null");
        }
        if (puzzleBoard.length != size * size) {
            throw new IllegalArgumentException("Puzzle board length does not match board size.");
        }
        this.puzzleBoard = puzzleBoard.clone();
        this.size = size;
        this.zeroPos = getZeroPos();
        this.compressedState = computeCompressedState(); // 计算压缩状态
    }

    public int[] getPuzzleBoard() {
        return puzzleBoard.clone();
    }

    public int getSize() {
        return size;
    }

    /*找到0所在位置*/
    public int getZeroPos(){
        for(int i = 0; i < puzzleBoard.length; i++){
            if(puzzleBoard[i] == 0){
                return i;
            }
        }
        return -1;
    }

    @Override
    public void draw() {
        System.out.println("Puzzle Board (" + size + "x" + size + "):");
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int value = puzzleBoard[i * size + j];
                if (value == 0) {
                    System.out.print("   ");
                } else {
                    System.out.printf("%2d ", value);
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    @Override
    public State next(Action action) {
        if(!(action instanceof PuzzleAction)){
            throw new IllegalArgumentException("Puzzle action must be a PuzzleAction");
        }

        PuzzleAction puzzleAction = (PuzzleAction) action;
        int zeroPos = getZeroPos();
        int zeroRow = zeroPos / size;
        int zeroCol = zeroPos % size;
        /* 找到下一步位置 */
        int targetRow = zeroRow + puzzleAction.getDeltaRow();
        int targetCol = zeroCol + puzzleAction.getDeltaCol();
        int targetPos = targetRow * size + targetCol;
        /* 创建新的board */
        int[] newPuzzleBoard = puzzleBoard.clone();
        newPuzzleBoard[zeroPos] = puzzleBoard[targetPos];
        newPuzzleBoard[targetPos] = 0;

        return new PuzzleBoard(size, newPuzzleBoard);
    }

    @Override
    public Iterable<? extends Action> actions() {
        List<PuzzleAction> actions = new ArrayList<>();
        int zeroPos = getZeroPos();
        int zeroRow = zeroPos / size;
        int zeroCol = zeroPos % size;

        /*检查四个方向并将可用方向填入actions中*/
        if(zeroRow > 0)actions.add(PuzzleAction.UP);
        if(zeroRow < size - 1)actions.add(PuzzleAction.DOWN);
        if(zeroCol > 0)actions.add(PuzzleAction.LEFT);
        if(zeroCol < size - 1)actions.add(PuzzleAction.RIGHT);

        return actions;
    }
    // 在PuzzleBoard类中添加以下方法
    /**
     * 计算压缩状态 - 使用64位长整型存储整个状态
     */

    private long computeCompressedState() {
        long state = 0;
        // 每个瓷砖用4位存储 (0-15)
        for (int i = 0; i < puzzleBoard.length; i++) {
            state = (state << 4) | (puzzleBoard[i] & 0xF);
        }
        return state;
    }

    public long getCompressedState() {
        return compressedState;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PuzzleBoard that = (PuzzleBoard) obj;
        // 使用压缩状态进行比较，提高性能
        return this.compressedState == that.compressedState;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(compressedState);
    }
}
