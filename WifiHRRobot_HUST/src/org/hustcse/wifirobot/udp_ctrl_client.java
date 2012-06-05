package org.hustcse.wifirobot;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedList;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

public class udp_ctrl_client extends Thread{
	private  String TAG = "UDP_CLIENT_SOCKET";
	public  boolean D = true;
	public  int PORT = 6666;
	public   String IP = "127.0.0.1"; // 'Within' the emulator!
	
	Handler 		mHandler;      // Handler for messages in the main thread 
	Context 		mContext;	   // Context to the application (for getting ip Addresses)
	private boolean socketOK=false; // True as long as we don't get socket errors
			
	InetAddress 	myBcastIPAddress; 		// my broadcast IP addresses
	InetAddress 	myIPAddress; 			// my IP addresses
	
	InetAddress 	udpserver_Addr;

	LinkedList<byte[]>	udp_send_msg_queue; 
	boolean			msg_send_available = false;
	byte[]			udp_msg_send;

	public udp_ctrl_client(Context currentContext,Handler handler, String ip, int port) {
		IP = ip;
		PORT = port;
		mHandler = handler;
		mContext = currentContext;
		try {
			udp_send_msg_queue = new LinkedList<byte[]>();

			/*建立到服务器端的socket客户端连接, 准备待发送的数据包*/
			udpserver_Addr = InetAddress.getByName(IP);
			socketOK = true;
			if(D) Log.d(TAG, ("UDP Client Socket Init @" + IP + ":" + PORT)); 
			disp_toast("UDP Client Socket Init @" + IP + ":" + PORT);
		} catch (Exception e) {
			disp_toast("Can't UDP Client Socket  @" + IP + ":" + PORT);
			socketOK = false;
			if(D) Log.e(TAG, e.getMessage()); 
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
			disp_toast("UDP Socket Client Is not ready!");
			return;
		}
		udp_send_msg_queue.add(msg); /*将消息添加到等待发送的数据队列中*/
		msg_send_available = true;
		notifyAll();
	}
	
	private synchronized byte[] get_msg() {
		byte[] msg;
		msg = udp_send_msg_queue.poll(); /*返回队列并移除最前面的消息，如果没有消息就返回null*/
		if ((msg == null) || (udp_send_msg_queue.peek() == null)){
			msg_send_available = false;
			notifyAll();
		}
		return msg;
	}
	
	private synchronized void waitMsgSndAvailable() throws InterruptedException{
		while (!msg_send_available){
			wait();
		}
	}
	
	@Override
	public void run(){
		while(socketOK){
			try {
				/*建立到服务器端的socket客户端连接, 准备待发送的数据包*/
				DatagramSocket m_udpsocket = new DatagramSocket();
				byte[] mudp_msg_send;
				/*等待待发送的UDP数据包*/
				waitMsgSndAvailable();
				if ( ( mudp_msg_send = get_msg() ) == null){
					if(D) Log.d(TAG, "Send Null Msg"); 
					continue;
				}
				DatagramPacket sendPacket = 
						new DatagramPacket(mudp_msg_send, mudp_msg_send.length, udpserver_Addr, PORT); 
				/*向服务器端发送数据，无需等待回复*/
				//udp_socket.setSoTimeout(1000);
				m_udpsocket.send(sendPacket);
				//Thread.sleep(100);
			} catch (Exception e) {
				if(D) Log.d(TAG, e.getMessage()); 
			}
		}
	}

}
