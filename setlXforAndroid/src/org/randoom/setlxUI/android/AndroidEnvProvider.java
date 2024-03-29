package org.randoom.setlxUI.android;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import org.randoom.setlx.exceptions.JVMException;
import org.randoom.setlx.utilities.EnvironmentProvider;
import org.randoom.setlx.utilities.State;
import org.randoom.setlxUI.android.SetlXforAndroidActivity.IO_Stream;

import java.io.File;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 *  This provides access to the I/O mechanisms of Android.
 */
/*package*/ class AndroidEnvProvider implements EnvironmentProvider {
    private final static String TAB          = "\t";
    private final static String ENDL         = "\n";
    private final static String CODE_DIR     = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SetlX/";
    private final static String CODE_DIR_low = CODE_DIR.toLowerCase(Locale.US);
    private final static String LIBRARY_DIR  = CODE_DIR + "library/";

    private final static int    MAX_CHARS    = 25000;

    // permission request flags
    private final static int FILE_IO_PERMISSION_REQUEST_CODE = 42;
    private final static String[] PERMISSIONS_TO_REQUEST = new String[]{
            "android.permission.WRITE_EXTERNAL_STORAGE",
    };

    private SetlXforAndroidActivity   activity;
    private Executioner               executioner;
    private String                    lastPrompt;
    private final ArrayDeque<Message> messageBuffer;
    private boolean                   isLocked;
    private String                    currentDir;
    private String                    input;

    /**
     * Create a new AndroidEnvProvider linked to the specified activity.
     *
     * @param activity Activity (i.e. GUI) to link to.
     */
    /*package*/ AndroidEnvProvider(final SetlXforAndroidActivity activity) {
        this.activity       = activity;
        this.executioner    = null;
        this.lastPrompt     = null;
        this.messageBuffer  = new ArrayDeque<>();
        this.isLocked       = false;
        this.currentDir     = CODE_DIR;
        this.input          = null;
    }

    /**
     * Link to the specified activity after the old one was destroyed.
     * This is required after closing and reopening the app while running.
     *
     * @param activity Activity (i.e. GUI) to link to.
     */
    /*package*/ void updateUI(final SetlXforAndroidActivity activity) {
        this.activity   = activity;
    }

    /**
     * Store lock state of the activity. In this state the user is unable to
     * click any buttons or to change options, except to kill the execution.
     *
     * This state is not (only) stored in the activity itself, because it might get
     * destroyed while the interpreter continues to run.
     *
     * @param isLocked True to lock, false to unlock.
     */
    /*package*/ void setLocked(final boolean isLocked) {
        this.isLocked = isLocked;
    }

    /**
     * Query lock state of the activity. In this state the user is unable to
     * click any buttons or to change options, except to kill the execution.
     *
     * This state is not (only) stored in the activity itself, because it might get
     * destroyed while the interpreter continues to run.
     *
     * @return True if locked, false if unlocked.
     */
    /*package*/ boolean isLocked() {
        return this.isLocked;
    }

    /**
     * Set current directory for file API commands.
     *
     * @param currentDir Current directory to set.
     */
    /*package*/ void setCurrentDir(final String currentDir) {
        this.currentDir = currentDir;
    }

    /**
     * Send some input back into the (waiting) environment provider after
     * it launched the activity to query something.
     *
     * @param input Input from the activity (GUI).
     */
    /*package*/ void setInput(final String input) {
        this.input = input;
    }

    /**
     * Execute a snippet of SetlX code.
     *
     * @param state Internal state reference to use during the execution (stores variables in current scope etc).
     * @param code  SetlX statements to execute.
     */
    /*package*/ void execute(final State state, final String code) {
        executioner = new Executioner(state, activity);
        executioner.execute(code);
    }

    /**
     * Execute a snippet of SetlX code.
     *
     * @param state    Internal state reference to use during the execution (stores variables in current scope etc).
     * @param fileName File expected to contain SetlX statements to execute.
     */
    /*package*/ void executeFile(final State state, final String fileName) {
        executioner = new Executioner(state, activity);
        executioner.executeFile(fileName);
    }

    /**
     * Try to stop the current execution by sending it an interrupt signal.
     */
    /*package*/ void interrupt() {
        if (executioner != null) {
            executioner.interrupt();
        }
    }

    /**
     * Test if the message buffer contains messages.
     *
     * @return True if the buffer contains messages, false otherwise.
     */
    /*package*/ boolean isMessagesBufferEmpty() {
        synchronized (messageBuffer) {
            return this.messageBuffer.isEmpty();
        }
    }

    /**
     * Poll messages from the buffer and put them into the activities output,
     * as long as the activity is active or the buffer is depleted.
     */
    /*package*/ void depleteMessageBuffer() {
        synchronized (messageBuffer) {
            Message msg;
            while (activity.isActive() && ! messageBuffer.isEmpty()) {
                msg = messageBuffer.pollFirst();
                if (msg != null) {
                    activity.appendOut(msg.type, msg.text);
                }
            }
        }
    }

    /**
     * Updates the statistics display in the UI.
     * Callback for statistics updater.
     *
     * @param ticks      Number of ticks passed.
     * @param usedCPU    CPU usage between 0.0 and 1.0
     * @param usedMemory Amount of used memory in Bytes.
     */
    /*package*/ void updateStats(final int ticks, final float usedCPU, final long usedMemory) {
        if (activity.isActive()) {
            final String ticksStr = String.format(Locale.ENGLISH, "%3d", ticks);
            final String cpuUsage = String.format(Locale.ENGLISH, "%3d", (int) (usedCPU * 100));
            final String memUsage = String.format(Locale.ENGLISH, "%3d", usedMemory / (1024 * 1024));
            activity.updateStats(ticksStr, cpuUsage, memUsage);
        }
    }

    /**
     * Get path of the default source code directory.
     *
     * @return Path of the default source code directory.
     */
    /*package*/ String getCodeDir() {
        return CODE_DIR;
    }

    /**
     * Get path relative to the default source code directory from absolute path.
     *
     * @param fileName Absolute file path.
     * @return         Relative file path.
     */
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

    /**
     * Get absolute path from one relative to the default source code directory.
     *
     * @param fileName Relative file path.
     * @return         Absolute file path.
     */
    /*package*/ String expandPath(String fileName) {
        fileName = fileName.trim();
        if (fileName.length() < 1 || fileName.charAt(0) == '/') {
            return fileName;
        } else {
            return CODE_DIR + fileName;
        }
    }

    /**
     * Create the default source code directory, if it does not exist.
     *
     * @return True on success.
     */
    /*package*/ boolean createCodeDir() {
        if (checkAndRequestFileIOPermission()) {
            final boolean result = createDirIfNotExists(CODE_DIR);
            return result && createDirIfNotExists(LIBRARY_DIR);
        }
        return false;
    }

    /*package*/ static int getFileIOPermissionRequestCode() {
        return FILE_IO_PERMISSION_REQUEST_CODE;
    }

    /*package*/ static boolean isFileIOPermissionRequestGranted(int[] grantResults) {
        return (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
    }

    /*package*/ boolean checkAndRequestFileIOPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri));
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(PERMISSIONS_TO_REQUEST, FILE_IO_PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) && Environment.getExternalStorageDirectory().canWrite();
    }

    /**
     * Create a directory at the given path, if necessary.
     *
     * @param path Path of the directory to create.
     * @return True if the directory is present.
     */
    private static boolean createDirIfNotExists(final String path) {
        final File file = new File(path);
        if (file.exists()) {
            return file.isDirectory();
        } else {
            return file.mkdirs();
        }
    }

    /**
     * delete a directory at the given path, if it exists.
     *
     * @param path Path of the directory to create.
     * @return True if the directory was deleted.
     */
    /*package*/
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteDirectory(String path) {
        return deleteRecursively(new File(path));
    }

    private boolean deleteRecursively(File path) {
        if (path.isDirectory()) {
            for (File child : Objects.requireNonNull(path.listFiles())) {
                boolean success = deleteRecursively(child);
                if (!success) {
                    return false;
                }
            }
        }

        return path.delete();
    }

    private void appendMessage(final IO_Stream type, final String msg) {
        synchronized (messageBuffer) {
            final int length;
            if ((length = msg.length()) < MAX_CHARS) {
                messageBuffer.addLast(new Message(type, msg));
            } else {
                // work around text corruption with very very long strings
                for (int i = 0; i < length; i += MAX_CHARS) {
                    if (i + MAX_CHARS < length) {
                        messageBuffer.addLast(new Message(type, msg.substring(i, i + MAX_CHARS) + "\n"));
                    } else {
                        messageBuffer.addLast(new Message(type, msg.substring(i)));
                    }
                }
            }
        }
        depleteMessageBuffer();
    }

    /* interface functions */

    @Override
    public boolean inReady() {
        return false;
    }

    @Override
    public String inReadLine() throws JVMException {
        this.input = null;

        activity.readLine(lastPrompt);
        lastPrompt = null;

        while (input == null) {
            try {
                //noinspection BusyWait
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                throw new JVMException("Unable to read input!", e);
            }
        }
        appendMessage(IO_Stream.STDIN, input + ENDL);

        return input;
    }

    @Override
    public void outWrite(final String msg) {
        appendMessage(IO_Stream.STDOUT, msg);
    }

    @Override
    public void errWrite(final String msg) {
        appendMessage(IO_Stream.STDERR, msg);
    }

    @Override
    public void promptForInput(final String msg) {
        appendMessage(IO_Stream.PROMPT, msg);
        lastPrompt = msg;
    }

    @Override
    public String promptSelectionFromAnswers(final String question, final List<String> answers) throws JVMException {
        this.input = null;

        appendMessage(IO_Stream.PROMPT, question + ENDL);
        for (final String answer : answers) {
            appendMessage(IO_Stream.PROMPT, "- " + answer + ENDL);
        }
        appendMessage(IO_Stream.PROMPT, "Select one answer: ");

        activity.selectFromAnswers(question, answers);

        while (input == null) {
            try {
                //noinspection BusyWait
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                throw new JVMException("Unable to read input!", e);
            }
        }
        appendMessage(IO_Stream.STDIN, input + ENDL);

        return input;
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
    public String getOsID() {
        return "Android";
    }

    @Override
    public String filterFileName(String fileName) {
        checkAndRequestFileIOPermission();
        fileName = fileName.trim();
        if (fileName.length() < 1 || fileName.charAt(0) == '/') {
            return fileName;
        } else {
            createDirIfNotExists(CODE_DIR);
            return currentDir + fileName;
        }
    }

    @Override
    public String filterLibraryName(String name) {
        checkAndRequestFileIOPermission();
        name = name.trim();
        if (name.length() < 1 || name.charAt(0) == '/') {
            return name;
        } else {
            createDirIfNotExists(LIBRARY_DIR);
            return LIBRARY_DIR + name;
        }
    }

    @Override
    public int getStackSizeWishInKb() {
        return 16 * 1024;
    }

    @Override
    public int getMediumStackSizeWishInKb() {
        return 256;
    }

    @Override
    public int getSmallStackSizeWishInKb() {
        return 0;
    }

    private static class Message {
        /*package*/ IO_Stream type;
        /*package*/ String    text;

        /*package*/ Message(final IO_Stream type, final String text) {
            this.type = type;
            this.text = text;
        }
    }
}
