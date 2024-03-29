package sg.edu.nus.comp.cs4218.impl.cmd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sg.edu.nus.comp.cs4218.Command;
import sg.edu.nus.comp.cs4218.exception.AbstractApplicationException;
import sg.edu.nus.comp.cs4218.exception.ShellException;
import sg.edu.nus.comp.cs4218.impl.GlobFinder;
import sg.edu.nus.comp.cs4218.impl.ShellImpl;

/**
 * A Call Command is a sub-command consisting of at least one non-keyword and
 * quoted (if any).
 * 
 * <p>
 * <b>Command format:</b> <code>(&lt;non-Keyword&gt; | &lt;quoted&gt;)*</code>
 * </p>
 */

public class CallCommand implements Command {
	public static final String EXP_INVALID_APP = "Invalid app.";
	public static final String EXP_SYNTAX = "Invalid syntax encountered.";
	public static final String EXP_REDIR_PIPE = "File output redirection and pipe "
			+ "operator cannot be used side by side.";
	public static final String EXP_SAME_REDIR = "Input redirection file same as " + "output redirection file.";
	public static final String EXP_STDOUT = "Error writing to stdout.";
	public static final String EXP_NOT_SUPPORTED = " not supported yet";
	public static final String EXP_GLOB_MULTI = "Ambigious globbing for IO Redirection.";
	public static final String EXP_GLOB_NONE = "File not found.";

	String app;
	String cmdline, inputStreamS, outputStreamS;
	String[] argsArray;
	Boolean error;
	String errorMsg;

	public CallCommand(String cmdline) {
		this.cmdline = cmdline.trim();
		app = inputStreamS = outputStreamS = "";
		error = false;
		errorMsg = "";
		argsArray = new String[0];
	}

	public CallCommand() {
		this("");
	}

	/**
	 * Getter function for inputStreamS
	 * 
	 * @return inputStreamS
	 * 
	 */
	public String getInputStreamS() {
		return inputStreamS;
	}

	/**
	 * Evaluates sub-command using data provided through stdin stream. Writes
	 * result to stdout stream.
	 * 
	 * @param stdin
	 *            InputStream to get data from.
	 * @param stdout
	 *            OutputStream to write resultant data to.
	 * 
	 * @throws AbstractApplicationException
	 *             If an exception happens while evaluating the sub-command.
	 * @throws ShellException
	 *             If an exception happens while evaluating the sub-command.
	 */
	@Override
	public void evaluate(InputStream stdin, OutputStream stdout) throws AbstractApplicationException, ShellException {
		if (error) {
			throw new ShellException(errorMsg);
		}

		InputStream inputStream;
		OutputStream outputStream;

		argsArray = ShellImpl.processBQ(argsArray);

		if (("").equals(inputStreamS)) {// empty
			inputStream = stdin;
		} else { // not empty
			inputStream = ShellImpl.openInputRedir(inputStreamS);
		}
		if (("").equals(outputStreamS)) { // empty
			outputStream = stdout;
		} else {
			outputStream = ShellImpl.openOutputRedir(outputStreamS);
		}
		ShellImpl.runApp(app, argsArray, inputStream, outputStream);
		ShellImpl.closeInputStream(inputStream);
		ShellImpl.closeOutputStream(outputStream);
	}

	/**
	 * Parses and splits the sub-command to the call command into its different
	 * components, namely the application name, the arguments (if any), the
	 * input redirection file path (if any) and output redirection file path (if
	 * any).
	 * 
	 * @throws ShellException
	 *             If an exception happens while parsing the sub-command, or if
	 *             the input redirection file path is same as that of the output
	 *             redirection file path.
	 */
	@Override
	public void parse() throws ShellException {
		Vector<String> cmdVector = new Vector<String>();
		Boolean result = true;
		int endIdx = 0;
		String str = " " + cmdline + " ";
		try {
			endIdx = extractArgs(str, cmdVector);
			cmdVector.add(""); // reserved for input redir
			cmdVector.add(""); // reserved for output redir
			endIdx = extractInputRedir(str, cmdVector, endIdx);
			endIdx = extractOutputRedir(str, cmdVector, endIdx);
		} catch (ShellException e) {
			result = false;
		}
		if (str.substring(endIdx).trim().isEmpty()) {
			result = true;
		} else {
			result = false;
		}
		if (!result) {
			errorMsg = ShellImpl.EXP_SYNTAX;
			throw new ShellException(errorMsg);
		}

		String[] cmdTokensArray = cmdVector.toArray(new String[cmdVector.size()]);
		this.app = cmdTokensArray[0];
		int nTokens = cmdTokensArray.length;

		// process inputRedir and/or outputRedir
		if (nTokens >= 3) { // last 2 for inputRedir & >outputRedir
			this.inputStreamS = cmdTokensArray[nTokens - 2].trim();
			this.outputStreamS = cmdTokensArray[nTokens - 1].trim();
			if (!("").equals(inputStreamS) && inputStreamS.equals(outputStreamS)) {
				error = true;
				errorMsg = ShellImpl.EXP_SAME_REDIR;
				throw new ShellException(errorMsg);
			}
			this.argsArray = Arrays.copyOfRange(cmdTokensArray, 1, nTokens - 2);
		} else {
			this.argsArray = Arrays.copyOfRange(cmdTokensArray, 1, nTokens - 1);
		}
	}

	/**
	 * Parses the sub-command's arguments to the call command and splits it into
	 * its different components, namely the application name and the arguments
	 * (if any), based on rules: Unquoted: any char except for whitespace
	 * characters, quotes, newlines, semicolons �;�, �|�, �<� and �>�. Double
	 * quoted: any char except \n, ", ` Single quoted: any char except \n, '
	 * Back quotes in Double Quote for command substitution: DQ rules for
	 * outside BQ + `anything but \n` in BQ.
	 * 
	 * @param str
	 *            String of command to split.
	 * @param cmdVector
	 *            Vector of String to store the split arguments into.
	 * 
	 * @return endIdx Index of string where the parsing of arguments stopped
	 *         (due to no more matches).
	 * 
	 * @throws ShellException
	 *             If an error in the syntax of the command is detected while
	 *             parsing.
	 */
	int extractArgs(String str, Vector<String> cmdVector) throws ShellException {
		String patternDash = "[\\s]+(-[A-Za-z]*)[\\s]";
		String patternUQ = "[\\s]+([^\\s\"'`\\n;|<>]*)[\\s]";
		String patternDQ = "[\\s]+\"([^\\n\"`]*)\"[\\s]";
		String patternSQ = "[\\s]+\'([^\\n']*)\'[\\s]";
		String patternBQ = "[\\s]+(`[^\\n`]*`)[\\s]";
		String patternBQinDQ = "[\\s]+\"([^\\n\"`]*`[^\\n]*`[^\\n\"`]*)\"[\\s]";
		String[] patterns = { patternDash, patternUQ, patternDQ, patternSQ, patternBQ, patternBQinDQ };
		String substring;
		int newStartIdx = 0, smallestStartIdx, smallestPattIdx, newEndIdx = 0;
		do {
			substring = str.substring(newEndIdx);
			smallestStartIdx = -1;
			smallestPattIdx = -1;
			if (substring.trim().startsWith("<") || substring.trim().startsWith(">")) {
				break;
			}
			for (int i = 0; i < patterns.length; i++) {
				Pattern pattern = Pattern.compile(patterns[i]);
				Matcher matcher = pattern.matcher(substring);
				if (matcher.find() && (matcher.start() < smallestStartIdx || smallestStartIdx == -1)) {
					smallestPattIdx = i;
					smallestStartIdx = matcher.start();
				}
			}
			if (smallestPattIdx != -1) { // if a pattern is found
				Pattern pattern = Pattern.compile(patterns[smallestPattIdx]);
				Matcher matcher = pattern.matcher(str.substring(newEndIdx));
				if (matcher.find()) {
					String matchedStr = matcher.group(1);
					newStartIdx = newEndIdx + matcher.start();
					if (newStartIdx != newEndIdx) {
						error = true;
						errorMsg = ShellImpl.EXP_SYNTAX;
						throw new ShellException(errorMsg);
					} // check if there's any invalid token not detected
					if (smallestPattIdx == 2 || smallestPattIdx == 3 || smallestPattIdx == 4 || smallestPattIdx == 5
							|| !matchedStr.contains("*")) {
						cmdVector.add(matchedStr);
					} else {
						cmdVector.addAll(Arrays.asList(processSingleGlob(matchedStr)));
					}
					newEndIdx = newEndIdx + matcher.end() - 1;
				}
			}
		} while (smallestPattIdx != -1);
		return newEndIdx;
	}

	/**
	 * Extraction of input redirection from cmdLine with two slots at end of
	 * cmdVector reserved for <inputredir and >outredir. For valid inputs,
	 * assumption that input redir and output redir are always at the end of the
	 * command and input stream first the output stream if both are in the args.
	 *
	 * Extraction does not support any types of quotes or command substitution.
	 *
	 * @param str
	 *            String of command to split.
	 * @param cmdVector
	 *            Vector of String to store the found result into.
	 * @param endIdx
	 *            Index of str to start parsing from.
	 * 
	 * @return endIdx Index of string where the parsing of arguments stopped
	 *         (due to no more matches).
	 * 
	 * @throws ShellException
	 *             When more than one input redirection string is found, or when
	 *             invalid syntax is encountered..
	 */
	public int extractInputRedir(String str, Vector<String> cmdVector, int endIdx) throws ShellException {
		String substring = str.substring(endIdx);
		String strTrm = substring.trim();
		if (strTrm.startsWith(">") || strTrm.isEmpty()) {
			return endIdx;
		}
		if (!strTrm.startsWith("<")) {
			throw new ShellException(EXP_SYNTAX);
		}

		int newEndIdx = endIdx;
		Pattern inputRedirP = Pattern.compile("[\\s]*<[\\s]*(([^\\n\"`'<>]*))[\\s]");
		Matcher inputRedirM;
		String inputRedirS = "";
		int cmdVectorIndex = cmdVector.size() - 2;

		boolean singleFlag = true;
		while (!substring.trim().isEmpty()) {
			inputRedirM = inputRedirP.matcher(substring);
			inputRedirS = "";
			if (inputRedirM.find()) {
				if (!cmdVector.get(cmdVectorIndex).isEmpty()) {
					throw new ShellException(EXP_SYNTAX);
				}
				inputRedirS = inputRedirM.group(1);
				String extractedInput = inputRedirS.replace(String.valueOf((char) 160), " ").trim();
				if (extractedInput.contains("*")) {
					String[] globResult = processSingleGlob(extractedInput);
					if (globResult.length == 0) {
						throw new ShellException(EXP_GLOB_NONE);
					} else if (globResult.length > 1) {
						throw new ShellException(EXP_GLOB_MULTI);
					} else {
						extractedInput = globResult[0];
					}
				}

				cmdVector.set(cmdVectorIndex, extractedInput);
				if (singleFlag) {
					singleFlag = false;
				} else {
					throw new ShellException(EXP_SYNTAX);
				}
				newEndIdx = newEndIdx + inputRedirM.end() - 1;
			} else {
				break;
			}
			substring = str.substring(newEndIdx);
		}
		return newEndIdx;
	}

	/**
	 * Extraction of output redirection from cmdLine with two slots at end of
	 * cmdVector reserved for <inputredir and >outredir. For valid inputs,
	 * assumption that input redir and output redir are always at the end of the
	 * command and input stream first the output stream if both are in the args.
	 *
	 * Extraction does not support any types of quotes or command substitution.
	 *
	 * @param str
	 *            String of command to split.
	 * @param cmdVector
	 *            Vector of String to store the found result into.
	 * @param endIdx
	 *            Index of str to start parsing from.
	 * 
	 * @return endIdx Index of string where the parsing of arguments stopped
	 *         (due to no more matches).
	 * 
	 * @throws ShellException
	 *             When more than one input redirection string is found, or when
	 *             invalid syntax is encountered..
	 */
	public int extractOutputRedir(String str, Vector<String> cmdVector, int endIdx) throws ShellException {
		String substring = str.substring(endIdx);
		String strTrm = substring.trim();
		if (strTrm.isEmpty()) {
			return endIdx;
		}
		if (!strTrm.startsWith(">")) {
			throw new ShellException(EXP_SYNTAX);
		}

		int newEndIdx = endIdx;
		Pattern inputRedirP = Pattern.compile("[\\s]*>[\\s]*(([^\\n\"`'<>]*))[\\s]*");
		Matcher inputRedirM;
		String inputRedirS = "";
		int cmdVectorIdx = cmdVector.size() - 1;
		while (!substring.trim().isEmpty()) {

			inputRedirM = inputRedirP.matcher(substring);
			inputRedirS = "";
			if (inputRedirM.find()) {
				if (!cmdVector.get(cmdVectorIdx).isEmpty()) {
					throw new ShellException(EXP_SYNTAX);
				}
				inputRedirS = inputRedirM.group(1);
				String extractedOutput = inputRedirS.replace(String.valueOf((char) 160), " ").trim();
				if (extractedOutput.contains("*")) {
					String[] globResult = processSingleGlob(extractedOutput);
					if (globResult.length == 0) {
						throw new ShellException(EXP_GLOB_NONE);
					} else if (globResult.length > 1) {
						throw new ShellException(EXP_GLOB_MULTI);
					} else {
						extractedOutput = globResult[0];
					}
				}

				cmdVector.set(cmdVectorIdx, extractedOutput);
				newEndIdx = newEndIdx + inputRedirM.end() - 1;
			} else {
				break;
			}
			substring = str.substring(newEndIdx);
		}
		return newEndIdx;
	}

	/**
	 * Evaluate globbing for each of the arguments. Replaces wildcards with
	 * appropriate files from globbing.
	 */
	public String[] evaluateGlob(String... args) throws ShellException {
		List<String> tempList = new ArrayList<>();
		Pattern singleQuote = Pattern.compile("['].*\\*.*[']");
		Pattern doubleQuote = Pattern.compile("[\"].*\\*.*[\"]");

		/* For each token, perform glob evaluation if any */
		for (String arg : args) {
			Matcher singleMatcher = singleQuote.matcher(arg);
			Matcher doubleMatcher = doubleQuote.matcher(arg);
			if (arg.contains("*") && !singleMatcher.find() && !doubleMatcher.find()) {
				tempList.addAll(Arrays.asList(processSingleGlob(arg)));
			} else {
				tempList.add(arg);
			}
		}
		return tempList.toArray(new String[tempList.size()]);
	}

	/**
	 * This method strictly does not check for quotes and treats all asterisks
	 * as special characters. Evaluates globbing for a single argumen and
	 * replaces wildcards symbol with matched files.
	 *
	 * @param arg
	 *            the argument to glob
	 * @return a String array contains matched paths
	 * @throws ShellException
	 */
	private String[] processSingleGlob(String arg) throws ShellException {
		List<String> tempList = new ArrayList<>();

		if (arg.contains("*")) {
			/* Retrieve parent directory before wildcard */
			int firstWildcard = arg.indexOf('*');

			/* Find separator before this wildcard */
			int beforeSeperator = arg.substring(0, firstWildcard).lastIndexOf(File.separator);

			/*
			 * If there is no separators, it means that path to search is
			 * relative path
			 */
			Path parentPath = beforeSeperator == -1 ? Paths.get("") : Paths.get(arg.substring(0, beforeSeperator));

			String pattern = arg.substring(beforeSeperator + 1);
			GlobFinder finder = new GlobFinder(pattern, parentPath.toAbsolutePath().toString());

			try {
				Files.walkFileTree(parentPath.toAbsolutePath(), finder);
			} catch (IOException e) {
				throw new ShellException(e);
			}

			List<String> results = finder.getResults();

			if (results.isEmpty()) {
				tempList.add(arg);
			} else {
				tempList.addAll(results);
			}

		} else {
			/* Nothing to glob, no change to the arg */
			tempList.add(arg);
		}

		return tempList.toArray(new String[tempList.size()]);
	}

	/**
	 * Terminates current execution of the command (unused for now)
	 */
	@Override
	public void terminate() {
		// TODO Auto-generated method stub

	}

}

// strange white space
// http://stackoverflow.com/questions/23974982/cant-trim-or-replaceall-a-strange-whitespace
// Question by : http://stackoverflow.com/users/558559/crayl
// Answer by : http://stackoverflow.com/users/558559/crayl
