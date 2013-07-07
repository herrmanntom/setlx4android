package org.randoom.setlxUI.android;

import org.randoom.setlx.exceptions.ParserException;
import org.randoom.setlx.statements.Block;
import org.randoom.setlx.utilities.ParseSetlX;
import org.randoom.setlx.utilities.State;
import org.randoom.util.AndroidUItools;

/*package*/ class Executioner {
    private final static int              ECUTE_CODE   = 88;
    private final static int              EXECUTE_FILE = 99;

    private final State                   state;
    private final AndroidEnvProvider      envProvider;
    private final SetlXforAndroidActivity activity;

    private       Thread                  statsUpdate;
    private       float                   cpuUsage;
    private       long                    memoryUsage;
    private       int                     ticks;

    private       Thread                  execution;

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

    /*package*/ void execute(final String code) {
        envProvider.setCurrentDir(envProvider.getCodeDir());
        execute(ECUTE_CODE, code);
    }

    /*package*/ void executeFile(final String fileName) {
        if (fileName.lastIndexOf('/') != -1) {
            envProvider.setCurrentDir(fileName.substring(0, fileName.lastIndexOf('/') + 1));
        } else {
            envProvider.setCurrentDir(envProvider.getCodeDir());
        }
        execute(EXECUTE_FILE, fileName);
    }

    private void execute(final int mode, final String setlXobject) {
        try {
            statsUpdate = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            cpuUsage    = AndroidUItools.getCPUusage(248);
                            memoryUsage = AndroidUItools.getUsedMemory();
                            ++ticks;
                            envProvider.updateStats(ticks, cpuUsage, memoryUsage);
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
