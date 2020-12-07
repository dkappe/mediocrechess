package mediocrechess.mediocre.main;

import mediocrechess.mediocre.def.Definitions;

import java.io.IOException;
import java.util.Properties;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * class Mediocre
 * 
 * This is the main class of Mediocre which is used to connect to Winboard etc.
 * 
 * @author Jonatan Pettersson (mediocrechess@gmail.com) Date: 2006-12-27
 */

public class Mediocre implements Definitions {
	private static Logger logger = (Logger)LoggerFactory.getLogger(Mediocre.class);

	public static String VERSION;
	
	/**
	 * The main method
	 * 
	 * @param String
	 *            arguments
	 */
	public static void main(String args[]) throws IOException {
		// Populate the version from properties file
		try {
			Properties properties = new Properties();
			properties.load((new Mediocre()).getClass().getClassLoader().getResourceAsStream("mediocre.properties"));
			VERSION = properties.getProperty("mediocre.version", "??");
		} catch (Exception e) {
			VERSION = "??";
		}
		
		// Handle arguments
		String startMode = "uci";
		if(args.length > 0) {
			for(int i = 0; i < args.length; i++) {
				if(args[i].equals("-debug")) {
					// Enable logging
					Logger rootLogger = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
					rootLogger.setLevel(Level.DEBUG);
				} else if(args[i].equals("-console")) {
					startMode = "console";
				}
			}
		}
		
		logger.info("Starting Mediocre Chess " + VERSION);
		Settings.getInstance();

		if(startMode.equals("console")) {
			Uci.lineInput();
		} else if(startMode.equals("uci")) {
			Uci.uci();
		}

	} // END main()
}
