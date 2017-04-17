package eu.exestrial.tools.online;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OnlineService extends Service {

    static final int OFFLINE = -1;
    static final int UNKNOWN = -2;

    private final IBinder mBinder = new LocalBinder();
    private OnlineChecker mTask;

    private int mLocaleState = UNKNOWN;
    private int mRemoteState = UNKNOWN;
    private String mType = null;
    private NetworkInfo.State mState = NetworkInfo.State.UNKNOWN;

    public interface OnStateChangeListener{
        void onOnlineChange(int localOnline, int remoteOnline);
        void onStateChange(String type, NetworkInfo.State state);
    }

    ArrayList<OnStateChangeListener> mStateChangeListeners = new ArrayList<>();

    public class LocalBinder extends Binder {

        boolean addOnStateChangeListener(OnStateChangeListener listener){
            if (! mStateChangeListeners.contains(listener)){
                if (mLocaleState != UNKNOWN || mRemoteState != UNKNOWN) {
                    listener.onOnlineChange(mLocaleState, mRemoteState);
                }
                if (mType != null) {
                    listener.onStateChange(mType, mState);
                }
                return mStateChangeListeners.add(listener);
            }
            return false;
        }
        boolean removeOnStateChangeListener(OnStateChangeListener listener){
            return mStateChangeListeners.remove(listener);
        }

    }

    private void callOnStateChangeListener(int localOnline, int remoteOnline){
        mLocaleState = localOnline;
        mRemoteState = remoteOnline;
        for (OnStateChangeListener listener : mStateChangeListeners){
            listener.onOnlineChange(localOnline, remoteOnline);
        }
    }
    private void callOnStateChangeListener(String type, NetworkInfo.State state){
        mType = type;
        mState = state;
        for (OnStateChangeListener listener : mStateChangeListeners){
            listener.onStateChange(type, state);
        }
    }


    public OnlineService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true; // ensures onRebind is called
    }

    @Override
    public void onRebind(Intent intent) {
    }

    @Override
    public void onCreate() {


        // Register Connectivity change receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mReceiver, filter);

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null){
            callOnStateChangeListener(netInfo.getTypeName(), netInfo.getState());
        } else {
            callOnStateChangeListener(getString(R.string.none), NetworkInfo.State.DISCONNECTED);
        }
        if (netInfo != null && netInfo.isConnectedOrConnecting()){
            startTask();
        } else {
            callOnStateChangeListener(OFFLINE, OFFLINE);
        }

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        stopTask();

        // Unregister receiver
        unregisterReceiver(mReceiver);

        super.onDestroy();
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            if (netInfo == null){
                callOnStateChangeListener(getString(R.string.none), NetworkInfo.State.DISCONNECTED);
                callOnStateChangeListener(OFFLINE, OFFLINE);
            } else {
                callOnStateChangeListener(netInfo.getTypeName(), netInfo.getState());
            }
            if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                startTask();
            } else {
                stopTask();
            }
        }
    };

    private void startTask(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String local = null;
        if (preferences.getBoolean(getString(R.string.pref_locale_enabled_key), false)){
            local = preferences.getString(getString(R.string.pref_locale_ip_key), getString(R.string.pref_default_locale_ip));
        }
        String remote = preferences.getString(getString(R.string.pref_remote_ip_key), getString(R.string.pref_default_remote_ip));
        int interval = Integer.valueOf(preferences.getString(getString(R.string.pref_ping_frequency_key), getString(R.string.pref_default_ping_frequency))); // milliseconds
        int iterations = Integer.valueOf(preferences.getString(getString(R.string.pref_ping_count_key), getString(R.string.pref_default_ping_count)));

        if (mTask == null || mTask.getStatus() == OnlineChecker.Status.FINISHED){
            mTask = new OnlineChecker(local, remote, interval, iterations);
            mTask.execute();
        }
    }
    private void stopTask(){
        if (mTask != null){
            mTask.cancel(false);
        }
    }

    class OnlineChecker extends AsyncTask<Void, Boolean, Void>{


        int mLocalState = UNKNOWN;
        int mRemoteState = UNKNOWN;
        private int mInterval = 1000;
        private int mIterations = 2;
        String mLocalAddress = null;
        String mRemoteAddress = null;

        OnlineChecker(String localAddress, String remoteAddress, int interval, int iterations){
            mLocalAddress = localAddress;
            mRemoteAddress = remoteAddress;
            mInterval = interval;
            mIterations = iterations;
        }

        @Override
        protected void onProgressUpdate(Boolean... values) {
            callOnStateChangeListener(mLocalState, mRemoteState);
        }

        @Override
        protected Void doInBackground(Void... params) {
            long start = System.currentTimeMillis();

            while (true){

                int local = mLocalAddress == null ? UNKNOWN : ping(mLocalAddress);
                int remote = mRemoteAddress == null ? UNKNOWN : ping(mRemoteAddress);

                if (isCancelled()) break;


                mLocalState = local;
                mRemoteState = remote;
                publishProgress();


                try {
                    while (System.currentTimeMillis() < start + mInterval) {
                        Thread.sleep(50);
                        if (isCancelled()) break;
                    }
                } catch (InterruptedException ignored) { }

                start = System.currentTimeMillis();

                if (isCancelled()) break;

            }
            return null;
        }

        protected int ping(String address){
            Runtime runtime = Runtime.getRuntime();
            try {
                long s = System.currentTimeMillis();
                Process  mIpAddrProcess = runtime.exec(
                        String.format("/system/bin/ping -c %d -W %d %s", mIterations, Math.max(1, mInterval / mIterations / 1000), address));
                int mExitValue = mIpAddrProcess.waitFor();
                int ping = (int) (System.currentTimeMillis() - s);

                BufferedReader tmp = new BufferedReader(new InputStreamReader(mIpAddrProcess.getInputStream()));
                String output = "", line;
                while ((line = tmp.readLine()) != null) {
                    output += line + "\n";
                }
                tmp.close();
                Pattern p = Pattern.compile(".*time=([0-9\\.]+).*");

                Matcher m = p.matcher(output);
                float sum = 0;
                int count = 0;
                while (m.find()) {
                    sum += Float.valueOf(m.group(1));
                    count ++;
                }
                if (count > 0){
                    ping = Math.round(sum/count);
                }

                Log.d(getPackageName(), output);

                return mExitValue == 0 ? ping : OFFLINE;

            } catch (InterruptedException | IOException ignore) {
                return OFFLINE;
            }
        }
    }
}
