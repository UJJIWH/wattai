package stud.g01.problem.npuzzle;

public enum PDirection {
    UP(-1, 0, '¡ü'),
    DOWN(1, 0, '¡ý'),
    LEFT(0, -1, '¡û'),
    RIGHT(0, 1, '¡ú');

    private final int deltaRow;
    private final int deltaCol;
    private final char symbol;

    PDirection(int deltaRow, int deltaCol, char symbol) {
        this.deltaRow = deltaRow;
        this.deltaCol = deltaCol;
        this.symbol = symbol;
    }

    public int getDeltaRow() {
        return deltaRow;
    }

    public int getDeltaCol() {
        return deltaCol;
    }

    public char getSymbol() {
        return symbol;
    }


}
