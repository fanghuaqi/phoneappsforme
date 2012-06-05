package org.hustcse.wifirobot;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.LinkedList;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

public class tcp_ctrl_client extends Thread {
	private  String TAG = "TCP_CTRL_CLIENT";
	public  boolean D = true;
	public  int PORT = 6666;
	public   String IP = "127.0.0.1"; // 'Within' the emulator!
	
	final static int receive_pool_size  =  1024;
	final static int MSG_WHAT = 1;
	final static int MAX_TRIES = 100;
	final static int RW_TIMEOUT = 1000;
	
	final static short no_ack = 0;
	final static short tcp_ack = 1;
	final static short udp_ack = 2;

	Handler 		mHandler;      // Handler for messages in the main thread 
	Context 		mContext;	   // Context to the application (for getting ip Addresses)
	Socket 			clientSocket;  // Socket used both for sending and receiving 
	private boolean 		socketOK=false; // True as long as we don't get socket errors
	
	OutputStream 	socket_output;
	InputStream		socket_input;
	
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
	

	
	public tcp_ctrl_client (Context currentContext,Handler handler, String ip, int port) {
		IP = ip;
		PORT = port;
		mHandler = handler;
		mContext = currentContext;
		
		try {
			tcp_send_msg_queue = new LinkedList<byte[]>();
			tcp_rec_msg_queue = new LinkedList<byte[]>();
			rec_msg_handle = new receive_msg_handle(mHandler);
			rec_msg_handle.start();

			InetAddress tcpserver_Addr = InetAddress.getByName(IP);
			clientSocket = new Socket(tcpserver_Addr, PORT);
			clientSocket.setSoTimeout(RW_TIMEOUT);
			socket_output = clientSocket.getOutputStream();
			socket_input = clientSocket.getInputStream();

			socketOK = true;
						
			if(D) Log.d(TAG, "Connect to Server @" + ip + ":" + port); 

			disp_toast("Connect to Server @" + ip + ":" + port);
		} catch (Exception e) {
			socketOK  = false;
			disp_toast("Can't Connect to Server @" + ip + ":" + port);
			if(D) Log.e(TAG, "TCP client connect to server error:" + e.getMessage()); 
		}
		
	}
	
	boolean isSocketOK(){
		return socketOK;
	}
	
	private void disp_toast(String msg){
		Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
	}
	
	public synchronized void post_msg(byte[] msg){
		if (!socketOK){
			disp_toast("TCP Socket Client Is not ready!");
			return;
		}
		tcp_send_msg_queue.add(msg); /*将消息添加到等待发送的数据队列中*/
		msg_send_available = true;
		notifyAll();
	}
	
	private synchronized byte[] get_msg() {
		byte[] msg;
		
		msg =  tcp_send_msg_queue.peek(); /*返回队列最前面的消息，如果没有消息就返回null*/
		if (msg == null){
			msg_send_available = false;
			notifyAll();
		}
		return msg;
	}
	
	private synchronized byte[] remove_msg() {
		byte[] msg;
		
		try {
			msg = tcp_send_msg_queue.remove(); /*返回队列最前面的消息，如果没有消息就返回null*/
			if ((tcp_send_msg_queue.peek()) == null){
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
	
	
	private  synchronized void add_receive_msg(byte[] msg) {
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
			while (socketOK){
				try {
					//while ((tcp_msg_send = get_msg()) == null); /*等待客户端待发送的消息到来*/
					waitMsgSndAvailable();
					tcp_msg_send = get_msg();
					if (tcp_msg_send == null){
						if(D) Log.d(TAG, "Send Null Msg"); 
						continue;
					}
					socket_output.write(tcp_msg_send);
					tcp_ack_type = check_ack_type(tcp_msg_send[0]);
					if (tcp_ack_type == tcp_ack){ /*如果需要TCP返回数据*/
						
						///executor.execute(future);
						try {
							while ( (tcp_msg_rec_cnt = socket_input.available()) == 0);
							//tcp_msg_rec_cnt = future.get(RW_TIMEOUT, TimeUnit.MICROSECONDS);
							if (tcp_msg_rec_cnt >= 4){
								tcp_msg_receive = new byte[tcp_msg_rec_cnt];
								socket_input.read(tcp_msg_receive);
								byte[] rec_frame_head = new byte[2];
								byte[] snd_frame_head = new byte[2];
								System.arraycopy(tcp_msg_receive, 0, rec_frame_head, 0, 2);
								System.arraycopy(tcp_msg_send, 0, snd_frame_head, 0, 2);
								if ((snd_frame_head[0] == rec_frame_head[0]) && 
										(snd_frame_head[1] == rec_frame_head[1])){
									remove_msg();
									add_receive_msg(tcp_msg_receive);
									max_tries = 0;
								}else{
									max_tries ++;
									if(D) Log.e(TAG, "Receive Failed " + max_tries); 
								}
							}else{
								max_tries ++;
							}
						} catch (Exception e) {
							//future.cancel(true);
							if(D) Log.e(TAG, "Receive timeout"); 
							max_tries ++;
						}
						if (max_tries > MAX_TRIES){ /*重试次数过多*/
							max_tries = 0;
							remove_msg(); /*清除本次失败消息，并开始下一个任务*/
							disp_toast("消息重试次数过多！取消本次发送命令");
						}
						//while ( (tcp_msg_rec_cnt = socket_input.available()) == 0); /*等待主机端待发送的数据*/
	
					}else{
						remove_msg();
					}
				} catch (Exception e) {
					disp_toast("Some Error Happened in TCP client!");
				} 
			}
		} catch (Exception e) {
			if(D) Log.e(TAG, e.getMessage()); 
		}finally{
			try {
				if (clientSocket != null){
					if (!clientSocket.isClosed()){
						clientSocket.close();
						if(D) Log.i(TAG, "Close Client Socket Success!"); 
					}
				}
			} catch (Exception e2) {
				if(D) Log.e(TAG, "Close Client Socket Error"); 
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
				if(D) Log.d(TAG, "TCP client receive msg handle error:" + e.getMessage()); 
			}

		}
	}
	
}
