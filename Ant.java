import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

public class Ant {
	
	public Tile tile;
	
	public boolean isDangered;
	public boolean isIndirectlyDangered;
	public boolean hasMoved;
	public boolean hasMission;
	
	public boolean isDetached = true;
	
	public TreeMap<Integer, Ant> closeEnemyDists = new TreeMap<Integer, Ant>();
	public Tile closestEnemyTile;
	public int closeEnemyDistsSum = 0;
	public Ant closestEnemy;
	public int closestEnemyDist = Integer.MAX_VALUE;
	
	public int numCloseEnemies = 0;
	
	public LinkedList<Ant> gammaDistEnemies = new LinkedList<Ant>();
	
	public boolean isDead;
	public int weakness = 0;
	
	public boolean isReached = false;
	public boolean isGrouped = false;
	public boolean isGammaGrouped = false;
	
	public Tile currTo = null;
	public Tile bestTo = null;
	
	public int compValue;
	
	public Strategy.Mission mission;

	public int numCloseOwnAnts = 0;
	
	public boolean willStay = false;
	
	public HashMap<Tile, List<Tile>> depTable;

	public boolean checkAll;
	public LinkedList<Tile> checkNeighbors;

	public HashMap<Tile, Integer> distMap;
	
	public Ant(Tile tile) {
		this.tile = tile;
	}
	
	public String toString() {
		return "[Ant " + tile.toString() + "]";
	}
}
