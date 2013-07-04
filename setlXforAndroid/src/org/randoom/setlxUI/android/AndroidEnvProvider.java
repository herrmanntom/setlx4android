package org.randoom.setlxUI.android;

import org.randoom.setlx.exceptions.JVMIOException;
import org.randoom.setlx.exceptions.ParserException;
import org.randoom.setlx.statements.Block;
import org.randoom.setlx.utilities.EnvironmentProvider;
import org.randoom.setlx.utilities.ParseSetlX;
import org.randoom.setlx.utilities.State;
import org.randoom.util.AndroidUItools;

import java.util.LinkedList;
import java.util.Locale;

// This provides access to the I/O mechanisms of Android
/*package*/ class AndroidEnvProvider implements EnvironmentProvider {
    private final static String TAB          = "\t";
    private final static String ENDL         = "\n";
    private final static String ERROR_KEY    = "\0\0sETLx_eRRoR_@48456890012\0\0";
    private final static String PROMPT_KEY   = "\0\0sETLx_pROMpT_@8478904199\0\0";
    private final static String CODE_DIR     = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/SetlX/";
    private final static String LIBRARY_DIR  = CODE_DIR + "library/";
    public  final static int    ECUTE_CODE   = 88;
    public  final static int    EXECUTE_FILE = 99;

    private final static int    sMAX_CHARS  = 25000;

    private SetlXforAndroidActivity  activity;
    private String                   lastPrompt;
    private final LinkedList<String> messageBuffer;
    private boolean                  isLocked;
    private String                   currentDir;
    private String                   input;

    private Thread                   statsUpdate;
    private float                    cpuUsage;
    private long                     memoryUsage;
    private int                      ticks;
    private Thread                   execution;

    /*package*/ AndroidEnvProvider(final SetlXforAndroidActivity activity) {
        this.activity       = activity;
        this.lastPrompt     = null;
        this.messageBuffer  = new LinkedList<String>();
        this.isLocked       = false;
        this.currentDir     = CODE_DIR;
        this.input          = null;

        this.statsUpdate    = null;
        this.cpuUsage       = 0.0f;
        this.memoryUsage    = 0;
        this.ticks          = 0;
        this.execution      = null;
    }

    /*package*/ void updateUI(final SetlXforAndroidActivity activity) {
        this.activity   = activity;
    }

    /*package*/ boolean isMessagesBufferEmpty() {
        return this.messageBuffer.isEmpty();
    }

    /*package*/ void depleteMessageBuffer() {
        String msg;
        while (activity.isActive() && ! messageBuffer.isEmpty()) {
            msg = messageBuffer.pollFirst();
            if (msg.startsWith(ERROR_KEY)) {
                activity.appendErr(msg.substring(ERROR_KEY.length()));
            } else if (msg.startsWith(PROMPT_KEY)) {
                activity.appendPrompt(msg.substring(PROMPT_KEY.length()));
            } else {
                activity.appendOut(msg);
            }
        }
    }

    /*package*/ void execute(final State state, final String code) {
        this.currentDir = CODE_DIR;
        execute(state, ECUTE_CODE, code);
    }

    /*package*/ void executeFile(final State state, final String fileName) {
        if (fileName.lastIndexOf('/') != -1) {
            this.currentDir = fileName.substring(0, fileName.lastIndexOf('/') + 1);
        } else {
            this.currentDir = CODE_DIR;
        }
        execute(state, EXECUTE_FILE, fileName);
    }

    private void execute(final State state, final int mode, final String setlXobject) {
        this.isLocked = true;
        this.activity.lockUI(true);

        final AndroidEnvProvider startEnv = this;
        try {
            statsUpdate = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (startEnv == state.getEnvironmentProvider() &&
                               ! state.isExecutionStopped
                        ) {
                            cpuUsage    = AndroidUItools.getCPUusage(248);
                            memoryUsage = AndroidUItools.getUsedMemory();
                            ++ticks;
                            startEnv.updateStats(ticks, cpuUsage, memoryUsage);
                            Thread.sleep(250);
                        }
                    } catch (final InterruptedException e) {
                        // while is already broken here => done
                    }
                }
            });
            statsUpdate.setPriority(Thread.MAX_PRIORITY);

            execution = new Thread(new Runnable() {
                @Override
                public void run() {
                    state.resetParserErrorCount();
                    Block b = null;
                    try {
                        if (mode == ECUTE_CODE) {
                            b = ParseSetlX.parseStringToBlock(state, setlXobject);
                            b.markLastExprStatement();
                        } else /* if (mode == sEXECUTE_FILE) */ {
                            b = ParseSetlX.parseFile(state, setlXobject);
                        }
                    } catch (final ParserException pe) {
                        state.errWriteLn(pe.getMessage());
                        b = null;
                    } catch (final StackOverflowError soe) {
                        state.errWriteOutOfStack(soe, true);
                        b = null;
                    } catch (final OutOfMemoryError oome) { // Somehow this never works properly on ANDROID ;-(
                        try {
                            // free some memory
                            state.resetState();
                            // give hint to the garbage collector
                            Runtime.getRuntime().gc();
                            // sleep a while
                            Thread.sleep(50);
                        } catch (final InterruptedException e) {
                            /* don't care anymore */
                        }

                        state.errWriteOutOfMemory(false, true);
                        b = null;
                    } catch (final Exception e) { // this should never happen...
                        state.errWriteInternalError(e);
                        b = null;
                    }
                    if (b != null) {
                        b.executeWithErrorHandling(state, false);
                    }

                    if (statsUpdate != null && statsUpdate.isAlive()) {
                        statsUpdate.interrupt();
                    }

                    if (startEnv == state.getEnvironmentProvider()) {
                        isLocked = false;
                        activity.lockUI(false);
                        activity.postExecute();
                    }
                }
            });
            execution.setPriority(Thread.MIN_PRIORITY);

            statsUpdate.start();
            execution.start();
        } catch (final StackOverflowError soe) {
            state.errWriteOutOfStack(soe, false);
        } catch (final OutOfMemoryError oome) { // Somehow this never works properly on ANDROID ;-(
            try {
                // free some memory
                state.resetState();
                // give hint to the garbage collector
                Runtime.getRuntime().gc();
                // sleep a while
                Thread.sleep(50);
            } catch (final InterruptedException e) {
                /* don't care anymore */
            }

            state.errWriteOutOfMemory(false, true);
        } catch (final Exception e) { // this should never happen...
            state.errWriteInternalError(e);
        }
    }

    /*package*/ boolean isLocked() {
        return this.isLocked;
    }

    /*package*/ boolean isExecuting() {
        return (execution   != null && execution.isAlive()  ) ||
               (statsUpdate != null && statsUpdate.isAlive());
    }

    /*package*/ void interrupt() {
        while (isExecuting()) {
            if (statsUpdate != null && statsUpdate.isAlive()) {
                statsUpdate.interrupt();
            }
            if (execution != null && execution.isAlive()) {
                execution.interrupt();
            }

            // wait until threads die
            try {
                Thread.sleep(250);
            } catch (final InterruptedException e) {}
        }
    }

    /*package*/ void setInput(final String input) {
        this.input = input;
    }

    /*package*/ void updateStats(final int ticks, final float usedCPU, final long usedMemory) {
        if (activity.isActive()) {
            final String ticksStr = String.format(Locale.ENGLISH, "%3d", ticks);
            final String cpuUsage = String.format(Locale.ENGLISH, "%3d", (int) (usedCPU * 100));
            final String memUsage = String.format(Locale.ENGLISH, "%3d", usedMemory / (1024 * 1024));
            activity.updateStats(ticksStr, cpuUsage, memUsage);
        }
    }

    /*package*/ String getCodeDir() {
        return CODE_DIR;
    }

    // create code directory, if it does not exist
    /*package*/ boolean createCodeDir() {
        final boolean result = AndroidUItools.createDirIfNotExists(CODE_DIR);
        if ( ! result) {
            return result;
        }
        return AndroidUItools.createDirIfNotExists(LIBRARY_DIR);
    }

    // get path relative to sCODE_DIR
    /*package*/ String stripPath(String fileName) {
        fileName = fileName.trim();
        if (fileName.charAt(0) != '/' || ! fileName.startsWith(CODE_DIR)) {
            return fileName;
        } else /* if (fileName.startsWith(sCODE_DIR)) */ {
            return fileName.replace(CODE_DIR, "");
        }
    }

    // get absolute path from one relative to sCODE_DIR
    /*package*/ String expandPath(String fileName) {
        fileName = fileName.trim();
        if (fileName.length() < 1 || fileName.charAt(0) == '/') {
            return fileName;
        } else {
            return CODE_DIR + fileName;
        }
    }

    /* interface functions */

    // read from input
    @Override
    public boolean inReady() {
        return false;
    }

    @Override
    public String inReadLine() throws JVMIOException {
        this.input = null;

        activity.readLine(lastPrompt);
        lastPrompt = null;

        while (input == null) {
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                throw new JVMIOException("Unable to read input!");
            }
        }
        outWrite(input + ENDL);

        return input;
    }

    // write to standard output
    @Override
    public void outWrite(final String msg) {
        final int length;
        if ((length = msg.length()) < sMAX_CHARS) {
            messageBuffer.addLast(msg);
        } else {
            // work around text corruption with very very long strings
            for (int i = 0; i < length; i += sMAX_CHARS) {
                if (i + sMAX_CHARS < length) {
                    messageBuffer.addLast(msg.substring(i, i + sMAX_CHARS) + "\n");
                } else {
                    messageBuffer.addLast(msg.substring(i));
                }
            }
        }
        depleteMessageBuffer();
    }

    // write to standard error
    @Override
    public void errWrite(final String msg) {
        outWrite(ERROR_KEY + msg);
    }

    // prompt user for input
    @Override
    public void promptForInput(final String msg) {
        outWrite(PROMPT_KEY + msg);
        lastPrompt = msg;
    }

    // some text format stuff
    @Override
    public String getTab() {
        return TAB;
    }

    @Override
    public String getEndl() {
        return ENDL;
    }

    @Override
    public int getMaxStackSize() {
        return 175;
    }

    // allow modification of fileName/path when reading files
    @Override
    public String filterFileName(String fileName) {
        fileName = fileName.trim();
        if (fileName.length() < 1 || fileName.charAt(0) == '/') {
            return fileName;
        } else {
            AndroidUItools.createDirIfNotExists(CODE_DIR);
            return currentDir + fileName;
        }
    }

    // allow modification of library name
    @Override
    public String filterLibraryName(String name) {
        name = name.trim();
        if (name.length() < 1 || name.charAt(0) == '/') {
            return name;
        } else {
            AndroidUItools.createDirIfNotExists(LIBRARY_DIR);
            return LIBRARY_DIR + name;
        }
    }
}
