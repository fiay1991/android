/**
 * ownCloud Android client application
 *
 * @author David A. Velasco
 * @author Christian Schabesberger
 * @author Shashvat Kedia
 * Copyright (C) 2011  Bartek Przybylski
 * Copyright (C) 2019 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.owncloud.android.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.owncloud.android.R;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.ui.adapter.LocalFileListAdapter;
import com.owncloud.android.utils.FileStorageUtils;
import com.owncloud.android.utils.PreferenceUtils;

import java.io.File;
import java.util.ArrayList;


/**
 * A Fragment that lists all files and folders in a given LOCAL path.
 */
public class LocalFileListFragment extends ExtendedListFragment {

    private static final String TAG = LocalFileListFragment.class.getName();

    private final String OUT_STATE_CHECKED_FILES = "out_state_checked_files";
    private static final String OUT_STATE_NO_OF_FILES_SELECTED = "NO_OF_FILES_SELECTED";

    /**
     * Reference to the Activity which this fragment is attached to. For callbacks
     */
    private LocalFileListFragment.ContainerActivity mContainerActivity;

    /**
     * Directory to show
     */
    private File mDirectory = null;

    /**
     * Adapter to connect the data from the directory with the View object
     */
    private LocalFileListAdapter mAdapter = null;

    private ArrayList<Integer> mNoOfFilesSelected;

    /**
     * Public factory method to create new {@link LocalFileListFragment} instances.
     *
     * @param justFolders When 'true', only folders will be shown to the user, not files
     * @return New fragment with arguments set
     */
    public static LocalFileListFragment newInstance(boolean justFolders) {
        LocalFileListFragment frag = new LocalFileListFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_JUST_FOLDERS, justFolders);
        frag.setArguments(args);
        return frag;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mContainerActivity = (ContainerActivity) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement " +
                    LocalFileListFragment.ContainerActivity.class.getSimpleName());
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log_OC.i(TAG, "onCreateView() start");
        View v = super.onCreateView(inflater, container, savedInstanceState);
        setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        setSwipeEnabled(false); // Disable pull-to-refresh
        setFabEnabled(false); // Disable FAB
        setMessageForEmptyList(
                isShowingJustFolders() ?
                        getString(R.string.local_file_list_empty_just_folders) :
                        getString(R.string.local_file_list_empty)
        );
        Log_OC.i(TAG, "onCreateView() end");
        return v;
    }

    public int size() {
        return mAdapter == null ? 0 : mAdapter.getCount();
    }

    public void sortByName(boolean isAscending) {
        mAdapter.setSortOrder(FileStorageUtils.SORT_NAME, isAscending);
    }

    public void sortBySize(boolean isAscending) {
        mAdapter.setSortOrder(FileStorageUtils.SORT_SIZE, isAscending);
    }

    public void sortByDate(boolean isAscending) {
        mAdapter.setSortOrder(FileStorageUtils.SORT_DATE, isAscending);
    }

    public void selectAll() {
        for (int i = 0; i < mAdapter.getCount(); i++) {
            File file = (File) mAdapter.getItem(i);
            if (!file.isDirectory() && !mAdapter.isAlreadyChecked(file)) {
                ((ImageView) getView().findViewById(R.id.custom_checkbox)).setImageResource(R.drawable.ic_checkbox_marked);
                mCurrentListView.setItemChecked(i, true);
                mAdapter.checkFile(file);
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    public void selectInverse() {
        for (int i = 0; i < mAdapter.getCount(); i++) {
            File file = (File) mAdapter.getItem(i);
            if (!file.isDirectory()) {
                ImageView checkBox = (ImageView) getView().findViewById(R.id.custom_checkbox);
                if (mCurrentListView.isItemChecked(i)) {
                    checkBox.setImageResource(R.drawable.ic_checkbox_blank_outline);
                    mCurrentListView.setItemChecked(i, false);
                    mAdapter.uncheckFile(file);
                } else {
                    checkBox.setImageResource(R.drawable.ic_checkbox_marked);
                    mCurrentListView.setItemChecked(i, true);
                    mAdapter.checkFile(file);
                }
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    public void storeNoOfFilesSelected() {
        mNoOfFilesSelected.add(getCheckedFilePaths().length - 1);
    }

    public int restoreNoOfFilesSelected() {
        if (mNoOfFilesSelected.size() > 0) {
            return mNoOfFilesSelected.remove(mNoOfFilesSelected.size() - 1);
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log_OC.i(TAG, "onActivityCreated() start");

        super.onActivityCreated(savedInstanceState);
        mDirectory = mContainerActivity.getCurrentFolder();
        mAdapter = new LocalFileListAdapter(mDirectory, isShowingJustFolders(), getActivity());
        setListAdapter(mAdapter);
        if (savedInstanceState != null) {
            mAdapter.setCheckedFiles(savedInstanceState.getStringArrayList(OUT_STATE_CHECKED_FILES));
            mNoOfFilesSelected = savedInstanceState.getIntegerArrayList(OUT_STATE_NO_OF_FILES_SELECTED);
        } else {
            mNoOfFilesSelected = new ArrayList<>();
        }

        // Allow or disallow touches with other visible windows
        CoordinatorLayout coordinatorLayout = getActivity().findViewById(R.id.coordinator_layout);
        if (coordinatorLayout != null) {
            coordinatorLayout.setFilterTouchesWhenObscured(
                    PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(getContext())
            );
        }

        Log_OC.i(TAG, "onActivityCreated() stop");
    }

    public LocalFileListAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void onResume() {
        Log_OC.d(TAG, "onResume() start");
        super.onResume();
        listFolder();
        Log_OC.d(TAG, "onResume() end");
    }

    /**
     * Checks the file clicked over. Browses inside if it is a directory.
     * Notifies the container activity in any case.
     */
    @Override
    public void onItemClick(AdapterView<?> l, View v, int position, long id) {
        File file = (File) mAdapter.getItem(position);
        if (file != null) {
            /// Click on a directory
            if (file.isDirectory()) {
                storeNoOfFilesSelected();
                // just local updates
                listFolder(file);
                // notify the click to container Activity
                mContainerActivity.onFolderClicked(file);
                // save index and top position
                saveIndexAndTopPosition(position);

            } else {    /// Click on a file
                ImageView checkBoxV = v.findViewById(R.id.custom_checkbox);
                if (checkBoxV != null) {
                    if (getListView().isItemChecked(position)) {
                        checkBoxV.setImageResource(R.drawable.ic_checkbox_marked);
                        mAdapter.checkFile((File) mAdapter.getItem(position));
                    } else {
                        checkBoxV.setImageResource(R.drawable.ic_checkbox_blank_outline);
                        mAdapter.uncheckFile((File) mAdapter.getItem(position));
                    }
                }
                // notify the change to the container Activity
                mContainerActivity.onFileClicked(file);
            }

        } else {
            Log_OC.w(TAG, "Null object in ListAdapter!!");
        }
    }


    /**
     * Browse up to the parent folder of the current one.
     */
    public void browseUp() {
        File parentDir = null;
        if (mDirectory != null) {
            parentDir = mDirectory.getParentFile();  // can be null
        }
        listFolder(parentDir);

        // restore index and top position
        restoreIndexAndTopPosition();
    }


    /**
     * Use this to query the {@link File} object for the directory
     * that is currently being displayed by this fragment
     *
     * @return File     The currently displayed directory
     */
    public File getCurrentFolder() {
        return mDirectory;
    }


    /**
     * Calls {@link LocalFileListFragment#listFolder(File)} with a null parameter
     * to refresh the current directory.
     */
    public void listFolder() {
        listFolder(null);
    }

    /**
     * Lists the given directory on the view. When the input parameter is null,
     * it will either refresh the last known directory. list the root
     * if there never was a directory.
     *
     * @param directory Directory to be listed
     */
    public void listFolder(File directory) {

        // Check input parameters for null
        if (directory == null) {
            if (mDirectory != null) {
                directory = mDirectory;
            } else {
                directory = Environment.getExternalStorageDirectory();
                // TODO be careful with the state of the storage; could not be available
                if (directory == null) return; // no files to show
            }
        }


        // if that's not a directory -> List its parent
        if (!directory.isDirectory()) {
            Log_OC.w(TAG, "You see, that is not a directory -> " + directory.toString());
            directory = directory.getParentFile();
        }

        // by now, only files in the same directory will be kept as selected
        mCurrentListView.clearChoices();
        mAdapter.swapDirectory(directory);
        if (mDirectory == null || !mDirectory.equals(directory)) {
            mCurrentListView.setSelection(0);
        }
        mDirectory = directory;
    }


    /**
     * Returns the file paths to the files checked by the user
     *
     * @return File paths to the files checked by the user.
     */
    public String[] getCheckedFilePaths() {
        ArrayList<String> result = new ArrayList<>();
        SparseBooleanArray positions = mCurrentListView.getCheckedItemPositions();
        if (positions.size() > 0) {
            for (int i = 0; i < positions.size(); i++) {
                if (positions.get(positions.keyAt(i))) {
                    result.add(((File) mCurrentListView.getItemAtPosition(
                            positions.keyAt(i))).getAbsolutePath());
                }
            }

            Log_OC.d(TAG, "Returning " + result.size() + " selected files");
        }
        return result.toArray(new String[result.size()]);
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putStringArrayList(OUT_STATE_CHECKED_FILES, mAdapter.getCheckedFiles());
        savedInstanceState.putIntegerArrayList(OUT_STATE_NO_OF_FILES_SELECTED, mNoOfFilesSelected);
    }

    /**
     * Interface to implement by any Activity that includes some instance of LocalFileListFragment
     */
    public interface ContainerActivity {

        /**
         * Callback method invoked when a directory is clicked by the user on the files list
         *
         * @param folder Folder shown in the item clicked by the user
         */
        void onFolderClicked(File folder);

        /**
         * Callback method invoked when a file (non directory)
         * is clicked by the user on the files list
         *
         * @param file File shown in the item clicked by the user
         */
        void onFileClicked(File file);


        /**
         * Callback method invoked when the parent activity
         * is fully created to get the directory to list firstly.
         *
         * @return Directory to list firstly. Can be NULL.
         */
        File getCurrentFolder();

    }

}
