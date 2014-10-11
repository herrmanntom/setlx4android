package org.randoom.setlxUI.android;

import group.pals.android.lib.ui.filechooser.FileChooserActivity;
import group.pals.android.lib.ui.filechooser.io.localfile.LocalFile;

import java.util.List;

import org.randoom.setlx.exceptions.IllegalRedefinitionException;
import org.randoom.setlx.types.SetlList;
import org.randoom.setlx.utilities.DummyEnvProvider;
import org.randoom.setlx.utilities.EnvironmentProvider;
import org.randoom.setlx.utilities.State;
import org.randoom.util.AndroidDataStorage;
import org.randoom.util.AndroidUItools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;

/**
 * Main UI class of setlX for Android.
 */
public class SetlXforAndroidActivity extends Activity {

    private     final static String  SETL_X_URL           = "http://setlX.randoom.org/";
    private     final static String  ANTLR_URL            = "http://www.antlr.org/";
    private     final static String  FILECHOOSER_URL      = "https://code.google.com/p/android-filechooser/";
    private     final static String  SETL_X_C_YEARS       = "2011-2014";

    private enum ExecutionMode {
        INTERACTIVE_MODE,
        FILE_MODE
    }

    /**
     * Flags for appendOut() to define which sort of message to print.
     */
    /*package*/ enum IO_Stream {
        /**
         * Flag for appendOut() to print a standard message.
         *
         * @see org.randoom.setlxUI.android.SetlXforAndroidActivity#appendOut(IO_Stream, String)
         */
        STDOUT,
        /**
         * Flag for appendOut() to print an error message.
         *
         * @see org.randoom.setlxUI.android.SetlXforAndroidActivity#appendOut(IO_Stream, String)
         */
        STDERR,
        /**
         * Flag for appendOut() to print user input.
         *
         * @see org.randoom.setlxUI.android.SetlXforAndroidActivity#appendOut(IO_Stream, String)
         */
        STDIN,
        /**
         * Flag for appendOut() to print a prompt message.
         *
         * @see org.randoom.setlxUI.android.SetlXforAndroidActivity#appendOut(IO_Stream, String)
         */
        PROMPT
    }

    // flag for the file-open-intent
    private final static int      REQEST_FILE_FLAG     = 0;

    private static State          state;
    private static boolean        isAutoResetEnabled   = true;

    private Handler               uiThreadHandler;
    private boolean               uiThreadHasWork;
    private StatsUpdater          currentStatsUpdater;

    private AndroidEnvProvider    envProvider;

    private TextView              prompt;
    private TextView              load;
    private EditText              inputInteractive;
    private EditText              inputFileMode;
    private ImageButton           openFileBtn;
    private Button                modeSwitchBtn;
    private Button                executeBtn;
    private ScrollView            outputScrollView;
    private TextView              output;

    // counter to enable interpreter debugging options
    // using menu options: trace, assert, about, trace, assert
    private int                   enableDebuggingCount;

    // current state
    private Editable              inputInteractiveTxt;
    private ExecutionMode         mode;
    private boolean               outputIsGreeting;
    private boolean               isActive;
    private boolean               isKilling;

    private class OpenListener implements View.OnClickListener {
        @Override
        public void onClick(final View v) {
            final Intent intent = new Intent(v.getContext(), FileChooserActivity.class);
            // pre-select root dir
            intent.putExtra(FileChooserActivity._Rootpath, (Parcelable) new LocalFile(envProvider.getCodeDir()));
            // pre-select last file
            final String currentFile = inputFileMode.getText().toString();
            if (! currentFile.equals("")) {
                intent.putExtra(FileChooserActivity._SelectFile, (Parcelable) new LocalFile(envProvider.expandPath(currentFile)));
            }
            // start selection dialog
            startActivityForResult(intent, REQEST_FILE_FLAG);
        }
    }

    private class ModeListener implements View.OnClickListener {
        @Override
        public void onClick(final View v) {
            if (mode == ExecutionMode.FILE_MODE) {
                mode =  ExecutionMode.INTERACTIVE_MODE;
            } else /* if (mode == ExecutionMode.INTERACTIVE_MODE) */ {
                mode = ExecutionMode.FILE_MODE;
            }
            switchMode();
        }
    }

    private class ExecListener implements View.OnClickListener {
        @Override
        public void onClick(final View v) {
            preExecute(v);

            if (mode == ExecutionMode.FILE_MODE) {
                final String fileName = inputFileMode.getText().toString();
                envProvider.executeFile(state, envProvider.expandPath(fileName));
            } else /* if (mode == ExecutionMode.INTERACTIVE_MODE) */ {
                final String code = getInteractiveInput().toString();
                envProvider.execute(state, code);
            }
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        uiThreadHandler     = new Handler();
        uiThreadHasWork     = false;
        currentStatsUpdater = null;

        // get persistent state
        AndroidDataStorage dh = new AndroidDataStorage(getBaseContext());

        // state / input for this launch
        final String inputInteractiveText;
        final String inputFileModeText;
        final String outputHtml;

        // Get intent, action and MIME type
        final Intent intent = getIntent();
        final String action = intent.getAction();
        final String type   = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null && "text/plain".equals(type)) {
            // Handle text being sent
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText == null) {
                sharedText = "";
            }
            inputInteractiveText = sharedText;
            outputHtml           = "";
            mode                 = ExecutionMode.INTERACTIVE_MODE;
        } else {
            // Handle other intents, such as being started from the home screen
            inputInteractiveText = dh.getCode("inputInteractiveText", "");
            outputHtml           = dh.getCode("outputHtml", "");
            try {
                mode             = ExecutionMode.valueOf(dh.getCode("inputMode", ExecutionMode.INTERACTIVE_MODE.name()));
            } catch (final IllegalArgumentException iae) { // invalid enum value
                mode             = ExecutionMode.INTERACTIVE_MODE;
            }
        }
        inputFileModeText        = dh.getCode("inputFileModeText", "");

        dh.close();
        dh = null;

        // display greeting if environment is not present OR
        // (output is empty, no output is queued and nothing executes)
        outputIsGreeting = state == null;
        if ( ! outputIsGreeting) {
            envProvider = (AndroidEnvProvider) state.getEnvironmentProvider();
            if (outputHtml.equals("") &&
                envProvider.isMessagesBufferEmpty() &&
                ( ! envProvider.isLocked())
            ) {
                outputIsGreeting = true;
            }
        }

        enableDebuggingCount = 0;

        // setup GUI and environment
        setup(outputIsGreeting);

        envProvider.createCodeDir();

        /* launch time only GUI setup */
        setInteractiveInput(inputInteractiveText);
        inputFileMode.setText(inputFileModeText);
        if (outputIsGreeting) {
            final String greeting = output.getText().toString();
            output.setText(Html.fromHtml(formatVersionText(greeting)), BufferType.SPANNABLE);
            output.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            output.setText(Html.fromHtml(outputHtml), BufferType.SPANNABLE);
        }

        isActive  = true;
        isKilling = false;

        // add buffered messages to output
        envProvider.depleteMessageBuffer();
    }

    @Override
    public void onResume() {
        super.onResume();

        isActive = true;

        // add buffered messages to output
        envProvider.depleteMessageBuffer();
    }

    @Override
    public void onPause() {
        super.onPause();

        storeState(true);

        isActive = false;
    }

    /** Called when the activity's configuration is changed (e.g. rotation) */
    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // read interface state
        final Editable     inputInteractiveTxt    = getInteractiveInput();
        final Editable     inputFileModeTxt       = inputFileMode.getText();
        final boolean      locked                 = ! executeBtn.isEnabled();
        final CharSequence outputTxt              = output.getText();

        setContentView(R.layout.main);
        setup(false);

        // reset interface state
        setInteractiveInput(inputInteractiveTxt);
        inputFileMode.setText(inputFileModeTxt);
        lockUI(locked);
        output.setText(outputTxt, BufferType.SPANNABLE);
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
        menu.add(0, v.getId(), 0, R.string.menuCopy);
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        if (item.getTitle() == getString(R.string.menuCopy)) {
            // place text of output into the clipboard
            final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clipBoardKey), output.getText()));

            // show user what was done
            uiThreadHandler.post(new Toaster(R.string.toastCopy, Toast.LENGTH_SHORT));
            return true;
        } else {
            return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        for (int i = 0; i < menu.size(); ++i) {
            final MenuItem item = menu.getItem(i);
            switch (item.getItemId()) {
                case R.id.menuItemKill:
                    item.setVisible(envProvider.isLocked() && ! isKilling);
                    break;
                case R.id.menuItemRandom:
                    if (envProvider.isLocked()) {
                        item.setVisible(false);
                    } else {
                        item.setVisible(true);
                        if (state.isRandoomPredictable()) {
                            item.setTitle(R.string.menuRandom2ON);
                        } else {
                            item.setTitle(R.string.menuRandom2OFF);
                        }
                    }
                    return true;
                case R.id.menuItemAssert:
                    if (envProvider.isLocked()) {
                        item.setVisible(false);
                    } else {
                        item.setVisible(true);
                        if (state.areAssertsDisabled()) {
                            item.setTitle(R.string.menuAsserts2ON);
                        } else {
                            item.setTitle(R.string.menuAsserts2OFF);
                        }
                    }
                    break;
                case  R.id.menuItemTrace:
                    if (envProvider.isLocked()) {
                        item.setVisible(false);
                    } else {
                        item.setVisible(true);
                        if (state.traceAssignments) {
                            item.setTitle(R.string.menuTrace2OFF);
                        } else {
                            item.setTitle(R.string.menuTrace2ON);
                        }
                    }
                    break;
                case R.id.menuItemRuntimeDebugging:
                    if (enableDebuggingCount == 5) {
                        item.setEnabled(true);
                        enableDebuggingCount = 0;
                    }
                    if (envProvider.isLocked()) {
                        item.setVisible(false);
                    } else {
                        if (item.isEnabled()) {
                            item.setVisible(true);
                        } else {
                            item.setVisible(false);
                        }
                        if (state.isRuntimeDebuggingEnabled()) {
                            item.setTitle(R.string.menuRuntimeDebugging2OFF);
                        } else {
                            item.setTitle(R.string.menuRuntimeDebugging2ON);
                        }
                    }
                    break;
                case R.id.menuItemAutoReset:
                    if (mode == ExecutionMode.FILE_MODE) {
                        item.setVisible(true);
                        if (isAutoResetEnabled) {
                            item.setTitle(R.string.menuAutoReset2OFF);
                        } else {
                            item.setTitle(R.string.menuAutoReset2ON);
                        }
                    } else {
                        item.setVisible(false);
                    }
                    break;
                default:
                    break;
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuItemKill:
                isKilling = true;

                state.setEnvironmentProvider(DummyEnvProvider.DUMMY);
                state.resetState();
                state.stopExecution(true);

                final SetlXforAndroidActivity _this  = this;
                final Thread                  killer = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        appendOut(IO_Stream.STDERR, getString(R.string.msgKillStarted));

                        envProvider.interrupt();

                        envProvider = new AndroidEnvProvider(_this);
                        state       = new State(envProvider);

                        // give hint to the garbage collector
                        Runtime.getRuntime().gc();

                        // unlock UI
                        lockUI(envProvider.isLocked());

                        // delete load text
                        if (! state.isRuntimeDebuggingEnabled()) {
                            load.post(new StatsUpdater("", "", ""));
                        }

                        // announce reset of memory to user
                        appendOut(IO_Stream.STDERR, getString(R.string.msgKill));

                        isKilling = false;
                    }
                });

                killer.start();

                return true;
            case R.id.menuItemRandom:
                if (envProvider.isLocked()) {
                    uiThreadHandler.post(new Toaster(R.string.toastNotPossibleWhileRunning, Toast.LENGTH_LONG));
                } else {
                    if (state.isRandoomPredictable()) {
                        state.setRandoomPredictable(false);
                        uiThreadHandler.post(new Toaster(R.string.toastRandomON, Toast.LENGTH_SHORT));
                    } else {
                        state.setRandoomPredictable(true);
                        uiThreadHandler.post(new Toaster(R.string.toastRandomOFF, Toast.LENGTH_SHORT));
                    }
                }
                return true;
            case R.id.menuItemAssert:
                if (enableDebuggingCount == 1 || enableDebuggingCount == 4) {
                    enableDebuggingCount++;
                } else {
                    enableDebuggingCount = 0;
                }
                if (envProvider.isLocked()) {
                    uiThreadHandler.post(new Toaster(R.string.toastNotPossibleWhileRunning, Toast.LENGTH_LONG));
                } else {
                    if (state.areAssertsDisabled()) {
                        state.setAssertsDisabled(false);
                        uiThreadHandler.post(new Toaster(R.string.toastAssertsOn, Toast.LENGTH_SHORT));
                    } else {
                        state.setAssertsDisabled(true);
                        uiThreadHandler.post(new Toaster(R.string.toastAssertsOff, Toast.LENGTH_SHORT));
                    }
                }
                return true;
            case R.id.menuItemTrace:
                if (enableDebuggingCount == 0 || enableDebuggingCount == 3) {
                    enableDebuggingCount++;
                } else {
                    enableDebuggingCount = 0;
                }
                if (envProvider.isLocked()) {
                    uiThreadHandler.post(new Toaster(R.string.toastNotPossibleWhileRunning, Toast.LENGTH_LONG));
                } else {
                    if (state.traceAssignments) {
                        state.setTraceAssignments(false);
                        uiThreadHandler.post(new Toaster(R.string.toastTraceOff, Toast.LENGTH_SHORT));
                    } else {
                        state.setTraceAssignments(true);
                        uiThreadHandler.post(new Toaster(R.string.toastTraceOn, Toast.LENGTH_SHORT));
                    }
                }
                return true;
            case R.id.menuItemRuntimeDebugging:
                if (envProvider.isLocked()) {
                    uiThreadHandler.post(new Toaster(R.string.toastNotPossibleWhileRunning, Toast.LENGTH_LONG));
                } else {
                    if (state.isRuntimeDebuggingEnabled()) {
                        state.setRuntimeDebugging(false);
                        uiThreadHandler.post(new Toaster(R.string.runtimeDebuggingOff, Toast.LENGTH_SHORT));
                    } else {
                        state.setRuntimeDebugging(true);
                        uiThreadHandler.post(new Toaster(R.string.runtimeDebuggingOn, Toast.LENGTH_SHORT));
                    }
                }
                return true;
            case R.id.menuItemClear:
                if (envProvider.isLocked()) {
                    uiThreadHandler.post(new Toaster(R.string.toastNotPossibleWhileRunning, Toast.LENGTH_LONG));
                } else {
                    setInteractiveInput("");
                    inputFileMode   .setText("");
                    output.setText("", BufferType.SPANNABLE);
                    if (mode == ExecutionMode.INTERACTIVE_MODE) {
                        inputInteractive.requestFocus();
                    } else /* if (mode == FILE_MODE) */ {
                        inputFileMode   .requestFocus();
                    }
                }
                return true;
            case R.id.menuItemReset:
                if (envProvider.isLocked()) {
                    uiThreadHandler.post(new Toaster(R.string.toastNotPossibleWhileRunning, Toast.LENGTH_LONG));
                } else {
                    state.resetState();
                    uiThreadHandler.post(new Toaster(R.string.toastReset, Toast.LENGTH_LONG));
                }
                return true;
            case R.id.menuItemAutoReset:
                if (isAutoResetEnabled) {
                    isAutoResetEnabled = false;
                    uiThreadHandler.post(new Toaster(R.string.toastAutoResetOFF, Toast.LENGTH_SHORT));
                } else {
                    isAutoResetEnabled = true;
                    uiThreadHandler.post(new Toaster(R.string.toastAutoResetON, Toast.LENGTH_SHORT));
                }
                return true;
            case R.id.menuItemAbout:
                if (enableDebuggingCount == 2) {
                    enableDebuggingCount++;
                } else {
                    enableDebuggingCount = 0;
                }
                final AlertDialog.Builder alert = new AlertDialog.Builder(this);

                alert.setMessage(R.string.menuAbout);

                // Set an Text view to display message
                final TextView aboutView = new TextView(this);
                aboutView.setText(Html.fromHtml(formatVersionText(getString(R.string.about))));
                aboutView.setMovementMethod(LinkMovementMethod.getInstance());
                aboutView.setGravity(Gravity.CENTER);

                alert.setView(aboutView);

                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int whichButton) {}
                });
                alert.setCancelable(true);

                alert.show();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQEST_FILE_FLAG:
                if (resultCode == RESULT_OK) {

                /*
                 * a list of files will always return,
                 * if selection mode is single, the list contains one file
                 */
                @SuppressWarnings("unchecked")
                final
                List<LocalFile> files = (List<LocalFile>) data.getSerializableExtra(FileChooserActivity._Results);
                for (final LocalFile f : files) {
                    this.inputFileMode.setText(this.envProvider.stripPath(f.getAbsolutePath()));
                }
            }
            break;
        }
    }

    /**
     * Lock the UI in a thread safe manor, e.g. disable buttons.
     *
     * @param locked lock-status to set.
     */
    /*package*/ void lockUI(final boolean locked) {
        if (uiThreadHandler.getLooper().getThread() != Thread.currentThread()) {

            int elapsedSeconds = 0;

            while (uiThreadHasWork && elapsedSeconds < 1000) {
                try {
                    Thread.sleep(10);
                    elapsedSeconds += 10;
                } catch (final InterruptedException e) {
                    // don't care
                }
            }

            uiThreadHasWork = true;
            uiThreadHandler.post(new UiLocker(locked));

            elapsedSeconds = 0;

            do {
                try {
                    Thread.sleep(10);
                    elapsedSeconds += 10;
                } catch (final InterruptedException e) {
                    // don't care
                }
            } while (uiThreadHasWork && elapsedSeconds < 1000);
        } else {
            uiLock(locked);
        }
    }

    /**
     * Actions to perform before executing setlX-code.
     *
     * @param v Reference to the current UI view object.
     */
    /*package*/ void preExecute(final View v) {
        envProvider.setLocked(true);
        lockUI(true);

        storeState(/*storeOutput = */false); // better safe than sorry ;-)

        output.setText("", BufferType.SPANNABLE);
        if (outputIsGreeting) {
            output.setGravity(Gravity.LEFT);
            output.setTypeface(Typeface.MONOSPACE);
            outputIsGreeting = false;
        }
        AndroidUItools.hideKeyboard(v);
    }

    /**
     * Actions to perform after executing setlX-code.
     */
    /*package*/ void postExecute() {
        if (isAutoResetEnabled && mode == ExecutionMode.FILE_MODE) {
            uiThreadHandler.post(new Toaster(R.string.toastAutoReset, Toast.LENGTH_SHORT));
            state.resetState();
        }
        if (! state.isRuntimeDebuggingEnabled()) {
            load.post(new StatsUpdater("", "", ""));
        }
        envProvider.setLocked(false);
        lockUI(false);
    }

    /**
     * Check if this activity is still visible to the user.
     *
     * @return True if the UI is still visible.
     */
    /*package*/ boolean isActive() {
        return isActive;
    }

    /**
     * Append a message to the UIs output in a thread safe manor.
     * Automatically scrolls the output view to the last message before returning.
     *
     * @param type Message type (see STDOUT, STDERR & STDIN flags)
     * @param msg  Message to append.
     */
    /*package*/ void appendOut(final IO_Stream type, final String msg) {
        try {
            final boolean isNotUiThread = uiThreadHandler.getLooper().getThread() != Thread.currentThread();

            int elapsedSeconds = 0;

            while (isNotUiThread && uiThreadHasWork && elapsedSeconds < 1000) {
                Thread.sleep(10);
                elapsedSeconds += 10;
            }

            uiThreadHasWork = true;
            uiThreadHandler.post(new OutputPoster(type, msg));

            elapsedSeconds = 0;

            if (isNotUiThread) {
                do {
                    Thread.sleep(10);
                    elapsedSeconds += 10;
                } while (uiThreadHasWork && elapsedSeconds < 1000);
            }

            uiThreadHasWork = true;
            uiThreadHandler.post(new BottomScroller());
        } catch (final InterruptedException e) {
            uiThreadHasWork = false;
        }
    }

    /**
     * Prompt the user for input.
     *
     * @param prompt Message to prompt the user with.
     */
    /*package*/ void readLine(final String prompt) {
        uiThreadHandler.post(new InputReader(prompt, this));
    }

    /**
     * Prompt the user to select one answer.
     *
     * @param question Question to prompt the user with.
     * @param answers  Answers to select from.
     */
    /*package*/ void selectFromAnswers(final String question, final List<String> answers) {
        uiThreadHandler.post(new InputSelector(question, answers, this));
    }

    /**
     * Update the UI display of current resource usage.
     *
     * @param ticks      Number of ticks passed.
     * @param usedCPU    CPU usage in percent.
     * @param usedMemory Amount of used memory in MB.
     */
    /*package*/ void updateStats(final String ticks, final String usedCPU, final String usedMemory) {
        if (currentStatsUpdater != null) {
            uiThreadHandler.removeCallbacks(currentStatsUpdater);
        }
        uiThreadHandler.post(currentStatsUpdater = new StatsUpdater(ticks, usedCPU, usedMemory));
    }

    private void setup(final boolean outputIsReset) {
        prompt           = (TextView)    findViewById(R.id.textViewPrompt);
        load             = (TextView)    findViewById(R.id.textViewLoad);
        inputInteractive = (EditText)    findViewById(R.id.inputInteractiveMode);
        inputFileMode    = (EditText)    findViewById(R.id.inputFileMode);
        openFileBtn      = (ImageButton) findViewById(R.id.buttonOpenFile);
        modeSwitchBtn    = (Button)      findViewById(R.id.buttonModeSwitch);
        executeBtn       = (Button)      findViewById(R.id.buttonExecute);
        outputScrollView = (ScrollView)  findViewById(R.id.scrollViewOutput);
        output           = (TextView)    findViewById(R.id.textViewOutput);

        // (re) initialize setlX Environment
        if (state != null) {
            final EnvironmentProvider env = state.getEnvironmentProvider();
            if (! isKilling || env instanceof AndroidEnvProvider) {
                envProvider = (AndroidEnvProvider) state.getEnvironmentProvider();
            }
        }
        if (state == null || envProvider == null) {
            envProvider = new AndroidEnvProvider(this);
            state       = new State(envProvider);
            if (! outputIsReset) {
                state.resetState();
                // announce reset of memory to user
                uiThreadHandler.post(new Toaster(R.string.toastRestart, Toast.LENGTH_LONG));
            }
            // Android version cannot pass parameters to programs (yet?)
            try {
                state.putValue("params", new SetlList(), "init");
            } catch (final IllegalRedefinitionException e) {
                // will not happen
            }
        }
        envProvider.updateUI(this);

        // GUI
        final DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

        if (AndroidUItools.isTablet(this)) {
            if (AndroidUItools.isInPortrait(displaymetrics)) {
                inputInteractive.setMaxHeight(displaymetrics.heightPixels / 2);
            } else {
                inputInteractive.setMaxHeight((11 * displaymetrics.heightPixels) / 30);
            }
        } else {
            inputInteractive.setMaxHeight(displaymetrics.heightPixels / 3);
        }

        // align file open and execute buttons
        final RelativeLayout.LayoutParams openBtnLayout = (RelativeLayout.LayoutParams) openFileBtn.getLayoutParams();
        openBtnLayout.addRule(RelativeLayout.ALIGN_LEFT,  R.id.buttonExecute);
        openBtnLayout.addRule(RelativeLayout.ALIGN_RIGHT, R.id.buttonExecute);
        openFileBtn.setLayoutParams(openBtnLayout);

        // set for file or interactive mode
        switchMode();

        if (outputIsGreeting) {
            output.setGravity(Gravity.CENTER);
            output.setTypeface(Typeface.DEFAULT);
        }

        openFileBtn.setOnClickListener(new OpenListener());
        modeSwitchBtn.setOnClickListener(new ModeListener());
        executeBtn.setOnClickListener(new ExecListener());

        registerForContextMenu(output);

        // relock UI if required
        lockUI(envProvider.isLocked());
    }

    private Editable getInteractiveInput() {
        if (mode == ExecutionMode.INTERACTIVE_MODE) {
            this.inputInteractiveTxt = this.inputInteractive.getText();
        }
        return this.inputInteractiveTxt;
    }

    private void setInteractiveInput(final String content) {
        setInteractiveInput(new SpannableStringBuilder(content));
    }

    private void setInteractiveInput(final Editable content) {
        this.inputInteractive.setText(content);
        this.inputInteractiveTxt = this.inputInteractive.getText();
        if (mode == ExecutionMode.FILE_MODE) {
            this.inputInteractive.setText("");
        }
    }

    private void switchMode() {
        if (mode == ExecutionMode.FILE_MODE) {
            this.inputInteractiveTxt = this.inputInteractive.getText();
            this.inputInteractive.setText("");
            this.inputInteractive.setVisibility(View.INVISIBLE);

            this.prompt.setText(R.string.promptFile);
            this.modeSwitchBtn.setText(R.string.buttonModeInteractive);
            this.inputFileMode.setVisibility(View.VISIBLE);
            this.openFileBtn.setVisibility(View.VISIBLE);

            state.setInteractive(false);
        } else /* if (mode == ExecutionMode.INTERACTIVE_MODE) */ {
            this.inputInteractive.setText(this.inputInteractiveTxt);
            this.inputFileMode.setVisibility(View.INVISIBLE);
            this.openFileBtn.setVisibility(View.INVISIBLE);

            this.prompt.setText(R.string.promptStatements);
            this.modeSwitchBtn.setText(R.string.buttonModeFile);
            this.inputInteractive.setVisibility(View.VISIBLE);

            state.setInteractive(true);
        }
    }

    // set persistent state
    private void storeState(final boolean storeOutput) {
        AndroidDataStorage dh = new AndroidDataStorage(getBaseContext());
        dh.setCode("inputInteractiveText", getInteractiveInput().toString());
        dh.setCode("inputFileModeText",    inputFileMode   .getText().toString());
        dh.setCode("inputMode",            mode.name());
        String outputHtml = "";
        if ( ! outputIsGreeting && storeOutput && output.getText().length() > 0) {
            outputHtml  = Html.toHtml((Spannable) output.getText());
        }
        dh.setCode("outputHtml", outputHtml);
        dh.close();
    }

    private String formatVersionText(String text){
        try {
            text = text.replace("$VERSION$", getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (final NameNotFoundException e) {
            text = text.replace("$VERSION$", "??");
        }
        text = text.replace("$BASE_VERSION$", filterVersionString(SourceVersion.SETL_X_BASE_VERSION));
        text = text.replace("$SHELL_VERSION$", filterVersionString(SourceVersion.SETL_X_SHELL_VERSION));
        text = text.replace("$URL_START$", "<a href=\""+ SETL_X_URL + "\">");
        text = text.replace("$ANTLR_URL_START$", "<a href=\""+ ANTLR_URL + "\">");
        text = text.replace("$FILECHOOSER_URL_START$", "<a href=\""+ FILECHOOSER_URL + "\">");
        text = text.replace("$URL_END$", "</a>");
        return text.replace("$YEARS$", SETL_X_C_YEARS);
    }

    private String filterVersionString(String setlXBaseVersion) {
        if (setlXBaseVersion.endsWith("-dirty")) {
            setlXBaseVersion = setlXBaseVersion.substring(0, 19) + "-dirty";
        } else {
            setlXBaseVersion = setlXBaseVersion.substring(0, 19);
        }
        return setlXBaseVersion;
    }

    private class UiLocker implements Runnable {
        private final boolean locked;

        private UiLocker(final boolean locked) {
            this.locked = locked;
        }

        @Override
        public void run() {
            uiLock(locked);
            uiThreadHasWork = false;
        }
    }

    private void uiLock(final boolean locked) {
        if (locked) {
            getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        inputInteractive.setClickable           (! locked);
        inputInteractive.setFocusable           (! locked);
        inputInteractive.setFocusableInTouchMode(! locked);
        inputFileMode.setClickable              (! locked);
        inputFileMode.setFocusable              (! locked);
        inputFileMode.setFocusableInTouchMode   (! locked);
        openFileBtn.setEnabled                  (! locked);
        modeSwitchBtn.setEnabled                (! locked);
        executeBtn.setEnabled                   (! locked);

        invalidateOptionsMenu();
    }

    private class Toaster implements Runnable {
        private final int toastID;
        private final int duration;

        private Toaster(final int toastID, final int duration) {
            this.toastID  = toastID;
            this.duration = duration;
        }

        @Override
        public void run() {
            Toast.makeText(getBaseContext(), toastID, duration).show();
        }
    }

    private class StatsUpdater implements Runnable {
        private final String ticks;
        private final String usedCPU;
        private final String usedMemory;

        private StatsUpdater(final String ticks, final String usedCPU, final String usedMemory) {
            this.ticks      = ticks;
            this.usedCPU    = usedCPU;
            this.usedMemory = usedMemory;
        }

        @Override
        public void run() {
            if (ticks.equals("") && usedCPU.equals("") && usedMemory.equals("")) {
                load.setText("");
            } else {
                String loadText = getString(R.string.load);
                loadText = loadText.replace("$TICKS$", ticks);
                loadText = loadText.replace("$CPU$",   usedCPU);
                loadText = loadText.replace("$MEM$",   usedMemory);
                load.setText(loadText);
            }
        }
    }

    private class OutputPoster implements Runnable {
        private final IO_Stream type;
        private final String    msg;

        private OutputPoster(final IO_Stream type, final String msg) {
            this.type = type;
            this.msg  = msg;
        }

        @Override
        public void run() {
            final int pre  = (outputIsGreeting)? 0 : output.getText().length();
            final int post = pre + msg.length();

            if (outputIsGreeting) {
                output.setText(msg, BufferType.SPANNABLE);
                output.setGravity(Gravity.LEFT);
                output.setTypeface(Typeface.MONOSPACE);
                outputIsGreeting = false;
            } else {
                output.append(msg);
            }
            if (type != IO_Stream.STDOUT) {
                final SpannableStringBuilder content = new SpannableStringBuilder(output.getText());
                if (type == IO_Stream.STDERR) {
                    // show errors in red
                    content.setSpan(new ForegroundColorSpan(0xFFFF0000), pre, post, 0);
                } else if (type == IO_Stream.STDIN) {
                    // show user input in light blue
                    content.setSpan(new ForegroundColorSpan(0xFF00AAFF), pre, post, 0);
                } else if (type ==IO_Stream.PROMPT) {
                    // show prompts in green
                    content.setSpan(new ForegroundColorSpan(0xFF00FF00), pre, post, 0);
                }
                output.setText(content, BufferType.SPANNABLE);
            }
            uiThreadHasWork = false;
        }
    }

    private class InputReader implements Runnable {
        private final String   prompt;
        private final Activity activity;

        private InputReader(final String prompt, final Activity activity) {
            this.prompt   = prompt;
            this.activity = activity;
        }

        @Override
        public void run() {
            final AlertDialog.Builder alert = new AlertDialog.Builder(activity);

            alert.setMessage(prompt);

            // Set an EditText view to get user input
            final EditText inputBox = new EditText(activity);
            inputBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            alert.setView(inputBox);

            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int whichButton) {
                    envProvider.setInput(inputBox.getText().toString());
                }
            });

            alert.setCancelable(false);

            alert.show();
        }
    }

    private class InputSelector implements Runnable {
        private final String      question;
        private final String[]    answers;
        private final Activity    activity;
        private       AlertDialog alert;

        private InputSelector(final String question, final List<String> answers, final Activity activity) {
            this.question = question;
            this.answers  = new String[answers.size()];
            for (int i = 0; i < answers.size(); ++i) {
                this.answers[i] = answers.get(i);
            }
            this.activity = activity;
            this.alert    = null;
        }

        @Override
        public void run() {
            final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(activity);

            final LayoutInflater inflater     = LayoutInflater.from(activity);
            final View           alertContent = inflater.inflate(R.layout.select_dialog, null);

            final TextView message = (TextView) alertContent.findViewById(R.id.messageText);
            message.setText(question);

            final ListView buttons = (ListView) alertContent.findViewById(R.id.list);
            buttons.setAdapter(new ArrayAdapter<String>(activity, android.R.layout.select_dialog_item, answers));
            buttons.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(final AdapterView<?> arg0, final View view,
                        final int whichButton, final long arg3) {
                    envProvider.setInput(answers[whichButton]);
                    alert.cancel();
                }
            });
            buttons.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

            alertBuilder.setView(alertContent);

            alertBuilder.setCancelable(false);

            alert = alertBuilder.create();

            alert.show();
        }
    }

    private class BottomScroller implements Runnable {
        @Override
        public void run() {
            outputScrollView.fullScroll(View.FOCUS_DOWN);
            uiThreadHasWork = false;
        }
    }
}
