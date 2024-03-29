package sg.edu.nus.comp.cs4218.impl.app;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import sg.edu.nus.comp.cs4218.Application;
import sg.edu.nus.comp.cs4218.exception.DateException;

/**
 * The date command prints the current date and time where the pattern is
 * specified as [week day] [month] [day] [hh:mm:ss] [time zone] [year].
 *
 * <p>
 * <b>Command format:</b> <code>date</code>
 * </p>
 */
public class DateApplication implements Application {
	private static final String DATE_FORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";

	/**
	 * Runs the date application with the specified arguments.
	 * 
	 * @param stdout
	 *            An OutputStream. The output of the command is written to this
	 *            OutputStream.
	 * 
	 * @throws DateException
	 *             If there is an error writing to the outputstream
	 */
	@Override
	public void run(String[] args, InputStream stdin, OutputStream stdout) throws DateException {
		if (args != null && args.length > 0) {
			throw new DateException("No arguments expected");
		}
		if (stdout == null) {
			throw new DateException("Cannot write to stdout as it is null");
		}

		String dateGenerated = generateDate(DATE_FORMAT);

		PrintWriter printWriter = new PrintWriter(stdout);
		printWriter.println(dateGenerated);
		printWriter.flush();
		printWriter.close();

	}

	/**
	 * Runs the date application with the specified arguments.
	 * 
	 * @param dateFormat
	 *            The format of the date to be generated
	 * @return The date generated in the specified format
	 */
	String generateDate(String dateFormat) {
		SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.ENGLISH);
		return sdf.format(new Date());
	}
}

// http://stackoverflow.com/questions/1459656/how-to-get-the-current-time-in-yyyy-mm-dd-hhmisec-millisecond-format-in-java
// Question by : http://stackoverflow.com/users/111988/sunil-kumar-sahoo
// Answer by : http://stackoverflow.com/users/171675/jayjay

// http://stackoverflow.com/questions/4069028/write-string-to-output-stream
// Question by : http://stackoverflow.com/users/232666/yart
// Answer by : http://stackoverflow.com/users/248432/peter-knego
