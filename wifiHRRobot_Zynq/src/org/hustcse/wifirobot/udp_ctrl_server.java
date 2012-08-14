package org.hustcse.wifirobot;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

public class udp_ctrl_server extends Thread{
	private  String TAG = "UDP_SERVER_SOCKET";
	public  boolean D = true;
	public  int PORT = 6666;
	public   String IP = "127.0.0.1"; // 'Within' the emulator!
	final static int receive_pool_size  =  1024;
	final static int MSG_WHAT = 1;

	
	Handler 		mHandler;      // Handler for messages in the main thread 
	Context 		mContext;	   // Context to the application (for getting ip Addresses)
	private boolean 		socketOK=false; // True as long as we don't get socket errors
	
	DatagramSocket 	udp_serverSocket;  // Socket used both for sending and receiving 
		
	
	InetAddress 	udpserver_Addr;

	byte[] receive_pool = new byte[receive_pool_size];

	LinkedList<byte[]>	udp_rec_msg_queue; 
	boolean			msg_rec_available = false;
	byte[]			udp_msg_rec;
	receive_msg_handle rec_msg_handle;


	public udp_ctrl_server(Context currentContext,Handler handler, String ip, int port) {
		IP = ip;
		PORT = port;
		mHandler = handler;
		mContext = currentContext;
		try {
			udp_rec_msg_queue = new LinkedList<byte[]>();
			rec_msg_handle = new receive_msg_handle(mHandler);
			rec_msg_handle.start();
			
			/*打开UDP服务端连接*/
			udpserver_Addr = InetAddress.getByName(IP);
			udp_serverSocket = new DatagramSocket(PORT, udpserver_Addr);

			socketOK = true;
			if(D) Log.d(TAG, "UDP Server Socket Open @" + IP + ":" + PORT); 
			disp_toast("UDP Server Socket Open @" + IP + ":" + PORT);
		} catch (Exception e) {
			socketOK = false;
			disp_toast("UDP Server Socket Init @" + IP + ":" + PORT);
			if(D) Log.e(TAG, "UDP Server Socket Init Error:" + e.getMessage()); 
		}
	} 
	
	/*获取udp服务器端socket状态*/
	boolean isSocketOK(){
		return socketOK;
	} 

	
	private void disp_toast(String msg){
		Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
	}
	
	private synchronized void add_receive_msg(byte[] msg) {
		if (!socketOK){
			disp_toast("UDP Socket Server Is not ready!");
			return;
		}
		udp_rec_msg_queue.add(msg);
		msg_rec_available = true;
		notifyAll();
	}
	
	private synchronized byte[] get_receive_msg() {
		byte[] msg;
		msg =  udp_rec_msg_queue.poll();
		if ( (msg == null) || (udp_rec_msg_queue.peek() == null)){
			msg_rec_available = false;
			notifyAll();
		}
		return msg;
	}
	
	private synchronized void waitMsgRecAvailable() throws InterruptedException{
		while (!msg_rec_available){
			wait();
		}
	}
	
	/*TCP 服务器端socket运行，监听连接，获取客户端发送来的数据*/
	@Override
	public void run(){
		try {
			/*接收客户端数据的buffer*/
			int read_cnt = 0;
			while (socketOK){
				/*构建接收的数据报*/
				DatagramPacket receivePacket = 
			    new DatagramPacket(receive_pool, receive_pool.length); 

				/*等待客户端连接数据输入*/
				udp_serverSocket.receive(receivePacket);
				/*读取客户端发来的数据,并交由handler处理*/
				read_cnt = receivePacket.getLength();
				if (read_cnt > 0){
					byte[] receive_msg = new byte[read_cnt];
					System.arraycopy(receivePacket.getData(), 0, receive_msg, 0, read_cnt);
					Log.d(TAG, "UDP Socket Receive Solid Data Count:" + read_cnt + " Frame Index: " + receive_msg[7]);
					add_receive_msg(receive_msg);
					Thread.yield();
				}else{
					Log.e(TAG, "UDP Socket Receive None Vaild Data!");
				}
			}			
		} catch (Exception e) {
			Log.e(TAG, "UDP Socket Receive Data Failed:" + e.getMessage());
			socketOK = false;
		} finally{
			try {
				if (udp_serverSocket != null){
					if (!udp_serverSocket.isClosed()){
						udp_serverSocket.close();
						Log.i(TAG, "UDP Socket Close Success!");
					}
				}
			} catch (Exception e2) {
				Log.e(TAG, "UDP Socket Close Failed!");
			}
		}
	}
	
	class receive_msg_handle extends Thread{
		Handler 		rec_Handler;
		byte[]			rec_msg;
		public  receive_msg_handle(Handler handler) {
			rec_Handler = handler;
		}
		
		@Override
		public void run(){
			try {
				while(true){
					//while( (rec_msg = (get_receive_msg())) == null ); /*等待数据待接收的数据*/
					waitMsgRecAvailable();
					if ( (rec_msg = (get_receive_msg())) != null ){
						rec_Handler.obtainMessage(MSG_WHAT, rec_msg).sendToTarget();
					}else{
						if(D) Log.d(TAG, "Receive Null Msg"); 
					}
				}
			} catch (Exception e) {
				if(D) Log.d(TAG, "Error Happened in UDP server receive process:" + e.getMessage()); 
			}
			
		}
	}
	

	
}
