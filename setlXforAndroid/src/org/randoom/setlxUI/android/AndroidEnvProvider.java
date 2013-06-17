package org.randoom.setlxUI.android;

import org.randoom.setlx.exceptions.JVMIOException;
import org.randoom.setlx.utilities.EnvironmentProvider;
import org.randoom.setlx.utilities.State;
import org.randoom.util.AndroidUItools;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;

import java.util.LinkedList;
import java.util.Locale;

// This provides access to the I/O mechanisms of Android
/*package*/ class AndroidEnvProvider implements EnvironmentProvider {
    private final static String sTAB        = "\t";
    private final static String sENDL       = "\n";
    private final static String sERROR_KEY  = "\0\0sETLx_eRRoR_@48456890012\0\0";
    private final static String sPROMPT_KEY = "\0\0sETLx_pROMpT_@8478904199\0\0";
    private final static String sCODE_DIR   = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/SetlX/";
    private final static String sLIBRARY_DIR= sCODE_DIR + "library/";

    private final static int    sMAX_CHARS  = 25000;

    private SetlXforAndroidActivity  activity;
    private String                   lastPrompt;
    private final LinkedList<String> messageBuffer;
    private boolean                  isLocked;
    private String                   currentDir;

    private SetlXExecutionTask       execTask;

    /*package*/ AndroidEnvProvider(final SetlXforAndroidActivity activity) {
        this.activity       = activity;
        this.lastPrompt     = null;
        this.messageBuffer  = new LinkedList<String>();
        this.isLocked       = false;
        this.currentDir     = sCODE_DIR;
        this.execTask       = null;
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
            if (msg.startsWith(sERROR_KEY)) {
                activity.appendErr(msg.substring(sERROR_KEY.length()));
            } else if (msg.startsWith(sPROMPT_KEY)) {
                activity.appendPrompt(msg.substring(sPROMPT_KEY.length()));
            } else {
                activity.appendOut(msg);
            }
        }
    }

    /*package*/ void execute(final State state, final String code) {
        this.currentDir = sCODE_DIR;
        this.execTask   = new SetlXExecutionTask(state);
        this.execTask.execute(SetlXExecutionTask.ECUTE_CODE, code);
    }

    /*package*/ void executeFile(final State state, final String fileName) {
        if (fileName.lastIndexOf('/') != -1) {
            this.currentDir = fileName.substring(0, fileName.lastIndexOf('/') + 1);
        } else {
            this.currentDir = sCODE_DIR;
        }
        this.execTask = new SetlXExecutionTask(state);
        this.execTask.execute(SetlXExecutionTask.EXECUTE_FILE, fileName);
    }

    /*package*/ void lockUI(final boolean lock) {
        this.isLocked = lock;
        this.activity.lockUI(lock);
    }

    /*package*/ boolean isLocked() {
        return this.isLocked;
    }

    /*package*/ boolean isExecuting() {
        if (execTask != null) {
            return execTask.isRunning();
        } else {
            return false;
        }
    }

    /*package*/ void appendErr(final String msg) {
        appendOut(sERROR_KEY + msg);
    }

    /*package*/ void appendOut(final String msg) {
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

    /*package*/ void appendPrompt(final String msg) {
        appendOut(sPROMPT_KEY + msg);
    }

    /*package*/ void getInputLine() {
        final AlertDialog.Builder alert = new AlertDialog.Builder(activity);

        alert.setMessage(lastPrompt);
        lastPrompt = null;

        // Set an EditText view to get user input
        final EditText inputBox = new EditText(activity);
        inputBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        alert.setView(inputBox);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int whichButton) {
                execTask.setInput(inputBox.getText().toString());
            }
        });

        alert.setCancelable(false);

        alert.show();
    }

    /*package*/ void updateStats(final int ticks, final float CPUusage, final long usedMemory) {
        if (activity.isActive()) {
            final String ticksStr = String.format(Locale.ENGLISH, "%3d", ticks);
            final String cpuUsage = String.format(Locale.ENGLISH, "%3d", (int) (CPUusage * 100));
            final String memUsage = String.format(Locale.ENGLISH, "%3d", usedMemory / (1024 * 1024));
            activity.updateStats(ticksStr, cpuUsage, memUsage);
        }
    }

    /*package*/ String getCodeDir() {
        return sCODE_DIR;
    }

    // create code directory, if it does not exist
    /*package*/ boolean createCodeDir() {
        final boolean result = AndroidUItools.createDirIfNotExists(sCODE_DIR);
        if ( ! result) {
            return result;
        }
        return AndroidUItools.createDirIfNotExists(sLIBRARY_DIR);
    }

    /* interface functions */

    // read from input
    @Override
    public boolean inReady() {
        return false;
    }

    @Override
    public String inReadLine() throws JVMIOException {
        final String input = this.execTask.readString();
        this.execTask.addOutput(input + sENDL);
        return input;
    }

    // write to standard output
    @Override
    public void outWrite(final String msg) {
        this.execTask.addOutput(msg);
    }

    // write to standard error
    @Override
    public void errWrite(final String msg) {
        this.execTask.addError(msg);
    }

    // prompt user for input
    @Override
    public void promptForInput(final String msg) {
        this.execTask.addPrompt(msg);
        lastPrompt = msg;
    }

    // some text format stuff
    @Override
    public String getTab() {
        return sTAB;
    }

    @Override
    public String getEndl() {
        return sENDL;
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
            AndroidUItools.createDirIfNotExists(sCODE_DIR);
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
            AndroidUItools.createDirIfNotExists(sLIBRARY_DIR);
            return sLIBRARY_DIR + name;
        }
    }
}
