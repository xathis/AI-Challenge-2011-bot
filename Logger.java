import java.io.*;

public class Logger {
	
	public static boolean ENABLED = Strategy.DEBUG;
	private static int i = 0;
	
	public static int MISC			= 1 << i++;
	public static int PREC 			= 1 << i++;
	public static int PREC_GROUP 	= 1 << i++;
	public static int TIME 			= 1 << i++;
	public static int TIMEOUT 		= 1 << i++;
	public static int PREC_TIME 	= 1 << i++;
	
	public static int FLAGS = MISC;
	

	protected PrintStream logStream = null;
	protected String logFileName = null;
	protected long cnt = 0;
	
	public Logger(String logFileName) {
		if (!ENABLED) return;
		this.logFileName = logFileName;
		resetLogStream();
	}
	
	public void print(String s, int flag) {
		if (!ENABLED || (flag & FLAGS) == 0) return;
		this.logStream.print(s);
	}
	public void print(String s) {
		print(s, MISC);
	}

	public void println(String s, int flag) {
		if (!ENABLED || (flag & FLAGS) == 0) return;
		this.logStream.println(s);
	}
	public void println(String s) {
		println(s, MISC);
	}

	protected void resetLogStream() {
		try {
			this.logStream = new PrintStream(new File(logFileName));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
