package org.twinone.irremote.providers.globalcache;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.Toast;

import org.twinone.irremote.R;
import org.twinone.irremote.components.Button;
import org.twinone.irremote.components.Remote;
import org.twinone.irremote.providers.ListableAdapter;
import org.twinone.irremote.providers.ProviderFragment;

public class GCProviderFragment extends ProviderFragment implements
        DBConnector.OnDataReceivedListener, OnItemClickListener,
        OnItemLongClickListener {

    public static final String ARG_URI_DATA = "com.twinone.irremote.arg.uri_data";


    private DBConnector mConnector;

    private boolean mCreated;
    private AlertDialog mDialog;

    private GlobalCacheProviderData mUriData;
    private Object[] mData;

    public GCProviderFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null && getArguments().containsKey(ARG_URI_DATA)) {
            mUriData = (GlobalCacheProviderData) getArguments()
                    .getSerializable(ARG_URI_DATA);
        } else {
            mUriData = new GlobalCacheProviderData();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        setHasOptionsMenu(true);

        setCurrentState(mUriData.targetType);

        View rootView = inflater.inflate(R.layout.fragment_listable, container,
                false);
        mListView = (ListView) rootView.findViewById(R.id.lvElements);
        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(this);

        mConnector = new DBConnector(getActivity(), this);

        if (mCreated) {
            mListView.setAdapter(mAdapter);
            mAdapter.restoreOriginalDataSet();
        } else if (mUriData.isAvailableInCache(getActivity())) {
            queryServer(false);
        } else {
            queryServer(true);
        }

        String title = mUriData.getFullyQualifiedName(" > ");
        if (title == null) {
            title = getString(R.string.db_select_manufacturer);
        }
        getActivity().setTitle(title);

        mCreated = true;
        return rootView;
    }

    private void queryServer(boolean showDialog) {
        mListView.setAdapter(null);

        if (showDialog)
            showDialog();

        if (mConnector != null)
            mConnector.cancelQuery();

        mConnector = new DBConnector(getActivity(), this);
        mConnector.setOnDataReceivedListener(this);
        mConnector.query(mUriData.clone());
    }

    private void cancelDialog() {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.cancel();
        }
    }

    private void showDialog() {
        cancelDialog();
        AlertDialog.Builder ab = new AlertDialog.Builder(getActivity());
        ab.setCancelable(false);
        ab.setTitle("Loading...");
        ab.setNegativeButton("Cancel", new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                mConnector.cancelQuery();
                getActivity().onNavigateUp();
            }
        });
        mDialog = ab.create();
        mDialog.show();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.db_menu, menu);
        setupSearchView(menu);
        mSearchView.setQueryHint(getSearchHint(mUriData));

        boolean show = mUriData.targetType == GlobalCacheProviderData.TYPE_IR_CODE
                && ACTION_SAVE_REMOTE.equals(getProvider().getAction());
        menu.findItem(R.id.menu_db_save).setVisible(show);
    }

    private String getSearchHint(GlobalCacheProviderData data) {
        if (data.targetType == GlobalCacheProviderData.TYPE_MANUFACTURER) {
            return getString(R.string.db_search_hint_manufacturers);
        } else if (data.targetType == GlobalCacheProviderData.TYPE_IR_CODE) {
            return getString(R.string.db_search_hint_buttons);
        } else {
            return getString(R.string.db_search_hint_custom,
                    data.getFullyQualifiedName(" "));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_db_refresh:
                mUriData.removeFromCache(getActivity());
                queryServer(true);
                return true;
            case R.id.menu_db_save:
                if (mData != null) {
                    String name = mUriData.manufacturer.Manufacturer + " "
                            + mUriData.deviceType.DeviceType;
                    Remote remote = IrCode.toRemote(getActivity(), name,
                            (IrCode[]) mData);
                    getProvider().saveRemote(remote);
                }
                return true;
        }
        return false;
    }

    @Override
    public void onDataReceived(Object[] data) {
        if (!isAdded())
            return;

        cancelDialog();
        mData = data;
        if (data == null) {
            Toast.makeText(getActivity(), "Oops! There was an error",
                    Toast.LENGTH_SHORT).show();
            mListView.setAdapter(null);
            return;
        }

        mAdapter = new ListableAdapter(getActivity(), data);
        mListView.setAdapter(mAdapter);
    }

    @Override
    public void onPause() {
        cancelDialog();

        // SearchView is so crappy that invalidateOptionsMenu will
        // not remove the keyboard, we have to use this "hack"
        // The null check is because the user could presses back very quickly

        // if (mSearchView != null) {
        // mSearchView.setQuery("", false);
        // mSearchView.clearFocus();
        // }

        super.onPause();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        GCBaseListable item = (GCBaseListable) mListView.getAdapter().getItem(
                position);
        if (item.getType() == GlobalCacheProviderData.TYPE_IR_CODE) {
            if (ACTION_SAVE_REMOTE.equals(getProvider().getAction())) {
                getProvider().transmit(((IrCode) item).getSignal());
            } else {
                Button b = IrCode.toButton(getActivity(), (IrCode) item);
                getProvider().saveButton(b);
            }
        } else {
            GlobalCacheProviderData clone = mUriData.clone();
            select(clone, item);
            getProvider().addGCProviderFragment(clone);
        }
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view,
                                   int position, long id) {

        // mListView.setItemChecked(position, true);
        // if (mUriData.targetType == UriData.TYPE_IR_CODE) {
        // // When the user long presses the button he can save it
        // Button b = IrCode.toButton((IrCode) mData[position]);
        // SaveButtonDialogFragment.showFor(getActivity(), b);
        // }
        // return true;
        return false;
    }

    private void select(GlobalCacheProviderData data, GCBaseListable listable) {
        data.targetType = GlobalCacheProviderData.TYPE_MANUFACTURER;
        if (listable != null) {
            if (listable.getType() == GlobalCacheProviderData.TYPE_MANUFACTURER) {
                data.manufacturer = (Manufacturer) listable;
                data.targetType = GlobalCacheProviderData.TYPE_DEVICE_TYPE;
            } else if (listable.getType() == GlobalCacheProviderData.TYPE_DEVICE_TYPE) {
                data.deviceType = (DeviceType) listable;
                data.targetType = GlobalCacheProviderData.TYPE_CODESET;
            } else if (listable.getType() == GlobalCacheProviderData.TYPE_CODESET) {
                data.codeset = (Codeset) listable;
                data.targetType = GlobalCacheProviderData.TYPE_IR_CODE;
            }
        }
    }

}