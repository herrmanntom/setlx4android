package org.randoom.setlxUI.android;

import org.randoom.setlx.exceptions.ParserException;
import org.randoom.setlx.statements.Block;
import org.randoom.setlx.utilities.ParseSetlX;
import org.randoom.setlx.utilities.State;
import org.randoom.util.AndroidUItools;

/**
 * Class handling the execution on setlX code and the concurrent update of the
 * statistics display.
 */
/*package*/ class Executioner {
    private enum ExecutionMode {
        EXECUTE_CODE,
        EXECUTE_FILE
    }

    private final State                   state;
    private final AndroidEnvProvider      envProvider;
    private final SetlXforAndroidActivity activity;

    private       Thread                  statsUpdate;
    private       float                   cpuUsage;
    private       long                    memoryUsage;
    private       int                     ticks;

    private       Thread                  execution;

    /**
     * Create a new Executioner.
     *
     * @param state    Current state of the running setlX program.
     * @param activity setlX main UI.
     */
    /*package*/ Executioner(final State state, final SetlXforAndroidActivity activity) {
        this.state       = state;
        this.envProvider = (AndroidEnvProvider) state.getEnvironmentProvider();
        this.activity    = activity;

        this.statsUpdate = null;
        this.cpuUsage    = 0.0f;
        this.memoryUsage = 0;
        this.ticks       = 0;
        this.execution   = null;
    }

    /**
     * Execute a string of setlX-code.
     *
     * @param code setlX-code to execute.
     */
    /*package*/ void execute(final String code) {
        envProvider.setCurrentDir(envProvider.getCodeDir());
        execute(ExecutionMode.EXECUTE_CODE, code);
    }

    /**
     * Execute a file containing setlX-code.
     *
     * @param fileName Name of the file to parse and execute.
     */
    /*package*/ void executeFile(final String fileName) {
        if (fileName.lastIndexOf('/') != -1) {
            envProvider.setCurrentDir(fileName.substring(0, fileName.lastIndexOf('/') + 1));
        } else {
            envProvider.setCurrentDir(envProvider.getCodeDir());
        }
        execute(ExecutionMode.EXECUTE_FILE, fileName);
    }

    private void execute(final ExecutionMode mode, final String setlXobject) {
        try {
            statsUpdate = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        do {
                            cpuUsage    = AndroidUItools.getCPUusage(248);
                            memoryUsage = AndroidUItools.getUsedMemory();
                            ++ticks;
                            envProvider.updateStats(ticks, cpuUsage, memoryUsage);
                            Thread.sleep(250);
                        } while (isExecuting());
                    } catch (final InterruptedException e) {
                        // loop is already broken here => done
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
                        if (mode == ExecutionMode.EXECUTE_CODE) {
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

                    try {
                        while (isUpdatingStats()) {
                            statsUpdate.interrupt();
                            // wait until thread dies
                            Thread.sleep(10);
                        }
                    } catch (final InterruptedException e) {}

                    if (envProvider == state.getEnvironmentProvider()) {
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

    private boolean isExecuting() {
        return (execution != null && execution.isAlive());
    }

    private boolean isUpdatingStats() {
        return (statsUpdate != null && statsUpdate.isAlive());
    }

    /**
     * Interrupt current execution.
     *
     * Returns only after all previously spawned threads are dead.
     */
    /*package*/ void interrupt() {
        while (isExecuting() || isUpdatingStats()) {
            try {
                while (isExecuting()) {
                    execution.interrupt();
                    // wait until thread dies
                    Thread.sleep(250);
                }

                while (isUpdatingStats()) {
                    statsUpdate.interrupt();
                    // wait until thread dies
                    Thread.sleep(10);
                }
            } catch (final InterruptedException e) {}
        }
    }
}
