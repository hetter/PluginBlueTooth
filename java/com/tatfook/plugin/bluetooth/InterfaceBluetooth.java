/****************************************************************************

****************************************************************************/
package plugin.Bluetooth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;


import plugin.Bluetooth.BluetoothLeService;
import com.tatfook.paracraft.ParaEngineWebView;
import com.tatfook.paracraft.AppActivity;
import com.tatfook.paracraft.ParaEngineLuaJavaBridge;
import com.tatfook.paracraft.LuaFunction;
import com.tatfook.paracraft.ParaEnginePluginInterface;
import com.tatfook.paracraft.ParaEnginePluginWrapper.PluginWrapperListener;

public class InterfaceBluetooth implements ParaEnginePluginInterface{
		public final static String  LogTag = "ParaEngine";

		// java call lua enum
		public final static int CHECK_DEVICE = 1101;
		public final static int SET_BLUE_STATUS = 1102;
		public final static int READ_BLUE_UUID = 1103;
		public final static int ON_CHARACTERISTIC = 1104;
		public final static int ON_DESCRIPTOR = 1105;

	 	private BluetoothAdapter mBluetoothAdapter;

	    private boolean mScanning = false;
	    private Handler mHandler;
	    private String mDeviceAddress;
		
		public static AppActivity mMainActivity;
	    
	    private static final int REQUEST_ENABLE_BT = 1;

	    private static final long SCAN_PERIOD = 3000;

	    private BluetoothLeService mBluetoothLeService;

	    private boolean mConnected = false;

		private static InterfaceBluetooth mSingle = null;

		HashMap<String, BluetoothGattService> mGattServicesMap;

		private static LuaFunction mLuaFunction = null;

		public InterfaceBluetooth()
		{
			
		}
	    
	    private final ServiceConnection mServiceConnection = new ServiceConnection() {

	        @Override
	        public void onServiceConnected(ComponentName componentName, IBinder service) {
				Log.e(LogTag, "AppActivity: onServiceConnectedonServiceConnectedonServiceConnectedonServiceConnected");
	            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
	            if (!mBluetoothLeService.initialize()) {
	                Log.e("appactivity", "Unable to initialize Bluetooth");
	                mMainActivity.finish();
	            }
	            // Automatically connects to the device upon successful start-up initialization.
	            mBluetoothLeService.connect(mDeviceAddress);
	        }

	        @Override
	        public void onServiceDisconnected(ComponentName componentName) {
	            mBluetoothLeService = null;
	        }
	    };
	    
	    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
	        @Override
	        public void onReceive(Context context, Intent intent) {
	            final String action = intent.getAction();
	            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) 
				{
					Log.e(LogTag, "!-------AppActivity: blue :connect");
	            	mConnected = true;
					callBaseBridge(SET_BLUE_STATUS, "1");
	            } 
				else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) 
				{
					Log.e(LogTag, "!-------AppActivity: blue :disconnect");
	            	mConnected = false;
					callBaseBridge(SET_BLUE_STATUS, "0");
					searchBlueDevice();    		
	            } 
				else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) 
				{
	            	enableTXNotification(mBluetoothLeService.getSupportedGattServices());
	            	Log.e(LogTag, "!-------AppActivity: blue start:find serverice");    		
	            } 
				else if(BluetoothLeService.ACTION_DATA_CHARACTERISTIC.equals(action))
	            {
	            	String uuid = intent.getStringExtra(BluetoothLeService.ON_CHARACTERISTIC_UUID);
					String io = intent.getStringExtra(BluetoothLeService.ON_CHARACTERISTIC_IO);
					String status = intent.getStringExtra(BluetoothLeService.ON_CHARACTERISTIC_STATUS);
					
					JSONObject luajs_value = new JSONObject();
					try
					{
						luajs_value.put("uuid", uuid);
						luajs_value.put("io", io);
						luajs_value.put("status", status);
					}
					catch(JSONException e) 
					{
						e.printStackTrace();
					}
					Log.e(LogTag, "!-------ACTION_DATA_CHARACTERISTIC");  
					callBaseBridge(ON_CHARACTERISTIC, luajs_value.toString()); 	
	            }
				else if (BluetoothLeService.ACTION_DATA_DESCRIPTOR.equals(action)) 
				{
	            	String uuid = intent.getStringExtra(BluetoothLeService.ON_DESCRIPTOR_UUID);
					String io = intent.getStringExtra(BluetoothLeService.ON_DESCRIPTOR_IO);
					String status = intent.getStringExtra(BluetoothLeService.ON_DESCRIPTOR_STATUS);
					
					JSONObject luajs_value = new JSONObject();
					try
					{
						luajs_value.put("uuid", uuid);
						luajs_value.put("io", io);
						luajs_value.put("status", status);
					}
					catch(JSONException e) 
					{
						e.printStackTrace();
					}
					callBaseBridge(ON_DESCRIPTOR, luajs_value.toString()); 
	            }
	        }
	    };


	public boolean onCreate(Context cxt, Bundle savedInstanceState, PluginWrapperListener listener)
	{
		mMainActivity = (AppActivity) cxt;

    	mHandler = new Handler();
    	if (!mMainActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
    		return false;
    	}
    	getMBluetoothAdapter();

		mSingle = this;

		return false;
	}


	public void onStart(){}
	public void onStop(){}
	public void onAppBackground(){}
	public void onAppForeground(){}

	public void onActivityResult(int requestCode, int resultCode, Intent data){}

	public void setDebugMode(boolean debug){}
	public void onSaveInstanceState(Bundle outState){}



	public void onInit(Map<String, Object> cpInfo, boolean bDebug)
	{
		 Log.e(LogTag, "onInit:");
	}

	public static void registerLuaCall(int luaFunc)
	{
		mLuaFunction = new LuaFunction(luaFunc);
	}

	private static BluetoothGattCharacteristic getCharacteristic(String ser_uuid, String cha_uuid)
	{
		BluetoothGattCharacteristic retcha = null;
		BluetoothGattService gattService = mSingle.mGattServicesMap.get(ser_uuid);
		if(gattService != null)
		{
			UUID cha_uuid_ = UUID.fromString(cha_uuid);
			retcha = gattService.getCharacteristic(cha_uuid_);
		}
		return retcha;
	}

	private static BluetoothGattDescriptor getDescriptor(String ser_uuid, String cha_uuid, String desc_uuid)
	{
		BluetoothGattCharacteristic retcha = getCharacteristic(ser_uuid, cha_uuid);

		if(retcha != null)
		{
			UUID desc_uuid_ = UUID.fromString(desc_uuid);
			return retcha.getDescriptor(desc_uuid_);
		}
		return null;
	}

	public static void connectDevice(String deviceAddr)
	{
        mSingle.mDeviceAddress = deviceAddr;
        if (mSingle.mBluetoothLeService != null) {
			final boolean result = mSingle.mBluetoothLeService.connect(mSingle.mDeviceAddress);			
			Log.d(LogTag, "AppActivity: link bluetooth Connect request result=" + result);
			if(result)
				mSingle._stopScanLeDevice();
		}
	}



	public static void writeToCharacteristic(String ser_uuid, String cha_uuid, String wdata_str)
	{
		Log.e(LogTag, "writeToCharacteristic: writeToCharacteristic" + ser_uuid);
		BluetoothGattCharacteristic wcharacteristic = getCharacteristic(ser_uuid, cha_uuid);
		if(wcharacteristic != null)
		{
			byte[] wdata = HexString2Bytes(wdata_str);
			
			wdata = new byte[]{(byte)0xAD,(byte)0x02};
    		wcharacteristic.setValue(wdata); 
			wcharacteristic.setWriteType(2);  		
    		mSingle.mBluetoothLeService.writeCharacteristic(wcharacteristic);
		}
		else
			Log.e(LogTag, "writeToCharacteristic: wcharacteristic null null null");
	}


	public static byte[] HexString2Bytes(String str) {
		if(str == null || str.trim().equals("")) {
			return new byte[0];
		}

		byte[] bytes = new byte[str.length() / 2];
		for(int i = 0; i < str.length() / 2; i++) {
			String subStr = str.substring(i * 2, i * 2 + 2);
			bytes[i] = (byte) Integer.parseInt(subStr, 16);
		}

		return bytes;
	}

	public static String Bytes2HexString(byte[] data)
	{
        String currDataStr = "";
        final StringBuilder stringBuilder = new StringBuilder(data.length);
        for(int i=0;i<data.length;i++)
        {
            byte byteChar = data[i];
            String str = String.format("%02X ", byteChar);
            stringBuilder.append(str);
            str = str.trim(); 
            currDataStr  += str;
        }
		return currDataStr;
	}

	public static String characteristicGetStrValue(String ser_uuid, String cha_uuid)
	{
		BluetoothGattCharacteristic characteristic = getCharacteristic(ser_uuid, cha_uuid);
		if(characteristic != null)
		{
			final byte[] data = characteristic.getValue();

			Log.e(LogTag, "characteristicGetStrValue length"+data.length);

			String currDataStr = Bytes2HexString(data);
			Log.e(LogTag, "characteristicGetStrValue currDataStr:" + currDataStr);

			JSONObject lua_js = new JSONObject();
			try
			{
				lua_js.put("data", currDataStr);
				lua_js.put("len", data.length);
			}
			catch(JSONException e) 
			{
				e.printStackTrace();
			}

			return lua_js.toString();
		}
		return null;
	}

	public static int characteristicGetIntValue(String ser_uuid, String cha_uuid, String wdata_str)
	{
		BluetoothGattCharacteristic characteristic = getCharacteristic(ser_uuid, cha_uuid);
		if(characteristic != null)
		{
        	int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
            }
            final int value = characteristic.getIntValue(format, 1);
            return value;
		}
		return 0;
	}

	public static void readCharacteristic(String ser_uuid, String cha_uuid)
	{
		BluetoothGattCharacteristic rcharacteristic = getCharacteristic(ser_uuid, cha_uuid);

		if(rcharacteristic != null)
		{
			mSingle.mBluetoothLeService.readCharacteristic(rcharacteristic);
		}        
		else
			Log.e(LogTag, "rcharacteristic null null null-----------" + ser_uuid + "," + cha_uuid);
	}

	public static void setCharacteristicNotification(String ser_uuid, String cha_uuid, boolean isNotify)
	{
		BluetoothGattCharacteristic characteristic = getCharacteristic(ser_uuid, cha_uuid);
		if(characteristic != null)
		{
			Log.e(LogTag, "setCharacteristicNotification-----------:" + ser_uuid + "," + cha_uuid + "," + isNotify);
			mSingle.mBluetoothLeService.setCharacteristicNotification(characteristic, isNotify);
		}
	}

	public static void setDescriptorNotification(String ser_uuid, String cha_uuid, String desc_uuid)
	{
		BluetoothGattCharacteristic characteristic = getCharacteristic(ser_uuid, cha_uuid);
		if(characteristic != null)
		{
			mSingle.mBluetoothLeService.setCharacteristicDescriptor(characteristic, UUID.fromString(desc_uuid));
		}
	}

	public static boolean isBlueConnected()
	{
		return mSingle.mConnected;
	}

	private static void callBaseBridge(int pId, String extData)
	{
		final String mergeData = pId + "_" + extData;
		final LuaFunction luaFunction = mLuaFunction;
		if(luaFunction != null)
		{
			mMainActivity.runOnGLThread(new Runnable() {
                        @Override
                        public void run() {
                            //ParaEngineLuaJavaBridge.callLuaFunctionWithString(callF, mergeData);
							luaFunction.callWithString(mergeData);
                        }
                    });
			
		}
	}

    private void enableTXNotification(List<BluetoothGattService> gattServices) 
	{
        if (gattServices == null) 
			return;

		mGattServicesMap = new HashMap<String, BluetoothGattService>();

		HashMap<String, String> luaTableMap = new HashMap<String, String>();

        String uuid = null;

		JSONObject luajs_value = new JSONObject();

		try 
		{
			for (BluetoothGattService gattService : gattServices) 
			{
				uuid = gattService.getUuid().toString();
				Log.e(LogTag, "charas-gattService-uuid:" + uuid);
				mGattServicesMap.put(uuid, gattService);

				JSONObject serviceChild_js = new JSONObject();

				List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
				for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics)
				{
					String uuid_cha = gattCharacteristic.getUuid().toString();
					Log.e(LogTag, "charas-gattCharacteristic-uuid:" + uuid_cha);

					luaTableMap.put(uuid_cha, uuid);

					List<BluetoothGattDescriptor> gattDescriptors = gattCharacteristic.getDescriptors();

					JSONObject characteristicChild_js = new JSONObject();
					for (BluetoothGattDescriptor gattDescriptor : gattDescriptors)
					{
						String uuid_desc = gattDescriptor.getUuid().toString();
						characteristicChild_js.put(uuid_desc, "");
					}
					serviceChild_js.put(uuid_cha, characteristicChild_js);
				}
				luajs_value.put(uuid, serviceChild_js);
			}
		}
		catch(JSONException e) 
		{
			e.printStackTrace();
		}

		callBaseBridge(READ_BLUE_UUID, luajs_value.toString());
    }

	private void _stopScanLeDevice()
	{
		if(mScanning)
		{
			mScanning = false;
			if(mBluetoothAdapter!=null)
			{
				mBluetoothAdapter.stopLeScan(mLeScanCallback);
				Log.e(LogTag, "-------------stop scanning");
			}
		}		
	}

	private void _startScanLeDevice()
	{
		if(!mScanning && !mConnected)
		{
			mScanning = true;
			if(mBluetoothAdapter!=null)
			{
				mBluetoothAdapter.startLeScan(mLeScanCallback);
				Log.e(LogTag, "-------------start scanning");
			}
		}
	}
    
	private void scanLeDevice(final boolean enable) {
		getMBluetoothAdapter();
        if (enable) {
			_startScanLeDevice();
			mHandler.postDelayed(new Runnable() {
						@Override
						public void run(){ 
							if(mConnected == false)
							{
								_stopScanLeDevice();
								scanLeDevice(true);
							}
						}
			}, SCAN_PERIOD);
        } else {
            _stopScanLeDevice();
        }
    }
	
	public void searchBlueDevice()
	{
		if(mConnected)
			return;

		//bluetooth permissions
		if (!mBluetoothAdapter.isEnabled()) {
	        if (!mBluetoothAdapter.isEnabled()) {
	            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	            mMainActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
	        }
	    }
	    scanLeDevice(true);
	}

	public void onPause() {
		scanLeDevice(false);
	}

	public void onResume() {
		if(!mConnected)
		{
			searchBlueDevice();
		    Log.e(LogTag, "onResume2");
		    mMainActivity.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
			if (mBluetoothLeService != null) {
			    if(mDeviceAddress!=null)
			    {
			    	final boolean result = mBluetoothLeService.connect(mDeviceAddress);
					Log.d("appactivity:", "Connect request result=" + result);
			    }
			}
		}
	}
	

	public void onDestroy() {
		Log.e(LogTag, "appactivity-onDestroy");

		if(mBluetoothLeService!=null)
		{
			mBluetoothLeService.disconnect();
		}
		scanLeDevice(false);
		mMainActivity.unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

			if(device!=null&&device.getName()!=null)
			{
				final int checkrssi = rssi;
        		mMainActivity.runOnUiThread(new Runnable() {
					@Override
					public void run() {

						if (!mScanning) {
							return;
						}

						if(mConnected){
							return;
						}


                		Log.e(LogTag, "AppActivity: now bluetooth device:" + device.getName()+"//"+device.getAddress() + "//" + checkrssi);

						JSONObject luajs_value = new JSONObject();
						try
						{
							luajs_value.put("name", device.getName());
							luajs_value.put("addr", device.getAddress());
							luajs_value.put("rssi", checkrssi);
						}
						catch(JSONException e) 
						{
							e.printStackTrace();
						}
						callBaseBridge(CHECK_DEVICE, luajs_value.toString());
					}
				});
			}
        }
    };
    
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_CHARACTERISTIC);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_DESCRIPTOR);
        return intentFilter;
    }
    
    private void getMBluetoothAdapter()
    {
    	if(mBluetoothAdapter==null)
		{
			final BluetoothManager bluetoothManager =
		    		(BluetoothManager) mMainActivity.getSystemService(Context.BLUETOOTH_SERVICE);
		    mBluetoothAdapter = bluetoothManager.getAdapter();
		    if(mBluetoothAdapter!=null)
		    {
		    	Intent gattServiceIntent = new Intent(mMainActivity, BluetoothLeService.class);

				Log.e(LogTag, "AppActivity: bindServicebindServicebindServicebindServicebindService");
		    	mMainActivity.bindService(gattServiceIntent, mServiceConnection, mMainActivity.BIND_AUTO_CREATE);
		    }
		}
    }
    
	
}