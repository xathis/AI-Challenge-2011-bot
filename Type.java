import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

// Comment

public enum Type {
	
	UNSEEN (-4, '?'),
	WATER (-3, '%'),
	FOOD (-2, '*'),
	LAND (-1, '.'),
	MY_ANT (0, 'A'),
	PLAYER1 (1, 'B'),
	PLAYER2 (2, 'C'),
	PLAYER3 (3, 'D'),
	PLAYER4 (4, 'E'),
	PLAYER5 (5, 'F'),
	PLAYER6 (6, 'G'),
	PLAYER7 (7, 'H'),
	PLAYER8 (8, 'I'),
	PLAYER9 (9, 'J');
	
	private static final Map<Integer, Type> idLookup = new HashMap<Integer, Type>();
	private static final Map<Character, Type> symbolLookup = new HashMap<Character, Type>();
	
	static {
		for (Type i : EnumSet.allOf(Type.class)) {
			idLookup.put(i.id, i);
			symbolLookup.put(i.symbol, i);
		}
	}
	
	public final int id;
	public final char symbol;

	private Type(int id, char symbol) {
		this.id = id;
		this.symbol = symbol;
	}
	
	public boolean isFree() {
		return id == LAND.id;
	}
	public boolean isEnemy() {
		return id > MY_ANT.id;
	}
	
	public static Type fromId(int id) {
		return idLookup.get(id);
	}
	public static Type fromSymbol(char symbol) {
		return symbolLookup.get(symbol);
	}
	
}
