import java.util.HashMap;
import java.util.Map;

public enum Direction {
	
	NORTH (-1, 0, 'n', 0),
	EAST (0, 1, 'e', 1),
	SOUTH (1, 0, 's', 2),
	WEST (0, -1, 'w', 3);
	
	private static final Map<Character, Direction> symbolLookup = new HashMap<Character, Direction>();
	private static final Map<Integer, Direction> indexLookup = new HashMap<Integer, Direction>();
	
	static {
		symbolLookup.put('n', NORTH);
		symbolLookup.put('e', EAST);
		symbolLookup.put('s', SOUTH);
		symbolLookup.put('w', WEST);
		indexLookup.put(0, NORTH);
		indexLookup.put(1, EAST);
		indexLookup.put(2, SOUTH);
		indexLookup.put(3, WEST);
	}
	
	public final int dCol;
	public final int dRow;
	public final char symbol;
	public final int index;
	
	private Direction(int dRow, int dCol, char symbol, int index) {
		this.dRow = dRow;
		this.dCol = dCol;
		this.symbol = symbol;
		this.index = index;
	}
	
	public static Direction fromSymbol(char symbol) {
		return symbolLookup.get(symbol);
	}
	
	public static Direction fromIndex(int index) {
		return indexLookup.get(index);
	}
	
	public String toString() {
		return symbol + "";
	}
}
