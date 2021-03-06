/**
 *   ownCloud Android client application
 *
 *   @author David A. Velasco
 *   @author Shashvat Kedia
 *   @author Christian Schabesberger
 *   @author David González Verdugo
 *   Copyright (C) 2019 ownCloud GmbH.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.ui.activity;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.owncloud.android.R;
import com.owncloud.android.files.services.FileUploader;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.ui.dialog.ConfirmationDialogFragment;
import com.owncloud.android.ui.dialog.ConfirmationDialogFragment.ConfirmationDialogFragmentListener;
import com.owncloud.android.ui.dialog.LoadingDialog;
import com.owncloud.android.ui.fragment.LocalFileListFragment;
import com.owncloud.android.ui.helpers.FilesUploadHelper;
import com.owncloud.android.utils.FileStorageUtils;
import com.owncloud.android.utils.PreferenceUtils;

import java.io.File;


/**
 * Displays local files and let the user choose what of them wants to upload
 * to the current ownCloud account.
 */
public class UploadFilesActivity extends FileActivity implements
        LocalFileListFragment.ContainerActivity, ActionBar.OnNavigationListener,
        OnClickListener, ConfirmationDialogFragmentListener, FilesUploadHelper.OnCheckAvailableSpaceListener {

    private static final String TAG = UploadFilesActivity.class.getName();

    public static final String EXTRA_CHOSEN_FILES =
            UploadFilesActivity.class.getCanonicalName() + ".EXTRA_CHOSEN_FILES";

    public static final String REQUEST_CODE_KEY = "requestCode";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    public static final int RESULT_OK_AND_MOVE = RESULT_FIRST_USER;

    private static final String KEY_DIRECTORY_PATH =
            UploadFilesActivity.class.getCanonicalName() + ".KEY_DIRECTORY_PATH";

    private static final String WAIT_DIALOG_TAG = "WAIT";
    private static final String QUERY_TO_MOVE_DIALOG_TAG = "QUERY_TO_MOVE";

    private ArrayAdapter<String> mDirectories;
    private File mCurrentDir = null;
    private LocalFileListFragment mFileListFragment;
    private Button mCancelBtn;
    private Button mUploadBtn;
    private Account mAccountOnCreation;
    private DialogFragment mCurrentDialog;
    private String capturedPhotoPath;
    private File image = null;

    private MenuItem mItemSelectAll;
    private MenuItem mItemSelectInverse;
    private RadioButton mRadioBtnCopyFiles;
    private RadioButton mRadioBtnMoveFiles;
    private Menu mMainMenu;
    private int requestCode;

    FilesUploadHelper mFilesUploadHelper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log_OC.d(TAG, "onCreate() start");
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mCurrentDir = new File(savedInstanceState.getString(
                    UploadFilesActivity.KEY_DIRECTORY_PATH));
        } else {
            mCurrentDir = Environment.getExternalStorageDirectory();
        }

        mAccountOnCreation = getAccount();

        mFilesUploadHelper = new FilesUploadHelper(this, mAccountOnCreation.name);

        /// USER INTERFACE

        // Drop-down navigation 
        mDirectories = new CustomArrayAdapter<String>(this,
                R.layout.support_simple_spinner_dropdown_item);
        File currDir = mCurrentDir;
        while (currDir != null && currDir.getParentFile() != null) {
            mDirectories.add(currDir.getName());
            currDir = currDir.getParentFile();
        }
        mDirectories.add(File.separator);

        // Inflate and set the layout view
        setContentView(R.layout.upload_files_layout);

        // Allow or disallow touches with other visible windows
        LinearLayout uploadFilesLayout = findViewById(R.id.upload_files_layout);
        uploadFilesLayout.setFilterTouchesWhenObscured(
                PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(this)
        );

        mFileListFragment = (LocalFileListFragment)
                getSupportFragmentManager().findFragmentById(R.id.local_files_list);

        mFileListFragment.getView().setFilterTouchesWhenObscured(
                PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(this)
        );

        // Set input controllers
        mCancelBtn = findViewById(R.id.upload_files_btn_cancel);
        mCancelBtn.setOnClickListener(this);
        mUploadBtn = findViewById(R.id.upload_files_btn_upload);
        mUploadBtn.setOnClickListener(this);

        SharedPreferences appPreferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        Integer localBehaviour = appPreferences.getInt("prefs_uploader_behaviour", FileUploader.LOCAL_BEHAVIOUR_COPY);

        mRadioBtnMoveFiles = findViewById(R.id.upload_radio_move);
        if (localBehaviour == FileUploader.LOCAL_BEHAVIOUR_MOVE) {
            mRadioBtnMoveFiles.setChecked(true);
        }

        mRadioBtnCopyFiles = findViewById(R.id.upload_radio_copy);
        if (localBehaviour == FileUploader.LOCAL_BEHAVIOUR_COPY) {
            mRadioBtnCopyFiles.setChecked(true);
        }

        // setup the toolbar
        setupToolbar();

        // Action bar setup
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);   // mandatory since Android ICS, according to the
        // official documentation
        actionBar.setDisplayHomeAsUpEnabled(mCurrentDir != null && mCurrentDir.getName() != null);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setListNavigationCallbacks(mDirectories, this);

        // wait dialog
        if (mCurrentDialog != null) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
        Intent callingIntent = getIntent();
        Bundle data = callingIntent.getExtras();
        requestCode = (int) data.get(REQUEST_CODE_KEY);
        Log_OC.d(TAG, "onCreate() end");
    }

    /**
     * Helper to launch the UploadFilesActivity for which you would like a result when it finished.
     * Your onActivityResult() method will be called with the given requestCode.
     *
     * @param activity    the activity which should call the upload activity for a result
     * @param account     the account for which the upload activity is called
     * @param requestCode If >= 0, this code will be returned in onActivityResult()
     */
    public static void startUploadActivityForResult(Activity activity, Account account, int requestCode) {
        Intent action = new Intent(activity, UploadFilesActivity.class);
        action.putExtra(EXTRA_ACCOUNT, (account));
        action.putExtra(REQUEST_CODE_KEY, requestCode);
        activity.startActivityForResult(action, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent capturedData) {
        if (resultCode != RESULT_CANCELED) {
            mFilesUploadHelper.onActivityResult(this);
        } else {
            setResult(RESULT_CANCELED);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.upload_files_menu, menu);
        getMenuInflater().inflate(R.menu.sort_menu, menu.findItem(R.id.action_sort).getSubMenu());
        mMainMenu = menu;
        mItemSelectAll = menu.findItem(R.id.action_select_all);
        mItemSelectInverse = menu.findItem(R.id.action_select_inverse);
        recoverSortMenuState(menu);
        setSelectAllMenuItemState();
        setSelectInverseMenuItemState();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean retval = true;
        switch (item.getItemId()) {
            case android.R.id.home: {
                if (mCurrentDir != null && mCurrentDir.getParentFile() != null) {
                    onBackPressed();
                }
                break;
            }
            case R.id.action_select_all:
                mItemSelectAll.setVisible(false);
                mFileListFragment.selectAll();
                break;
            case R.id.action_select_inverse:
                mFileListFragment.selectInverse();
                setSelectAllMenuItemState();
                break;
            case R.id.action_sort_descending:
                item.setChecked(!item.isChecked());
                boolean isAscending = !item.isChecked();
                com.owncloud.android.db.PreferenceManager.setSortAscending(isAscending,
                        this, FileStorageUtils.UPLOAD_SORT);
                switch (com.owncloud.android.db.PreferenceManager.getSortOrder(this, FileStorageUtils.UPLOAD_SORT)) {
                    case FileStorageUtils.SORT_NAME:
                        sortByName(isAscending);
                        break;
                    case FileStorageUtils.SORT_SIZE:
                        sortBySize(isAscending);
                        break;
                    case FileStorageUtils.SORT_DATE:
                        sortByDate(isAscending);
                        break;
                }
                break;
            case R.id.action_sort_by_name:
                item.setChecked(true);
                sortByName(com.owncloud.android.db.PreferenceManager.getSortAscending(this, FileStorageUtils.UPLOAD_SORT));
                break;
            case R.id.action_sort_by_size:
                item.setChecked(true);
                sortBySize(com.owncloud.android.db.PreferenceManager.getSortAscending(this, FileStorageUtils.UPLOAD_SORT));
                break;
            case R.id.action_sort_by_date:
                item.setChecked(true);
                sortByDate(com.owncloud.android.db.PreferenceManager.getSortAscending(this, FileStorageUtils.UPLOAD_SORT));
                break;
            default:
                retval = super.onOptionsItemSelected(item);
        }
        return retval;
    }

    @Override
    protected void onResume() {
        recoverSortMenuState(mMainMenu);
        super.onResume();
    }

    private void sortByName(boolean isAscending) {
        mFileListFragment.sortByName(isAscending);
    }

    private void sortBySize(boolean isAscending) {
        mFileListFragment.sortBySize(isAscending);
    }

    private void sortByDate(boolean isAscending) {
        mFileListFragment.sortByDate(isAscending);
    }

    private void recoverSortMenuState(Menu menu) {
        if (menu != null) {
            menu.findItem(R.id.action_sort_descending).setChecked(!com.owncloud.android.db.PreferenceManager.getSortAscending(this, FileStorageUtils.UPLOAD_SORT));
            switch (com.owncloud.android.db.PreferenceManager.getSortOrder(this, FileStorageUtils.UPLOAD_SORT)) {
                case FileStorageUtils.SORT_NAME:
                    menu.findItem(R.id.action_sort_by_name).setChecked(true);
                    sortByName(com.owncloud.android.db.PreferenceManager.getSortAscending(this, FileStorageUtils.UPLOAD_SORT));
                    break;
                case FileStorageUtils.SORT_SIZE:
                    menu.findItem(R.id.action_sort_by_size).setChecked(true);
                    sortBySize(com.owncloud.android.db.PreferenceManager.getSortAscending(this, FileStorageUtils.UPLOAD_SORT));
                    break;
                case FileStorageUtils.SORT_DATE:
                    menu.findItem(R.id.action_sort_by_date).setChecked(true);
                    sortByDate(com.owncloud.android.db.PreferenceManager.getSortAscending(this, FileStorageUtils.UPLOAD_SORT));
                    break;
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        int i = itemPosition;
        while (i-- != 0) {
            onBackPressed();
        }
        // the next operation triggers a new call to this method, but it's necessary to 
        // ensure that the name exposed in the action bar is the current directory when the 
        // user selected it in the navigation list
        if (itemPosition != 0)
            getSupportActionBar().setSelectedNavigationItem(0);
        return true;
    }


    @Override
    public void onBackPressed() {
        if (mDirectories.getCount() <= 1) {
            finish();
            return;
        }
        int noOfFilesSelected = mFileListFragment.restoreNoOfFilesSelected();
        popDirname();
        mFileListFragment.browseUp();
        mCurrentDir = mFileListFragment.getCurrentFolder();
        if (mCurrentDir.getParentFile() == null) {
            ActionBar actionBar = getSupportActionBar();
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        int noOfFiles = mFileListFragment.getAdapter().getNoOfFilesInDir();
        if (noOfFilesSelected == noOfFiles) {
            mItemSelectAll.setVisible(false);
        } else {
            mItemSelectAll.setVisible(true);
        }
        if (noOfFiles > 0) {
            mItemSelectInverse.setVisible(true);
        } else {
            mItemSelectInverse.setVisible(false);
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // responsibility of restore is preferred in onCreate() before than in
        // onRestoreInstanceState when there are Fragments involved
        Log_OC.d(TAG, "onSaveInstanceState() start");
        super.onSaveInstanceState(outState);
        outState.putString(UploadFilesActivity.KEY_DIRECTORY_PATH, mCurrentDir.getAbsolutePath());
        Log_OC.d(TAG, "onSaveInstanceState() end");
    }


    /**
     * Pushes a directory to the drop down list
     *
     * @param directory to push
     * @throws IllegalArgumentException If the {@link File#isDirectory()} returns false.
     */
    public void pushDirname(File directory) {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Only directories may be pushed!");
        }
        mDirectories.insert(directory.getName(), 0);
        mCurrentDir = directory;
        setSelectAllMenuItemState();
        setSelectInverseMenuItemState();
    }

    /**
     * Pops a directory name from the drop down list
     *
     * @return True, unless the stack is empty
     */
    public boolean popDirname() {
        mDirectories.remove(mDirectories.getItem(0));
        return !mDirectories.isEmpty();
    }


    // Custom array adapter to override text colors
    private class CustomArrayAdapter<T> extends ArrayAdapter<T> {

        public CustomArrayAdapter(UploadFilesActivity ctx, int view) {
            super(ctx, view);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = super.getView(position, convertView, parent);

            ((TextView) v).setTextColor(getResources().getColorStateList(
                    android.R.color.white));
            return v;
        }

        public View getDropDownView(int position, View convertView,
                                    ViewGroup parent) {
            View v = super.getDropDownView(position, convertView, parent);

            ((TextView) v).setTextColor(getResources().getColorStateList(
                    android.R.color.white));

            return v;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFolderClicked(File directory) {
        pushDirname(directory);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void onFileClicked(File file) {
        setSelectAllMenuItemState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getCurrentFolder() {
        return mCurrentDir;
    }

    /**
     * Sets the state of select all menu item based on the comparison
     * between number of files that have been selected and the
     * number of files present in current directory.
     */
    private void setSelectAllMenuItemState() {
        if (mFileListFragment.getAdapter().getNoOfFilesInDir() ==
                mFileListFragment.getCheckedFilePaths().length) {
            mItemSelectAll.setVisible(false);
        } else {
            mItemSelectAll.setVisible(true);
        }
    }

    /**
     * Sets the state of select inverse menu item based on the comparison
     * between number of files that have been selected and the
     * number of files present in current directory (as it should be disabled
     * if the directory contains other directories but no files).
     */
    private void setSelectInverseMenuItemState() {
        if (mFileListFragment.getAdapter().getNoOfFilesInDir() > 0) {
            mItemSelectInverse.setVisible(true);
        } else {
            mItemSelectInverse.setVisible(false);
        }
    }

    /**
     * Performs corresponding action when user presses 'Cancel' or 'Upload' button
     * <p>
     * TODO Make here the real request to the Upload service ; will require to receive the account and
     * target folder where the upload must be done in the received intent.
     */
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.upload_files_btn_cancel) {
            setResult(RESULT_CANCELED);
            finish();

        } else if (v.getId() == R.id.upload_files_btn_upload) {
            mFilesUploadHelper.checkIfAvailableSpace(mFileListFragment.getCheckedFilePaths(), this);
        }
    }


    @Override
    public void onCheckAvailableSpaceStart() {
        /// progress dialog and disable 'Move' button
        if (requestCode == FileDisplayActivity.REQUEST_CODE__SELECT_FILES_FROM_FILE_SYSTEM) {
            mCurrentDialog = LoadingDialog.newInstance(R.string.wait_a_moment, false);
            mCurrentDialog.show(getSupportFragmentManager(), WAIT_DIALOG_TAG);
        }
    }

    @Override
    public void onCheckAvailableSpaceFinished(final boolean hasEnoughSpace, final String[] capturedFilePaths) {
        if (requestCode == FileDisplayActivity.REQUEST_CODE__SELECT_FILES_FROM_FILE_SYSTEM) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
        if (hasEnoughSpace) {
            // return the list of selected files (success)
            Intent data = new Intent();
            SharedPreferences.Editor appPreferencesEditor = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext()).edit();

            if (requestCode == FileDisplayActivity.REQUEST_CODE__UPLOAD_FROM_CAMERA) {
                data.putExtra(EXTRA_CHOSEN_FILES, capturedFilePaths);
                setResult(RESULT_OK_AND_MOVE, data);
            } else {
                data.putExtra(EXTRA_CHOSEN_FILES, mFileListFragment.getCheckedFilePaths());
                if (mRadioBtnMoveFiles.isChecked()) {
                    setResult(RESULT_OK_AND_MOVE, data);
                    appPreferencesEditor.putInt("prefs_uploader_behaviour",
                            FileUploader.LOCAL_BEHAVIOUR_MOVE);
                } else {
                    setResult(RESULT_OK, data);
                    appPreferencesEditor.putInt("prefs_uploader_behaviour",
                            FileUploader.LOCAL_BEHAVIOUR_COPY);
                }
            }
            appPreferencesEditor.apply();
            finish();
        } else {
            // show a dialog to query the user if wants to move the selected files
            // to the ownCloud folder instead of copying
            String[] args = {getString(R.string.app_name)};
            ConfirmationDialogFragment dialog = ConfirmationDialogFragment.newInstance(
                    R.string.upload_query_move_foreign_files, args, 0, R.string.common_yes, -1,
                    R.string.common_no
            );
            dialog.setOnConfirmationListener(UploadFilesActivity.this);
            dialog.show(getSupportFragmentManager(), QUERY_TO_MOVE_DIALOG_TAG);
        }
    }

    @Override
    public void onConfirmation(String callerTag) {
        Log_OC.d(TAG, "Positive button in dialog was clicked; dialog tag is " + callerTag);
        if (callerTag.equals(QUERY_TO_MOVE_DIALOG_TAG)) {
            // return the list of selected files to the caller activity (success),
            // signaling that they should be moved to the ownCloud folder, instead of copied
            Intent data = new Intent();
            data.putExtra(EXTRA_CHOSEN_FILES, mFileListFragment.getCheckedFilePaths());
            setResult(RESULT_OK_AND_MOVE, data);
            finish();
        }
    }


    @Override
    public void onNeutral(String callerTag) {
        Log_OC.d(TAG, "Phantom neutral button in dialog was clicked; dialog tag is " + callerTag);
    }


    @Override
    public void onCancel(String callerTag) {
        /// nothing to do; don't finish, let the user change the selection
        Log_OC.d(TAG, "Negative button in dialog was clicked; dialog tag is " + callerTag);
    }


    @Override
    protected void onAccountSet(boolean stateWasRecovered) {
        super.onAccountSet(stateWasRecovered);
        if (getAccount() != null) {
            if (!mAccountOnCreation.equals(getAccount())) {
                setResult(RESULT_CANCELED);
                finish();
            }

        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

}
