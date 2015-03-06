package io.daydev.vkrdo;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import com.vk.sdk.VKUIHelper;
import io.daydev.vkrdo.bean.RadioBuilder;
import io.daydev.vkrdo.bean.RadioInfo;
import io.daydev.vkrdo.external.ConfigurationHolder;
import io.daydev.vkrdo.fragment.*;
import io.daydev.vkrdo.preference.PreferenceHelper;
import io.daydev.vkrdo.service.MediaPlayerService;
import io.daydev.vkrdo.slide.NavDrawerItem;
import io.daydev.vkrdo.slide.NavDrawerListAdapter;
import io.daydev.vkrdo.slide.SlidePreferencesHelper;
import io.daydev.vkrdo.util.Callback;
import io.daydev.vkrdo.util.CallbackChecker;
import io.daydev.vkrdo.util.ResultTuple;
import io.daydev.vkrdo.util.Tuple;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends VKActivity implements MediaEvent {

    private static final String PREF_NAME = "vkrdo";
    private static final String PREF_RADIO = "myradio";

    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;

    // nav drawer title
    private CharSequence mDrawerTitle;

    // used to store app title
    private CharSequence mTitle;

    // slide menu items
    private SlidePreferencesHelper slidePreferencesHelper;

    private Callback<Message> currentFragmentCallback;
    private CallbackChecker<RadioInfo> currentFragmentRadioChecker;
    private RadioInfo currentRadio;

    private NavDrawerListAdapter slideAdapter;


    private enum State {
        HOME, SETTINGS, RADIO
    }

    private State currentState;

    private boolean preventBack = false;

    private io.daydev.vkrdo.bean.Configuration configuration;
    private static final String CONFIG = "http://78.47.96.12/public/vkrdo.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        if (configuration == null) {
            ConfigurationHolder task = new ConfigurationHolder();
            task.load(CONFIG, new Callback<ResultTuple<io.daydev.vkrdo.bean.Configuration>>() {
                @Override
                public void callback(ResultTuple<io.daydev.vkrdo.bean.Configuration> result) {
                    if (result != null && result.hasResult()) {
                        configuration = result.getResult();
                    } else {
                        displayError(result == null ? "" : result.getError());
                    }
                }
            });
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter(MediaEvent.EVENT));

        Intent intent = new Intent(this, MediaPlayerService.class);
        intent.setAction(MediaPlayerService.ACTION_EMPTY);
        startService(intent);

        intent = new Intent(this, MediaPlayerService.class);
        intent.setAction(MediaPlayerService.ACTION_STATUS);
        startService(intent);

        mTitle = mDrawerTitle = getTitle();
        currentState = null;
        preventBack = false;

        // PreferenceHelper.clean(getSharedPreferences(PREF_NAME, MODE_PRIVATE), PREF_RADIO);
        slidePreferencesHelper = new SlidePreferencesHelper();
        slidePreferencesHelper.onCreate(PreferenceHelper.getMap(getSharedPreferences(PREF_NAME, MODE_PRIVATE), PREF_RADIO));

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.list_slidermenu);


        mDrawerList.setOnItemClickListener(new SlideMenuClickListener());

        List<NavDrawerItem> navDrawerItems = new ArrayList<>();
        navDrawerItems.add(new NavDrawerItem(getString(R.string.add_radio_slide), android.R.drawable.ic_input_add));
        navDrawerItems.addAll(slidePreferencesHelper.convertToNavDrawItems());

        // setting the nav drawer list adapter
        slideAdapter = new NavDrawerListAdapter(getApplicationContext(), navDrawerItems);
        mDrawerList.setAdapter(slideAdapter);

        // enabling action bar app icon and behaving it as toggle button
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
        }

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.drawable.ic_drawer, //nav menu toggle icon
                R.string.app_name, // nav drawer open - description for accessibility
                R.string.app_name // nav drawer close - description for accessibility
        ) {
            public void onDrawerClosed(View view) {
                getActionBar().setTitle(mTitle);
                preventBack = false;
                // calling onPrepareOptionsMenu() to show action bar icons
                invalidateOptionsMenu();
            }

            public void onDrawerOpened(View drawerView) {
                getActionBar().setTitle(mDrawerTitle);
                preventBack = true;
                // calling onPrepareOptionsMenu() to hide action bar icons
                invalidateOptionsMenu();
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        if (savedInstanceState == null) {
            // on first time display view for first nav item
            SplashFragment fragment = new SplashFragment();
            FragmentManager fragmentManager = getFragmentManager();
            fragmentManager
                    .beginTransaction()
                    .replace(R.id.frame_container, fragment)
                    .commit();
        }
        VKUIHelper.onCreate(this);
    }


    /**
     * Slide menu item click listener
     */
    private class SlideMenuClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            slideAdapter.setIcon(currentRadio, android.R.drawable.ic_media_pause);
            // display view for selected nav drawer item
            displayView(position);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // toggle nav drawer on selecting action bar app icon/title
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle action bar actions click
        switch (item.getItemId()) {
            case R.id.action_settings:
                if (!State.HOME.equals(currentState)) {
                    radioSettings(currentRadio);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Called when invalidateOptionsMenu() is triggered
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // if nav drawer is opened, hide the action items
        if (menu != null) {
            boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
            if (menu.findItem(R.id.action_settings) != null) {
                menu.findItem(R.id.action_settings).setVisible(!drawerOpen);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * Diplaying fragment view for selected nav drawer list item
     */
    private void displayView(int position) {
        displayView(position, null);
    }

    private void displayView(int position, Serializable extra) {
        // update the main content by replacing fragments
        Fragment fragment = null;
        Bundle bundle = null;
        String title = getString(R.string.app_name);

        currentFragmentCallback = null;
        currentFragmentRadioChecker = null;

        switch (position) {
            case -1:
                initSdk(configuration);
                fragment = new HomeFragment();
                currentState = State.HOME;
                break;
            case 0:
                radioSettings(null);
                title = getString(R.string.new_radio_title);
                break;
            default:
                currentState = State.RADIO;
                fragment = new RadioFragment();
                currentFragmentCallback = (Callback<Message>) fragment;
                currentFragmentRadioChecker = (CallbackChecker<RadioInfo>) fragment;

                currentRadio = slidePreferencesHelper.getByPosition(position-1);
                title = currentRadio.getTitle();

                bundle = new Bundle();
                bundle.putSerializable(RadioFragment.RADIO_PARAM, currentRadio);
                if (extra != null) {
                    bundle.putSerializable(RadioFragment.EXTRA_PARAM, extra);
                }
                slideAdapter.setIcon(currentRadio, android.R.drawable.ic_media_pause);
        }

        if (bundle != null) {
            fragment.setArguments(bundle);
        }
        if (fragment != null) {
            try {
                FragmentManager fragmentManager = getFragmentManager();
                FragmentTransaction transaction = fragmentManager
                        .beginTransaction()
                        .replace(R.id.frame_container, fragment)
                        .addToBackStack(null);

                //stupid fix for this http://stackoverflow.com/questions/7575921/illegalstateexception-can-not-perform-this-action-after-onsaveinstancestate-h
                if (fragment instanceof RadioFragment) {
                    transaction.commit();
                } else {
                    transaction.commitAllowingStateLoss();
                }
            } catch (Exception e) {
                Log.e("MainActivity", "displayView", e);
                return;
            }
        }

        // update selected item and title, then close the drawer
        if (position >= 0) {
            mDrawerList.setItemChecked(position, true);
            mDrawerList.setSelection(position);
        }

        setTitle(title);
        mDrawerLayout.closeDrawer(mDrawerList);
    }

    private void radioSettings(RadioInfo radioInfo) {
        if (State.SETTINGS.equals(currentState)) {
            return;
        }
        slideAdapter.setIcon(radioInfo, android.R.drawable.ic_menu_preferences);

        currentState = State.SETTINGS;
        RadioPreferencesFragment fragment = new RadioPreferencesFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable(RadioPreferencesFragment.RADIO_PARAM, radioInfo);
        fragment.setArguments(bundle);
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager
                .beginTransaction()
                .replace(R.id.frame_container, fragment)
                .addToBackStack(null)
                .commit();

        fragment.setSettingsCallback(new Callback<Tuple<RadioInfo, RadioInfo>>() {
            @Override
            public void callback(Tuple<RadioInfo, RadioInfo> obj) {
                RadioInfo oldRadio = obj.getFirst();
                RadioInfo newRadio = obj.getSecond();

                if (!slidePreferencesHelper.addOrReplace(oldRadio, newRadio, slideAdapter)){
                    return;
                }
                PreferenceHelper.saveMap(getSharedPreferences(PREF_NAME, MODE_PRIVATE), PREF_RADIO, slidePreferencesHelper.getPreferencesMap());

                if (oldRadio != null && oldRadio.isSame(currentRadio)) {
                    currentRadio = newRadio;
                    setTitle(currentRadio.getTitle());
                }
                //startNewRadio(newRadio.getTitle(), newRadio);
            }
        });
    }


    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        if (getActionBar() != null) {
            getActionBar().setTitle(mTitle);
        }
    }

    /**
     * When using the ActionBarDrawerToggle, you must call it during
     * onPostCreate() and onConfigurationChanged()...
     */

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggls
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String type = intent.getStringExtra(TYPE);
            if (type == null || type.isEmpty()) {
                return;
            }

            switch (type) {
                case GLOBAL_ERROR:
                    // display error
                    String globalError = intent.getStringExtra(GLOBAL_ERROR);
                    if (globalError != null && !globalError.isEmpty()) {
                        displayError(globalError);
                    }
                    break;

                case RADIO_REMOVE:
                    // remove radio and go to home
                    String radioRemove = intent.getStringExtra(RADIO_REMOVE);
                    if (radioRemove != null && !radioRemove.isEmpty()) {
                        // if "magic" - display home screen - mean remove not saved radio
                        if (!radioRemove.equals(MAGIC_HOME)) {
                            slidePreferencesHelper.remove(radioRemove, slideAdapter);
                            PreferenceHelper.saveMap(getSharedPreferences(PREF_NAME, MODE_PRIVATE), PREF_RADIO, slidePreferencesHelper.getPreferencesMap());
                        }

                        displayView(-1);
                    }
                    break;

                case SIMPLE_RADIO:
                    // display and maybe play radio
                    String radio = intent.getStringExtra(SIMPLE_RADIO);
                    Integer position = null;
                    String extra = null;
                    if (radio != null && !radio.isEmpty()) {
                        // if "magic" - display home screen
                        if (radio.equals(MAGIC_HOME)) {
                            if (!State.RADIO.equals(currentState) && configuration != null) {
                                position = -1;
                            }
                        } else {
                            RadioInfo radioInfo = (RadioInfo) intent.getSerializableExtra(REAL_RADIO);
                            if (radioInfo == null) {
                                //setup "virtual" radio - do not save it!
                                radioInfo = RadioBuilder.getInstance()
                                        .setTitle(radio)
                                        .setArtist(radio)
                                        .setArtistLinkType(RadioInfo.ArtistLinkType.LIMIT)
                                        .build();

                                if (slidePreferencesHelper.addWithoutPreferences(radioInfo, slideAdapter)) {
                                    position = slidePreferencesHelper.position(radioInfo) + 1;
                                } else {
                                    position = slidePreferencesHelper.getNavMenuSize();
                                }
                            } else {
                                // display current radio & play it
                                position = slidePreferencesHelper.position(radioInfo) + 1;
                                extra = RadioFragment.EXTRA_PLAY;
                            }
                        }
                    }
                    if (position != null){
                        displayView(position, extra);
                    }
                    break;

                case RADIO_TITLE:
                    // setup header title
                    String radioTitle = intent.getStringExtra(RADIO_TITLE);
                    if (radioTitle != null && !radioTitle.isEmpty()) {
                        setTitle(radioTitle);
                        int pos = slidePreferencesHelper.position(radioTitle) + 1;
                        mDrawerList.setItemChecked(pos, true);
                        mDrawerList.setSelection(pos);
                    }
                    break;

                default:
                    // currently played event from media service
                    RadioInfo radioSource = (RadioInfo) intent.getSerializableExtra(MediaEvent.DATA_RADIO);
                    if (radioSource != null) {

                        // if no settings then goto radio fragment
                        if (currentFragmentRadioChecker == null && !State.SETTINGS.equals(currentState)) {
                            displayView(slidePreferencesHelper.position(radioSource) + 1);
                        }

                        // setup icon if it pause/stop event
                        int message = intent.getIntExtra(MediaEvent.DATA_MESSAGE_CODE, 1);
                        switch (message) {
                            case MediaPlayerService.MSG_STOP:
                            case MediaPlayerService.MSG_PAUSE:
                                slideAdapter.setIcon(radioSource, android.R.drawable.ic_media_pause);
                                break;
                            case MediaPlayerService.MSG_PLAY:
                            case MediaPlayerService.MSG_PROGRESS:
                            case MediaPlayerService.MSG_SEEK:
                                slideAdapter.setIcon(radioSource, android.R.drawable.ic_media_play);
                                break;
                        }

                        // next processing by current radio fragment
                        if (currentFragmentRadioChecker != null && currentFragmentCallback != null && currentFragmentRadioChecker.check(radioSource)) {
                            Message msg = new Message();

                            msg.what = message;
                            msg.obj = intent.getSerializableExtra(MediaEvent.DATA_SERIALIZEBLE);
                            if (msg.obj == null) {
                                msg.obj = intent.getParcelableExtra(MediaEvent.DATA_PARCEABLE);
                            }

                            currentFragmentCallback.callback(msg);
                        }
                    }
            }

        }
    };

    private boolean doubleBackToExitPressedOnce;

    @Override
    public void onBackPressed() {
        if (preventBack) {
            return;
        }

        FragmentManager fm = getFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate();
            Fragment f = getFragmentManager().findFragmentById(R.id.frame_container);
            if (f instanceof RadioFragment) {
                currentState = State.RADIO;
                slideAdapter.setIcon(currentRadio, android.R.drawable.ic_media_pause);
            } else if (f instanceof RadioPreferencesFragment) {
                currentState = State.SETTINGS;
            } else if (f instanceof HomeFragment) {
                currentState = State.HOME;
                setTitle(R.string.app_name);
                mDrawerList.clearChoices();
            }
        } else {
            if (doubleBackToExitPressedOnce) {
                super.onBackPressed();
                return;
            }

            this.doubleBackToExitPressedOnce = true;
            Toast.makeText(this, getString(R.string.back_hint), Toast.LENGTH_SHORT).show();

            new Handler().postDelayed(new Runnable() {

                @Override
                public void run() {
                    doubleBackToExitPressedOnce = false;
                }
            }, 2000);
        }
    }

    private void displayError(String description) {
        ErrorFragment fragment = new ErrorFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable(ErrorFragment.ERROR_DESCRIPTION, description);
        fragment.setArguments(bundle);

        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager
                .beginTransaction()
                .replace(R.id.frame_container, fragment)
                .commit();
    }
}