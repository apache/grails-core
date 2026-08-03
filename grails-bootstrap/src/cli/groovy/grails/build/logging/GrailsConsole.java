/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.build.logging;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StackTraceUtils;
import org.codehaus.groovy.runtime.typehandling.NumberMath;

import org.apache.tools.ant.BuildException;
import org.jline.reader.Completer;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import grails.util.Environment;
import org.grails.build.logging.GrailsConsoleErrorPrintStream;
import org.grails.build.logging.GrailsConsolePrintStream;

import org.springframework.boot.ansi.AnsiOutput;

import org.grails.build.logging.ConsoleAnsi;

import static org.grails.build.logging.ConsoleAnsi.ansi;

/**
 * Utility class for delivering console output in a nicely formatted way.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class GrailsConsole implements ConsoleLogger {

    private static GrailsConsole instance;

    public static final String ENABLE_TERMINAL = "grails.console.enable.terminal";

    /**
     * Spring Boot's ansi switch ({@code spring.output.ansi.enabled}: {@code always}, {@code detect} or
     * {@code never}). Honoured rather than a Grails-specific property so applications configure console
     * colour in one place. Inside a Spring application Boot binds this onto {@link AnsiOutput}; the CLI
     * has no Environment, so the system property is read directly as well.
     */
    private static final String SPRING_ANSI_PROPERTY = "spring.output.ansi.enabled";

    /** The last rejected {@link #SPRING_ANSI_PROPERTY} value, so the warning is not repeated per message. */
    private static final AtomicReference<String> WARNED_ANSI_VALUE = new AtomicReference<>();

    /**
     * Set to {@code true} while a caller has deliberately replaced {@code System.out}/{@code System.err}
     * and does not want the console to re-install its own streams over the top. See
     * {@code org.grails.test.io.SystemOutAndErrSwapper}.
     */
    public static final String SUSPEND_SYSTEM_OUT_REDIRECT = "grails.console.suspend.system.out.redirect";
    public static final String ENABLE_INTERACTIVE = "grails.console.enable.interactive";
    public static final String LINE_SEPARATOR = System.getProperty("line.separator");
    public static final String CATEGORY_SEPARATOR = "|";
    public static final String PROMPT = "grails> ";
    public static final String SPACE = " ";
    public static final String ERROR = "Error";
    public static final String WARNING = "Warning";
    public static final String HISTORYFILE = ".grails_history";
    public static final String STACKTRACE_FILTERED_MESSAGE = " (NOTE: Stack trace has been filtered. Use --verbose to see entire trace.)";
    public static final String STACKTRACE_MESSAGE = " (Use --stacktrace to see the full trace)";
    public static final Character SECURE_MASK_CHAR = Character.valueOf('*');
    private PrintStream originalSystemOut;
    private PrintStream originalSystemErr;
    private StringBuilder maxIndicatorString;
    private int cursorMove;
    private Thread shutdownHookThread;
    private Character defaultInputMask = null;

    /**
     * Whether to enable verbose mode
     */
    private boolean verbose = Boolean.getBoolean("grails.verbose");

    /**
     * Whether to show stack traces
     */
    private boolean stacktrace = Boolean.getBoolean("grails.show.stacktrace");

    private boolean progressIndicatorActive = false;

    /**
     * The progress indicator to use
     */
    String indicator = ".";
    /**
     * The last message that was printed
     */
    String lastMessage = "";

    ConsoleAnsi lastStatus = null;
    /**
     * The reader to read info from the console
     */
    LineReader reader;

    Terminal terminal;

    PrintStream out;
    PrintStream err;

    History history;

    /**
     * List of completers to be aggregated for tab completion
     */
    private final List<Completer> completers = new java.util.ArrayList<>();

    /**
     * The category of the current output
     */
    @SuppressWarnings("serial")
    Stack<String> category = new Stack<>() {
        @Override
        public String toString() {
            if (size() == 1) return peek() + CATEGORY_SEPARATOR;
            return DefaultGroovyMethods.join((Iterable) this, CATEGORY_SEPARATOR) + CATEGORY_SEPARATOR;
        }
    };

    /**
     * Whether ANSI should be enabled for output
     */
    private boolean ansiEnabled = true;

    /**
     * Whether user input is currently active
     */
    private boolean userInputActive;

    public void addShutdownHook() {
        if (!Environment.isFork()) {
            shutdownHookThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    beforeShutdown();
                }
            });
            Runtime.getRuntime().addShutdownHook(shutdownHookThread);
        }
    }

    public void removeShutdownHook() {
        if (shutdownHookThread != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
        }
    }

    protected GrailsConsole() throws IOException {
        cursorMove = 1;

        initialize(System.in, System.out, System.err);

        // bit of a WTF this, but see no other way to allow a customization indicator
        maxIndicatorString = new StringBuilder(indicator).append(indicator).append(indicator).append(indicator).append(indicator);

    }

    /**
     * Use in testing when System.out, System.err or System.in change
     *
     * @throws IOException
     */
    public void reinitialize(InputStream systemIn, PrintStream systemOut, PrintStream systemErr) throws IOException {
        if (terminal != null) {
            terminal.close();
        }
        initialize(systemIn, systemOut, systemErr);
    }

    protected void initialize(InputStream systemIn, PrintStream systemOut, PrintStream systemErr) throws IOException {
        bindSystemOutAndErr(systemOut, systemErr);

        redirectSystemOutAndErr(true);

        if (isInteractiveEnabled()) {
            if (isActivateTerminal()) {
                terminal = createTerminal();
            }
            history = prepareHistory();
            if (terminal != null) {
                reader = createLineReader(terminal, history);
                initializeHistory();
            }
        } else if (isActivateTerminal()) {
            terminal = createTerminal();
        }
    }

    /**
     * Initializes history by attaching it to the reader and loading existing entries.
     * This must be called after the LineReader is fully constructed.
     */
    private void initializeHistory() {
        if (history instanceof DefaultHistory && reader != null) {
            DefaultHistory defaultHistory = (DefaultHistory) history;
            try {
                defaultHistory.attach(reader);
            } catch (Exception e) {
                // History initialization failed, continue without persistent history
            }
        }
    }

    protected void bindSystemOutAndErr(PrintStream systemOut, PrintStream systemErr) {
        originalSystemOut = unwrapPrintStream(systemOut);
        out = originalSystemOut;
        originalSystemErr = unwrapPrintStream(systemErr);
        err = originalSystemErr;
    }

    private PrintStream unwrapPrintStream(PrintStream printStream) {
        if (printStream instanceof GrailsConsolePrintStream) {
            return ((GrailsConsolePrintStream) printStream).getTargetOut();
        }
        if (printStream instanceof GrailsConsoleErrorPrintStream) {
            return ((GrailsConsoleErrorPrintStream) printStream).getTargetOut();
        }
        return printStream;
    }

    public PrintStream getErr() {
        return err;
    }

    public void setErr(PrintStream err) {
        this.err = err;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public boolean isInteractiveEnabled() {
        return readPropOrTrue(ENABLE_INTERACTIVE);
    }

    private boolean isActivateTerminal() {
        return readPropOrTrue(ENABLE_TERMINAL);
    }

    private boolean readPropOrTrue(String prop) {
        String property = System.getProperty(prop);
        return property == null ? true : Boolean.valueOf(property);
    }

    protected LineReader createLineReader(Terminal terminal, History history) throws IOException {
        LineReaderBuilder builder = LineReaderBuilder.builder()
                .terminal(terminal)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true);
        if (history != null) {
            builder.variable(LineReader.HISTORY_FILE, new File(System.getProperty("user.home"), HISTORYFILE).toPath());
            builder.history(history);
        }
        return builder.build();
    }

    /**
     * Creates the instance of Terminal used directly in GrailsConsole.
     */
    protected Terminal createTerminal() throws IOException {
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        return terminal;
    }

    public void resetCompleters() {
        completers.clear();
        updateCompleter();
    }

    public void addCompleter(Completer completer) {
        if (completer != null) {
            completers.add(completer);
            updateCompleter();
        }
    }

    /**
     * Updates the LineReader completer using an AggregateCompleter when needed.
     */
    private void updateCompleter() {
        if (reader == null) {
            return;
        }
        if (!(reader instanceof LineReaderImpl)) {
            return;
        }
        LineReaderImpl lineReader = (LineReaderImpl) reader;
        if (completers.isEmpty()) {
            lineReader.setCompleter(null);
        } else {
            lineReader.setCompleter(new AggregateCompleter(completers));
        }
    }

    /**
     * Prepares a history file to be used by the LineReader. This file
     * will live in the home directory of the user.
     */
    protected History prepareHistory() throws IOException {
        File file = new File(System.getProperty("user.home"), HISTORYFILE);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {
                // can't create the file, so no history for you
            }
        }
        return file.canWrite() ? new DefaultHistory() : null;
    }

    public boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().indexOf("windows") != -1;
    }

    public static synchronized GrailsConsole getInstance() {
        if (instance == null) {
            try {
                final GrailsConsole console = createInstance();
                console.addShutdownHook();
                setInstance(console);
            } catch (IOException e) {
                throw new RuntimeException("Cannot create grails console: " + e.getMessage(), e);
            }
        }
        return instance;
    }

    public static synchronized void removeInstance() {
        if (instance != null) {
            instance.removeShutdownHook();
            instance.restoreOriginalSystemOutAndErr();
            if (instance.terminal != null) {
                try {
                    instance.terminal.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            instance = null;
        }
    }

    public void beforeShutdown() {
        persistHistory();
        restoreTerminal();
    }

    protected void restoreTerminal() {
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    protected void persistHistory() {
        if (history != null && reader != null) {
            try {
                history.save();
            } catch (Throwable e) {
                // ignore exception
            }
        }
    }

    public static void setInstance(GrailsConsole newConsole) {
        instance = newConsole;
        instance.redirectSystemOutAndErr(false);
    }

    protected void redirectSystemOutAndErr(boolean force) {
        if (!force && isSystemOutRedirectSuspended()) {
            return;
        }
        if (force || !(System.out instanceof GrailsConsolePrintStream)) {
            System.setOut(new GrailsConsolePrintStream(out));
        }
        if (force || !(System.err instanceof GrailsConsoleErrorPrintStream)) {
            System.setErr(new GrailsConsoleErrorPrintStream(err));
        }
    }

    /**
     * Whether something has deliberately taken over {@code System.out}/{@code System.err} and asked the
     * console to keep its hands off.
     *
     * <p>Every output method calls {@code verifySystemOut()}, which re-installs the console streams
     * whenever it does not recognise the current ones. That is the right default for a stream some
     * library replaced behind the console's back, but it also destroys a capture a caller installed on
     * purpose - test output capture, most obviously. Such a caller sets {@link #SUSPEND_SYSTEM_OUT_REDIRECT}
     * for the duration of the swap. A plain system property rather than a marker type keeps modules that
     * capture output (grails-test-core) free of any dependency on the cli tier.</p>
     */
    private static boolean isSystemOutRedirectSuspended() {
        return Boolean.getBoolean(SUSPEND_SYSTEM_OUT_REDIRECT);
    }

    public static GrailsConsole createInstance() throws IOException {
        String className = System.getProperty("grails.console.class");
        if (className != null) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends GrailsConsole> klass = (Class<? extends GrailsConsole>) Class.forName(className);
                return klass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new GrailsConsole();
    }

    public void setAnsiEnabled(boolean ansiEnabled) {
        this.ansiEnabled = ansiEnabled;
    }

    /**
     * @param verbose Sets whether verbose output should be used
     */
    public void setVerbose(boolean verbose) {
        if (verbose) {
            // enable big traces in verbose mode
            // note - can't use StackTraceFilterer#SYS_PROP_DISPLAY_FULL_STACKTRACE as it is in grails-core
            System.setProperty("grails.full.stacktrace", "true");
        }
        this.verbose = verbose;
    }

    /**
     * @param stacktrace Sets whether to show stack traces on errors
     */
    public void setStacktrace(boolean stacktrace) {
        this.stacktrace = stacktrace;
    }

    /**
     * @return Whether verbose output is being used
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * @return Whether to show stack traces
     */
    public boolean isStacktrace() {
        return stacktrace;
    }

    /**
     * @return The input stream being read from
     */
    public InputStream getInput() {
        assertAllowInput();
        return terminal != null ? terminal.input() : System.in;
    }

    private void assertAllowInput() {
        assertAllowInput(null);
    }

    private void assertAllowInput(String prompt) {
        if (reader == null) {
            String msg = "User input is not enabled, cannot obtain input stream";
            if (prompt != null) {
                msg = msg + " - while trying: " + prompt;
            }

            throw new IllegalStateException(msg);
        }
    }

    /**
     * @return The last message logged
     */
    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public LineReader getReader() {
        return reader;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public PrintStream getOut() {
        return out;
    }

    public Stack<String> getCategory() {
        return category;
    }

    /**
     * Indicates progress with the default progress indicator
     */
    @Override
    public void indicateProgress() {
        verifySystemOut();
        progressIndicatorActive = true;
        if (isAnsiEnabled()) {
            if (lastMessage != null && lastMessage.length() > 0) {
                if (!lastMessage.contains(maxIndicatorString)) {
                    updateStatus(lastMessage + indicator);
                }
            }
        } else {
            out.print(indicator);
        }
    }

    /**
     * Indicate progress for a number and total
     *
     * @param number The current number
     * @param total  The total number
     */
    @Override
    public void indicateProgress(int number, int total) {
        progressIndicatorActive = true;
        String currMsg = lastMessage;
        try {
            updateStatus(currMsg + ' ' + number + " of " + total);
        } finally {
            lastMessage = currMsg;
        }
    }

    /**
     * Indicates progress as a percentage for the given number and total
     *
     * @param number The number
     * @param total  The total
     */
    @Override
    public void indicateProgressPercentage(long number, long total) {
        verifySystemOut();
        progressIndicatorActive = true;
        String currMsg = lastMessage;
        try {
            int percentage = Math.round(NumberMath.multiply(NumberMath.divide(number, total), 100).floatValue());

            if (!isAnsiEnabled()) {
                out.print("..");
                out.print(percentage + '%');
            } else {
                updateStatus(currMsg + ' ' + percentage + '%');
            }
        } finally {
            lastMessage = currMsg;
        }
    }

    /**
     * Indicates progress by number
     *
     * @param number The number
     */
    @Override
    public void indicateProgress(int number) {
        verifySystemOut();
        progressIndicatorActive = true;
        String currMsg = lastMessage;
        try {
            if (isAnsiEnabled()) {
                updateStatus(currMsg + ' ' + number);
            } else {
                out.print("..");
                out.print(number);
            }
        } finally {
            lastMessage = currMsg;
        }
    }

    /**
     * Updates the current state message
     *
     * @param msg The message
     */
    @Override
    public void updateStatus(String msg) {
        outputMessage(msg, 1);
    }

    private void outputMessage(String msg, int replaceCount) {
        verifySystemOut();
        if (msg == null || msg.trim().length() == 0) return;
        try {
            if (isAnsiEnabled()) {
                if (replaceCount > 0) {
                    out.print(erasePreviousLine(CATEGORY_SEPARATOR));
                }
                lastStatus = outputCategory(ansi(), CATEGORY_SEPARATOR)
                        .fg(ConsoleAnsi.FG_DEFAULT).a(msg).reset();
                out.println(lastStatus);
                if (!userInputActive) {
                    cursorMove = replaceCount;
                }
            } else {
                if (lastMessage != null && lastMessage.equals(msg)) return;

                if (progressIndicatorActive) {
                    out.println();
                }

                out.print(CATEGORY_SEPARATOR);
                out.println(msg);
            }
            lastMessage = msg;
        } finally {
            postPrintMessage();
        }
    }

    private ConsoleAnsi moveDownToSkipPrompt() {
        return ansi()
                .cursorDown(1)
                .cursorLeft(PROMPT.length());
    }

    private void postPrintMessage() {
        progressIndicatorActive = false;
        appendCalled = false;
        if (userInputActive) {
            showPrompt();
        }
    }

    /**
     * Keeps doesn't replace the status message
     *
     * @param msg The message
     */
    @Override
    public void addStatus(String msg) {
        outputMessage(msg, 0);
        lastMessage = "";
    }

    /**
     * Prints an error message
     *
     * @param msg The error message
     */
    @Override
    public void error(String msg) {
        error(ERROR, msg);
    }

    /**
     * Prints an error message
     *
     * @param msg The error message
     */
    @Override
    public void warning(String msg) {
        error(WARNING, msg);
    }

    /**
     * Prints a warn message
     *
     * @param msg The message
     */
    @Override
    public void warn(String msg) {
        warning(msg);
    }

    private void logSimpleError(String msg) {
        verifySystemOut();
        if (progressIndicatorActive) {
            out.println();
        }
        out.println(CATEGORY_SEPARATOR);
        out.println(msg);
    }

    public boolean isAnsiEnabled() {
        AnsiOutput.Enabled configured = resolveConfiguredAnsi();
        if (configured == AnsiOutput.Enabled.NEVER) {
            return false;
        }
        if (configured == AnsiOutput.Enabled.ALWAYS) {
            return ansiEnabled;
        }
        return (terminal != null && !"dumb".equals(terminal.getType())) && ansiEnabled;
    }

    /**
     * Resolves Spring Boot's ansi setting, preferring what Boot has already bound and falling back to the
     * raw system property for the CLI, where no Environment exists to bind it.
     */
    private AnsiOutput.Enabled resolveConfiguredAnsi() {
        AnsiOutput.Enabled bound = AnsiOutput.getEnabled();
        if (bound != null && bound != AnsiOutput.Enabled.DETECT) {
            return bound;
        }
        String property = System.getProperty(SPRING_ANSI_PROPERTY);
        if (property == null) {
            return AnsiOutput.Enabled.DETECT;
        }
        try {
            return AnsiOutput.Enabled.valueOf(property.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            warnAboutUnsupportedAnsiValue(property);
            return AnsiOutput.Enabled.DETECT;
        }
    }

    /**
     * Reports an unusable ansi setting. Falling back to {@code detect} silently reads as "colour on",
     * which is the opposite of what someone writing {@code =false} or {@code =off} intended, and Spring
     * Boot's binder - which would reject the value outright - never sees a property read this way.
     *
     * <p>The notice goes to the captured stderr rather than through this console, because
     * {@link #isAnsiEnabled()} runs on every message; it is repeated only when the offending value
     * changes so a bad setting costs one line, not one per message.</p>
     */
    private void warnAboutUnsupportedAnsiValue(String property) {
        if (!property.equals(WARNED_ANSI_VALUE.getAndSet(property)) && err != null) {
            err.println("Ignoring unsupported " + SPRING_ANSI_PROPERTY + " value '" + property
                    + "'; expected one of always, detect or never. Falling back to detect.");
        }
    }

    /**
     * Use to log an error
     *
     * @param msg   The message
     * @param error The error
     */
    @Override
    public void error(String msg, Throwable error) {
        try {
            if ((verbose || stacktrace) && error != null) {
                printStackTrace(msg, error);
                error(ERROR, msg);
            } else {
                error(ERROR, msg + STACKTRACE_MESSAGE);
            }
        } finally {
            postPrintMessage();
        }
    }

    /**
     * Use to log an error
     *
     * @param error The error
     */
    @Override
    public void error(Throwable error) {
        printStackTrace(null, error);
    }

    private void printStackTrace(String message, Throwable error) {
        if ((error instanceof BuildException) && error.getCause() != null) {
            error = error.getCause();
        }
        if (!isVerbose() && !Boolean.getBoolean("grails.full.stacktrace")) {
            StackTraceUtils.deepSanitize(error);
        }
        StringWriter sw = new StringWriter();
        PrintWriter ps = new PrintWriter(sw);
        message = message == null ? error.getMessage() : message;
        if (!isVerbose()) {
            message = message + STACKTRACE_FILTERED_MESSAGE;
        }
        ps.println(message);
        error.printStackTrace(ps);
        error(sw.toString());
    }

    /**
     * Logs a message below the current status message
     *
     * @param msg The message to log
     */
    @Override
    public void log(String msg) {
        verifySystemOut();
        PrintStream printStream = out;
        try {
            if (userInputActive) {
                erasePrompt(printStream);
            }
            if (msg.endsWith(LINE_SEPARATOR)) {
                printStream.print(msg);
            } else {
                printStream.println(msg);
            }
            cursorMove = 0;
        } finally {
            printStream.flush();
            postPrintMessage();
        }
    }

    private void erasePrompt(PrintStream printStream) {
        // ConsoleAnsi always renders the escape it is asked for, so the caller has to decide whether
        // ansi is wanted. The previous ansi library returned a no-op builder while output was
        // disabled, which silently covered this call site.
        if (!isAnsiEnabled()) {
            return;
        }
        printStream.print(ansi()
                .eraseLineBackward().cursorLeft(PROMPT.length()));
    }

    /**
     * Logs a message below the current status message
     *
     * @param msg The message to log
     */
    private boolean appendCalled = false;

    public void append(String msg) {
        verifySystemOut();
        PrintStream printStream = out;
        try {
            if (userInputActive && !appendCalled) {
                if (isAnsiEnabled()) {
                    printStream.print(moveDownToSkipPrompt());
                }
                appendCalled = true;
            }
            if (msg.endsWith(LINE_SEPARATOR)) {
                printStream.print(msg);
            } else {
                printStream.println(msg);
            }
            cursorMove = 0;
        } finally {
            progressIndicatorActive = false;
        }
    }

    /**
     * Synonym for #log
     *
     * @param msg The message to log
     */
    @Override
    public void info(String msg) {
        log(msg);
    }

    @Override
    public void verbose(String msg) {
        verifySystemOut();
        try {
            if (verbose) {
                out.println(msg);
                cursorMove = 0;
            }
        } finally {
            postPrintMessage();
        }
    }

    /**
     * Replays the last status message
     */
    public void echoStatus() {
        if (lastStatus != null) {
            updateStatus(lastStatus.toString());
        }
    }

    /**
     * Replacement for AntBuilder.input() to eliminate dependency of
     * GrailsScriptRunner on the Ant libraries. Prints a message and
     * returns whatever the user enters (once they press &lt;return&gt;).
     *
     * @param msg The message/question to display.
     * @return The line of text entered by the user. May be a blank
     * string.
     */
    public String userInput(String msg) {
        return doUserInput(msg, false);
    }

    /**
     * Like {@link #userInput(String)} except that the user's entered characters will be replaced with '*' on the CLI,
     * masking the input (i.e. suitable for capturing passwords etc.).
     *
     * @param msg The message/question to display.
     * @return The line of text entered by the user. May be a blank
     * string.
     */
    public String secureUserInput(String msg) {
        return doUserInput(msg, true);
    }

    private String doUserInput(String msg, boolean secure) {
        // Add a space to the end of the message if there isn't one already.
        if (!msg.endsWith(" ") && !msg.endsWith("\t")) {
            msg += ' ';
        }

        lastMessage = "";
        msg = isAnsiEnabled() ? outputCategory(ansi(), ">").fg(ConsoleAnsi.FG_DEFAULT).a(msg).reset().toString() : msg;
        try {
            return readLine(msg, secure);
        } finally {
            cursorMove = 0;
        }
    }

    /**
     * Shows the prompt to request user input
     *
     * @param prompt The prompt to use
     * @return The user input prompt
     */
    private String showPrompt(String prompt) {
        verifySystemOut();
        cursorMove = 0;
        if (!userInputActive) {
            return readLine(prompt, false);
        }

        out.print(prompt);
        out.flush();
        return null;
    }

    private String readLine(String prompt, boolean secure) {
        assertAllowInput(prompt);
        userInputActive = true;
        try {
            if (secure) {
                return reader.readLine(prompt, SECURE_MASK_CHAR);
            } else if (defaultInputMask == null) {
                return reader.readLine(prompt);
            } else {
                return reader.readLine(prompt, defaultInputMask);
            }
        } catch (org.jline.reader.UserInterruptException e) {
            return null;
        } catch (org.jline.reader.EndOfFileException e) {
            return null;
        } finally {
            userInputActive = false;
        }
    }

    /**
     * Shows the prompt to request user input
     *
     * @return The user input prompt
     */
    public String showPrompt() {
        String prompt = isAnsiEnabled() ? ansiPrompt(PROMPT).toString() : PROMPT;
        return showPrompt(prompt);
    }

    private ConsoleAnsi ansiPrompt(String prompt) {
        return ansi()
                .bold()
                .fg(ConsoleAnsi.FG_YELLOW)
                .a(prompt)
                .boldOff()
                .fg(ConsoleAnsi.FG_DEFAULT);
    }

    public String userInput(String message, List<String> validResponses) {
        return userInput(message, validResponses.toArray(new String[validResponses.size()]));
    }

    /**
     * Replacement for AntBuilder.input() to eliminate dependency of
     * GrailsScriptRunner on the Ant libraries. Prints a message and
     * list of valid responses, then returns whatever the user enters
     * (once they press &lt;return&gt;). If the user enters something
     * that is not in the array of valid responses, the message is
     * displayed again and the method waits for more input. It will
     * display the message a maximum of three times before it gives up
     * and returns <code>null</code>.
     *
     * @param message        The message/question to display.
     * @param validResponses An array of responses that the user is
     *                       allowed to enter. Displayed after the message.
     * @return The line of text entered by the user, or <code>null</code>
     * if the user never entered a valid string.
     */
    public String userInput(String message, String[] validResponses) {
        if (validResponses == null) {
            return userInput(message);
        }

        String question = createQuestion(message, validResponses);
        String response = userInput(question);
        for (String validResponse : validResponses) {
            if (validResponse.equalsIgnoreCase(response)) {
                return response;
            }
        }
        cursorMove = 0;
        return userInput("Invalid input. Must be one of ", validResponses);
    }

    private String createQuestion(String message, String[] validResponses) {
        return message + "[" + DefaultGroovyMethods.join(validResponses, ",") + "] ";
    }

    private ConsoleAnsi outputCategory(ConsoleAnsi ansi, String categoryName) {
        return ansi
                .bold()
                .fg(ConsoleAnsi.FG_YELLOW)
                .a(categoryName)
                .a(SPACE)
                .boldOff();
    }

    private ConsoleAnsi outputErrorLabel(ConsoleAnsi ansi, String label) {
        return ansi
                .bold()
                .fg(ConsoleAnsi.FG_RED)
                .a(CATEGORY_SEPARATOR)
                .a(SPACE)
                .a(label)
                .a(" ")
                .boldOff()
                .fg(ConsoleAnsi.FG_DEFAULT);
    }

    private ConsoleAnsi erasePreviousLine(String categoryName) {
        int cursorMove = this.cursorMove;
        if (userInputActive) cursorMove++;
        if (cursorMove > 0) {
            int moveLeftLength = categoryName.length() + lastMessage.length();
            if (userInputActive) {
                moveLeftLength += PROMPT.length();
            }
            return ansi()
                    .cursorUp(cursorMove)
                    .cursorLeft(moveLeftLength)
                    .eraseLineForward();

        }
        return ansi();
    }

    @Override
    public void error(String label, String message) {
        verifySystemOut();
        if (message == null) {
            return;
        }

        cursorMove = 0;
        try {
            if (isAnsiEnabled()) {
                ConsoleAnsi ansi = outputErrorLabel(userInputActive ? moveDownToSkipPrompt() : ansi(), label).a(message).reset();

                if (message.endsWith(LINE_SEPARATOR)) {
                    out.print(ansi);
                } else {
                    out.println(ansi);
                }
            } else {
                out.print(label);
                out.print(" ");
                logSimpleError(message);
            }
        } finally {
            postPrintMessage();
        }
    }

    private void verifySystemOut() {
        // something bad may have overridden the system out
        redirectSystemOutAndErr(false);
    }

    public void restoreOriginalSystemOutAndErr() {
        System.setOut(originalSystemOut);
        System.setErr(originalSystemErr);
    }

    public void cleanlyExit(int status) {
        flush();
        System.exit(status);
    }

    /**
     * Makes sure that the console has been reset to the default state and that
     * the out stream has been flushed.
     */
    public void flush() {
        if (isAnsiEnabled()) {
            out.print(ansi().reset().toString());
        }
        out.flush();
    }

    public Character getDefaultInputMask() {
        return defaultInputMask;
    }

    public void setDefaultInputMask(Character defaultInputMask) {
        this.defaultInputMask = defaultInputMask;
    }

    /**
     * Gets the history for the LineReader
     * @return the history
     */
    public History getHistory() {
        return history;
    }
}
