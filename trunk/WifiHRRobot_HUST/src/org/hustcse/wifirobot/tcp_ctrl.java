package org.hustcse.wifirobot;

import java.net.InetAddress;
import java.net.UnknownHostException;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class tcp_ctrl {
	final static boolean D = true;
	final static int SERVER = 1;
	final static int CLIENT  = 2;
	private static String TAG = "TCP_CTRL";
	final static int MSG_WHAT = 1;

	ctrl_frame	tcp_server_frame;
	ctrl_frame	tcp_client_frame;

	tcp_ctrl_server mTcp_ctrl_server;
	tcp_ctrl_client mTcp_ctrl_client;
	
	Handler 		mHandler; 
	Context 		mContext;

	
	public static int CLIENT_PORT = 1234;
	public static  String CLIENT_IP = "192.168.0.200"; // 'Within' the emulator!
//	private static String CLINET_TAG = "tcp_thread_client";

	public static int SERVER_PORT = 12344;
	public static  String SERVER_IP = "127.0.0.1"; // 'Within' the emulator!
//	private static String SERVER_TAG = "tcp_thread_server";

	InetAddress 	myBcastIPAddress; 		// my broadcast IP addresses
	InetAddress 	myIPAddress; 			// my IP addresses

	public tcp_ctrl(Context currentContext, Handler handler) {
		mHandler = handler;
		mContext = currentContext;
		
		try{
			/*获取Wifi信息并且获取当前的ip地址*/
			if (getMyWiFiBcastAndIPAddress()){
				SERVER_IP = myIPAddress.getHostAddress();
			}
		}catch (Exception e) {
			disp_toast("Wifi Maybe Not Opened");
		}
		
		/*mTcp_ctrl_server = new tcp_ctrl_server(currentContext, mHandler_Server, SERVER_IP, SERVER_PORT);
		if (!mTcp_ctrl_server.isSocketOK()){
			Log.e(TAG,"TCP Server NOT STARTED");
			//disp_toast("Cannot Start TCP Server Server");
     	   	return;
		}*/
		mTcp_ctrl_client = new tcp_ctrl_client(currentContext, mHandler_Client, CLIENT_IP, CLIENT_PORT);
		if (!mTcp_ctrl_client.isSocketOK()){
			Log.e(TAG,"TCP Client NOT STARTED");
			//disp_toast("Cannot Start TCP Client Server");
     	   	return;
		}
		
 		//mTcp_ctrl_server.start();
		mTcp_ctrl_client.start();
	}
	
	private void disp_toast(String msg){
		Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
	}
	
	private final Handler mHandler_Server = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if(D) Log.d(TAG, "Message Received From TCP server:" + 
					ctrl_prefixs.bytes2hexformatstring(((byte[])msg.obj)) );
			handle_server_msg( ((byte[])msg.obj));
		}
	};
	
	private final Handler mHandler_Client = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if(D) Log.d(TAG, "Message Received From TCP Client:" + 
					ctrl_prefixs.bytes2hexformatstring(((byte[])msg.obj)) );
			//handle_server_msg( ((byte[])msg.obj));
		}
	};
	
	private void handle_server_msg(byte[] frame){
		if (frame == null){
			if(D) Log.d(TAG, "No Byte Receved!");
			return ;
		}

		if (frame.length == 0){
			if(D) Log.d(TAG, "No Byte Receved!");
			return ;
		}
		
		ctrl_frame mTcp_server_ctrl_frame = new ctrl_frame();
		if (mTcp_server_ctrl_frame.decode_framefrombytes(frame)){ /*判断是否构造帧成功*/
			handle_all_server_msg(mTcp_server_ctrl_frame);
		}

	}

	private void handle_all_server_msg(ctrl_frame mCtrl_frame){
		switch (mCtrl_frame.ctrl_prefix_operate) {
			case ctrl_prefixs.write_request:
				handle_server_write_msg(mCtrl_frame);
				break;
			case ctrl_prefixs.read_request:
				handle_server_read_msg(mCtrl_frame);
				break;
			default:
				break;
		}
	}
	
	private void handle_server_read_msg(ctrl_frame mCtrl_frame) {
		switch (mCtrl_frame.ctrl_prefix_request) {
			case ctrl_prefixs.less_data_request:
				handle_server_read_less_msg(mCtrl_frame);
				break;
			case ctrl_prefixs.mass_data_request:
				handle_server_read_mass_msg(mCtrl_frame);
				break;
			default:
				break;
		}
	}
	
	private void handle_server_write_msg(ctrl_frame mCtrl_frame){
		switch ( mCtrl_frame.ctrl_cmd ){
			case ctrlcmds.SPEED_UP:
				handle_SPEED_UP_msg(mCtrl_frame);
				break;
			case ctrlcmds.SPEED_DOWN:
				break;
			case ctrlcmds.TURN_LEFT:
				break;
			case ctrlcmds.TURN_RIGHT:
				break;
			case ctrlcmds.TURN_DIRECT:
				break;
			case ctrlcmds.START:
				break;
			case ctrlcmds.STOP:
				break;
			case ctrlcmds.ACQUIRE_CAMERA_IMAGE:
				break;
			case ctrlcmds.ACQUIRE_CAMERA_VIDEO_START:
				break;
			default:
				break;
		}
	}
	
	private void handle_server_read_less_msg(ctrl_frame mCtrl_frame){
		switch ( mCtrl_frame.ctrl_cmd ){
			case ctrlcmds.ACQUIRE_ROBOT_INFO:
				handle_ACQUIRE_ROBOT_INFO_msg(mCtrl_frame);
				break;
			default:
				break;
		}
	}
	
	private void handle_server_read_mass_msg(ctrl_frame mCtrl_frame){
		mHandler.obtainMessage(MSG_WHAT, mCtrl_frame.ctrl_cmd).sendToTarget();
	}
	
	
	private void handle_ACQUIRE_ROBOT_INFO_msg(ctrl_frame mCtrl_frame){
		ctrl_frame mCtrl_frame_ack;
		short mCtrl_prefix_ack;
		byte[] mTcp_ack;
		byte[] robotinfo = new byte[]{1,2,3,4};

		mCtrl_prefix_ack = ctrl_prefixs.encode_ctrlprefix(ctrl_prefixs.read_request, ctrl_prefixs.less_data_request,ctrl_prefixs.withack);
		mCtrl_frame_ack = new ctrl_frame(mCtrl_prefix_ack, ctrlcmds.ACQUIRE_ROBOT_INFO, robotinfo);
		mTcp_ack = new byte[mCtrl_frame_ack.datalength + 4];
		mCtrl_frame_ack.encode_frametobytes(mTcp_ack);
		mTcp_ctrl_server.post_msg(mTcp_ack);
		if(D) {
			Log.d(TAG, "ACQUIRE_ROBOT_INFO Cmd Ack Frame is As Follows:");
			mCtrl_frame_ack.display_ctrl_frame();
			Log.d(TAG, "ACQUIRE_ROBOT_INFO Command Received");
		}
	}
	
	private void handle_SPEED_UP_msg(ctrl_frame mCtrl_frame){
		//ctrl_frame mCtrl_frame_ack;
		//short mCtrl_prefix_ack;
		//byte[] mTcp_ack;

		//mCtrl_prefix_ack = ctrl_prefixs.encode_ctrlprefix(ctrl_prefixs.write_request, ctrl_prefixs.less_data_request);
		//mCtrl_frame_ack = new ctrl_frame(mCtrl_prefix_ack, ctrlcmds.SPEED_UP, null);
		//mTcp_ack = new byte[mCtrl_frame_ack.datalength + 4];
		//mCtrl_frame_ack.encode_frametobytes(mTcp_ack);
		//mTcp_ctrl_server.post_msg(mTcp_ack);
		if(D) {
			//Log.d(TAG, "Speed UP Cmd Ack Frame is As Follows:");
			//mCtrl_frame_ack.display_ctrl_frame();
			Log.d(TAG, "Speed UP Command Received");
		}
	}
	
	
	private boolean getMyWiFiBcastAndIPAddress() throws UnknownHostException{

        WifiManager mWifi = (WifiManager) (mContext.getSystemService(Context.WIFI_SERVICE));
        WifiInfo info = mWifi.getConnectionInfo();
        if(info==null){
            if(D) Log.e(TAG,"Cannot Get WiFi Info");
            return false;
        }
        else{
        	if(D) Log.d(TAG,"\n\nWiFi Status: " + info.toString());
        }
		  
  	  // DhcpInfo  is a simple object for retrieving the results of a DHCP request
        DhcpInfo dhcp = mWifi.getDhcpInfo(); 
        if (dhcp == null) { 
          Log.d(TAG, "Could not get dhcp info"); 
          return false; 
        } 

        int myIntegerIPAddress = dhcp.ipAddress;

        byte[] quads = new byte[4]; 
        for (int k = 0; k < 4; k++) 
           quads[k] = (byte) ((myIntegerIPAddress>> k * 8) & 0xFF);

        myIPAddress = InetAddress.getByAddress(quads);

        
        int myIntBroadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask; 
        for (int k = 0; k < 4; k++) 
          quads[k] = (byte) ((myIntBroadcast >> k * 8) & 0xFF);
        
        // Returns the InetAddress corresponding to the array of bytes. 
        myBcastIPAddress=InetAddress.getByAddress(quads); 
        return true;
    	
    }
}
