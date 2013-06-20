package org.randoom.setlxUI.android;

import org.randoom.setlx.exceptions.JVMIOException;
import org.randoom.setlx.exceptions.ParserException;
import org.randoom.setlx.statements.Block;
import org.randoom.setlx.utilities.ParseSetlX;
import org.randoom.setlx.utilities.State;
import org.randoom.util.AndroidUItools;

import android.os.AsyncTask;

/*package*/ class SetlXExecutionTask extends AsyncTask<String, String, Void> {

    public  final static String  ECUTE_CODE    = "code";
    public  final static String  EXECUTE_FILE  = "file";

    private final static String  PUBLISH_ERR   = "err";
    private final static String  PUBLISH_IN    = "in";
    private final static String  PUBLISH_OUT   = "out";
    private final static String  PUBLISH_PMP   = "prompt";
    private final static String  PUBLISH_STATS = "cpu+mem";
    private       static String  input;

    private final        State              state;
    private final        AndroidEnvProvider startEnv;
    private              Thread             statsUpdate;
    private              float              cpuUsage;
    private              long               memoryUsage;
    private              int                ticks;
    private              Thread             execution;

    /*package*/ SetlXExecutionTask(final State state) {
        this.state       = state;
        this.startEnv    = (AndroidEnvProvider) state.getEnvironmentProvider();
        this.statsUpdate = null;
        this.execution   = null;
        this.cpuUsage    = 0.0f;
        this.memoryUsage = 0;
        this.ticks       = 0;
    }

    /*package*/ void addError(final String msg) {
        publishProgress(PUBLISH_ERR, msg);
    }

    /*package*/ void addOutput(final String msg) {
        publishProgress(PUBLISH_OUT, msg);
    }

    /*package*/ void addPrompt(final String msg) {
        publishProgress(PUBLISH_PMP, msg);
    }

    /*package*/ String readString() throws JVMIOException {
        input = null;
        publishProgress(PUBLISH_IN);
        while (input == null) {
            try {
                Thread.sleep(250);
            } catch (final InterruptedException e) {
                throw new JVMIOException("Unable to read input!");
            }
        }
        return input;
    }

    /*package*/ void setInput(final String in) {
        input = in;
    }

    /*package*/ boolean isRunning() {
        if (execution != null && statsUpdate != null) {
            return execution.isAlive() || statsUpdate.isAlive();
        } else {
            return false;
        }
    }

    /*package*/ void interrupt() {
        if (statsUpdate != null && statsUpdate.isAlive()) {
            statsUpdate.interrupt();
        }
        if (execution != null && execution.isAlive()) {
            execution.interrupt();
        }
    }

    @Override
    protected void onPreExecute() {
        startEnv.lockUI(true);
    }

    @Override
    protected Void doInBackground(final String... code) {
        final String mode        = code[0];
        final String setlXobject = code[1];

        statsUpdate = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (startEnv == state.getEnvironmentProvider()) {
                        cpuUsage    = AndroidUItools.getCPUusage();
                        memoryUsage = AndroidUItools.getUsedMemory();
                        ++ticks;
                        publishProgress(PUBLISH_STATS);
                        Thread.sleep(450);
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
                }  catch (final OutOfMemoryError oome) { // Somehow this never works properly on ANDROID ;-(
                    state.errWriteOutOfMemory(false, true);
                    b = null;
                } catch (final Exception e) { // this should never happen...
                    state.errWriteInternalError(e);
                    b = null;
                }
                if (b != null) {
                    b.executeWithErrorHandling(state, false);
                }
            }
        });
        execution.setPriority(Thread.MIN_PRIORITY);

        statsUpdate.start();
        execution.start();
        try {
            execution.join();
        } catch (final InterruptedException e) {
            execution.interrupt();
        }
        statsUpdate.interrupt();

        return null;
    }

    @Override
    protected void onProgressUpdate(final String...msg) {
        if (startEnv == state.getEnvironmentProvider()) {
            if (msg[0] == PUBLISH_OUT) {
                startEnv.appendOut(msg[1]);
            } else if (msg[0] == PUBLISH_ERR) {
                startEnv.appendErr(msg[1]);
            } else if (msg[0] == PUBLISH_PMP) {
                startEnv.appendPrompt(msg[1]);
            } else if (msg[0] == PUBLISH_IN) {
                startEnv.getInputLine();
            } else if (msg[0] == PUBLISH_STATS) {
                startEnv.updateStats(ticks, cpuUsage, memoryUsage);
            }
        }
    }

    @Override
    protected void onPostExecute(final Void result) {
        if (startEnv == state.getEnvironmentProvider()) {
            startEnv.lockUI(false);
        }
    }

    @Override
    protected void onCancelled(final Void result) {
        if (statsUpdate != null && statsUpdate.isAlive()) {
            statsUpdate.interrupt();
        }
        if (execution != null && execution.isAlive()) {
            execution.interrupt();
        }
    }
}
