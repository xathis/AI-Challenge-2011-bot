import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

public class Strategy {
	
	public static boolean DEBUG = false;
	
	private static final int CLOSE_ENEMY_RADIUS = 9;
	private static final int CLOSE_ENEMY_RADIUS2 = (int)Math.pow(CLOSE_ENEMY_RADIUS, 2);
	private static final int AREA_DIST = 20;
	
	//private int turns = 0;
	private int rows = 0;
	private int cols = 0;
	//private int loadtime = 0;
	//private int turntime = 0;
	//private int viewradius2 = 0;
	//private int attackradius2 = 0;
	//private int spawnradius2 = 0;
	//private long player_seed = 0;
	
	private static int distributeCheckDist;
	public long startTime;
	private boolean isTimeout;
	private Random turnRandom;
	private int turn;
	private boolean isMissionPhase = false;
	private Tile[][] map;
	private LinkedList<Ant> ants;
	private LinkedList<Tile> foods;
	private LinkedList<Tile> hills;
	private LinkedList<Tile> myHills = new LinkedList<Tile>();
	private LinkedList<Tile> enemyHills = new LinkedList<Tile>();
	private LinkedList<Ant> myAnts = new LinkedList<Ant>();
	private LinkedList<Ant> enemyAnts = new LinkedList<Ant>();
	private LinkedList<Ant> dangeredAnts = new LinkedList<Ant>();
	private Collection<Ant> enemyAntsOrdered;
	private LinkedList<Area> areas = new LinkedList<Strategy.Area>();
	private LinkedList<Area> fightAreas = new LinkedList<Strategy.Area>();
	private LinkedList<Mission> missions = new LinkedList<Mission>();
	private Connection conn;
	private Logger logger;
	
	public void init(Connection conn) {
		this.conn = conn;
		rows = conn.rows;
		cols = conn.cols;
		
		String s = new SimpleDateFormat("MM-dd_HH-mm-ss").format(new Date());
		logger = new Logger("logs/log_" + s + "_" + new Random().nextInt(100) + ".txt");
	}
	
	public void doTurn(Connection conn) {
		map = conn.map;
		ants = conn.ants;
		hills = conn.hills;
		foods = conn.foods;
		turn = conn.turn;
		
		logger.println("============== turn " + turn);
		actions();
		logger.println("=" + (System.currentTimeMillis()-startTime) + "ms");
	}
	
	private void initTurn() {
		myAnts.clear();
		enemyAnts.clear();
		dangeredAnts.clear();
		myHills.clear();
		enemyHills.clear();
		gammaGroups.clear();
		
		isTimeout = false;
		turnRandom = new Random(turn);
		
		for (Ant ant : ants) {
			if (ant.tile.type == Type.MY_ANT) myAnts.add(ant);
			else enemyAnts.add(ant);
		}
		logger.println("my hills: ");
		for (Tile hill : hills) {
			if (hill.hillPlayer == Type.MY_ANT) {myHills.add(hill); logger.print(hill.toString());}
			else enemyHills.add(hill);
		}
		logger.println("");
		
		// my - my
		Iterator<Ant> a = myAnts.iterator();
		while (a.hasNext()) {
			Ant ant1 = a.next();
			
			Iterator<Ant> b = myAnts.descendingIterator();
			Ant ant2 = b.next();
			while (ant1 != ant2) {
				int d1 = distRow(ant1.tile, ant2.tile);
				if (d1 <= 5) {
					int d2 = distCol(ant1.tile, ant2.tile);
					if (d2 <= 5) {
						ant1.numCloseOwnAnts++;
						ant2.numCloseOwnAnts++;
					}
				}
				ant2 = b.next();
			}
		}
		
		// my - enemy
		for (Ant myAnt : myAnts) {
			myAnt.tile.oldAnt = myAnt;
			for (Ant enemyAnt : enemyAnts) {
				int dy = distRow(myAnt.tile, enemyAnt.tile);
				if (dy > CLOSE_ENEMY_RADIUS) continue;
				int dx = distCol(myAnt.tile, enemyAnt.tile);
				if (dx > CLOSE_ENEMY_RADIUS) continue;
				int dist = dy*dy + dx*dx;
				if (dist <= CLOSE_ENEMY_RADIUS2) {
					myAnt.closeEnemyDists.put(dist, enemyAnt);
					enemyAnt.closeEnemyDists.put(dist, myAnt);
					myAnt.closeEnemyDistsSum += dist;
					enemyAnt.closeEnemyDistsSum += dist;
					if (dx + dy <= 5 && !((dx == 0 && dy == 5) || (dy == 0 && dx == 5))) {
						if (!myAnt.isIndirectlyDangered) myAnt.isIndirectlyDangered = true;
						myAnt.gammaDistEnemies.add(enemyAnt);
						enemyAnt.gammaDistEnemies.add(myAnt);
						if (!myAnt.isDangered && dx + dy <= 4 && !((dx == 0 && dy == 4) || (dy == 0 && dx == 4))) {
							myAnt.isDangered = true;
							dangeredAnts.add(myAnt);
						}
					}
				}
			}
			if (!myAnt.closeEnemyDists.isEmpty())
				myAnt.closestEnemyTile = myAnt.closeEnemyDists.firstEntry().getValue().tile;
		}
		
		TreeMap<Integer, Ant> enemyAntsOrderedMap = new TreeMap<Integer, Ant>();
		
		// enemy - enemy
		a = enemyAnts.iterator();
		while (a.hasNext()) {
			Ant ant1 = a.next();
			
			enemyAntsOrderedMap.put(-ant1.closeEnemyDists.size()*CLOSE_ENEMY_RADIUS +ant1.closeEnemyDistsSum, ant1);
			
			Iterator<Ant> b = enemyAnts.descendingIterator();
			Ant ant2 = b.next();
			while (ant1 != ant2) {
				int d1 = distRow(ant1.tile, ant2.tile);
				if (d1 <= 5) {
					int d2 = distCol(ant1.tile, ant2.tile);
					if (d2 <= 5) {
						ant1.isDetached = false;
						ant2.isDetached = false;
						break;
					}
				}
				ant2 = b.next();
			}
		}
		enemyAntsOrdered = enemyAntsOrderedMap.values();
		
		distributeCheckDist = (myAnts.size() < 160) ? 9 : 8;
		for (Tile[] row : map) for (Tile tile : row) {
			tile.exploreValue++;
			tile.isBorder = false;
			if (tile.ant == null) tile.stayValue = -1;
		}
		
		visualize("v setLineColor 0x99 0 0 0.5");
		visualize("v setLineWidth 2");
		for (Ant enemy : enemyAnts) {
			int currStayValue = 0;
			for (int i = 0; i < enemy.tile.neighbors.length; i++) 
				currStayValue |= (enemy.tile.neighbors[i].type.isEnemy() ? 1 : 0) << i;
			
			if (enemy.tile.stayValue == currStayValue) {
				enemy.tile.stayTurnCount++;
				if (enemy.tile.stayTurnCount >= 5) {
					enemy.willStay = true;
					//logger.println(enemy + " will stay");
					visualize("v circle "+enemy.tile.row+" "+enemy.tile.col+" 1 false");
				}
			} else {
				enemy.tile.stayValue = currStayValue;
				enemy.tile.stayTurnCount = 0;
				enemy.willStay = false;
			}
		}
	}
	private void actions() {
		initTurn();
		calcNumCloseEnemies();
		initMissions();
		enemyHills();
		food();
		initExplore();
		createAreas();
		fight();
		defence();
		approachEnemies();
		attackDetachedEnemies();
		escapeEnemies();
		distribute(true);
		explore();
		doMissions();
		createMissions();
		distribute(false);
		cleanAreas();
	}
	
	private void calcNumCloseEnemies() {
		for (Ant enemy : enemyAnts) {
			if (enemy.closeEnemyDists.size() == 0) continue;
			LinkedList<Tile> openList = new LinkedList<Tile>();
			LinkedList<Tile> changedTiles = new LinkedList<Tile>();
			Tile enemyTile = enemy.tile;
			openList.add(enemyTile);
			enemyTile.dist = 0;
			enemyTile.isReached = true;
			changedTiles.add(enemyTile);
			while (!openList.isEmpty()) {
				Tile tile = openList.removeFirst();
				if (tile.dist >= 12) break;
				for (Tile n : tile.neighbors) {
					if (n.isReached) continue;
					n.isReached = true;
					n.dist = tile.dist + 1;
					changedTiles.add(n);
					openList.add(n);
					if (n.type == Type.MY_ANT) {
						n.ant.numCloseEnemies++;
						if (n.ant.closestEnemyDist > n.dist) {
							n.ant.closestEnemy = enemy;
							n.ant.closestEnemyDist = n.dist;
						}
					}
				}
			}
			for (Tile tile : changedTiles) tile.isReached = false;
		}
	}
	
	class Mission {
		Tile target;
		Tile currTile;
		int lastUpdated;
		boolean isRemoved;
	}
	
	private void initMissions() {
		ListIterator<Mission> it = missions.listIterator();
		while (it.hasNext()) {
			Mission m = it.next();
			if (m.isRemoved) {
				it.remove();
				continue;
			}
			Ant ant = m.currTile.ant;
			if (ant == null) {
				it.remove();
				logger.println("remove mission because no ant at " + m.currTile);
				continue;
			}
			ant.hasMission = true;
			ant.mission = m;
		}
	}
	private void doMissions() {
		isTimeout = false;
		isMissionPhase = true;
		for (Mission m : missions) doMission(m);
		isMissionPhase = false;
	}
	private void doMission(Mission m) {
		if (m.isRemoved) return;
		Ant ant = m.currTile.ant;
		long time = System.currentTimeMillis()-startTime;
		if (time > 470) isTimeout = true;
		if (isTimeout || ant.hasMoved) {
			ant.hasMoved = true;
			return;
		}
		if (turn - m.lastUpdated >= 10 || time < 250) updateMission(m);
		Tile dest = aStar2(ant.tile, m.target, true);
		if (dest == null) { m.isRemoved = true; ant.hasMission = false; logger.println("remove mission because cannot savely reach " + m.target + " from " + m.currTile); return; }
		ant.hasMission = true;
		//System.out.println("v arrow " + ant.tile.row + " " + ant.tile.col + " " + m.target.row + " " + m.target.col);
		doMove(ant.tile, dest, "mission to " + m.target);
		if (dest == m.target) m.isRemoved = true; 
		else m.currTile = dest;
	}
	private void updateMission(Mission m) {
		logger.println("update mission from " + m.currTile + " to " + m.target);
		Tile searchStartTile = m.target.type == Type.WATER ? m.currTile : m.target;
		Tile borderTile = findBorder(searchStartTile);
		m.lastUpdated = turn;
		if (borderTile != null) m.target = borderTile;
		else logger.println("borderTile = null !");
	}
	private void createMissions() {
		if (isTimeout) return;
		for (Area area : fightAreas) {
			if (area.ants.size() < 2 || area.border.size() == 0) continue;
			for (Ant ant : area.ants) {
				if (ant.hasMission || ant.hasMoved) continue;
				Tile target;
				if (ant.tile.isHill) target = area.border.get(turnRandom.nextInt(area.border.size()));
				else target = findBorder(ant.tile);
				if (target == null) continue;
				Tile dest = aStar2(ant.tile, target, true);
				if (dest == null) continue;
				doMove(ant.tile, dest, "new created mission");
				ant.hasMission = true;
				
				Mission m = new Mission();
				m.currTile = dest;
				m.target = target;
				m.lastUpdated = turn;
				missions.add(m);
				//logger.println("create mission from " + m.currTile + " to " + m.target);
			}
		}
	}
	private Tile findBorder(Tile startTile) {
		//logger.println("search border from " + startTile);
		if (startTile.isBorder) return startTile;
		final int maxDist = 400;
		Tile borderTile = null;
		LinkedList<Tile> openList = new LinkedList<Tile>();
		LinkedList<Tile> changedTiles = new LinkedList<Tile>();
		openList.add(startTile);
		startTile.dist = 0;
		startTile.isReached = true;
		changedTiles.add(startTile);
		while (!openList.isEmpty()) {
			Tile tile = openList.removeFirst();
			if (tile.dist >= maxDist) break;
			for (Tile n : tile.neighbors) {
				if (n.isBorder) {
					borderTile = n;
					break;
				}
				if (n.isReached) continue;
				n.isReached = true;
				n.dist = tile.dist + 1;
				changedTiles.add(n);
				openList.add(n);
			}
			if (borderTile != null) break;
		}
		for (Tile tile : changedTiles) tile.isReached = false;
		return borderTile;
	}
	
	private void defence() {
		if (myHills.size() > 4) return;
		for (Tile hill : myHills) defendHill(hill);
	}
	private Comparator<Tile> defComp = new Comparator<Tile>() {
		public int compare(Tile a, Tile b) {
			return ((a.hillDist - b.hillDist) << 10) + turnRandom.nextInt(100);
		}
	};
	private void defendHill(Tile hill) {
		TreeSet<Tile> dangerousEnemyTiles = new TreeSet<Tile>(defComp);
		{
			LinkedList<Tile> openList = new LinkedList<Tile>();
			LinkedList<Tile> changedTiles = new LinkedList<Tile>();
			openList.add(hill);
			hill.hillDist = 0;
			hill.isReached = true;
			hill.prev = null;
			changedTiles.add(hill);
			while (!openList.isEmpty()) {
				Tile tile = openList.removeFirst();
				if (tile.hillDist >= 14) break;
				for (Tile n : tile.neighbors) {
					if (n.isReached) continue;
					n.isReached = true;
					n.hillDist = tile.hillDist + 1;
					if (n.hillDist <= 10) n.exploreValue = 0;
					n.prev = tile;
					changedTiles.add(n);
					openList.add(n);
					if (n.type.isEnemy()) dangerousEnemyTiles.add(n);
				}
			}
			
			//visualize("v setFillColor 0 255 0 0.075");
			for (Tile tile : changedTiles) {
				//visualize("v tile "+tile.row+" "+tile.col+" true");
				tile.isReached = false;
			}
		}
		
		enemyLoop : for (Tile enemyTile : dangerousEnemyTiles) {
			int dist = enemyTile.hillDist;
			//logger.println(enemyTile + " " + enemyTile.hillDist + " " + hill);
			int halfDist = dist / 2;
			int quarterDist = dist / 4;
			Tile halfTile = enemyTile;
			if (halfDist > 1) while (dist-- > halfDist) halfTile = halfTile.prev;
			else halfTile = hill;
			Tile quarterTile = halfTile;
			if (quarterDist > 1) while (dist-- > quarterDist) quarterTile = quarterTile.prev;
			else quarterTile = hill;
			
			Tile t = enemyTile;
			boolean found = false;
			Ant defender = null;
			prevLoop : while (!t.isHill) {
				if (t.type == Type.MY_ANT && !t.ant.hasMoved) {
					found = true;
					defender = t.ant;
					break prevLoop;
				} else if (t.type == Type.MY_ANT && t.ant.hasMoved && t.ant.isGrouped && t.hillDist > 3) {
					found = true;
					continue enemyLoop;
				}
				if (!found) {
					for (Tile n : t.neighbors) {
						if (n.type == Type.MY_ANT && !n.ant.hasMoved) {
							found = true;
							defender = n.ant;
							break prevLoop;
						} else if (n.type == Type.MY_ANT && n.ant.hasMoved && n.ant.isGrouped && t.hillDist > 3) {
							found = true;
							continue enemyLoop;
						}
					}
				}
				t = t.prev;
			}
			if (!found) {
				// search defender
				//if (quarterTile == null) logger.println("ERROR: " + hill + " " + enemyTile + " " + dist + " " + halfDist + " " + quarterDist);
				LinkedList<Tile> openList = new LinkedList<Tile>();
				LinkedList<Tile> changedTiles = new LinkedList<Tile>();
				openList.add(quarterTile);
				quarterTile.dist = 0;
				quarterTile.isReached = true;
				changedTiles.add(quarterTile);
				while (!openList.isEmpty()) {
					Tile tile = openList.removeFirst();
					if (tile.dist >= 30) break;
					for (Tile n : tile.neighbors) {
						if (n.isReached) continue;
						if (n.type == Type.MY_ANT && !n.ant.hasMoved) {
							defender = n.ant;
							found = true;
							break;
						}
						n.isReached = true;
						n.dist = tile.dist + 1;
						changedTiles.add(n);
						openList.add(n);
					}
					if (found) break;
				}
				for (Tile tile : changedTiles) tile.isReached = false;
			}
			if (!found) continue;
			
			visualize("v setLineColor 0xCC 0xCC 0xCC 0.8");
			visualize("v setLineWidth 1");
			visualize("v circle " + defender.tile.row + " " + defender.tile.col + " 0.8 false");
			Tile dest = aStar(defender.tile, halfTile, true);
			if (dest == null || isSuicide(defender, dest)) continue;
			doMove(defender.tile, dest, "defend");
		}
	}
	
	private void enemyHills() {
		for (Tile hillTile : enemyHills) {
			int count = myAnts.size() <= 10 ? 1 : 4;
			LinkedList<Tile> changedTiles = new LinkedList<Tile>();
			LinkedList<Tile> openList = new LinkedList<Tile>();
			hillTile.dist = 0;
			hillTile.isReached = true;
			openList.add(hillTile);
			changedTiles.add(hillTile);
			while (!openList.isEmpty()) {
				Tile tile = openList.removeFirst();
				if (tile.dist >= 20) break;
				for (Tile n : tile.neighbors) {
					if (n.isReached) continue;
					n.isReached = true;
					if (n.type == Type.MY_ANT) {
						if (!n.ant.hasMoved && tile.type != Type.MY_ANT && isTileSafe(n.ant, tile)) 
							doMove(n, tile, "enemy hill");
						count--;
					}
					n.dist = tile.dist + 1;
					changedTiles.add(n);
					openList.add(n);
				}
				if (count <= 0) break;
			}
			for (Tile tile : changedTiles) tile.isReached = false;
		}
	}
	
	private void food() {
		HashMap<Tile, Boolean> isEnemyNearFood = new HashMap<Tile, Boolean>();
		LinkedList<Tile> openList = new LinkedList<Tile>();
		LinkedList<Tile> changedTiles = new LinkedList<Tile>();
		for (Tile food : foods) {
			openList.add(food);
			food.dist = 0;
			food.isReached = true;
			food.source = food;
			isEnemyNearFood.put(food, false);
			changedTiles.add(food);
		}
		while (!openList.isEmpty()) {
			Tile tile = openList.removeFirst();
			if (tile.dist <= 2 && tile.type.isEnemy()) isEnemyNearFood.put(tile.source, true);
			if (tile.dist > 2 && isEnemyNearFood.get(tile.source)) {
				Iterator<Tile> it = changedTiles.iterator();
				while (it.hasNext()) {
					Tile t = it.next();
					if (t.source == tile.source) {
						t.isReached = false;
						it.remove();
					}
				}
				it = openList.iterator();
				while (it.hasNext()) if (!it.next().isReached) it.remove();
			}
			else if (tile.type == Type.MY_ANT && !tile.ant.hasMoved && 
					tile.prev.type != Type.MY_ANT && !isSuicide(tile.ant, tile.prev)) {
				if (tile.prev == tile.source) tile.ant.hasMoved = true;
				else {
					Ant enemyAnt = isTileSafe2(tile.ant, tile.prev);
					if (enemyAnt != null) {
						// tile is not safe
						Collection<Ant> closeAnts = getCloseAntDists(tile.source).values();
						boolean iHaveBackup = false;
						for (Ant closeAnt : closeAnts) {
							if (closeAnt == enemyAnt || closeAnt == tile.ant) continue;
							if (closeAnt.tile.type == Type.MY_ANT) iHaveBackup = true;
							break;
						}
						if (iHaveBackup) doMove(tile, tile.prev, "food");
					}
					else doMove(tile, tile.prev, "food"); // tile is safe
				}
				Iterator<Tile> it = changedTiles.iterator();
				while (it.hasNext()) {
					Tile t = it.next();
					if (t.source == tile.source) {
						t.isReached = false;
						it.remove();
					}
				}
				it = openList.iterator();
				while (it.hasNext()) if (!it.next().isReached) it.remove();
			}
			else if (tile.dist < 13) {
				for (Tile n : tile.neighbors) {
					if (n.isReached) continue;
					n.isReached = true;
					n.prev = tile;
					n.dist = tile.dist + 1;
					n.source = tile.source;
					changedTiles.add(n);
					openList.add(n);
				}
			}
		}
		for (Tile tile : changedTiles) tile.isReached = false;
	}
	
	private void attackDetachedEnemies() {
		if (!isTimeout) 
			for (Ant enemyAnt : enemyAntsOrdered) 
				if (enemyAnt.isDetached) 
					if (enemyAnt.closeEnemyDists.size() > 2) 
						attackEnemy(enemyAnt);
	}
	
	private void escapeEnemies() {
		for (Ant ant : dangeredAnts) if (!ant.hasMoved) escapeAnt(ant);
	}
	private void escapeAnt(Ant myAnt) {
		final int ESCAPE_CHECK_DIST = 8;
		HashMap<Tile, Integer> values;
		if (DEBUG) values = new LinkedHashMap<Tile, Integer>();
		else values = new HashMap<Tile, Integer>();
		LinkedList<Tile> openList = new LinkedList<Tile>();
		LinkedList<Tile> changedTiles = new LinkedList<Tile>();
		Tile antTile = myAnt.tile;
		antTile.isReached = true;
		antTile.dist = 0;
		changedTiles.add(antTile);
		for (Tile n : antTile.neighbors) {
			values.put(n, 0);
			openList.add(n);
			n.dist = 1;
			n.isReached = true;
			n.prevFirsts.add(n);
			changedTiles.add(n);
		}
		while (!openList.isEmpty()) {
			Tile tile = openList.removeFirst();
			if (tile.dist >= ESCAPE_CHECK_DIST) break;
			for (Tile n : tile.neighbors) {
				if (n.isReached) {
					if (n.dist == tile.dist + 1) n.prevFirsts.addAll(tile.prevFirsts);
					continue;
				}
				n.isReached = true;
				n.prev = tile;
				n.dist = tile.dist + 1;
				n.prevFirsts.addAll(tile.prevFirsts);
				changedTiles.add(n);
				openList.add(n);
			}
		}
		for (Tile tile : changedTiles) {
			int addValue = (ESCAPE_CHECK_DIST+1-tile.dist);
			if (tile.type == Type.MY_ANT) addValue *= 3;
			else if (tile.type.isEnemy()) addValue *= -3;
			for (Tile prevFirst : tile.prevFirsts) 
				values.put(prevFirst, values.get(prevFirst) + addValue);
			tile.isReached = false;
			tile.prevFirsts.clear();
		}
		int bestValue = Integer.MIN_VALUE;
		Tile bestDest = null;
		for (Entry<Tile, Integer> entry : values.entrySet()) {
			if (entry.getKey().type.isFree() && isTileSafe(myAnt, entry.getKey()) && entry.getValue() > bestValue) {
				bestValue = entry.getValue();
				bestDest = entry.getKey();
			}
		}
		
		if (bestValue != 0 && bestDest != null) {
			doMove(antTile, bestDest, "escape (" + bestValue + ")");
		} else {
			if (!isSuicide(myAnt, myAnt.tile)) {
				myAnt.hasMoved = true;
				logger.println(myAnt + " escape stay");
			}
			else for (Tile dest : myAnt.tile.neighbors) {
				if (dest.type.isFree() && !isSuicide(myAnt, dest)) {
					doMove(myAnt.tile, dest, "escape kill");
					break;
				}
			}
		}
		
	}
	
	private class Area {
		LinkedList<Ant> ants = new LinkedList<Ant>();
		LinkedList<Tile> tiles = new LinkedList<Tile>();
		boolean containsHill = false; // only true if contains own hill
		Tile hill = null;
		LinkedList<Tile> border = new LinkedList<Tile>();
		boolean isChecked = false;
		Type player = null;
	}
	
	private void createAreas() {
		HashMap<Tile, Area> areaMap = new HashMap<Tile, Area>();
		{
			LinkedList<Tile> openList = new LinkedList<Tile>();
			LinkedList<Tile> changedTiles = new LinkedList<Tile>();
			
			for (Ant ant : enemyAnts) openList.add(ant.tile);
			for (Ant ant : myAnts) {
				openList.add(ant.tile);
				ant.tile.isInMyArea = true;
			}
			for (Tile tile : openList) {
				tile.dist = 0;
				tile.isReached = true;
				changedTiles.add(tile);
				tile.startTile = tile;
				Area area = new Area();
				if (tile.isHill && tile.hillPlayer == Type.MY_ANT) {
					area.containsHill = true;
					area.hill = tile;
				}
				area.player = tile.type;
				area.ants.add(tile.ant);
				area.tiles.add(tile);
				areaMap.put(tile, area);
			}
			while (!openList.isEmpty()) {
				Tile tile = openList.removeFirst();
				if (tile.dist >= AREA_DIST) break;
				for (Tile n : tile.neighbors) {
					if (n.isReached) {
						if (n.startTile != tile.startTile && n.startTile.type == tile.startTile.type && 
								areaMap.get(n.startTile) != areaMap.get(tile.startTile)) {
							Area nArea = areaMap.get(n.startTile);
							Area tileArea = areaMap.get(tile.startTile);
							tileArea.ants.addAll(nArea.ants);
							tileArea.tiles.addAll(nArea.tiles);
							for (Ant ant : nArea.ants) areaMap.put(ant.tile, tileArea);
							if (nArea.containsHill) {
								tileArea.containsHill = true;
								tileArea.hill = nArea.hill;
							}
						}
					} else {
						n.isReached = true;
						n.dist = tile.dist + 1;
						n.startTile = tile.startTile;
						areaMap.get(n.startTile).tiles.add(n);
						if (n.startTile.type == Type.MY_ANT) n.isInMyArea = true;
						if (n.isHill && n.hillPlayer == Type.MY_ANT) {
							areaMap.get(n.startTile).containsHill = true;
							areaMap.get(n.startTile).hill = n;
						}
						changedTiles.add(n);
						openList.add(n);
					}
				}
			}
			for (Tile tile : changedTiles) tile.isReached = false;
		}
		
		areas.clear();
		fightAreas.clear();
		for (Area area : areaMap.values()) {
			if (area.isChecked) continue;
			area.isChecked = true;
			areas.add(area);
			//logger.println("area " + area.ants.get(0) + " " + area.ants.size() + " " + area.player + " " + area.containsHill);
			if (area.player == Type.MY_ANT && (area.containsHill || area.ants.size() >= 5)) {
				fightAreas.add(area);
			}
		}
		
		// determine border
		//System.out.println("v setFillColor 0 255 0 0.075");
		for (Area area : fightAreas) {
			LinkedList<Tile> changedTiles = new LinkedList<Tile>();
			for (Tile tile : area.tiles) {
				//System.out.println("v tile "+tile.row+" "+tile.col+" true");
				for (Tile n : tile.neighbors) {
					if (n.type != Type.WATER && !n.isInMyArea) {
						tile.dist = 0;
						tile.isReached = true;
						area.border.add(tile);
						tile.isBorder = true;
						
						changedTiles.add(tile);
						break;
					}
				}
			}
			for (Tile tile : changedTiles) tile.isReached = false;
		}
	}
	
	private void cleanAreas() {
		for (Area area : areas) 
			if (area.player == Type.MY_ANT) 
				for (Tile tile : area.tiles) tile.isInMyArea = false;
	}
	
	private boolean beAgressive = false;
	
	private void fight() {
		for (Area area : fightAreas) {
			for (Ant startAnt : area.ants) {
				if (startAnt.hasMoved || startAnt.gammaDistEnemies.size() == 0 || startAnt.numCloseEnemies == 0 || startAnt.isGammaGrouped) continue;
				GammaGroup group = findGammaGroup(startAnt);
				createDepTables(group);
				for (Ant ant : group.myAnts) if (ant.checkNeighbors.size() == 0) ant.isGrouped = true; // workaround
				
				for (Ant ant : group.myAnts) {
					if (ant.isGrouped) continue;
					
					if (System.currentTimeMillis()-startTime > 420) isTimeout = true;
					if (isTimeout) break;
					
					findPrecGroup(ant);
					
					if (myAntsPrec.size() == 0) continue;
					if (myAntsPrec.size() == 1) continue;
					if (enemyAntsPrec.size() == 0) continue;
					
					for (Ant a : myAntsPrec) {
						a.distMap = new HashMap<Tile, Integer>();
						Tile enemyTile = a.closestEnemyTile;
						int dy = distRow(a.tile, enemyTile);
						int dx = distCol(a.tile, enemyTile);
						//a.distMap.put(a.tile, 4*(dx*dx + dy*dy)-a.tile.neighbors.length);
						a.distMap.put(a.tile, dx*dx + dy*dy);
						for (Tile n : a.tile.neighbors) {
							dy = distRow(n, enemyTile);
							dx = distCol(n, enemyTile);
							//a.distMap.put(n, 4*(dx*dx + dy*dy)-n.neighbors.length);
							a.distMap.put(n, dx*dx + dy*dy);
						}
						
						if (a.checkAll) continue;
						for (Ant b : myAntsPrec) {
							if (a == b) continue;
							for (Tile n : a.tile.neighbors) {
								if (n == b.tile) {
									a.checkAll = true;
									break;
								}
							}
							if (a.checkAll) break;
						}
						if (a.checkAll) {
							a.checkNeighbors.clear();
							for (Tile n : a.tile.neighbors) a.checkNeighbors.add(n);
						}
					}
					printDepTables();
					
					for (Ant myAnt : myAntsPrec) {
						myAnt.tile.ant = null;
						myAnt.tile.type = Type.LAND;
					}
					
					bestPrecValue = Integer.MIN_VALUE;
					
					//long precStartTime = System.currentTimeMillis();
					beAgressive = group.maxNumCloseOwnAnts >= 14;
					if (beAgressive) logger.println("agressive");
					
					maxMulti(0, 0);
					logger.println("=> " + bestPrecValue);
					
					/*long duration = (System.currentTimeMillis()-precStartTime);
					if (!isTimeout) 
						logger.println( myMoveSafeCount + " \t" + myAntsPrec.size() + "  " + enemySafeCount + " \t" + duration+"", Logger.PREC_TIME);
					else if (enoughTime) {
						logger.println( myMoveSafeCount + " \t" + myAntsPrec.size() + "  " + enemySafeCount + " \t" + 500+"", Logger.PREC_TIME);
					}*/
					
					if (isTimeout) {
						logger.println(myMoveSafeCount + " \t" + myAntsPrec.size() + "  " + enemySafeCount + " \t" , Logger.MISC);
						logger.println("TIMEOUTED!");
					}
					/*logger.println("prec value: " + bestPrecValue);
					logger.println("size " + (myAntsPrec.size() + enemyAntsPrec.size()), Logger.TIME);
					logger.println(System.currentTimeMillis()-precStartTime+"ms", Logger.TIME);*/
					
					for (Ant myAnt : myAntsPrec) {
						if (myAnt.bestTo != null) doMove2(myAnt.tile, myAnt.bestTo, "calculated multi 2");
						else {
							logger.println(myAnt + " stays (calculated multi 2)");
							myAnt.hasMoved = true; // stays were it is
							myAnt.tile.ant = myAnt;
							myAnt.tile.type = Type.MY_ANT;
						}
					}
				}
			}
		}
	}
	
	private void approachEnemies() {
		for (Area area : fightAreas) {
			for (Ant ant : area.ants) {
				if (ant.hasMoved || ant.isIndirectlyDangered || ant.numCloseEnemies == 0) continue;
				Tile enemyAntTile = ant.closestEnemy.tile;
				Tile dest = aStar(ant.tile, enemyAntTile, true);
				if (dest != null) doMove(ant.tile, dest, "group fight :: move to enemy " + enemyAntTile);
				else if (!exploreAnt(ant)) {
					dest = aStar2(ant.tile, enemyAntTile, true);
					if (dest != null) doMove(ant.tile, dest, "group fight :: move to enemy " + enemyAntTile);
				}
			}
		}
	}
	
	private void initExplore() {
		LinkedList<Tile> openList = new LinkedList<Tile>();
		LinkedList<Tile> changedTiles = new LinkedList<Tile>();
		for (Ant ant : myAnts) openList.add(ant.tile);
		for (Tile tile : openList) {
			tile.dist = 0;
			tile.isReached = true;
			tile.startTile = tile;
			changedTiles.add(tile);
		}
		while (!openList.isEmpty()) {
			Tile tile = openList.removeFirst();
			if (tile.dist > 10) break;
			tile.exploreValue = 0;
			for (Tile n : tile.neighbors) {
				if (n.isReached) continue;
				n.isReached = true;
				n.prev = tile;
				n.dist = tile.dist + 1;
				n.startTile = tile.startTile;
				changedTiles.add(n);
				openList.add(n);
			}
		}
		for (Tile tile : changedTiles) tile.isReached = false;
	}
	private void explore() {
		for (Ant ant : myAnts) {
			if (ant.hasMoved || ant.isIndirectlyDangered) continue;
			exploreAnt(ant);
		}
	}
	private boolean exploreAnt(Ant ant) {
		logger.println("explore for " + ant);
		HashMap<Tile, Integer> values;
		if (DEBUG) values = new LinkedHashMap<Tile, Integer>();
		else values = new HashMap<Tile, Integer>();
		LinkedList<Tile> openList = new LinkedList<Tile>();
		LinkedList<Tile> changedTiles = new LinkedList<Tile>();
		Tile antTile = ant.tile;
		antTile.isReached = true;
		antTile.dist = 0;
		changedTiles.add(antTile);
		for (Tile n : antTile.neighbors) {
			values.put(n, 0);
			openList.add(n);
			n.dist = 1;
			n.isReached = true;
			n.prevFirsts.add(n);
			changedTiles.add(n);
		}
		while (!openList.isEmpty()) {
			Tile tile = openList.removeFirst();
			if (tile.dist > 10) {
				for (Tile prevFirst : tile.prevFirsts) 
					values.put(prevFirst, values.get(prevFirst) + tile.exploreValue);
				continue;
			}
			for (Tile n : tile.neighbors) {
				if (n.isReached) {
					if (n.dist == tile.dist + 1) {
						n.prevFirsts.addAll(tile.prevFirsts);
					}
					continue;
				}
				n.isReached = true;
				n.prev = tile;
				n.dist = tile.dist + 1;
				n.prevFirsts.addAll(tile.prevFirsts);
				changedTiles.add(n);
				openList.add(n);
			}
		}
		int bestValue = 0;
		Tile bestDest = null;
		for (Entry<Tile, Integer> entry : values.entrySet()) {
			//logger.println(entry.getValue() + " " + entry.getKey());
			if (entry.getKey().type.isFree() && !entry.getKey().isHill && entry.getValue() > bestValue) {
				bestValue = entry.getValue();
				bestDest = entry.getKey();
			}
		}
		
		if (bestValue == 0 || bestDest == null) {
			for (Tile tile : changedTiles) {
				tile.isReached = false;
				tile.prevFirsts.clear();
			}
			return false;
		}
		for (Tile tile : changedTiles) {
			if (tile.dist > 10 && tile.prevFirsts.contains(bestDest)) tile.exploreValue = 0;
			tile.isReached = false;
			tile.prevFirsts.clear();
		}
		doMove(antTile, bestDest, "explore");
		return true;
	}
	
	private void distribute(boolean onlyNearEnemy) {
		for (Ant ant : myAnts) {
			if (!ant.hasMoved && (ant.numCloseEnemies > 0 || !onlyNearEnemy)) 
				distributeAnt(ant);
		}
	}
	private void distributeAnt(Ant ant) {
		if (ant.hasMoved) return;
		final int closeDist = 2*distributeCheckDist + 2;
		LinkedList<Tile> closeAnts = getCloseAntTiles(ant.tile, closeDist);
		Tile bestNeighbor = null;
		int bestValue = Integer.MIN_VALUE;
		for (Tile dest : ant.tile.neighbors) {
			if (!dest.type.isFree() || dest.isHill || !isTileSafe(ant, dest) || dest.isHill) continue;
			int value = calcSpaceValueE(dest, closeAnts);
			if (value > bestValue) {
				bestValue = value;
				bestNeighbor = dest;
			}
		}
		visualize("v setLineColor 0 0 0xFF 0.8");
		visualize("v setLineWidth 1");
		visualize("v circle " + ant.tile.row + " " + ant.tile.col + " 0.8 false");
		if (bestNeighbor != null) doMove(ant.tile, bestNeighbor, "distribute");
	}
	
	private class GammaGroup {
		LinkedList<Ant> myAnts = new LinkedList<Ant>();
		LinkedList<Ant> enemyAnts = new LinkedList<Ant>();
		int maxNumCloseOwnAnts;
	}
	private LinkedList<GammaGroup> gammaGroups = new LinkedList<GammaGroup>();
	private GammaGroup findGammaGroup(Ant startAnt) {
		//if (startAnt.gammaDistEnemies.isEmpty()) return;
		GammaGroup group = new GammaGroup();
		LinkedList<Ant> myOpenList = new LinkedList<Ant>();
		LinkedList<Ant> enemyOpenList = new LinkedList<Ant>();
		myOpenList.add(startAnt);
		group.myAnts.add(startAnt);
		startAnt.isGammaGrouped = true;
		//logger.println("find gamma group for " + startAnt);
		while (!myOpenList.isEmpty() || !enemyOpenList.isEmpty()) {
			if (!myOpenList.isEmpty()) {
				Ant myAnt = myOpenList.removeFirst();
				//logger.println("check " + myAnt);
				group.maxNumCloseOwnAnts = Math.max(group.maxNumCloseOwnAnts, myAnt.numCloseOwnAnts);
				for (Ant enemy : myAnt.gammaDistEnemies) {
					//logger.println("look at enemy " + enemy);
					if (enemy.isGammaGrouped) continue;
					//logger.println("add enemy " + enemy);
					enemy.isGammaGrouped = true;
					enemyOpenList.add(enemy);
					group.enemyAnts.add(enemy);
				}
			}
			if (!enemyOpenList.isEmpty()) {
				Ant enemy = enemyOpenList.removeFirst();
				//logger.println("check " + enemy);
				//group.enemyAnts.add(enemy);
				for (Ant myAnt : enemy.gammaDistEnemies) {
					//logger.println("look at my " + myAnt);
					if (myAnt.isGammaGrouped || myAnt.hasMoved) continue;
					//logger.println("add my " + myAnt);
					myAnt.isGammaGrouped = true;
					myOpenList.add(myAnt);
					group.myAnts.add(myAnt);
				}
			}
		}
		return group;
	}
	
	private int myMoveSafeCount;
	private int enemySafeCount;
	
	private void findPrecGroup(Ant startAnt) {
		Comparator<Ant> comp = new Comparator<Ant>() {
			public int compare(Ant a, Ant b) {
				return ((a.compValue - b.compValue) << 10) + turnRandom.nextInt(100);
			}
		};
		//logger.println("***find prec group for " + startAnt);
		Ant myAnt;
		LinkedList<Ant> myOpenList = new LinkedList<Ant>();
		LinkedList<Ant> myAntsChanged = new LinkedList<Ant>();
		LinkedList<Ant> enemyAntsChanged = new LinkedList<Ant>();
		myOpenList.add(startAnt);
		startAnt.isReached = true;
		myAntsPrec = new ArrayList<Ant>();
		enemyAntsPrec = new ArrayList<Ant>();
		myMoveSafeCount = 0;
		enemySafeCount = 0;
		final int MY_MOVE_MAX = 20;
		
		while (!myOpenList.isEmpty()) {
			myAnt = myOpenList.removeFirst();
			int myMoveTestCount = myAnt.checkNeighbors == null ? 0 : myAnt.checkNeighbors.size();
			boolean doesFit = true;
			LinkedList<Ant> newEnemies = null;
			int enemyTestCount = 0;
			newEnemies = new LinkedList<Ant>();
			if (myMoveSafeCount + myMoveTestCount > MY_MOVE_MAX) doesFit = false;
			else {
				//logger.print("check " + myAnt + " - new enemies: ");
				for (Ant e : myAnt.gammaDistEnemies) {
					if (e.isReached) continue; // rethink, shouldn't there be isChecked?
					//logger.print(e.toString());
					enemyAntsChanged.add(e);
					e.isReached = true;
					newEnemies.add(e);
					if (!e.willStay) enemyTestCount++;
				}
				if (myMoveSafeCount + myMoveTestCount > 17-3 && enemySafeCount + enemyTestCount > 5-1) doesFit = false;
				else if (myMoveSafeCount + myMoveTestCount > 13-3 && enemySafeCount + enemyTestCount > 8-1) doesFit = false;
			}
			if (doesFit) {
				myMoveSafeCount += myMoveTestCount;
				enemySafeCount += enemyTestCount;
				myAntsPrec.add(myAnt);
				enemyAntsPrec.addAll(newEnemies);
				myAnt.isGrouped = true;
				TreeSet<Ant> newTestAnts = new TreeSet<Ant>(comp);
				for (Ant e : newEnemies) {
					//logger.print("added " + e + " to enemies :: e.gammaDist ");
					for (Ant m : e.gammaDistEnemies) {
						m.compValue = dist(m.tile, startAnt.tile);
						//logger.print(m.toString());
					}
					//logger.println("");
					newTestAnts.addAll(e.gammaDistEnemies);
				}
				//logger.print("new test ants: ");
				for (Ant m : newTestAnts) {
					//if (m.hasMoved || m.isGrouped || m.isReached) logger.print("--don't add " + m + "(" +m.hasMoved + m.isGrouped + m.isReached +")--");
					if (m.hasMoved || m.isGrouped || m.isReached) continue;
					//logger.print(m.toString());
					m.isReached = true;
					myOpenList.add(m);
					myAntsChanged.add(m);
				}
				//logger.println("");
			} else {
				//logger.println("does not fit ["+saveCount+" + "+testCount+"]");
				for (Ant e : newEnemies) e.isReached = false;
			}
		}
		for (Ant ant : enemyAntsChanged) ant.isReached = false;
		for (Ant ant : myAntsChanged) if (!ant.isGrouped) ant.isReached = false;
		
		visualize("v setLineWidth 1");
		visualize("v setLineColor "+turnRandom.nextInt(256)+" "+turnRandom.nextInt(256)+" "+turnRandom.nextInt(256)+" 1");
		logger.print("group fight BIG (size "+(myAntsPrec.size() + enemyAntsPrec.size())+"): ");
		for (Ant a : myAntsPrec) {
			logger.print(a.toString()+" ");
			visualize("v tile "+a.tile.row+" "+a.tile.col);
		}
		logger.print(" vs ");
		for (Ant a : enemyAntsPrec) {
			logger.print(a.toString()+" ");
			visualize("v tile "+a.tile.row+" "+a.tile.col);
		}
		logger.println("");
	}
	
	private void attackEnemy(Ant enemy) {
		myAntsPrec = new ArrayList<Ant>();
		logger.println("attack " + enemy);
		LinkedList<Tile> openList = new LinkedList<Tile>();
		LinkedList<Tile> changedTiles = new LinkedList<Tile>();
		Tile enemyTile = enemy.tile;
		openList.add(enemyTile);
		enemyTile.dist = 0;
		enemyTile.isReached = true;
		changedTiles.add(enemyTile);
		int count = 0;
		while (!openList.isEmpty()) {
			Tile tile = openList.removeFirst();
			if (tile.type == Type.MY_ANT && !tile.ant.hasMoved) {
				if (tile.prev.type.isFree() && isTileSafe(tile.ant, tile.prev) ) 
					doMove(tile, tile.prev, "attack :: out of range");
				else if (isGammaDist(tile, enemyTile) && tile.ant.gammaDistEnemies.size() <= 1) 
					myAntsPrec.add(tile.ant);
				if (++count > 5) break;
			}
			if (tile.dist >= 10) break;
			for (Tile n : tile.neighbors) {
				if (n.isReached) continue;
				n.isReached = true;
				n.prev = tile;
				n.dist = tile.dist + 1;
				changedTiles.add(n);
				openList.add(n);
			}
		}
		for (Tile tile : changedTiles) tile.isReached = false;
		
		if (myAntsPrec.size() > 0) {
			bestPrecValue = Integer.MIN_VALUE;
			enemyAntsPrec = new ArrayList<Ant>();
			enemyAntsPrec.add(enemy);
			int max = 0;
			for (Ant myAnt : myAntsPrec) max = Math.max(max, myAnt.numCloseOwnAnts);
			beAgressive = max >= 6;
			maxSingle(0);
			for (Ant myAnt : myAntsPrec) {
				if (myAnt.bestTo != null) doMove(myAnt.tile, myAnt.bestTo, "calculated attack");
				else myAnt.hasMoved = true;
			}
		}
	}
	
	private ArrayList<Ant> myAntsPrec;
	private ArrayList<Ant> enemyAntsPrec;
	private int bestPrecValue;
	
	private void maxSingle(int i) {
		if (i < myAntsPrec.size()) {
			if (System.currentTimeMillis()-startTime > 420) isTimeout = true;
			if (isTimeout) return;
			Ant myAnt = myAntsPrec.get(i);
			Tile from = myAnt.tile;
			Tile currTile = from;
			int len = myAnt.tile.neighbors.length;
			if (len > 0) {
				for (int j = 0, index = turnRandom.nextInt(len); j < len; j++) {
					index--;
					if (index == -1) index = len - 1;
					Tile n = from.neighbors[index];
					if (!n.type.isFree() || n.hasVirtAnt) continue;
					simpleMove(currTile, n, myAnt);
					currTile = n;
					n.hasVirtAnt = true;
					myAnt.currTo = n;
					maxSingle(i+1);
					n.hasVirtAnt = false;
				}
			}
			if (currTile != from) simpleMove(currTile, from, myAnt); // undo
			if (!from.hasVirtAnt) {
				from.hasVirtAnt = true;
				myAnt.currTo = null;
				maxSingle(i+1);
				from.hasVirtAnt = false;
			}
		} else {
			int value = minSingle();
			if (value > bestPrecValue) {
				bestPrecValue = value;
				for (Ant ant : myAntsPrec) ant.bestTo = ant.currTo;
			}
		}
	}
	
	private int minSingle() {
		Ant enemyAnt = enemyAntsPrec.get(0);
		Tile from = enemyAnt.tile;
		Tile currTile = from;
		int bestValue = evaluateSingle();
		if (!enemyAnt.willStay) {
			for (Tile n : enemyAnt.tile.neighbors) {
				if (!n.type.isFree()) continue;
				simpleMove(currTile, n, enemyAnt);
				currTile = n;
				int value = evaluateSingle();
				if (value < bestValue) {
					bestValue = value;
				}
			}
			simpleMove(currTile, from, enemyAnt); // undo
		}
		return bestValue;
	}
	private int evaluateSingle() {
		Ant enemyAnt = enemyAntsPrec.get(0);
		boolean canAttack = false;
		int value = 0;
		for (Ant myAnt : myAntsPrec) {
			if (!canAttack) value -= dist(myAnt.tile, enemyAnt.tile);
			if (isAlphaDist(myAnt.tile, enemyAnt.tile)) {
				if (canAttack) return 10000; // i win
				else canAttack = true;
			}
		}
		if (canAttack) return beAgressive ? 5000 : -5000; // one of my ants and enemy ant die
		return value;
	}
	
	private void createDepTables(GammaGroup group) {
		for (Ant myAnt : group.myAnts) {
			myAnt.checkNeighbors = new LinkedList<Tile>();
			myAnt.checkAll = false;
			for (Ant enemy : group.enemyAnts) {
				if (enemy.willStay) {
					for (Tile myN : myAnt.tile.neighbors) {
						if (isBetaDist(myN, enemy.tile)) {
							if (!myAnt.checkNeighbors.contains(myN)) myAnt.checkNeighbors.add(myN);
						}
					}
				} else for (Tile enemyN : enemy.tile.neighbors) {
					if (isAlphaDist(enemyN, myAnt.tile)) {
						myAnt.checkAll = true;
						for (Tile n : myAnt.tile.neighbors) myAnt.checkNeighbors.add(n);
						break;
					}
				}
				if (myAnt.checkNeighbors.size() == myAnt.tile.neighbors.length) break;
			}
		}
		for (Ant enemy : group.enemyAnts) {
			if (enemy.willStay) {
				for (Ant myAnt : group.myAnts) 
					if (!myAnt.checkAll) 
						for (Tile myN : myAnt.tile.neighbors) 
							if (isAlphaDist(myN, enemy.tile) && !myAnt.checkNeighbors.contains(myN)) 
								myAnt.checkNeighbors.add(myN);
			} else {
				enemy.depTable = new HashMap<Tile, List<Tile>>();
				for (Ant myAnt : group.myAnts) {
					List<Tile> list;
					for (Tile myN : myAnt.tile.neighbors) {
						if (isAlphaDist(myN, enemy.tile)) {
							list = new LinkedList<Tile>();
							enemy.depTable.put(myN, list);
							for (Tile enemyN : enemy.tile.neighbors) {
								list.add(enemyN);
								enemyN.isChecked = false;
							}
						}
						else for (Tile enemyN : enemy.tile.neighbors) {
							if (!isAlphaDist(myN, enemyN)) continue;
							if (!enemy.depTable.containsKey(myN)) {
								list = new LinkedList<Tile>();
								enemy.depTable.put(myN, list);
								list.add(enemyN);
							} else {
								list = enemy.depTable.get(myN);
								if (!list.contains(enemyN)) list.add(enemyN);
							}
							if (!myAnt.checkAll && !myAnt.checkNeighbors.contains(myN)) myAnt.checkNeighbors.add(myN);
						}
					}
					for (Tile enemyN : enemy.tile.neighbors) {
						enemyN.isChecked = false;
						if (!isAlphaDist(myAnt.tile, enemyN)) continue;
						if (!enemy.depTable.containsKey(myAnt.tile)) {
							list = new LinkedList<Tile>();
							enemy.depTable.put(myAnt.tile, list);
							list.add(enemyN);
						} else {
							list = enemy.depTable.get(myAnt.tile);
							if (!list.contains(enemyN)) list.add(enemyN);
						}
					}
				}
			}
		}
	}
	private void printDepTables() {
		for (Ant enemyAnt : enemyAntsPrec) {
			logger.println("*** " + enemyAnt);
			if (enemyAnt.willStay) logger.println("	will stay");
			else for (Entry<Tile, List<Tile>> entry : enemyAnt.depTable.entrySet()) {
				logger.println("	for " + entry.getKey());
				List<Tile> list = entry.getValue();
				for (Tile dest : list) {
					logger.println("		check " + dest);
				}
			}
		}
	}
	private void maxMulti(int i, int distValue) {
		if (System.currentTimeMillis()-startTime > 420) isTimeout = true;
		if (isTimeout) return;
		//String spaces = "";
		//for (int j = 0; j < i; j++) spaces += "\t";
		if (i < myAntsPrec.size()) {
			Ant myAnt = myAntsPrec.get(i);
			//logger.println(spaces + "max " + myAnt);
			Tile from = myAnt.tile;
			Tile currTile = from;
			
			for (Tile n : myAnt.checkNeighbors) {
				//if (!n.type.isFree()) logger.println("!!! " + from + " cannot move to " + n);
				if (n.hasVirtAnt || !n.type.isFree() /*(n.ant != null && n.ant.hasMoved)*/) continue;
				if (n.oldAnt != null && n.oldAnt.currTo == from) continue;
				simpleMove(currTile, n, myAnt);
				//logger.println(spaces + "" + from + " go to " + n);
				currTile = n;
				n.hasVirtAnt = true;
				myAnt.currTo = n;
				int newDistValue = distValue + myAnt.distMap.get(n);
				maxMulti(i+1, newDistValue);
				n.hasVirtAnt = false;
			}
			if (currTile != from) simpleMove(currTile, from, myAnt); // undo
			if (!from.hasVirtAnt) {
				//logger.println(spaces + "" + myAnt + " stay");
				from.hasVirtAnt = true;
				myAnt.currTo = null;
				int newDistValue = distValue + myAnt.distMap.get(from);
				maxMulti(i+1, newDistValue);
				from.hasVirtAnt = false;
			}
		} else {
			doCut = false;
			int value = minMulti(0, distValue);
			//logger.println(spaces + " => " + value);
			if (value > bestPrecValue) {
				bestPrecValue = value;
				for (Ant ant : myAntsPrec) ant.bestTo = ant.currTo;
			}
		}
	}
	private boolean doCut = false;
	private int minMulti(int i, int distValue) {
		if (System.currentTimeMillis()-startTime > 420) isTimeout = true;
		if (isTimeout) return bestPrecValue;
		if (i < enemyAntsPrec.size()) {
			Ant enemyAnt = enemyAntsPrec.get(i);
			Tile from = enemyAnt.tile;
			Tile currTile = from;
			int bestValue = Integer.MAX_VALUE;
			
			if (!enemyAnt.willStay) {
				for (Entry<Tile, List<Tile>> entry : enemyAnt.depTable.entrySet()) {
					if (entry.getKey().hasVirtAnt) {
						List<Tile> list = entry.getValue();
						for (Tile dest : list) {
							if (dest.isChecked || dest.hasVirtAnt) continue;
							simpleMove(currTile, dest, enemyAnt);
							currTile = dest;
							dest.hasVirtAnt = true;
							int value = minMulti(i+1, distValue);
							dest.hasVirtAnt = false;
							if (doCut) {
								if (currTile != from) simpleMove(currTile, from, enemyAnt); // undo
								return bestPrecValue;
							}
							if (value < bestValue) bestValue = value;
						}
					}
				}
				if (currTile != from) simpleMove(currTile, from, enemyAnt); // undo
			}
			if (!from.hasVirtAnt) {
				from.hasVirtAnt = true;
				int value = minMulti(i+1, distValue);
				from.hasVirtAnt = false;
				if (doCut) return bestPrecValue;
				if (value < bestValue) bestValue = value;
			}
			return bestValue;
		} else {
			int result = evaluateMulti(distValue);
			if (result < bestPrecValue) doCut = true;
			//logger.println("min value: " + result);
			//logger.println("\t\t\t\t" + "min " + result + " / " + enemyAntsPrecMulti.get(0).tile);
			return result;
		}
	}
	
	private int evaluateMulti(int distValue) {
		int myDeadCount = 0;
		int enemyDeadCount = 0;
		for (Ant enemy : enemyAntsPrec) {
			enemy.isDead = false;
			enemy.weakness = 0;
			for (Ant myAnt : enemy.gammaDistEnemies) {
				if (isAlphaDist(enemy.tile, myAnt.tile)) {
					enemy.weakness++;
					myAnt.weakness++;
				}
			}
		}
		for (Ant myAnt : myAntsPrec) {
			if (myAnt.weakness != 0) {
				for (Ant enemy : myAnt.gammaDistEnemies) {
					if (enemy.weakness == 0 || !isAlphaDist(myAnt.tile, enemy.tile)) continue;
					if (!enemy.isDead && enemy.weakness >= myAnt.weakness) {
						// enemy ant dies
						enemy.isDead = true;
						enemyDeadCount++;
					}
					if (!myAnt.isDead && myAnt.weakness >= enemy.weakness) {
						myAnt.isDead = true;
						myDeadCount++;
					}
				}
				myAnt.isDead = false;
				myAnt.weakness = 0;
			}
		}
		
		if (beAgressive) return enemyDeadCount * 300 - myDeadCount * 180 - distValue;
		else return enemyDeadCount * 512 - myDeadCount * (512+256) - distValue;
	}
	
	private void simpleMove(Tile from, Tile to, Ant ant) {
		to.type = from.type;
		from.type = Type.LAND;
		ant.tile = to;
	}
	
	// used by distribute
	private int calcSpaceValueE(Tile dest, LinkedList<Tile> closeAntTiles) {
		int value = 0;
		LinkedList<Tile> openList = new LinkedList<Tile>();
		LinkedList<Tile> changedTiles = new LinkedList<Tile>();
		closeAntTiles.add(dest);
		for (Tile antTile : closeAntTiles) {
			openList.add(antTile);
			antTile.dist = 0;
			antTile.isReached = true;
			antTile.isReachedByMe = (antTile == dest || antTile.type == Type.MY_ANT);
			changedTiles.add(antTile);
		}
		closeAntTiles.removeLast();
		while (!openList.isEmpty()) {
			Tile tile = openList.removeFirst();
			if (tile.dist >= distributeCheckDist) break;
			for (Tile n : tile.neighbors) {
				if (n.isReached) continue;
				n.isReached = true;
				n.dist = tile.dist + 1;
				n.isReachedByMe = tile.isReachedByMe;
				if (n.isReachedByMe) value += 10 + distributeCheckDist - n.dist;
				changedTiles.add(n);
				openList.add(n);
			}
		}
		for (Tile tile : changedTiles) tile.isReached = false;
		return value;
	}
	
	private Tile aStar(Tile from, Tile to, boolean startPyt) {
		Tile result = null;
		final int maxDist = 3 * dist(from, to);
		ArrayList<Tile> openList = new ArrayList<Tile>();
		LinkedList<Tile> changedTiles = new LinkedList<Tile>();
		openList.add(to);
		to.f = 0;
		to.dist = 0;
		to.isReached = true;
		changedTiles.add(to);
		while (!openList.isEmpty()) {
			Tile tile = openList.remove(0);
			if (tile.dist >= maxDist) continue;
			for (Tile n : tile.neighbors) {
				if (n == from) {
					if (tile.type.isFree()) result = tile;
					break;
				}
				if (n.isReached || !n.type.isFree() || (n.isHill && n.hillPlayer == Type.MY_ANT)) continue;
				n.dist = tile.dist + 1;
				n.f = n.dist + dist(n, from);
				if (startPyt && tile == from && from.ant.closestEnemyTile != null) {
					int dx = distCol(n, to);
					int dy = distRow(n, to);
					n.f = (int)Math.floor(Math.sqrt(dx*dx+dy*dy));
				}
				if (n.f > maxDist) continue;
				n.isReached = true;
				changedTiles.add(n);
				if (openList.isEmpty()) openList.add(n);
				else {
					int i = -1;
					int openListSize = openList.size();
					while (++i < openListSize) {
						if (openList.get(i).f > n.f) {
							openList.add(i, n);
							break;
						}
					}
					if (i == openListSize) openList.add(n);
				}
			}
			if (result != null) break;
		}
		for (Tile tile : changedTiles) tile.isReached = false;
		return result;
	}
	// only returns paths beginning with a safe move and searches through own ants unless in the first move
	private Tile aStar2(Tile from, Tile to, boolean firstFree) {
		boolean found = false;
		final int maxDist = 400;
		ArrayList<Tile> openList = new ArrayList<Tile>();
		LinkedList<Tile> changedTiles = new LinkedList<Tile>();
		openList.add(from);
		from.f = 0;
		from.dist = 0;
		from.isReached = true;
		changedTiles.add(from);
		from.prev = null;
		while (!openList.isEmpty()) {
			Tile tile = openList.remove(0);
			if (tile.dist >= maxDist) continue;
			for (Tile n : tile.neighbors) {
				if (n.isReached || (tile == from && ((firstFree && !n.type.isFree()) || !isTileSafe(from.ant, n))) 
						|| (n.isHill && n.hillPlayer == Type.MY_ANT)) continue;
				n.dist = tile.dist + 1;
				n.f = n.dist + dist(n, to);
				n.prev = tile;
				if (n == to) {
					found = true;
					break;
				}
				if (n.f > maxDist) continue;
				n.isReached = true;
				changedTiles.add(n);
				if (openList.isEmpty()) openList.add(n);
				else {
					int i = -1;
					int openListSize = openList.size();
					while (++i < openListSize) {
						if (openList.get(i).f > n.f) {
							openList.add(i, n);
							break;
						}
					}
					if (i == openListSize) openList.add(n);
				}
			}
			if (found) break;
		}
		for (Tile tile : changedTiles) tile.isReached = false;
		if (!found) return null;
		Tile tile = to;
		while (tile.prev != from) tile = tile.prev;
		return tile;
	}
	
	// used by distribute
	private LinkedList<Tile> getCloseAntTiles(Tile t, int d) {
		LinkedList<Tile> closeAntTiles = new LinkedList<Tile>();
		for (Ant ant : ants) if (ant.tile != t && dist(t, ant.tile) <= d) closeAntTiles.add(ant.tile);
		return closeAntTiles;
	}
	
	// used by food
	private TreeMap<Integer,Ant> getCloseAntDists(Tile t) {
		TreeMap<Integer,Ant> closeAntDists = new TreeMap<Integer,Ant>();
		for (Ant ant : ants) {
			Integer d = dist(t, ant.tile);
			if (d < 8) {
				closeAntDists.put(d, ant);
				if (closeAntDists.size() >= 3) break;
			}
		}
		return closeAntDists;
	}
	
	// doMove2 does not change the from tile
	private void doMove2(Tile from, Tile to, String info) {
		long time = System.currentTimeMillis() - startTime;
		logger.println("do move from " + from + " to " + to + " (" + info + ") [" + time + "]");
		if (from.oldAnt == null) logger.println("ERROR: no ant on " + from + " to move to " + to);
		if (!isMissionPhase && from.oldAnt.hasMission) {
			from.oldAnt.mission.isRemoved = true;
			from.oldAnt.hasMission = false;
		}
		to.type = Type.MY_ANT;
		to.ant = from.oldAnt;
		to.ant.hasMoved = true;
		to.ant.tile = to;
		conn.issueOrder(from.row, from.col, from.dirTo(to));
	}
	private void doMove(Tile from, Tile to, String info) {
		long time = System.currentTimeMillis() - startTime;
		logger.println("do move from " + from + " to " + to + " (" + info + ") [" + time + "]");
		if (from.oldAnt == null) logger.println("ERROR: no ant on " + from + " to move to " + to);
		if (!isMissionPhase && from.oldAnt.hasMission) {
			from.oldAnt.mission.isRemoved = true;
			from.oldAnt.hasMission = false;
		}
		to.type = from.type;
		to.ant = from.oldAnt;
		from.type = Type.LAND;
		from.ant = null;
		to.ant.hasMoved = true;
		to.ant.tile = to;
		conn.issueOrder(from.row, from.col, from.dirTo(to));
	}
	
	private boolean isTileSafe(Ant ant, Tile dest) {
		for (Ant enemyAnt : ant.gammaDistEnemies) {
			if (enemyAnt.willStay) {
				if (isAlphaDist(enemyAnt.tile, dest)) return false;
			} else {
				if (isBetaDist(enemyAnt.tile, dest)) {
					if (enemyAnt.tile.neighbors.length == 4) return false;
					for (Tile n : enemyAnt.tile.neighbors) if (isAlphaDist(n, dest)) return false;
				}
			}
		}
		return true;
	}
	
	// returns null if safe or the enemy ant if not safe
	private Ant isTileSafe2(Ant ant, Tile dest) {
		for (Ant enemyAnt : ant.gammaDistEnemies) {
			if (enemyAnt.willStay) {
				if (isAlphaDist(enemyAnt.tile, dest)) return enemyAnt;
			} else {
				if (isBetaDist(enemyAnt.tile, dest)) {
					if (enemyAnt.tile.neighbors.length == 4) return enemyAnt;
					for (Tile n : enemyAnt.tile.neighbors) if (isAlphaDist(n, dest)) return enemyAnt;
				}
			}
		}
		return null;
	}
	
	private boolean isSuicide(Ant ant, Tile dest) {
		boolean dangered = false;
		for (Ant enemyAnt : ant.gammaDistEnemies) {
			if (enemyAnt.willStay) {
				if (isAlphaDist(enemyAnt.tile, dest)) {
					if (dangered) return true;
					dangered = true;
				}
			} else {
				if (isBetaDist(enemyAnt.tile, dest)) {
					if (enemyAnt.tile.neighbors.length == 4) {
						if (dangered) return true;
						dangered = true;
					}
					else for (Tile n : enemyAnt.tile.neighbors) if (isAlphaDist(n, dest)) {
						if (dangered) return true;
						dangered = true;
					}
				}
			}
		}
		return false;
	}
	
	private boolean isAlphaDist(Tile tile1, Tile tile2) {
		int dx = distRow(tile1, tile2);
		int dy = distCol(tile1, tile2);
		return (dy <= 1 && dx <= 2) || (dy == 2 && dx <= 1);
	}
	
	private boolean isBetaDist(Tile tile1, Tile tile2) {
		int dx = distRow(tile1, tile2);
		int dy = distCol(tile1, tile2);
		if (dx + dy <= 4) {
			if ((dx == 0 && dy == 4) || (dy == 0 && dx == 4)) return false;
			return true;
		}
		return false;
	}
	
	private boolean isGammaDist(Tile tile1, Tile tile2) {
		int dx = distRow(tile1, tile2);
		int dy = distCol(tile1, tile2);
		if (dx + dy <= 5) {
			if ((dx == 0 && dy == 5) || (dy == 0 && dx == 5)) return false;
			return true;
		}
		return false;
	}
	
	private int dist(Tile t1, Tile t2) {
		//return distRow(t1, t2) + distCol(t1, t2);
		int dCol = Math.abs(t1.col - t2.col);
		int dRow = Math.abs(t1.row - t2.row);
		return Math.min(dCol, cols - dCol) + Math.min(dRow, rows - dRow);
	}
	
	private int distRow(Tile t1, Tile t2) {
		int dRow = Math.abs(t1.row - t2.row);
		return Math.min(dRow, rows - dRow);
	}
	private int distCol(Tile t1, Tile t2) {
		int dCol = Math.abs(t1.col - t2.col);
		return Math.min(dCol, cols - dCol);
	}
	
	private void visualize(String s) {
		if (!DEBUG) return;
		System.out.println(s);
	}
	
}
