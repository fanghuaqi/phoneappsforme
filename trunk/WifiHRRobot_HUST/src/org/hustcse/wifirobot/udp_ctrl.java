package org.hustcse.wifirobot;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

public class udp_ctrl {
	final static boolean D = true;
	final static int SERVER = 1;
	final static int CLIENT  = 2;
	private static String TAG = "UDP_CTRL";
	private static int small_frame_datalength = 800;
	final static int MSG_WHAT = 1;

	private static int image_size = 12800;
	private static long image_timeout = 1000; /*ms*/
	private static long video_timeout = 1000; /*ms*/

	short image_small_frame_mask = 0x0;
	short video_small_frame_mask = 0x0;
	short image_receiving = 0;
	short image_receive_cnt = 0;
	short video_receive_cnt = 0;
	byte[] image_binary_data = new byte[12800];
	
	byte[] video_binary_data = new byte[12800];
	
	udp_ctrl_client mUdp_ctrl_client;
	udp_ctrl_server mUdp_ctrl_server;

//	udp_ctrl_frame udp_ctrl_frame_server; 	/* udp 服务器端 帧 */
//	udp_ctrl_frame udp_ctrl_frame_client; 	/* udp 客户端 帧 */
//	udp_thread udp_thread_server;			/* udp 服务器端 */
//	udp_thread udp_thread_client;			/* udp 客户端 */
	Context 		mContext;
	Handler 		mHandler;      // Handler for messages in the main thread 

	public long image_time_start = 0;
	public long image_index_last = 0;
	
	public long video_time_start = 0;
	public long video_index_last = 0;
	
	public static int CLIENT_PORT = 3333;
	public   String CLIENT_IP = "127.0.0.1"; // 'Within' the emulator!
//	private static String CLINET_TAG = "udp_thread_client";

	public static int SERVER_PORT = 1234;
	public   String SERVER_IP = "127.0.0.1"; // 'Within' the emulator!
//	private static String SERVER_TAG = "udp_thread_server";

	InetAddress 	myBcastIPAddress; 		// my broadcast IP addresses
	InetAddress 	myIPAddress; 			// my IP addresses


	public udp_ctrl(Context currentContext , Handler handler) {
		mContext = currentContext;
		mHandler  = handler;
		
		try{
			/*获取Wifi信息并且获取当前的ip地址*/
			if (getMyWiFiBcastAndIPAddress()){
				SERVER_IP = myIPAddress.getHostAddress();
				CLIENT_IP = myIPAddress.getHostAddress();
			}
		}catch (Exception e) {
			disp_toast("Wifi Maybe Not Opened");
		}

		mUdp_ctrl_server = new udp_ctrl_server(currentContext, mHandler_Server, SERVER_IP, SERVER_PORT);
		if (!mUdp_ctrl_server.isSocketOK()){
			Log.e(TAG,"UDP Server NOT STARTED");
			//disp_toast("Cannot Start UDP Server Server");
     	   	return;
		}
		/*mUdp_ctrl_client = new udp_ctrl_client(currentContext, mHandler_Client, CLIENT_IP, CLIENT_PORT);
		if (!mUdp_ctrl_client.isSocketOK()){
			Log.e(TAG,"UDP Client NOT STARTED");
     	   	//disp_toast("Cannot Start UDP Client Server");
     	   	return;
		}*/
		
 		mUdp_ctrl_server.start();
		//mUdp_ctrl_client.start();
	}
	
	private void disp_toast(String msg){
		Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
	}

	
	private final Handler mHandler_Server = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			handle_server_msg( ((byte[])msg.obj));
		}
	};
	
	private final Handler mHandler_Client = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			handle_client_msg( ((byte[])msg.obj));
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
		
		ctrl_frame mUdp_server_ctrl_frame = new ctrl_frame();
		if (mUdp_server_ctrl_frame.decode_framefrombytes(frame)){ /*判断是否构造帧成功*/
			switch (mUdp_server_ctrl_frame.ctrl_cmd){
				case ctrlcmds.ACQUIRE_CAMERA_IMAGE:
					handle_ACQUIRE_CAMERA_IMAGE_msg(frame);
					break;
				case ctrlcmds.ACQUIRE_CAMERA_VIDEO_START:
					handle_ACQUIRE_CAMERA_VIDEO_START_msg(frame);
					break;
				default:
					break;	
			}
		}
	}
	
	private void handle_client_msg(byte[] frame){
		if (frame == null){
			if(D) Log.d(TAG, "No Byte Receved!");
			return ;
		}

		if (frame.length == 0){
			if(D) Log.d(TAG, "No Byte Receved!");
			return ;
		}
		
		ctrl_frame mUdp_client_ctrl_frame = new ctrl_frame();
		if (mUdp_client_ctrl_frame.decode_framefrombytes(frame)){ /*判断是否构造帧成功*/

		}
	}
	

	private void handle_ACQUIRE_CAMERA_IMAGE_msg(byte[] frame){
		short frame_index = 0;
		long image_index = 0;
		long image_time_end ;
		
		/*获取图像编号以及小帧编号*/
		frame_index = (short) (frame[7] & 0xf);
		image_index  = (long) ((frame[7] >> 4)& 0xf) + (((long)frame[6]) << 4) 
				+ (((long)frame[5]) << 12) + (((long)frame[4]) << 20);
		
		if (image_receive_cnt == 0){
			image_time_start = SystemClock.elapsedRealtime();
			image_index_last = image_index;
		}
		
		if ( ((image_small_frame_mask & (1<<frame_index)) != (1<<frame_index)) ||
				(image_index_last == image_index) ){
			image_small_frame_mask = (short) (image_small_frame_mask | (1<<frame_index));
			image_receive_cnt ++;
			System.arraycopy(frame, 8, image_binary_data, frame_index*small_frame_datalength, small_frame_datalength);
			Log.d(TAG, "A picture small frame received, index " + frame_index + " Count:" + image_receive_cnt);
		}else{
			image_receive_cnt = 0;
			image_receiving = 0;
			image_small_frame_mask = 0x0;
			///Arrays.fill(image_binary_data, (byte) 0); /*数组清零*/  //不清零的话就可以显示图像 不出现黑色的条条
			image_time_start = SystemClock.elapsedRealtime();
			disp_toast("Image Data Received Error!");
			Log.d(TAG, "A picture received Failed!");
		}
		
		image_time_end = SystemClock.elapsedRealtime();
		if (((image_time_end-image_time_start) > image_timeout) || (image_receive_cnt == 16)){
			image_receive_cnt = 0;
			image_receiving = 0;
			image_small_frame_mask = 0x0;
			disp_toast("Image Data Received!");
			Log.d(TAG, "A picture received!");
			mHandler.obtainMessage(MSG_WHAT, image_binary_data).sendToTarget();	
		}
	}
	
		private void handle_ACQUIRE_CAMERA_VIDEO_START_msg(byte[] frame){
		short frame_index = 0;
		long image_index = 0;
		long image_time_end ;
		boolean next_image_come = false;
		
		/*获取图像编号以及小帧编号*/
		frame_index = (short) (frame[7] & 0xf);
		image_index  = (long) ((frame[7] >> 4)& 0xf) + (((long)frame[6]) << 4) 
				+ (((long)frame[5]) << 12) + (((long)frame[4]) << 20);
		
		if (video_receive_cnt == 0){
			video_time_start = SystemClock.elapsedRealtime();
			video_index_last = image_index;
		}
		if (image_index > video_index_last){ /*如果不是同一帧图像就开始读取下一帧图像*/
			next_image_come = true;
			mHandler.obtainMessage(MSG_WHAT, video_binary_data).sendToTarget();	
			//Arrays.fill(video_binary_data, (byte) 0); /*数组清零*/
			video_index_last = image_index;
			video_receive_cnt = 0;
			video_small_frame_mask = 0x0;
		}
		if ( ((video_small_frame_mask & (1<<frame_index)) != (1<<frame_index)) ||
				(video_index_last == image_index) ){
			video_small_frame_mask = (short) (video_small_frame_mask | (1<<frame_index));
			video_receive_cnt ++;
			System.arraycopy(frame, 8, video_binary_data, frame_index*small_frame_datalength, small_frame_datalength);
			Log.d(TAG, "A picture small frame received, index " + frame_index + " Count:" + video_receive_cnt);
		}else{
			video_receive_cnt = 0;
			video_small_frame_mask = 0x0;
			//Arrays.fill(video_binary_data, (byte) 0); /*数组清零*/ //不清零的话就可以显示图像 不出现黑色的条条 显示效果好些
			video_time_start = SystemClock.elapsedRealtime();
			//disp_toast("Image Data Received Error!");
			Log.d(TAG, "A picture received Failed!");
		}
		
		image_time_end = SystemClock.elapsedRealtime();
		if ( (video_receive_cnt == 16)){
			video_receive_cnt = 0;
			video_small_frame_mask = 0x0;
			//disp_toast("Image Data Received!");
			Log.d(TAG, "A picture received!");
			mHandler.obtainMessage(MSG_WHAT, video_binary_data).sendToTarget();	
		}
	}
	
	
	
	public void send_image_frames(int image_cnt, byte[] imagedata) throws InterruptedException{
		short ctrlcode;
		short ctrlprefix;
		short datalength;
		
		image_receiving = 1;
		if (imagedata.length == image_size){
			for (int i = 0; i < 16; i++){
				ctrlprefix = ctrl_prefixs.encode_ctrlprefix(ctrl_prefixs.read_request, ctrl_prefixs.mass_data_request,ctrl_prefixs.withack);
				ctrlcode = ctrl_prefixs.make_ctrl_code(ctrlcmds.ACQUIRE_CAMERA_IMAGE, ctrlprefix);
				datalength = (short) (small_frame_datalength + 4);
				if ((i == 15) && ((imagedata.length % small_frame_datalength) != 0)){
					datalength = (short) (((short) imagedata.length % small_frame_datalength) + 4);
				}
				byte[] small_frame = new byte[small_frame_datalength+8];
				small_frame[0] = (byte) ((ctrlcode >> 8) & 0xff);
				small_frame[1] = (byte) ((ctrlcode) & 0xff);
				small_frame[2] = (byte) ((datalength >> 8) & 0xff);
				small_frame[3] = (byte) ((datalength) & 0xff);
				small_frame[4] = (byte)((image_cnt >> 16) & 0xff);
				small_frame[5] = (byte)((image_cnt >> 8) & 0xff);
				small_frame[6] = (byte)(image_cnt & 0xff);
				small_frame[7] = (byte) i;
				if (datalength > 0){
					System.arraycopy(imagedata, i*small_frame_datalength, small_frame, 8, small_frame_datalength);
				}
				mUdp_ctrl_client.post_msg(small_frame);
				Thread.sleep(1);
			}
			
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
