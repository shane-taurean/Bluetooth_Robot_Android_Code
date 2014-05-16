package com.example.experiment12_robotcontrols;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;


import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	Button btnFwd;
	Button btnLeft;
	Button btnRight;
	Button btnRev;
	Button btnStop;
	
	private static final String DEBUG_TAG = "Bluetooth";
	private static final int ENABLE_BLUETOOTH = 1;

	
	public static final int DATA_RECEIVED = 3;
	public static final int SOCKET_CONNECTED = 4;
	public static final int STATUS = 7;
	private static final int CONNECT = 2;
	
	private BluetoothAdapter btAdapter;
	private BtReceiver btReceiver;
	
	private BluetoothDevice remoteDevice = null;
	private BluetoothDevice btDevice = null;
	
	public static final UUID BT_APP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private static String bc04_address = "00:06:71:00:3e:aa";	// BC-04 address
	
    private ClientConnectThread clientConnectThread;
    private BluetoothDataCommThread bluetoothDataCommThread;
    
    private MediaPlayer player;
    
    TextView status;
    //TextView txtView1;
	Switch btToggle;
	Button pickDevice;
	TextView myBtDevName;
	
	boolean got_a_socket = false;
	
	char[] display_txt = new char[64];
	int d_index = 0;
	
	private HashMap<String, BluetoothDevice> discoveredDevices = new HashMap<String, BluetoothDevice>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		btnFwd = (Button)findViewById(R.id.bttnFwd); 
		btnLeft = (Button)findViewById(R.id.bttnLeft);
		btnRight = (Button)findViewById(R.id.bttnRight);
		btnRev =  (Button)findViewById(R.id.bttnRev);
		btnStop =  (Button)findViewById(R.id.bttnStop);
		
		
		btnFwd.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View arg0) {
				sendMsg("forward\n");				
			}
		});
		
		btnLeft.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View arg0) {
				sendMsg("left\n");				
			}
		});
		
		btnRight.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View arg0) {
				sendMsg("right\n");				
			}
		});
		
		btnRev.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View arg0) {
				sendMsg("reverse\n");				
			}
		});
		
		btnStop.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View arg0) {
				sendMsg("stop\n");				
			}
		});
		
		
		
		
		/*
		btnLeft.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// TODO Auto-generated method stub
				if ( event.getAction() == MotionEvent.ACTION_DOWN ) {
					if ( btnLeft.isPressed() && !btnRight.isPressed() ) {
						status.setText("Left button pressed");
					} else if ( btnLeft.isPressed() && btnRight.isPressed() ) {
						status.setText("STOP");
					}
				} else if ( event.getAction() == MotionEvent.ACTION_UP ) {
					
					status.setText("Left button released");
				}
				return false;
			}
		});
		*/
		
		btToggle = (Switch) findViewById(R.id.bt_toggle);
		pickDevice = (Button)findViewById(R.id.bt_pick_device);
		//txtView1 = (TextView)findViewById(R.id.txtView1);
		status = (TextView)findViewById(R.id.status);
		myBtDevName =  (TextView)findViewById(R.id.myBtDevName);
		
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if (btAdapter == null) {
            // no bluetooth available on device
			show_message("Couldn't detect bluetooth on your device"); 
            disable_UI();
        } else {
        	// bluetooth is available on the device
        	Log.v(DEBUG_TAG, "Bluetooth available on device");
     		
        	// change listener for toggle switch 
        	btToggle.setOnCheckedChangeListener( btToggleListner );
        	
        	btReceiver = new BtReceiver();
       		
        	if (btAdapter.isEnabled()) {
        		set_bt_name();
        	}
        	
        	regBroadcasts();	// set broadcast receivers
        	
            // check current state
            int currentState = btAdapter.getState();
            setUIForBTState(currentState);
        }
	}
	
	public void show_message(String msg) {
        Toast.makeText(getBaseContext(), msg,
                Toast.LENGTH_SHORT).show();
	}
	
	private void disable_UI(){
		Button button;
        int[] buttonIds = { R.id.bt_toggle, R.id.bt_pick_device, R.id.bttnFwd, R.id.bttnLeft,
        		R.id.bttnRight, R.id.bttnRev};
        for (int buttonId : buttonIds) {
            button = (Button) findViewById(buttonId);
            button.setEnabled(false);
        }        
	}
		
	public void regBroadcasts(){
        // register for state change broadcast events
        IntentFilter stateChangedFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        this.registerReceiver(btReceiver, stateChangedFilter);
        
        // register for local name changed events
        IntentFilter nameChangedFilter = new IntentFilter(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        this.registerReceiver(btReceiver, nameChangedFilter);
	}
	
	public void close_threads(){
        if (clientConnectThread != null) {
            clientConnectThread.stopConnecting();
        }
        
        if (bluetoothDataCommThread != null) {
            bluetoothDataCommThread.disconnect();
        }
        
        remoteDevice = null;
        clientConnectThread = null;
        bluetoothDataCommThread = null;
        got_a_socket = false;
        
        btAdapter.cancelDiscovery();
        
        this.unregisterReceiver(btReceiver);
        
        if (player != null) {
            player.stop();  
            player.release();
            player = null;
        }
        
	}
	
	public CompoundButton.OnCheckedChangeListener btToggleListner = new CompoundButton.OnCheckedChangeListener() {
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	        Log.v(DEBUG_TAG, "doToggleBT() called");
	    	if ( isChecked == false ) {
	    	   	Log.v(DEBUG_TAG, "Disabling bluetooth");
	    	   	
	    	   	pickDevice.setEnabled(false);
	    	   	
		    	close_threads();
		    	
	    	   	if ( btAdapter.isDiscovering() ){
	    	   		btAdapter.cancelDiscovery();
	    	   	}
	    	   	
		    	if (!btAdapter.disable()) {
		    	   	Log.v(DEBUG_TAG, "Disable adapter failed");
		    	}
		    	
		    	
	    	} else {	    		
	    		if ( !btAdapter.isEnabled()) {
	    		    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	    		    startActivityForResult(intent, ENABLE_BLUETOOTH);
	    		}
	    	}
	    }
	};
	

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == ENABLE_BLUETOOTH) {
			if (resultCode == RESULT_OK) {
				// Bluetooth has been enabled, initialize the UI.
				Log.v(DEBUG_TAG, "Bluetooth Enabled");
				
			}
			else if ( resultCode == RESULT_CANCELED ) {
	            btToggle.setChecked(false);
	            btToggle.setEnabled(true);
			}
			else {
				Log.v(DEBUG_TAG, "Enable Bluetooth adapter failed");
			}
		}
		else if (requestCode == CONNECT && resultCode == RESULT_OK) {
			btDevice = data.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			doConnectToDevice(btDevice);
			//remoteDevice = btDevice;
		}
    }
    
	
	private class BtReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(DEBUG_TAG, "Broadcast: Got some action");
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            	Log.v(DEBUG_TAG, "Broadcast: Got ACTION_STATE_CHANGED");
                int currentState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                setUIForBTState(currentState);
            }
            else if (action.equals(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)) {
            	Log.v(DEBUG_TAG, "Broadcast: Got ACTION_LOCAL_NAME_CHANGED");
            	set_bt_name();
            }
        }
    }
	
	public void pickDevice(View v){
		remoteDevice = null;
		discoveredDevices.clear();
		Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices) {
				discoveredDevices.put(device.getName(), device);
			}
		}
		
		Intent showDevicesIntent = new Intent(this, pickDevice.class);
		showDevicesIntent.putExtra("hashmapDevices", discoveredDevices);
		startActivityForResult(showDevicesIntent, CONNECT);
	}
	
	
	public void set_bt_name(){
    	String bt_Name = btAdapter.getName();
    	if ( bt_Name.length() > 20 ) {
    		bt_Name = (String) bt_Name.subSequence(0, 20) + "...";
    	}
    	myBtDevName.setText(bt_Name);
	}
	
	private void setUIForBTState(int state) {
        switch (state) {
	        case BluetoothAdapter.STATE_ON:
	            btToggle.setChecked(true);
	            btToggle.setEnabled(true);
            	set_bt_name();
            	btnFwd.setEnabled(true);
            	btnLeft.setEnabled(true);
            	btnRight.setEnabled(true);
            	btnRev.setEnabled(true);
            	btnStop.setEnabled(true);
            	pickDevice.setEnabled(true);
	            Log.v(DEBUG_TAG, "BT state now on");
	            break;
	        case BluetoothAdapter.STATE_OFF:
	            btToggle.setChecked(false);
	            btToggle.setEnabled(true);
	            pickDevice.setEnabled(false);	            
            	btnFwd.setEnabled(false);
            	btnLeft.setEnabled(false);
            	btnRight.setEnabled(false);
            	btnRev.setEnabled(false);
            	btnStop.setEnabled(false);
	            Log.v(DEBUG_TAG, "BT state now off");
	            break;
	        case BluetoothAdapter.STATE_TURNING_OFF:
	            Log.v(DEBUG_TAG, "BT state turning off");
	            break;
	        case BluetoothAdapter.STATE_TURNING_ON:
	            Log.v(DEBUG_TAG, "BT state turning on");
	            break;
        }
    }
	
	public void doConnectToDevice(BluetoothDevice device) {
        // halt the resource intensive bluetooth discovery
        btAdapter.cancelDiscovery();
        Log.v(DEBUG_TAG, "Starting connect thread");
        clientConnectThread = new ClientConnectThread(device, handler);
        clientConnectThread.start();
    }
	
	public Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case SOCKET_CONNECTED: {
				bluetoothDataCommThread = (BluetoothDataCommThread) msg.obj;
				got_a_socket = true;
				//bluetoothDataCommThread.send("this is a message");
				break;
			}
			case DATA_RECEIVED: {
				String data = ((String) msg.obj);
				
				if (data != null ){
					
					//
					for ( int i = 0; i < data.length(); i++ ) {
						char ch = data.charAt(i);
						display_txt[d_index++] = ch;
						if ( ch == '\n' ) {
							display_txt[--d_index] = '\0';
							String out = String.copyValueOf(display_txt);
							out.trim();
							Log.v(DEBUG_TAG, out);
							//txtView1.setText(out);
							
							d_index = 0;							
							
							// Clear display_txt array
							for ( int j = 0; j < 64; j++)
								display_txt[j] = '\0';
							
							play_audio(0);
							break;
						}
					}
				}
				else {
					Log.v(DEBUG_TAG, "DATA_RECEIVED: NULL");
				}
				
				break;
			}
			case STATUS: {
				status.setText((String)msg.obj);
				break;
			}
			default:
				break;
			}
		}
	};
	
	public void play_audio(int who){
		if (player != null) {
            player.release();
        }
        player = new MediaPlayer();
        try {
        	Log.v(DEBUG_TAG, "Play Audio");
        	if ( who == 1 ) {
        		player.setDataSource(getBaseContext(), Uri.parse("android.resource://com.example.experiment12_robotControls/"+R.raw.send));
        	} else {
        		player.setDataSource(getBaseContext(), Uri.parse("android.resource://com.example.experiment12_robotControls/"+R.raw.received));
        	}
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.e(DEBUG_TAG, "Failed to start audio", e);
        }
	}
	
	public void sendMsg(String myMsg){
		if ( got_a_socket ) {
			if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
			String deviceName = btAdapter.getName();
			if ( bluetoothDataCommThread != null && got_a_socket ) {
				bluetoothDataCommThread.send(myMsg);	///////////////////
				//<>?//
				play_audio(1);
			}
		} else {
			show_message("Message not sent.\nNot connected to a remote device");
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	public void onPause() {
		super.onPause();
		  
	    Log.d(DEBUG_TAG, "...In onPause()...");
	   
	    try     {
	    	//close_threads();
	    	remoteDevice = btDevice;
	    } catch (Exception e) {
            Log.e(DEBUG_TAG, "Failed to start audio", e);
	    }
	}
	
	 @Override
	  public void onResume() {
	    super.onResume();
	    
	    Log.v(DEBUG_TAG, "...onResume...");
	    /*
	    if (remoteDevice != null) {
	    	doConnectToDevice(remoteDevice);
	    }
	    */
	    
	 }
	 
	 @Override
	 protected void onDestroy() {
		 close_threads();
		 super.onDestroy();
	 }

	 
	 //////////////////////
	 
		
	
}
