package org.randoom.setlxUI.android;

import org.randoom.setlx.exceptions.JVMIOException;
import org.randoom.setlx.utilities.EnvironmentProvider;
import org.randoom.setlx.utilities.State;

import org.randoom.util.AndroidUItools;

import java.util.LinkedList;
import java.util.Locale;

/**
 *  This provides access to the I/O mechanisms of Android.
 */
/*package*/ class AndroidEnvProvider implements EnvironmentProvider {
    private final static String TAB              = "\t";
    private final static String ENDL             = "\n";
    private final static String ERROR_KEY        = "\0\0sETLx_eRRoR_@48456890012\0\0";
    private final static String PROMPT_KEY       = "\0\0sETLx_pROMpT_@8478904199\0\0";
    private final static String CODE_DIR         = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/SetlX/";
    private final static String CODE_DIR_low     = CODE_DIR.toLowerCase(Locale.US);
    private final static String LIBRARY_DIR      = CODE_DIR + "library/";

    private final static int    MAX_CHARS        = 25000;
    private       static int    STACK_ESTIMATION = -1;

    private SetlXforAndroidActivity  activity;
    private Executioner              executioner;
    private String                   lastPrompt;
    private final LinkedList<String> messageBuffer;
    private boolean                  isLocked;
    private String                   currentDir;
    private String                   input;


    /*package*/ AndroidEnvProvider(final SetlXforAndroidActivity activity) {
        this.activity       = activity;
        this.executioner    = null;
        this.lastPrompt     = null;
        this.messageBuffer  = new LinkedList<String>();
        this.isLocked       = false;
        this.currentDir     = CODE_DIR;
        this.input          = null;
    }

    /*package*/ void updateUI(final SetlXforAndroidActivity activity) {
        this.activity   = activity;
    }

    /*package*/ void setLocked(final boolean isLocked) {
        this.isLocked = isLocked;
    }

    /*package*/ boolean isLocked() {
        return this.isLocked;
    }

    /*package*/ void setCurrentDir(final String currentDir) {
        this.currentDir = currentDir;
    }

    /*package*/ void setInput(final String input) {
        this.input = input;
    }

    /*package*/ String getCodeDir() {
        return CODE_DIR;
    }

    /*package*/ void execute(final State state, final String code) {
        executioner = new Executioner(state, activity);
        executioner.execute(code);
    }

    /*package*/ void executeFile(final State state, final String fileName) {
        executioner = new Executioner(state, activity);
        executioner.executeFile(fileName);
    }

    /*package*/ void interrupt() {
        if (executioner != null) {
            executioner.interrupt();
        }
    }

    /*package*/ boolean isMessagesBufferEmpty() {
        return this.messageBuffer.isEmpty();
    }

    /*package*/ void depleteMessageBuffer() {
        String msg;
        while (activity.isActive() && ! messageBuffer.isEmpty()) {
            msg = messageBuffer.pollFirst();
            if (msg.startsWith(ERROR_KEY)) {
                activity.appendOut(SetlXforAndroidActivity.STDERR, msg.substring(ERROR_KEY.length()));
            } else if (msg.startsWith(PROMPT_KEY)) {
                activity.appendOut(SetlXforAndroidActivity.STDIN, msg.substring(PROMPT_KEY.length()));
            } else {
                activity.appendOut(SetlXforAndroidActivity.STDOUT, msg);
            }
        }
    }

    /*package*/ void updateStats(final int ticks, final float usedCPU, final long usedMemory) {
        if (activity.isActive()) {
            final String ticksStr = String.format(Locale.ENGLISH, "%3d", ticks);
            final String cpuUsage = String.format(Locale.ENGLISH, "%3d", (int) (usedCPU * 100));
            final String memUsage = String.format(Locale.ENGLISH, "%3d", usedMemory / (1024 * 1024));
            activity.updateStats(ticksStr, cpuUsage, memUsage);
        }
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
        if (fileName.charAt(0) != '/' ||
            ! fileName.toLowerCase(Locale.US).startsWith(CODE_DIR_low)
        ) {
            return fileName;
        } else /* if (fileName.startsWith(sCODE_DIR)) */ {
            String tmpName = fileName.toLowerCase(Locale.US);
            tmpName = tmpName.replace(CODE_DIR_low, "");

            return fileName.substring(fileName.length() - tmpName.length());
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

    @Override
    public void outWrite(final String msg) {
        final int length;
        if ((length = msg.length()) < MAX_CHARS) {
            messageBuffer.addLast(msg);
        } else {
            // work around text corruption with very very long strings
            for (int i = 0; i < length; i += MAX_CHARS) {
                if (i + MAX_CHARS < length) {
                    messageBuffer.addLast(msg.substring(i, i + MAX_CHARS) + "\n");
                } else {
                    messageBuffer.addLast(msg.substring(i));
                }
            }
        }
        depleteMessageBuffer();
    }

    @Override
    public void errWrite(final String msg) {
        outWrite(ERROR_KEY + msg);
    }

    @Override
    public void promptForInput(final String msg) {
        outWrite(PROMPT_KEY + msg);
        lastPrompt = msg;
    }

    @Override
    public String getTab() {
        return TAB;
    }

    @Override
    public String getEndl() {
        return ENDL;
    }

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
