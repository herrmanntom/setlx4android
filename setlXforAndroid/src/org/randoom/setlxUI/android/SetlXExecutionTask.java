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
    private              boolean            running;
    private              int                ticks;

    /*package*/ SetlXExecutionTask(final State state) {
        this.state    = state;
        this.startEnv = (AndroidEnvProvider) state.getEnvironmentProvider();
        this.running  = false;
        this.ticks    = 0;
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
        return running;
    }

    @Override
    protected void onPreExecute() {
        running = true;
        startEnv.lockUI(true);
    }

    @Override
    protected Void doInBackground(final String... code) {
        final String              mode        = code[0];
        final String              setlXobject = code[1];
        final Thread              process     = new Thread( new Runnable() {
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
                    return;
                } catch (final StackOverflowError soe) {
                    state.errWriteOutOfStack(soe, true);
                    return;
                }  catch (final OutOfMemoryError oome) { // Somehow this never works properly on ANDROID ;-(
                    state.errWriteOutOfMemory(false, true);
                    return;
                } catch (final Exception e) { // this should never happen...
                    state.errWriteInternalError(e);
                    return;
                }
                b.executeWithErrorHandling(state, false);
            }
        });
        process.start();

        int count = 0;
        while (process.isAlive()) {
            if (startEnv != state.getEnvironmentProvider()) {
                // this task is obsolete... kill it
                state.stopExecution(true);
                process.interrupt();
            }
            try {
                if (count == 2) {
                    final float CPUusage  = AndroidUItools.getCPUusage();
                    final long usedMemory = AndroidUItools.getUsedMemory();
                    ++ticks;
                    publishProgress(PUBLISH_STATS, ticks + ":" + CPUusage + ":" + usedMemory);
                    count = 0;
                }
                Thread.sleep(500);
            } catch (final InterruptedException e) {
                // don't care
            }
            ++count;
        }

        state.stopExecution(false);
        running = false;

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
                final String[] message = msg[1].split(":");
                startEnv.updateStats(Integer.parseInt(message[0]), Float.parseFloat(message[1]), Long.parseLong(message[2]));
            }
        }
    }

    @Override
    protected void onPostExecute(final Void result) {
        if (startEnv == state.getEnvironmentProvider()) {
            startEnv.lockUI(false);
        }
    }
}
