package cf.playhi.freezeyou.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs one privileged shell command and retains its real result for the caller. */
public final class PrivilegedShellUtils {

    private static final java.util.concurrent.atomic.AtomicBoolean sBinderRequested =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static final java.util.concurrent.ExecutorService COMMAND_EXECUTOR =
            java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "privileged-shell");
                t.setDaemon(true);
                return t;
            });

    static {
        try {
            rikka.shizuku.Shizuku.addBinderDeadListener(() -> sBinderRequested.set(false));
        } catch (Throwable ignored) {}
    }

    private PrivilegedShellUtils() {}

    public static final class CommandResult {
        public final int exitCode;
        public final String output;

        public CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        public boolean isSuccessful() {
            return PrivilegedShellUtils.isSuccessful(exitCode, output);
        }
    }

    /** Which privileged shell runs a batch. */
    public enum Backend {
        /** Shizuku's shell: the binder is already held, no privilege escalation per session. */
        SHIZUKU,
        /** {@code su}: a process per session, and the approval prompt that comes with it. */
        ROOT
    }

    /**
     * One command of a batch and what it alone left behind: its exit code and the text it wrote,
     * with none of the output of the commands before or after it.
     */
    public static final class CommandOutcome {
        public final String command;
        public final int exitCode;
        public final String output;
        /**
         * False when the session ended before this command reported: nothing is known about it,
         * which is not the same as knowing it worked.
         */
        public final boolean reported;

        private CommandOutcome(String command, int exitCode, String output, boolean reported) {
            this.command = command;
            this.exitCode = exitCode;
            this.output = output;
            this.reported = reported;
        }

        public boolean isSuccessful() {
            return reported && PrivilegedShellUtils.isSuccessful(exitCode, output);
        }
    }

    /**
     * What a batch did. {@link #outcomes} holds one entry per command, in the order the commands
     * were given, so a caller can pair them with what it asked for by position.
     */
    public static final class BatchResult {
        public final Backend backend;
        /** False when no shell session could be opened at all, which means nothing was run. */
        public final boolean sessionStarted;
        /** Why a session could not be opened, for the caller to report; null when it could. */
        public final String error;
        public final List<CommandOutcome> outcomes;

        private BatchResult(Backend backend, boolean sessionStarted, String error,
                            List<CommandOutcome> outcomes) {
            this.backend = backend;
            this.sessionStarted = sessionStarted;
            this.error = error;
            this.outcomes = Collections.unmodifiableList(outcomes);
        }
    }

    public static boolean isShizukuAvailable() {
        return isShizukuAvailable(null);
    }

    public static boolean isShizukuAvailable(Context context) {
        try {
            if (!rikka.shizuku.Shizuku.pingBinder() && context != null && sBinderRequested.compareAndSet(false, true)) {
                rikka.shizuku.ShizukuProvider.requestBinderForNonProviderProcess(context.getApplicationContext());
            }
            return rikka.shizuku.Shizuku.pingBinder()
                    && rikka.shizuku.Shizuku.checkSelfPermission()
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static CommandResult runShizukuCommand(String command) throws Exception {
        return runShizukuCommand(command, 0L);
    }

    public static CommandResult runShizukuCommand(String command, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        boolean debug = DebugModeUtils.isDebugModeEnabled();
        if (debug) {
            Log.d("FreezeYouDebug", "runShizukuCommand starting: " + command);
        }
        Process process = rikka.shizuku.Shizuku.newProcess(
                new String[]{"sh", "-c", redirectOutput(command)}, null, null);
        CommandResult result = collectResult(process, timeoutMs);
        if (debug) {
            Log.d("FreezeYouDebug", "runShizukuCommand finished in " + (System.currentTimeMillis() - start)
                    + " ms, exitCode=" + result.exitCode + ", output=" + result.output);
        }
        return result;
    }

    public static boolean isRootAvailable() {
        return RootUtils.checkRootPermission();
    }

    public static CommandResult runRootCommand(String command) throws Exception {
        return runRootCommand(command, 0L);
    }

    public static CommandResult runRootCommand(String command, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        boolean debug = DebugModeUtils.isDebugModeEnabled();
        if (debug) {
            Log.d("FreezeYouDebug", "runRootCommand starting: " + command);
        }
        Process process = Runtime.getRuntime().exec(
                new String[]{RootUtils.getSuBinary(), "-c", redirectOutput(command)});
        CommandResult result = collectResult(process, timeoutMs);
        if (debug) {
            Log.d("FreezeYouDebug", "runRootCommand finished in " + (System.currentTimeMillis() - start)
                    + " ms, exitCode=" + result.exitCode + ", output=" + result.output);
        }
        return result;
    }

    /**
     * Prefix of the line written after every command of a batch, carrying that command's position
     * and its exit code: {@code __FREEZEYOU_RC__17:0}.
     */
    /**
     * Prefix of the line written after every command of a batch, carrying that command's position
     * and its exit code: {@code __FREEZEYOU_RC__17:0}.
     */
    private static final String RESULT_MARKER = "__FREEZEYOU_RC__";

    /**
     * Handshake marker written immediately upon opening a privileged shell session to confirm that
     * root permission has been granted by the user before starting the batch execution timer.
     */
    private static final String READY_MARKER = "__FREEZEYOU_READY__";

    /** Commands handed to one shell session before a fresh process is taken. */
    private static final int MAX_COMMANDS_PER_SESSION = 40;

    /** Timeout for the user to respond to root authorization prompts (Magisk/KernelSU/SuperSU). */
    private static final long ROOT_APPROVAL_TIMEOUT_MS = 120_000L;

    /** Base time a session has to finish its chunk of commands before it is taken as lost. */
    private static final long SESSION_TIMEOUT_BASE_MS = 30_000L;

    /** Extra time a session gets for every command in its chunk. */
    private static final long SESSION_TIMEOUT_PER_COMMAND_MS = 2_000L;

    /**
     * Time a chunk of {@code commandCount} commands has to finish before its session is taken as
     * lost. A flat deadline that suits one command cuts a full chunk short on a slow device, where
     * a cold {@code app_process} or {@code pm} start costs a second or two per command.
     */
    public static long computeSessionTimeoutMs(int commandCount) {
        return SESSION_TIMEOUT_BASE_MS + commandCount * SESSION_TIMEOUT_PER_COMMAND_MS;
    }

    /**
     * Runs the commands through as few privileged shell sessions as it can and reports each command
     * on its own. Shizuku's shell is used where it is granted, {@code su} otherwise.
     *
     * <p>Freezing two dozen applications this way means two processes rather than two dozen. It also
     * means the caller learns which of them failed: a session reports one exit code for everything
     * it was fed, so a batch that was refused for one application looked exactly like a batch that
     * did its work.
     *
     * @param commands single-line commands, already shell-quoted by the caller
     * @param context used only to reach Shizuku's binder; may be null to force {@code su}
     * @return one outcome per command, in the order the commands were given
     */
    public static BatchResult executeBatchPrivilegedCommand(List<String> commands, Context context) {
        return executeBatchPrivilegedCommand(commands, context,
                isShizukuAvailable(context) ? Backend.SHIZUKU : Backend.ROOT);
    }

    /**
     * The same, with the shell named by the caller — for the paths whose whole meaning is tied to one
     * of them: a root-mode freeze asks for root even where Shizuku is also available, because the
     * commands it sends are not the ones the Shizuku path sends.
     */
    public static BatchResult executeBatchPrivilegedCommand(List<String> commands, Context context,
                                                            Backend backend) {
        List<CommandOutcome> outcomes = new ArrayList<>();
        if (commands == null || commands.isEmpty()) {
            return new BatchResult(backend, true, null, outcomes);
        }
        Backend used = backend;
        boolean started = false;
        String failure = null;
        int sessions = 0;
        long startMs = System.currentTimeMillis();
        boolean debug = DebugModeUtils.isDebugModeEnabled();
        java.util.concurrent.atomic.AtomicBoolean sessionStarted =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        for (int from = 0; from < commands.size(); from += MAX_COMMANDS_PER_SESSION) {
            int to = Math.min(from + MAX_COMMANDS_PER_SESSION, commands.size());
            List<String> chunk = commands.subList(from, to);
            Map<Integer, CommandResult> reported = new java.util.concurrent.ConcurrentHashMap<>();
            try {
                // Shizuku can be gone by the time a chunk runs, even when the caller asked for it
                // because it was there a moment ago. Root is the shell left over, and the batch
                // still runs rather than reporting packages it never touched.
                if (used == Backend.SHIZUKU && !isShizukuAvailable(context)) {
                    used = Backend.ROOT;
                }
                sessionStarted.set(false);
                runBatchSession(chunk, from, used, context, reported, sessionStarted);
                sessions++;
            } catch (Exception e) {
                e.printStackTrace();
                failure = e.getMessage();
            }
            if (sessionStarted.get()) {
                started = true;
            }
            outcomes.addAll(toOutcomes(chunk, from, reported));
        }
        if (debug) {
            Log.d("FreezeYouDebug", "executeBatchPrivilegedCommand ran " + commands.size()
                    + " commands through " + sessions + " " + used + " session(s) in "
                    + (System.currentTimeMillis() - startMs) + " ms, failure=" + failure);
        }
        return new BatchResult(used, started, failure, outcomes);
    }

    /**
     * One command of a session, and straight after it the exit code it left, marked with its place in
     * the batch. Pairing them on one line is what makes a session answer per command: the shell's own
     * exit code says only whether the last line was understood, and by the time it is known the
     * commands that failed are twenty lines back.
     *
     * <p>Package names reach here through {@link #shellQuote}, which is what keeps a name typed into
     * a list, or read out of a backup file, from becoming a second command.
     */
    public static String batchLine(String command, int index) {
        if (command == null) {
            throw new IllegalArgumentException("Command must not be null");
        }
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
            // A command spanning lines would leave its marker after only the first of them, and the
            // exit code that follows belongs to the last.
            throw new IllegalArgumentException("A batched command must be a single line");
        }
        return "(" + command + ") 2>&1; echo " + RESULT_MARKER + index + ":$?\n";
    }

    /**
     * Reads a session's output and splits it back into one result per command.
     *
     * @return exit code and output by the position of the command in the batch
     */
    public static Map<Integer, CommandResult> parseBatchResults(InputStream inputStream)
            throws IOException {
        Map<Integer, CommandResult> results = new LinkedHashMap<>();
        parseBatchResults(inputStream, results);
        return results;
    }

    /**
     * Reads a session's output into a live results map as each result line arrives, ensuring that
     * outcomes completed prior to a timeout or stream closure are preserved.
     */
    public static void parseBatchResults(InputStream inputStream,
                                         Map<Integer, CommandResult> results)
            throws IOException {
        parseBatchResultsFromReader(new BufferedReader(new InputStreamReader(inputStream)), results);
    }

    private static void parseBatchResultsFromReader(BufferedReader reader,
                                                   Map<Integer, CommandResult> results)
            throws IOException {
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(RESULT_MARKER)) {
                int colon = line.indexOf(':', RESULT_MARKER.length());
                if (colon > 0) {
                    try {
                        int index = Integer.parseInt(
                                line.substring(RESULT_MARKER.length(), colon));
                        int exitCode = Integer.parseInt(line.substring(colon + 1).trim());
                        results.put(index, new CommandResult(exitCode, output.toString()));
                        output.setLength(0);
                        continue;
                    } catch (NumberFormatException ignored) {
                        // Something printed the prefix without carrying a result. It is output.
                    }
                }
            }
            if (output.length() > 0) {
                output.append('\n');
            }
            output.append(line);
        }
    }

    /**
     * Opens one shell, confirms readiness via handshake, feeds it a chunk of the batch and reads
     * back what each command said.
     *
     * @param offset where this chunk starts in the batch, so the markers name the right commands
     */
    private static void runBatchSession(List<String> commands, int offset,
                                        Backend backend, Context context,
                                        Map<Integer, CommandResult> liveResults,
                                        java.util.concurrent.atomic.AtomicBoolean sessionStarted)
            throws Exception {
        Process process = null;
        DataOutputStream writer = null;
        try {
            final Process session = startSession(backend, context);
            process = session;
            writer = new DataOutputStream(session.getOutputStream());
            final BufferedReader readyReader =
                    new BufferedReader(new InputStreamReader(session.getInputStream()));

            // Handshake: write ready marker and wait for confirmation so that user approval time
            // (e.g. root prompts) does not consume the batch execution deadline.
            writer.writeBytes("echo " + READY_MARKER + "\n");
            writer.flush();
            Future<Boolean> handshake = COMMAND_EXECUTOR.submit(() -> {
                String line;
                while ((line = readyReader.readLine()) != null) {
                    if (READY_MARKER.equals(line.trim())) {
                        return true;
                    }
                }
                return false;
            });
            boolean ready;
            try {
                ready = handshake.get(ROOT_APPROVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                handshake.cancel(true);
                throw new IOException("Shell handshake timed out or was refused: " + e.getMessage(), e);
            }
            if (!ready) {
                throw new IOException("Shell closed without handshake confirmation");
            }
            sessionStarted.set(true);

            // Shell confirmed ready; now start background stream reader for batch outcomes
            Future<?> reading = COMMAND_EXECUTOR.submit(() -> {
                try {
                    parseBatchResultsFromReader(readyReader, liveResults);
                } catch (IOException ignored) {
                }
            });
            StringBuilder batch = new StringBuilder();
            for (int i = 0; i < commands.size(); i++) {
                batch.append(batchLine(commands.get(i), offset + i));
            }
            batch.append("exit\n");
            writer.writeBytes(batch.toString());
            writer.flush();
            reading.get(computeSessionTimeoutMs(commands.size()), TimeUnit.MILLISECONDS);
        } finally {
            ProcessUtils.destroyProcess(writer, process);
        }
    }
    private static Process startSession(Backend backend, Context context) throws Exception {
        if (backend == Backend.SHIZUKU) {
            // "-s" reads the commands from this stream. Without an argument sh would take the first
            // line of the batch as a script file and run nothing at all.
            return rikka.shizuku.Shizuku.newProcess(new String[]{"sh", "-s"}, null, null);
        }
        return Runtime.getRuntime().exec(new String[]{RootUtils.getSuBinary()});
    }

    /**
     * Pairs the commands of a chunk with what the session said about them, in order. A command the
     * session never answered is kept as an outcome that did not report, so the caller's list lines up
     * with the list it sent.
     */
    public static List<CommandOutcome> toOutcomes(List<String> commands, int offset,
                                                  Map<Integer, CommandResult> reported) {
        List<CommandOutcome> outcomes = new ArrayList<>(commands.size());
        for (int i = 0; i < commands.size(); i++) {
            CommandResult result = reported == null ? null : reported.get(offset + i);
            outcomes.add(result == null
                    ? new CommandOutcome(commands.get(i), -1, "", false)
                    : new CommandOutcome(commands.get(i), result.exitCode, result.output, true));
        }
        return outcomes;
    }

    public static String shellQuote(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Shell argument must not be null");
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static boolean isSuccessful(int exitCode, String output) {
        if (exitCode != 0) return false;
        if (output == null || output.isEmpty()) return true;

        for (String line : output.split("\\r?\\n")) {
            String normalized = line.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("failure")
                    || normalized.startsWith("error")
                    || normalized.startsWith("exception")
                    || normalized.startsWith("failed")
                    || normalized.startsWith("unknown command")) {
                return false;
            }
        }
        return true;
    }

    private static String redirectOutput(String command) {
        return "(" + command + ") 2>&1";
    }

    private static CommandResult collectResult(Process process, long timeoutMs) throws Exception {
        if (timeoutMs <= 0) {
            StringBuilder output = new StringBuilder();
            try {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (output.length() > 0) output.append('\n');
                        output.append(line);
                    }
                }
                return new CommandResult(process.waitFor(), output.toString());
            } finally {
                process.destroy();
            }
        }
        java.util.concurrent.Future<CommandResult> future = COMMAND_EXECUTOR.submit(() -> {
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) output.append('\n');
                    output.append(line);
                }
            }
            return new CommandResult(process.waitFor(), output.toString());
        });
        try {
            return future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            process.destroy();
            throw e;
        } finally {
            process.destroy();
        }
    }
}
