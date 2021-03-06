/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.app.AlarmManager;
import android.app.StatusBarManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.telecom.TelecomManager;
import android.util.Log;
import java.io.File;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.CastController;
import com.android.systemui.statusbar.policy.CastController.CastDevice;

import java.io.*; // gxp18
import android.os.Environment; // gxp18

/**
 * This class contains all of the policy about which icons are installed in the status
 * bar at boot time.  It goes through the normal API for icons, even though it probably
 * strictly doesn't need to.
 */

public class PhoneStatusBarPolicy {
    private static final String TAG = "PhoneStatusBarPolicy";

	



    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final boolean SHOW_SYNC_ICON = false;

    private static final String SLOT_SYNC_ACTIVE = "sync_active";
    private static final String SLOT_CAST = "cast";
    private static final String SLOT_BLUETOOTH = "bluetooth";
    private static final String SLOT_TTY = "tty";
    private static final String SLOT_ZEN = "zen";
    private static final String SLOT_VOLUME = "volume";
    private static final String SLOT_CDMA_ERI = "cdma_eri";
    private static final String SLOT_ALARM_CLOCK = "alarm_clock";
	//* gxp18 
	 private static final String SLOT_MIKE = "mike";
	/**/

    private final Context mContext;  //gxp18
    private final StatusBarManager mService;
    private final Handler mHandler = new Handler();
    private final CastController mCast;

    // Assume it's all good unless we hear otherwise.  We don't always seem
    // to get broadcasts that it *is* there.
    IccCardConstants.State mSimState = IccCardConstants.State.READY;

    private boolean mZenVisible;
    private boolean mVolumeVisible;

    private int mZen;

    private boolean mBluetoothEnabled = false;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { // Method not called when Broadcast is sent from AudioSystem
	    
            Log.d("gxp18", "PhoneStatusBarPolicy.java received Intent "); 
            String action = intent.getAction();


            // gxp18 - This run in an app process u0_a13    1492  197   1603080 105952 ffffffff 00000000 S com.android.systemui


            if (action.equals(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)) {
                updateAlarm();
            }
            else if (action.equals(Intent.ACTION_SYNC_STATE_CHANGED)) {
                updateSyncState(intent);
            }
            else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED) ||
                    action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                updateBluetooth();
            }
            else if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                updateVolumeZen();
            }
            else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                updateSimState(intent);
            }
            else if (action.equals(TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED)) {
                updateTTY(intent);
            }
            else if (action.equals(Intent.ACTION_USER_SWITCHED)) {
                updateAlarm();
            }
	    //* gxp18 
	    else if (action.equals(Intent.ACTION_MIKE_ACCESSED)) {

                enableMike();
            }
	    else if (action.equals(Intent.ACTION_MIKE_RELEASED)) {
                disableMike();
            } /**/
        }
    };


    public PhoneStatusBarPolicy(Context context, CastController cast) {
        mContext = context;
        mCast = cast;
        mService = (StatusBarManager)context.getSystemService(Context.STATUS_BAR_SERVICE);

        // listen for broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        filter.addAction(Intent.ACTION_SYNC_STATE_CHANGED);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
	//* gxp18 
	filter.addAction(Intent.ACTION_MIKE_ACCESSED);
	filter.addAction(Intent.ACTION_MIKE_RELEASED);

	/**/
        mContext.registerReceiver(mIntentReceiver, filter, null, mHandler);

	//* gxp18
 	mService.setIcon(SLOT_TTY,  R.drawable.mike, 0, null); //Change the ICON LATER
        mService.setIconVisibility(SLOT_TTY, false); // Chagne it to be SLOT_MIKE to the Service

	//Thread t = new PeriodicChecker();
      	//t.start();	

	/**/

        // TTY status
        //mService.setIcon(SLOT_TTY,  R.drawable.stat_sys_tty_mode, 0, null);
        mService.setIcon(SLOT_TTY,  R.drawable.mike, 0, null);
        mService.setIconVisibility(SLOT_TTY, false);

        // Cdma Roaming Indicator, ERI
        mService.setIcon(SLOT_CDMA_ERI, R.drawable.stat_sys_roaming_cdma_0, 0, null);
        mService.setIconVisibility(SLOT_CDMA_ERI, false);

        // bluetooth status
        updateBluetooth();

        // Alarm clock
        mService.setIcon(SLOT_ALARM_CLOCK, R.drawable.stat_sys_alarm, 0, null);
        mService.setIconVisibility(SLOT_ALARM_CLOCK, false);

        // Sync state
        mService.setIcon(SLOT_SYNC_ACTIVE, R.drawable.stat_sys_sync, 0, null);
        mService.setIconVisibility(SLOT_SYNC_ACTIVE, false);
        // "sync_failing" is obsolete: b/1297963

        // zen
        mService.setIcon(SLOT_ZEN, R.drawable.stat_sys_zen_important, 0, null);
        mService.setIconVisibility(SLOT_ZEN, false);

        // volume
        mService.setIcon(SLOT_VOLUME, R.drawable.stat_sys_ringer_vibrate, 0, null);
        mService.setIconVisibility(SLOT_VOLUME, false);
        updateVolumeZen();

        // cast
        mService.setIcon(SLOT_CAST, R.drawable.stat_sys_cast, 0, null);
        mService.setIconVisibility(SLOT_CAST, false);
        mCast.addCallback(mCastCallback);

	
    }

    public void setZenMode(int zen) {
        mZen = zen;
        updateVolumeZen();
    }

    //* gxp18 - Methods called through Intents
    private void enableMike(){
	mService.setIconVisibility(SLOT_TTY, true);	
    }

    private void disableMike(){
	mService.setIconVisibility(SLOT_TTY, false);	
    }

    private void updateAlarm() {
        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
	boolean alarmSet = alarmManager.getNextAlarmClock(UserHandle.USER_CURRENT) != null;
        mService.setIconVisibility(SLOT_ALARM_CLOCK, alarmSet);
    }

    private final void updateSyncState(Intent intent) {
        if (!SHOW_SYNC_ICON) return;
        boolean isActive = intent.getBooleanExtra("active", false);
        mService.setIconVisibility(SLOT_SYNC_ACTIVE, isActive);
    }

    private final void updateSimState(Intent intent) {
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            mSimState = IccCardConstants.State.ABSENT;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR.equals(stateExtra)) {
            mSimState = IccCardConstants.State.CARD_IO_ERROR;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
            mSimState = IccCardConstants.State.READY;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason =
                    intent.getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
            if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PIN_REQUIRED;
            }
            else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PUK_REQUIRED;
            }
            else {
                mSimState = IccCardConstants.State.NETWORK_LOCKED;
            }
        } else {
            mSimState = IccCardConstants.State.UNKNOWN;
        }
    }

    private final void updateVolumeZen() {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        boolean zenVisible = false;
        int zenIconId = 0;
        String zenDescription = null;

        boolean volumeVisible = false;
        int volumeIconId = 0;
        String volumeDescription = null;

        if (mZen == Global.ZEN_MODE_NO_INTERRUPTIONS) {
            zenVisible = true;
            zenIconId = R.drawable.stat_sys_zen_none;
            zenDescription = mContext.getString(R.string.zen_no_interruptions);
        } else if (mZen == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS) {
            zenVisible = true;
            zenIconId = R.drawable.stat_sys_zen_important;
            zenDescription = mContext.getString(R.string.zen_important_interruptions);
        }

        if (mZen != Global.ZEN_MODE_NO_INTERRUPTIONS &&
                audioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
            volumeVisible = true;
            volumeIconId = R.drawable.stat_sys_ringer_vibrate;
            volumeDescription = mContext.getString(R.string.accessibility_ringer_vibrate);
        }

        if (zenVisible) {
            mService.setIcon(SLOT_ZEN, zenIconId, 0, zenDescription);
        }
        if (zenVisible != mZenVisible) {
            mService.setIconVisibility(SLOT_ZEN, zenVisible);
            mZenVisible = zenVisible;
        }

        if (volumeVisible) {
            mService.setIcon(SLOT_VOLUME, volumeIconId, 0, volumeDescription);
        }
        if (volumeVisible != mVolumeVisible) {
            mService.setIconVisibility(SLOT_VOLUME, volumeVisible);
            mVolumeVisible = volumeVisible;
        }
    }

    private final void updateBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        int iconId = R.drawable.stat_sys_data_bluetooth;
        String contentDescription =
                mContext.getString(R.string.accessibility_bluetooth_disconnected);
        if (adapter != null) {
            mBluetoothEnabled = (adapter.getState() == BluetoothAdapter.STATE_ON);
            if (adapter.getConnectionState() == BluetoothAdapter.STATE_CONNECTED) {
                iconId = R.drawable.stat_sys_data_bluetooth_connected;
                contentDescription = mContext.getString(R.string.accessibility_bluetooth_connected);
            }
        } else {
            mBluetoothEnabled = false;
        }

        mService.setIcon(SLOT_BLUETOOTH, iconId, 0, contentDescription);
        mService.setIconVisibility(SLOT_BLUETOOTH, mBluetoothEnabled);
    }

    private final void updateTTY(Intent intent) {
        int currentTtyMode = intent.getIntExtra(TelecomManager.EXTRA_CURRENT_TTY_MODE,
                TelecomManager.TTY_MODE_OFF);
        boolean enabled = currentTtyMode != TelecomManager.TTY_MODE_OFF;

        if (DEBUG) Log.v(TAG, "updateTTY: enabled: " + enabled);

        if (enabled) {
            // TTY is on
            if (DEBUG) Log.v(TAG, "updateTTY: set TTY on");
            mService.setIcon(SLOT_TTY, R.drawable.stat_sys_tty_mode, 0,
                    mContext.getString(R.string.accessibility_tty_enabled));
            mService.setIconVisibility(SLOT_TTY, true);
        } else {
            // TTY is off
            if (DEBUG) Log.v(TAG, "updateTTY: set TTY off");
            mService.setIconVisibility(SLOT_TTY, false);
        }
    }

    private void updateCast() {
        boolean isCasting = false;
        for (CastDevice device : mCast.getCastDevices()) {
            if (device.state == CastDevice.STATE_CONNECTING
                    || device.state == CastDevice.STATE_CONNECTED) {
                isCasting = true;
                break;
            }
        }
        if (DEBUG) Log.v(TAG, "updateCast: isCasting: " + isCasting);
        if (isCasting) {
            mService.setIcon(SLOT_CAST, R.drawable.stat_sys_cast, 0,
                    mContext.getString(R.string.accessibility_casting));
        }
        mService.setIconVisibility(SLOT_CAST, isCasting);
    }

    private final CastController.Callback mCastCallback = new CastController.Callback() {
        @Override
        public void onCastDevicesChanged() {
            updateCast();
        }
    };
}
