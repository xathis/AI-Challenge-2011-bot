import java.util.HashSet;

public class Tile {
	
	public int row;
	public int col;
	public Type type = Type.LAND;
	public Ant ant = null;
	public Ant oldAnt = null;
	
	public Tile[] neighbors = new Tile[4];
	
	public boolean isHill;
	public Type hillPlayer;
	
	// dist and prev are not resetted between runs
	public int dist;
	public int hillDist = Integer.MAX_VALUE;
	public Tile prev = null;
	public boolean isReached = false;
	
	// used for food finding
	public Tile source;
	
	// used in explore method
	public int exploreValue = 100;
	public HashSet<Tile> prevFirsts = new HashSet<Tile>();
	public boolean hasNewLand = false;
	
	// used in A*
	public int f;
	
	// used in distribution
	public boolean isReachedByMe;
	
	// used in precalculation
	public boolean hasVirtAnt;
	
	// used in area creation
	public Tile startTile;
	public boolean isInMyArea = false;
	public boolean isBorder;
	
	public int stayTurnCount;
	public int stayValue = -1;
	
	public boolean isChecked; // for dep tables
	
	public Tile(int row, int col) {
		this.row = row;
		this.col = col;
	}
	
	// Direction to a direct neighbor tile
	public Direction dirTo(Tile to) {
		if (to.row == row) {
			if (to.col == col+1) return Direction.EAST;
			if (to.col == col-1) return Direction.WEST;
			return col == 0 ? Direction.WEST : Direction.EAST;
		} else {
			if (to.row == row+1) return Direction.SOUTH;
			if (to.row == row-1) return Direction.NORTH;
			return row == 0 ? Direction.NORTH : Direction.SOUTH;
		}
	}
	
	public void removeNeighbor(Tile n) {
		Tile[] newNeighbors = new Tile[neighbors.length-1];
		int i = 0;
		for (Tile m : neighbors) if (m != n) newNeighbors[i++] = m;
		neighbors = newNeighbors;
	}
	
	public String toString() {
		return "(" + type.symbol + ": " + row + "," + col + ")";
	}
}
