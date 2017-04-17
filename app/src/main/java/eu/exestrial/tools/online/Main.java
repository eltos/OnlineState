package eu.exestrial.tools.online;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

public class Main extends AppCompatActivity implements OnlineService.OnStateChangeListener {

    private static final String SAVE_LOCAL = "1";
    private static final String SAVE_REMOTE = "2";
    private static final String SAVE_TYPE = "3";
    private static final String SAVE_STATE = "4";
    private RelativeLayout vBackground;
    private TextView vState;
    private TextView vPing;
    private ProgressBar vStateSpinner;
    private TextView vNetType;
    private TextView vNetState;
    private TextView vRemoteIP;
    private TextView vRemoteIPTitle;
    private TextView vLocaleIP;
    private TextView vLocaleIPTitle;
    private TextView vRemotePing;
    private TextView vLocalePing;
    private TextView vLocaleStateTitle;

    private int mLocale = OnlineService.UNKNOWN;
    private int mRemote = OnlineService.UNKNOWN;
    private String mType = "";
    private NetworkInfo.State mState = NetworkInfo.State.UNKNOWN;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vBackground = (RelativeLayout) findViewById(R.id.background);
        vState = (TextView) findViewById(R.id.state);
        vPing = (TextView) findViewById(R.id.ping);
        vStateSpinner = (ProgressBar) findViewById(R.id.stateSpinner);
        vNetType = (TextView) findViewById(R.id.netType);
        vNetState = (TextView) findViewById(R.id.netState);
        vRemoteIP = (TextView) findViewById(R.id.remoteAddress);
        vRemoteIPTitle = (TextView) findViewById(R.id.remoteAddressTitle);
        vLocaleIP = (TextView) findViewById(R.id.localAddress);
        vLocaleIPTitle = (TextView) findViewById(R.id.localAddressTitle);
        vRemotePing = (TextView) findViewById(R.id.remotePing);
        vLocalePing = (TextView) findViewById(R.id.localePing);
        vLocaleStateTitle = (TextView) findViewById(R.id.localePingTitle);

        if (savedInstanceState != null){
            mLocale = savedInstanceState.getInt(SAVE_LOCAL);
            mRemote = savedInstanceState.getInt(SAVE_REMOTE);
            mType = savedInstanceState.getString(SAVE_TYPE);
            mState = (NetworkInfo.State) savedInstanceState.getSerializable(SAVE_STATE);
            updateGUI();
        }
    }

    private void updateGUI(){
        vBackground.setBackgroundColor(getResources().getColor(
                mRemote >= 0 ?
                        R.color.background_online :
                        mLocale == OnlineService.OFFLINE || mRemote == OnlineService.OFFLINE ?
                                R.color.background_offline :
                                android.R.color.background_light));
        vState.setText(
                mRemote >= 0 ? R.string.online :
                        mLocale >= 0 ? R.string.locale_online :
                                mLocale == OnlineService.OFFLINE || mRemote == OnlineService.OFFLINE ?
                                        R.string.offline :
                                        R.string.wait);
        vPing.setText(mRemote >= 0 ? String.format("%d ms", mRemote) : mLocale >= 0 ? String.format("%d ms", mLocale) : "");
        vPing.setVisibility(mRemote >= 0 || mLocale >= 0 ? View.VISIBLE : View.GONE);
        vState.setVisibility(mRemote == OnlineService.UNKNOWN && mLocale == OnlineService.UNKNOWN ? View.GONE : View.VISIBLE);
        vStateSpinner.setVisibility(mRemote == OnlineService.UNKNOWN && mLocale == OnlineService.UNKNOWN ? View.VISIBLE : View.GONE);
        vLocalePing.setText(mLocale >= 0 ? String.format("%d ms", mLocale) :
                mLocale == OnlineService.OFFLINE ? getString(R.string.offline) : getString(R.string.unknown));
        vRemotePing.setText(mRemote >= 0 ? String.format("%d ms", mRemote) :
                mRemote == OnlineService.OFFLINE ? getString(R.string.offline) : getString(R.string.unknown));
        vNetType.setText(mType);
        switch (mState){
            case CONNECTING:
                vNetState.setText(getString(R.string.connecting));
                break;
            case CONNECTED:
                vNetState.setText(getString(R.string.connected));
                break;
            case DISCONNECTING:
                vNetState.setText(getString(R.string.disconnecting));
                break;
            case DISCONNECTED:
                vNetState.setText(getString(R.string.disconnected));
                break;
            case SUSPENDED:
                vNetState.setText(getString(R.string.suspended));
                break;
            case UNKNOWN:
            default:
                vNetState.setText(getString(R.string.unknown));
                break;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        vLocaleIP.setText(preferences.getString(getString(R.string.pref_locale_ip_key),
                getString(R.string.pref_default_locale_ip)));
        vRemoteIP.setText(preferences.getString(getString(R.string.pref_remote_ip_key),
                getString(R.string.pref_default_remote_ip)));
        boolean localEnabled = preferences.getBoolean(getString(R.string.pref_locale_enabled_key), getResources().getBoolean(R.bool.pref_locale_enabled_default));
        vLocaleIP.setVisibility(localEnabled ? View.VISIBLE : View.GONE);
        vLocalePing.setVisibility(localEnabled ? View.VISIBLE : View.GONE);
        vLocaleIPTitle.setVisibility(localEnabled ? View.VISIBLE : View.GONE);
        vLocaleStateTitle.setVisibility(localEnabled ? View.VISIBLE : View.GONE);

        if (getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE) {
            vRemoteIP.setVisibility(View.GONE);
            vRemoteIPTitle.setVisibility(View.GONE);
            vLocaleIP.setVisibility(View.GONE);
            vLocaleIPTitle.setVisibility(View.GONE);
        }
    }

    private void playBeep(boolean positive){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if (preferences.getBoolean(getString(R.string.pref_sounds_key), getResources().getBoolean(R.bool.pref_sounds_default))) {

            ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            //toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
            if (positive) {
                toneG.startTone(ToneGenerator.TONE_PROP_ACK, 200);
            } else {
                toneG.startTone(ToneGenerator.TONE_PROP_NACK, 200);
            }
        }



    }


    OnlineService.LocalBinder mServiceBinder;
    boolean mServiceBound = false;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mServiceBinder = (OnlineService.LocalBinder) service;
            mServiceBound = true;

            mServiceBinder.addOnStateChangeListener(Main.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
            mServiceBinder.removeOnStateChangeListener(Main.this);
        }
    };

    @Override
    public void onOnlineChange(int local, int remote) {
        if (remote != OnlineService.UNKNOWN && (
                (mRemote == OnlineService.OFFLINE && remote != OnlineService.OFFLINE)
                || (mRemote >= 0 && remote < 0)
                || (mRemote == OnlineService.UNKNOWN))){
            playBeep(remote >= 0);
        }
        mLocale = local;
        mRemote = remote;
        updateGUI();

    }

    @Override
    public void onStateChange(String type, NetworkInfo.State state) {
        mType = type;
        mState = state;
        updateGUI();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Binds the service
        bindService(new Intent(this, OnlineService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbinds the service
        unbindService(mServiceConnection);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(SAVE_LOCAL, mLocale);
        outState.putInt(SAVE_REMOTE, mRemote);
        outState.putString(SAVE_TYPE, mType);
        outState.putSerializable(SAVE_STATE, mState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.settings){
            Intent intent = new Intent( this, Settings.class );
            intent.putExtra( Settings.EXTRA_SHOW_FRAGMENT, Settings.GeneralPreferenceFragment.class.getName() );
            intent.putExtra( Settings.EXTRA_NO_HEADERS, true );
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
