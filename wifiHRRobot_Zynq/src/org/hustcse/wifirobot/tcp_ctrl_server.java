package org.hustcse.wifirobot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

public class tcp_ctrl_server extends Thread{
	private  String TAG = "TCP_CTRL_SERVER";
	public  boolean D = true;
	public  int PORT = 6666;
	public   String IP = "127.0.0.1"; // 'Within' the emulator!
	
	final static int receive_pool_size  =  1024;
	final static int MSG_WHAT = 1;
	final static int MAX_TRIES = 100;
	final static int READ_TIMEOUT = 10000;
	
	final static short no_ack = 0;
	final static short tcp_ack = 1;
	final static short udp_ack = 2;

	Handler 		mHandler;      // Handler for messages in the main thread 
	Context 		mContext;	   // Context to the application (for getting ip Addresses)
	ServerSocket 	serverSocket;  // Socket used both for sending and receiving 
	Socket 			clientSocket;
	private boolean 	socketOK=false; // True as long as we don't get socket errors
		
	InetAddress 	myBcastIPAddress; 		// my broadcast IP addresses
	InetAddress 	myIPAddress; 			// my IP addresses

	LinkedList<byte[]>	tcp_send_msg_queue; 
	LinkedList<byte[]>	tcp_rec_msg_queue; 
	byte[]			tcp_msg_send;
	byte[]			tcp_msg_receive;
	boolean			msg_send_available = false;
	boolean			msg_rec_available = false;

	int				tcp_msg_rec_cnt;
	short			tcp_ctrl_prefix;
	short			tcp_ack_type;
	int				max_tries = 0;
	receive_msg_handle rec_msg_handle;

	
	public tcp_ctrl_server(Context currentContext,Handler handler, String ip, int port){
		IP = ip;
		PORT = port;
		mHandler = handler;
		mContext = currentContext;
		
		try {
			tcp_send_msg_queue = new LinkedList<byte[]>();
			tcp_rec_msg_queue = new LinkedList<byte[]>();
			rec_msg_handle = new receive_msg_handle(mHandler);
			rec_msg_handle.start();

			serverSocket = new ServerSocket(PORT);
			socketOK = true;
			
			disp_toast("Start TCP Server @" + ip + ":" + port);
			if(D) Log.d(TAG, "Start TCP Server @" + ip + ":" + port); 
		} catch (Exception e) {
			socketOK  = false;
			disp_toast("Can't Start  TCP Server @" + ip + ":" + port);
			if(D) Log.e(TAG, e.getMessage()); 
		}
		
	}
	
	/*获取tcp服务器端socket状态*/
	boolean isSocketOK(){
		return socketOK;
	} 
	
	private void disp_toast(String msg){
		Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
	}
	
	public synchronized void post_msg(byte[] msg){
		if (!socketOK){
			disp_toast("TCP Socket Server Is not ready!");
			return;
		}
		tcp_send_msg_queue.add(msg); /*将消息添加到等待发送的数据队列中*/
		msg_send_available = true;
		notifyAll();
	}
	
	private synchronized byte[] get_msg() {
		byte[] msg;
		try {
			msg =  tcp_send_msg_queue.remove(); /*返回队列并移除最前面的消息，如果没有消息就返回null*/
			if (tcp_send_msg_queue.peek() == null){
				msg_send_available = false;
				notifyAll();
			}
			return msg;
		} catch (Exception e) {
			msg_send_available = false;
			notifyAll();
			return null;
		}
	}

	private synchronized void waitMsgSndAvailable() throws InterruptedException{
		while (!msg_send_available){
			wait();
		}
	}
	
	private synchronized void add_receive_msg(byte[] msg) {
		tcp_rec_msg_queue.add(msg);
		msg_rec_available = true;
		notifyAll();
	}
	
	private synchronized byte[] get_receive_msg() {
		byte[] msg;
		try {
			msg = tcp_rec_msg_queue.remove();
			if (tcp_rec_msg_queue.peek() == null){
				msg_rec_available = false;
				notifyAll();
			}
			return msg;
		} catch (Exception e) {
			msg_rec_available = false;
			notifyAll();
			return null;
		}
	}
	
	private synchronized void waitMsgRecAvailable() throws InterruptedException{
		while (!msg_rec_available){
			wait();
		}
	}
	
	private short get_msg_type(byte ctrl_prefix){
		return (short) ((ctrl_prefix >> 4) & 0xf);
	}
	
	private short check_ack_type(byte ctrlprefix){
		
		short ctrl_prefix = get_msg_type(ctrlprefix);
		
		//short operate = (short) ((ctrl_prefix & (ctrl_prefixs.operate_mask)) >> (ctrl_prefixs.operate_offset) );
		short data_request = (short) ((ctrl_prefix & (ctrl_prefixs.data_request_mask)) >> (ctrl_prefixs.data_request_offset) );
		short ack = (short) ((ctrl_prefix & (ctrl_prefixs.ack_mask)) >> (ctrl_prefixs.ack_offset) );
		short ack_type;
		
		if ( (ack)  == ctrl_prefixs.withoutack){
			ack_type = no_ack;
		}else{
			if ( data_request == ctrl_prefixs.less_data_request ){
				ack_type = tcp_ack;
			}else{
				ack_type = udp_ack;
			}
		}
		return ack_type;
	}
	
	@Override
	public void run(){
		try {
			while(socketOK){
					clientSocket = serverSocket.accept();
					tcpreceiveThread mrecThread = new tcpreceiveThread(clientSocket);
					mrecThread.start();
			} 
		}catch (IOException e) {
			if(D) Log.e(TAG, e.getMessage()); 
		} finally{
			try {
				if (serverSocket != null){
					if (!serverSocket.isClosed()){
						serverSocket.close();
						if(D) Log.i(TAG, "Close Server Socket Success!"); 
					}
				}
			} catch (Exception e2) {
				if(D) Log.e(TAG, "Close Server Socket Error"); 
			}
		}
	}
	
	class tcpreceiveThread extends Thread{
		private InputStream mInputStream;
		private OutputStream mOutputStream;
		private Socket msocket;
		private boolean mstop = false;
				
		public  tcpreceiveThread(Socket s) {
			msocket = s;
			try {
				mInputStream = msocket.getInputStream();
				mOutputStream = msocket.getOutputStream();
			} catch (IOException e) {
				if(D) Log.d(TAG, e.getMessage()); 
			}
		}
		
		@Override
		public void run(){
			while (!mstop){
				try {
					while ( (tcp_msg_rec_cnt = mInputStream.available()) == 0); /*等待主机端待发送的数据*/
					tcp_msg_receive = new byte[tcp_msg_rec_cnt];
					mInputStream.read(tcp_msg_receive);
					add_receive_msg(tcp_msg_receive);
					tcp_ack_type = check_ack_type(tcp_msg_receive[0]);
					if (tcp_ack_type == tcp_ack){
						waitMsgSndAvailable();
						if((tcp_msg_send = get_msg()) != null){/*等待客户端待发送的消息到来*/
							mOutputStream.write(tcp_msg_send);
						}else{
							if(D) Log.d(TAG, "Send Null Msg"); 
						}
					}
				} catch (Exception e) {
					if(D) Log.d(TAG, e.getMessage()); 
				}
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
					rec_msg = get_receive_msg();
					if (rec_msg != null){
						rec_Handler.obtainMessage(MSG_WHAT, rec_msg).sendToTarget();
					}else{
						if(D) Log.d(TAG, "Receive Null Msg"); 
					}
				}
			} catch (Exception e) {
				if(D) Log.d(TAG, e.getMessage()); 
			}
		}
	}
}
