import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class Connection {

	public int turns = 0;
	public int rows = 0;
	public int cols = 0;
	public int loadtime = 0;
	public int turntime = 0;
	public int viewradius2 = 0;
	public int attackradius2 = 0;
	public int spawnradius2 = 0;
	public long player_seed = 0;
	
	public int turn = 0;
	public Tile[][] map;
	public int numWaterTiles = 0;
	
	public LinkedList<Ant> ants = new LinkedList<Ant>();
	public LinkedList<Tile> hills = new LinkedList<Tile>();
	public LinkedList<Tile> foods = new LinkedList<Tile>();
	
	public void setup(List<String> data) {
		for (String line : data) {
			String tokens[] = line.toLowerCase().split(" ");
			if (tokens[0].equals("cols")) {
				this.cols = Integer.parseInt(tokens[1]);		    	
			} else if (tokens[0].equals("rows")) {
				this.rows = Integer.parseInt(tokens[1]);
			} else if (tokens[0].equals("turns")) {
				this.turns = Integer.parseInt(tokens[1]);
			} else if (tokens[0].equals("loadtime")) {
				this.loadtime = Integer.parseInt(tokens[1]);
			} else if (tokens[0].equals("turntime")) {
				this.turntime = Integer.parseInt(tokens[1]);
			} else if (tokens[0].equals("viewradius2")) {
				this.viewradius2 = Integer.parseInt(tokens[1]);
			} else if (tokens[0].equals("attackradius2")) {
				this.attackradius2 = Integer.parseInt(tokens[1]);
			} else if (tokens[0].equals("spawnradius2")) {
				this.spawnradius2 = Integer.parseInt(tokens[1]);
			} else if (tokens[0].equals("player_seed")) {
				this.player_seed = Long.parseLong(tokens[1]);
			}
		}
		this.map = new Tile[this.rows][this.cols];
		for (int row = 0; row < rows; row++) {
			map[row] = new Tile[this.cols];
			for (int col = 0; col < cols; col++) {
				map[row][col] = new Tile(row, col);
			}
		}
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				Tile tile = map[row][col];
				Tile n = map[(row+rows-1)%rows][col];
				Tile e = map[row][(col+1)%cols];
				Tile s = map[(row+1)%rows][col];
				Tile w = map[row][(col+cols-1)%cols];
				if (((col + row) & 1) == 1) {
					tile.neighbors[0] = n;
					tile.neighbors[1] = s;
					tile.neighbors[2] = e;
					tile.neighbors[3] = w;
				} else {
					tile.neighbors[0] = e;
					tile.neighbors[1] = w;
					tile.neighbors[2] = n;
					tile.neighbors[3] = s;
				}
			}
		}
	}
	
	private boolean update(List<String> data) {
		// clear
		for (Ant ant : this.ants) {
			ant.tile.type = Type.LAND;
			ant.tile.ant = null;
		}
		this.ants.clear();

		for (Tile food : this.foods) food.type = Type.LAND;
		this.foods.clear();
		
		for (Tile hill : this.hills) {
			hill.type = Type.LAND;
			hill.isHill = false;
		}
		this.hills.clear();
		
		// get new data
		for (String line : data) {
			String tokens[] = line.split(" ");
			if (tokens.length > 2) {
				int row = Integer.parseInt(tokens[1]);
				int col = Integer.parseInt(tokens[2]);
				Tile tile = map[row][col];
				if (tokens[0].equals("w")) {
					tile.type = Type.WATER;
					for (Tile n : tile.neighbors) n.removeNeighbor(tile);
					tile.neighbors = null;
					numWaterTiles++;
				} else if (tokens[0].equals("a")) {
					tile.type = Type.fromId(Integer.parseInt(tokens[3]));
					tile.ant = new Ant(tile);
					this.ants.add(tile.ant);
				} else if (tokens[0].equals("h")) {
					//tile.type = Type.fromId(Integer.parseInt(tokens[3]));
					tile.isHill = true;
					tile.hillPlayer = Type.fromId(Integer.parseInt(tokens[3]));
					this.hills.add(tile);
				} else if (tokens[0].equals("f")) {
					tile.type = Type.FOOD;
					this.foods.add(tile);
				} else if (tokens[0].equals("d")) {
					// dead ant - do nothing
				}
			}
		}
		return true;
	}

	public void issueOrder(int row, int col, Direction direction) {
		System.out.println("o " + row + " " + col + " " + direction.symbol);
		System.out.flush();
	}

	public void finishTurn() {
		System.out.println("go");
		System.out.flush();
		this.turn++;
	}
	
	public static void run(Strategy bot) {
		Connection ants = new Connection();
		StringBuffer line = new StringBuffer();
		LinkedList<String> data = new LinkedList<String>();
		int c;
		try {
			while ((c = System.in.read()) >= 0) {
				switch (c) {
				case '\n':
				case '\r':
					if (line.length() > 0) {
						String full_line = line.toString();
						if (full_line.equals("ready")) {
							ants.setup(data);
							bot.init(ants);
							ants.finishTurn();
							data.clear();
						} else if (full_line.equals("go")) {
							bot.startTime = System.currentTimeMillis();
							try {
								ants.update(data);
								bot.doTurn(ants);
							} catch (Exception e) {
								//if (!Strategy.DEBUG) 
								e.printStackTrace();
							}
							ants.finishTurn();
							data.clear();
						}
						else if (line.length() > 0) data.add(full_line);
						line = new StringBuffer();
					}
					break;
				default:
					line.append((char)c);
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
	}
}
