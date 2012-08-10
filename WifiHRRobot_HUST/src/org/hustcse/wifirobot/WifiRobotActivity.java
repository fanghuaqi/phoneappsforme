package org.hustcse.wifirobot;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.security.auth.callback.Callback;

import org.apache.http.HttpClientConnection;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.MobileAnarchy.Android.Widgets.Joystick.JoystickMovedListener;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickView;

public class WifiRobotActivity extends Activity {
	private static String TAG = "WifiHRRobot";
	private static String VLC_VIDEO_ADDR = "http://115.156.219.39:8080/?action=stream";
	private static String DIST_TCPIPADDR = "192.168.0.200";
	private static int DIST_TCPPORT = 1234;

	final static boolean D = true;
	private SharedPreferences preferences;
	MediaPlayer vlcmediaPlayer;
	SurfaceView surface_vlc;
	SurfaceHolder surfaceholder_vlc; 	
	DrawVideo m_DrawVideo;
	
	private  String dist_tcp_addr;
	private int dist_tcp_port;

	
	final static int MSG_VIDEO_UPDATE = 1;
	final static int MSG_VIDEO_ERROR = 2;



	Button btn_image;
	Button btn_video;
	Button btn_follow_road_mode_ctrl;
	Button btn_set_camera2LCD;
	
	ImageView img_camera;
	
	private boolean follow_road_flag = false;
	private boolean show_camera2LCD_flag = false;
	private boolean need_lock_button = true; /*是否需要在进入巡线模式后让Button不可用*/

	/*vlc video mode */
	private boolean vlc_video_mode = false;
	private String vlc_video_addr; 
	private boolean vlc_video_flag = false;
	private boolean player_sel = false;
	
	private String target_ipaddr ;
	
	short tcp_ctrl_code;
	short tcp_data_length;
	byte[] tcp_ctrl_data = new byte[1024];
	tcp_ctrl tcp_ctrl_obj ;
	udp_ctrl udp_ctrl_obj;
	byte[] image_binary_data = new byte[12800];
	
	static final int tcp_small_frame_size = 512;
	
	int img_height = 100;
    int img_width = 128;
    int video_cnt  = 0;
    int img_size = img_height*img_width;
	int[] img_pixels = new int[img_size];
	Config img_cfg = Config.ARGB_8888;
	//Bitmap img_org;
	Bitmap img_camera_bmp;
	boolean video_flag = false;
	JoystickView joystick;
	TextView txtAngle, txtSpeed;
	int operate_angle_last = 0;
	int operate_speed_last = 0;
	int operate_angle = 0;
	int operate_speed = 0;
	private  final  static int MAX_SPEED_UNIT = 10;
	private  final  static int SPEED_SCALE = 5;

	private Context mContext;
	
	private  final  static int LCD_X_MAX = 255;
	private  final  static int LCD_Y_MAX = 63;
	private static final int REQ_SYSTEM_SETTINGS = 0x0;
	private static final int REQ_SET_WITH_PIC = 0x1;

	
	Display display;
	private int screen_Width = 0;
	private int screen_Height = 0;
	private int screen_Orientation = 0;
	private float joystick_scale = (float) 3;
	LayoutParams joyviewParams;
	
	private float btn_scale = (float) 30;
	private float txtview_scale = (float) 28;
	
	private long start_time = 0;
	private long end_time = 0;


	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.i(TAG, "program startup");

    	start_time = System.currentTimeMillis();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // Initialize preferences
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        //preferences.registerOnSharedPreferenceChangeListener(sys_set_chg_listener);
        
        surface_vlc = (SurfaceView)findViewById(R.id.SurfaceView_camera);
        surfaceholder_vlc = surface_vlc.getHolder();
        surfaceholder_vlc.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceholder_vlc.addCallback(new SHCallback());
        
       // surface_vlc.setVisibility(View.INVISIBLE);
        
        /*获取屏幕的宽度长度*/
        display = getWindowManager().getDefaultDisplay();
        screen_Orientation = display.getOrientation();
        if ((screen_Orientation == Surface.ROTATION_0) ||
        		(screen_Orientation == Surface.ROTATION_180) ){
	        screen_Width = display.getHeight();
	        screen_Height = display.getWidth();
        }else{
        	screen_Width = display.getWidth();
	        screen_Height = display.getHeight();        	
        }
        
        Log.i(TAG, "Screen Resolution:" + screen_Height + " X " + screen_Width);
        
        btn_image = (Button)findViewById(R.id.button_image);
        btn_video = (Button)findViewById(R.id.button_video);
        btn_follow_road_mode_ctrl = (Button)findViewById(R.id.button_follow_road_mode_ctrl);
        btn_set_camera2LCD = (Button)findViewById(R.id.button_show_camera2LCD);
        
        img_camera = (ImageView)findViewById(R.id.imageView_camera);
        
        joystick = (JoystickView)findViewById(R.id.joystickView);
        txtAngle = (TextView)findViewById(R.id.TextViewX);
        txtSpeed = (TextView)findViewById(R.id.TextViewY);

        btn_image.getBackground().setAlpha(100); /*设置透明度为半透明 alpha 0-255*/
        btn_video.getBackground().setAlpha(100); /*设置透明度为半透明*/
        btn_follow_road_mode_ctrl.getBackground().setAlpha(100);
        btn_set_camera2LCD.getBackground().setAlpha(100);
        
        btn_image.setTextSize(screen_Width / btn_scale);
        btn_video.setTextSize(screen_Width / btn_scale);
        btn_follow_road_mode_ctrl.setTextSize(screen_Width / btn_scale);
        btn_set_camera2LCD.setTextSize(screen_Width / btn_scale);
        
        ((TextView)findViewById(R.id.TextViewAngle)).setTextSize(screen_Width / txtview_scale);
        ((TextView)findViewById(R.id.TextViewSpeed)).setTextSize(screen_Width / txtview_scale);
        txtAngle.setTextSize(screen_Width / txtview_scale);
        txtSpeed.setTextSize(screen_Width / txtview_scale);
        
        btn_image.setOnClickListener(image_acquire_listener);
        btn_video.setOnClickListener(video_acquire_listener);
        
        btn_follow_road_mode_ctrl.setOnClickListener(ctrl_btn_listener);
        btn_set_camera2LCD.setOnClickListener(ctrl_btn_listener);
        
        tcp_ctrl_obj = new tcp_ctrl(getApplicationContext(), mHandler_UDP_SEND_MSG);
        udp_ctrl_obj = new udp_ctrl(getApplicationContext(), mHandler_UDP_MSG);
        mContext = getApplicationContext();
        

        img_camera_bmp = Bitmap.createBitmap(img_width, img_height, img_cfg);
        //Bitmap bitmap_rgb = bitmap_cp.copy(img_cfg, true);
        //img_org = BitmapFactory.decodeResource(getResources(), R.drawable.grepscale);
        //Log.i(TAG, "Orginal Picture Alpha:" + img_org.hasAlpha());
        //Bitmap bitmap_rgb = Bitmap.createScaledBitmap(img_org, img_width, img_height, false);
        //img_camera_bmp = bitmap_rgb.copy(img_cfg, true);

		//Bitmap bitmap_cp = Bitmap.createScaledBitmap(bitmap, img_width, img_height, false);
		//Bitmap bitmap_rgb = bitmap_cp.copy(img_cfg, true);
		
        joyviewParams = joystick.getLayoutParams();
        joyviewParams.width = (int) (screen_Width / joystick_scale);
        joyviewParams.height = (int) (screen_Width / joystick_scale);
        joystick.setLayoutParams(joyviewParams);
        joystick.setOnJostickMovedListener(_listener);
        
    	end_time = System.currentTimeMillis();
    	
    	Log.i(TAG, "startup use "+ (end_time-start_time) + " ms");
    	disp_toast("启动耗时" + (end_time-start_time) + " ms");

    }
    
    public class SHCallback implements SurfaceHolder.Callback{
    	public void surfaceChanged(SurfaceHolder holder, int format, 
                 int width, int height) {
        }
          public void surfaceCreated(SurfaceHolder holder) {
              Log.v(TAG, "surfaceCreated");
              try {
//                  mMediaPlayer = new MediaPlayer();
 //      mMediaPlayer.setDataSource(mPath);
            	  vlcmediaPlayer = new MediaPlayer();
                  vlcmediaPlayer.setDisplay(surfaceholder_vlc);
 //                 mMediaPlayer.prepare();
              } catch (Exception e) {
                 Log.e(TAG, "error: " + e.getMessage());
              }  
    }
        public void surfaceDestroyed(SurfaceHolder holder) {}
    }
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}
    
    public void update_preference(){
    	try {
			vlc_video_addr = preferences.getString(getResources().getString(R.string.vlcaddr), VLC_VIDEO_ADDR);
			dist_tcp_addr = preferences.getString(getResources().getString(R.string.distipaddr), DIST_TCPIPADDR);
			dist_tcp_port = Integer.parseInt( (preferences.getString(getResources().getString(R.string.disttcpport), String.valueOf(DIST_TCPPORT))) );
			vlc_video_mode = preferences.getBoolean(getResources().getString(R.string.vlcvideostate), false);
			player_sel = preferences.getBoolean(getResources().getString(R.string.playersel), false);
		} catch (Exception e) {
			Log.d(TAG,e.toString());
		}
    }
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.Settings){
			startActivityForResult(new Intent(this, Preferences.class), REQ_SYSTEM_SETTINGS);
		}else{
			if (show_camera2LCD_flag){
				disp_toast("正在动态显示摄像头图像到液晶,请先退出该模式,再进行操作!");
				return true;
			}
			switch (item.getItemId()) {
				case R.id.SetLCDClrScreen:
					post_clr_screen_msg();
					break;
				case R.id.SetLCDString:
					post_set_screen_with_string_msg();
					break;
				case R.id.SetLCDWithPic:
					post_set_screen_with_pic_msg();
					break;
				case R.id.Settings:
					
					break;
				default:
					break;
			}
		}
		return true;
	}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {  
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {   
        	//按下的如果是BACK，同时没有重复
        	Log.d(TAG, "Program Exit!");
        	System.exit(0);
        }  
        return super.onKeyDown(keyCode, event);  
    } 
	    
    private JoystickMovedListener _listener = new JoystickMovedListener() {

		@Override
		public void OnMoved(int pan, int tilt) {
			int operate_x = 0;
			int operate_y = 0;

			operate_x = pan;
			operate_y = -tilt;
			calc_speed_and_angle(operate_x, operate_y);
			txtAngle.setText(Integer.toString(operate_angle));
			txtSpeed.setText(Integer.toString(operate_speed));
			checkSendOperateCarMsg();
			Thread.yield(); /*进程主动让出 控制权，这样的话 在操作摇杆时 还是可以显示动态图像的虽然效果不好*/
		}

		@Override
		public void OnReleased() {
			txtAngle.setText("released");
			txtSpeed.setText("released");
			Thread.yield();
		}
		
		public void OnReturnedToCenter() {
			txtAngle.setText("stopped");
			txtSpeed.setText("stopped");
			operate_angle = 0;
			operate_speed = 0;
			checkSendOperateCarMsg();
			Thread.yield();
		};
	}; 
	
	/*测试是否角度和速度没有改变 并发送控制小车命令*/
	private void checkSendOperateCarMsg(){
		tcp_ctrl_obj.mTcp_ctrl_client.tcp_connect();
		if (! ((operate_angle == operate_angle_last)  && (operate_speed == operate_speed_last))){
			if ((!follow_road_flag) && (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK())){ /*非巡线模式才可以进行遥控 并且当前socket可用才进行数据发送*/
				postOperateCarMessage(operate_angle, operate_speed);
			}
			operate_angle_last = operate_angle;
			operate_speed_last = operate_speed;
		}
	}
    /*发送获取角度控制和速度控制的命令*/
    private void postOperateCarMessage(int angle, int speed){
    	short ctrl_cmd ;
    	short ctrl_prefix;
    	byte[] msg = new byte[4];
    	
    	ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,ctrl_prefixs.withoutack);
    	ctrl_cmd = ctrlcmds.OPERATE_CAR;
    	msg[0] = (byte)(angle & 0xff);
    	msg[1] = (byte)((angle >> 8) & 0xff);
    	msg[2] = (byte)(speed & 0xff);
    	msg[3] = (byte)((speed >> 8) & 0xff);
    	
    	post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
    }
    /*通过当前坐标计算角度和速度信息*/
    private void calc_speed_and_angle(int operate_x, int operate_y){
		operate_speed = (int)Math.sqrt((operate_x*operate_x) + (operate_y*operate_y));
		
		if (operate_y < 0){
			operate_speed = -operate_speed;
		}
		
		if (operate_x == 0){
			if (operate_y == 0){
				operate_angle = 0;
			}else if (operate_y > 0){
				operate_angle = 90;
			}else{
				operate_angle = -90;
			}
		}else if (operate_y == 0){
			if (operate_x == 0){
				operate_angle = 0;
			}else if (operate_x > 0){
				operate_angle = 0;
			}else{
				operate_angle = 180;
			}
		}else{
			operate_angle = (int) ((Math.atan2(operate_y,operate_x)/Math.PI )* 180);
		}
		
		if (operate_speed == 0){
			operate_angle = 0;
		}else if (operate_speed > 0){
			operate_angle =  90 - operate_angle;
		}else{
			operate_angle = operate_angle + 90;
		}
		
		if (operate_speed > MAX_SPEED_UNIT){
			operate_speed = MAX_SPEED_UNIT;
		}else if (operate_speed < -MAX_SPEED_UNIT){
			operate_speed = -MAX_SPEED_UNIT;
		}
		operate_speed = operate_speed * SPEED_SCALE;

    }
	
      
    
	private final Handler mHandler_UDP_SEND_MSG = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			short ctrlcmd =  (Short)(msg.obj);
			switch (ctrlcmd) {
				case ctrlcmds.ACQUIRE_CAMERA_IMAGE:
					try {
						udp_ctrl_obj.send_image_frames(0, image_binary_data);
						for (int i = 0; i < img_size; i++){
			        		 image_binary_data[i] = (byte) (image_binary_data[i] + 10);
			        	 }
					} catch (InterruptedException e) {
						Log.e(TAG, "Error in acquire image command :" + e.getMessage());
					}
					break;
				case ctrlcmds.ACQUIRE_CAMERA_VIDEO_START:
					while (video_cnt < 20){
			        	video_cnt += 1;
		        		for (int i = 0; i < img_size; i++){
		        			image_binary_data[i] = (byte) (image_binary_data[i] + 10);
		        		}
			        	try {
							udp_ctrl_obj.send_image_frames(video_cnt, image_binary_data);
							Thread.yield();
						} catch (InterruptedException e) {
							Log.e(TAG, "Error in acquire video command :" + e.getMessage());
						}
		        	}
		        	video_cnt = 0;
					break;
					
				default:
					break;
			}
		}
	};
    
	private final Handler mHandler_video_process = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what){
				case MSG_VIDEO_UPDATE:
					img_camera.setImageBitmap(img_camera_bmp);
					break;
				case MSG_VIDEO_ERROR:
					((Button)findViewById(R.id.button_video)).setText(R.string.button_video_start);
					disp_toast("获取视频数据失败,请确认视频网址是否正确!");
					break;
				default:
					break;
			}
		}
	};
	
    private final Handler mHandler_UDP_MSG = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			byte[] img_pixels_data = (byte[])(msg.obj);
			for (int i = 0; i < img_size; i++){
				img_pixels[i] = (0xFF000000) | (img_pixels_data[i] << 0) | 
						(img_pixels_data[i] << 8) | (img_pixels_data[i] << 16);
			}
			try {
				Canvas m_canvas = new Canvas(img_camera_bmp);
				Paint m_paint = new Paint();
				ColorMatrix cm = new ColorMatrix();
				cm.setSaturation(0);
				ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
				m_paint.setColorFilter(f);
				m_canvas.drawBitmap(img_pixels, 0, (img_width>>2)<<2, 0, 0, img_width, img_height, false, m_paint);
				//img_camera_bmp.setPixels(img_pixels, 0, (img_width>>2)<<2, 0, 0, img_width, img_height);
				//img_camera_bmp.setDensity(Bitmap.DENSITY_NONE);
 				 //img_camera.setAlpha(255);
 				img_camera.setImageBitmap(img_camera_bmp);
 				 
			} catch (Exception e) {
				Log.d(TAG,e.toString());
			}
		}
	};
    private OnClickListener image_acquire_listener = new OnClickListener() {
        public void onClick(View v) {
        	post_ctrl_btnclk_msg(v.getId());
        }
    };
    
    private OnClickListener video_acquire_listener = new OnClickListener() {
        public void onClick(View v) {
        	post_ctrl_btnclk_msg(v.getId());
        }
    };
    
    private OnClickListener ctrl_btn_listener = new OnClickListener() {
        public void onClick(View v) {
        	/*根据小车控制按钮的id来处理事件*/
        	post_ctrl_btnclk_msg(v.getId());
        }
    };
    
    private OnSharedPreferenceChangeListener sys_set_chg_listener = new OnSharedPreferenceChangeListener() {
		
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
				String key) {
			if (key == getResources().getString(R.string.vlcaddr)){
				vlc_video_addr = preferences.getString(key, VLC_VIDEO_ADDR);			
			}else if (key == getResources().getString(R.string.vlcvideostate)){
				
			}else if ( (key == getResources().getString(R.string.distipaddr)) || 
					(key == getResources().getString(R.string.disttcpport))){
				dist_tcp_addr = preferences.getString(getResources().getString(R.string.distipaddr), DIST_TCPIPADDR);
				dist_tcp_port = Integer.parseInt( (preferences.getString(getResources().getString(R.string.disttcpport), String.valueOf(DIST_TCPPORT))) );
				tcp_ctrl_obj.mTcp_ctrl_client.tcpreconnect(dist_tcp_addr,dist_tcp_port);
			}
		}
	};
    
    /*处理对小车控制按钮的消息*/
    private void post_ctrl_btnclk_msg(int btn_id) {
    	short ctrl_cmd = 0 ;
    	short ctrl_prefix = 0;
    	byte[] msg = null;
    	Button btn;
    	ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,ctrl_prefixs.withoutack);
		switch (btn_id){
			case R.id.button_follow_road_mode_ctrl:
				btn = (Button) findViewById(R.id.button_follow_road_mode_ctrl);
				if (follow_road_flag == false){
					ctrl_cmd = (short) (ctrlcmds.ENTER_FOLLOW_ROAD_MODE);	
					btn.setText(R.string.button_exit_follow_road_mode);
					if (need_lock_button == true){
						((Button) findViewById(R.id.button_image)).setEnabled(false);
						((Button) findViewById(R.id.button_video)).setEnabled(false);
						((Button) findViewById(R.id.button_video)).setText(R.string.button_video_start);
						video_flag = false;
					}
					follow_road_flag = true;
				}else{
					ctrl_cmd = (short) (ctrlcmds.EXIT_FOLLOW_ROAD_MODE);
					btn.setText(R.string.button_enter_follow_road_mode);
					((Button) findViewById(R.id.button_image)).setEnabled(true);
					((Button) findViewById(R.id.button_video)).setEnabled(true);
					follow_road_flag = false;
				}
				break;
			case R.id.button_show_camera2LCD:
				btn = (Button) findViewById(R.id.button_show_camera2LCD);
				if (show_camera2LCD_flag == false){
					ctrl_cmd = (short) (ctrlcmds.SET_LCD_SHOW_CAMREA_START);	
					btn.setText(R.string.button_show_camera_onLCD_stop);
					/*if (need_lock_button == true){
						((Button) findViewById(R.id.button_image)).setEnabled(false);
						((Button) findViewById(R.id.button_video)).setEnabled(false);
						((Button) findViewById(R.id.button_video)).setText(R.string.button_video_start);
						video_flag = false;
					}*/
					show_camera2LCD_flag = true;
				}else{
					ctrl_cmd = (short) (ctrlcmds.SET_LCD_SHOW_CAMREA_STOP);
					btn.setText(R.string.button_show_camera_onLCD_start);
					((Button) findViewById(R.id.button_image)).setEnabled(true);
					((Button) findViewById(R.id.button_video)).setEnabled(true);
					show_camera2LCD_flag = false;
				}
				break;
				
			case R.id.button_image:
				update_preference();
				if (vlc_video_mode == false){
					ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(ctrl_prefixs.read_request, ctrl_prefixs.mass_data_request,ctrl_prefixs.withack);
					ctrl_cmd = (short) (ctrlcmds.ACQUIRE_CAMERA_IMAGE);	
				}else{
					get_remote_image(vlc_video_addr);
					return;
				}
				
				break;
			case R.id.button_video:
				btn = (Button) findViewById(R.id.button_video);
				update_preference();
				if (vlc_video_mode == false){
					if (video_flag == false){
						ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(ctrl_prefixs.read_request, ctrl_prefixs.mass_data_request,ctrl_prefixs.withack);
						ctrl_cmd = (short) (ctrlcmds.ACQUIRE_CAMERA_VIDEO_START);	
						btn.setText(R.string.button_video_stop);
						video_flag = true;
					}else{
						ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(ctrl_prefixs.read_request, ctrl_prefixs.mass_data_request,ctrl_prefixs.withack);
						ctrl_cmd = (short) (ctrlcmds.ACQUIRE_CAMERA_VIDEO_STOP);	
						video_flag = false;
						btn.setText(R.string.button_video_start);
					}
				}else{
					if (player_sel){
						play_stream_withotherplayer();
					}else{
						if (video_flag == false){
/*							img_camera.setVisibility(View.INVISIBLE);
							surface_vlc.setVisibility(View.VISIBLE);
							while (!surface_vlc.isShown()); //等待surface创建完毕
							if (vlc_video_process()){
								btn.setText(R.string.button_video_stop);
								video_flag = true;
							}else{
								img_camera.setVisibility(View.VISIBLE);
								surface_vlc.setVisibility(View.INVISIBLE);
							}*/
							m_DrawVideo = new DrawVideo(vlc_video_addr,mHandler_video_process);
							if (m_DrawVideo.testconnection() == true){
								m_DrawVideo.start();
								btn.setText(R.string.button_video_stop);
								video_flag = true;
							}else{
								disp_toast("获取视频数据失败,请确认视频网址是否正确!");
							}
						}else{
							if (m_DrawVideo != null){
								m_DrawVideo.exit_thread();
								m_DrawVideo.stop();
							}
							btn.setText(R.string.button_video_start);
							video_flag = false;
/*							video_flag = false;
							vlcmediaPlayer.reset();
							img_camera.setVisibility(View.VISIBLE);
							surface_vlc.setVisibility(View.INVISIBLE);
							btn.setText(R.string.button_video_start);*/
						}
					}

					return;
				}
				break;
			default:
				return;
		}
		
		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);		
	}

    public boolean get_remote_image(String url_addr){
    	boolean flag = false;
    	
    	String m_video_addr = "http://115.156.219.39:8080/?action=stream"; 
    	URL m_video_url = null;
    	HttpURLConnection m_video_conn = null;
    	InputStream m_InputStream= null;
    	
    	try {
    		m_video_addr = url_addr;
    		m_video_url = new URL(m_video_addr);
			m_video_conn = (HttpURLConnection)m_video_url.openConnection();
			m_video_conn.connect();
			m_InputStream = m_video_conn.getInputStream();
			Bitmap bmp = BitmapFactory.decodeStream(m_InputStream);//从获取的流中构建出BMP图像
			//img_camera_bmp= Bitmap.createScaledBitmap(bmp, img_width, img_height, true);
			
			img_camera.setImageBitmap(bmp);
			flag = true;
		} catch (Exception e) {
			disp_toast("获取图像数据失败,请确认图像网址是否正确!");
			Log.e(TAG, "Error In Get Image Msg:" + e.getMessage());
			flag = false;
		}
    	if (m_video_conn != null){
			m_video_conn.disconnect();
		}
    	return flag;
    }
    
    public boolean vlc_video_process(){
    	boolean play_state = false;
    	try {
			if (vlcmediaPlayer.isPlaying()){
				vlcmediaPlayer.stop();
			}
			vlcmediaPlayer.reset();
			vlcmediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			vlcmediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
			//vlcmediaPlayer.setDataSource("http://daily3gp.com/vids/747.3gp"); /*测试这个是可以播放的*/
			vlcmediaPlayer.setDataSource(vlc_video_addr);
			vlcmediaPlayer.prepare();
			vlcmediaPlayer.start();
			play_state = true;
		} catch (Exception e) {
			Log.e(TAG, "Error In Player");
			disp_toast("无法播放该流媒体视频,请尝试调用外部播放器,推荐使用MX播放器!");
			play_state = false;			
		}
    	return play_state;
    }
    
    public void play_stream_withotherplayer(){
    	try {
			Intent it = new Intent(Intent.ACTION_VIEW);
			Uri uri = Uri.parse(vlc_video_addr);
			it.setDataAndType(uri, "video/*");
			startActivity(it);
		} catch (Exception e) {
			Log.e(TAG, "Error In Play With Other Player");
		}
    }

    
    private void post_tcp_msg(short ctrl_prefix, short ctrl_cmd, byte[] msg){
    	ctrl_frame mCtrl_frame = new ctrl_frame(ctrl_prefix, ctrl_cmd, msg);
		byte[] tcp_msg = new byte[4+mCtrl_frame.datalength];
		mCtrl_frame.encode_frametobytes(tcp_msg);
		tcp_ctrl_obj.mTcp_ctrl_client.post_msg(tcp_msg);
		if(D) {
			Log.d(TAG, "The Send TCP Message is As Follows:");
			mCtrl_frame.display_ctrl_frame();
		}
    }

    private void post_clr_screen_msg(){
    	short ctrl_cmd = 0 ;
    	short ctrl_prefix = 0;
    	byte[] msg = null;
    	
    	ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,ctrl_prefixs.withoutack);
    	ctrl_cmd = (short) (ctrlcmds.SET_LCD_CLR_SCREEN);	
		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
    }
    
    private void post_set_screen_with_string_msg(){

    	LayoutInflater inflater = (LayoutInflater)(WifiRobotActivity.this).getSystemService(LAYOUT_INFLATER_SERVICE); /*必须使用WifiRobotActivity.this*/
		final View view = inflater.inflate(R.layout.dialog, null);
		AlertDialog.Builder builder2 =new AlertDialog.Builder(WifiRobotActivity.this);
		builder2.setView(view);
		builder2.setTitle("设置LCD何处显示何字符").setPositiveButton("确定", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				short CoordX;
				short CoordY;
				short font;
				short fontcolor;
				short frame_prefix = 6;
				CharSequence lcdstring;
				short ctrl_cmd = 0 ;
		    	short ctrl_prefix = 0;
    	    	TextView txtviewX = (TextView)view.findViewById(R.id.editTextCoordX);
		    	TextView txtviewY = (TextView)view.findViewById(R.id.editTextCoordY);
		    	TextView txtviewStr = (TextView)view.findViewById(R.id.editTextLCDString);
		    	Spinner spinner_fontset = (Spinner)view.findViewById(R.id.spinnerFontSet);
		    	Spinner spinner_fontcolorset = (Spinner)view.findViewById(R.id.spinnerFontColorSet);

				try {
					/*获取X,Y,以及即将写入液晶的字符*/
					try {
						CoordX = (short) Integer.parseInt((String) (txtviewX.getText().toString()));
					} catch (Exception e) {
						CoordX = 0;
					}
					try {
						CoordY = (short) Integer.parseInt((String) (txtviewY.getText().toString()));
					} catch (Exception e) {
						CoordY = 0;
					}
					try {
						font = (short) spinner_fontset.getFirstVisiblePosition();
					} catch (Exception e) {
						font = 0;
					}
					font = (short) (font + 1); /*adjust to ARM */
					
					try {
						fontcolor = (short) spinner_fontcolorset.getFirstVisiblePosition();
					} catch (Exception e) {
						fontcolor = 0;
					}
					
					if (CoordX > LCD_X_MAX){
						CoordX = LCD_X_MAX;
					}
					if (CoordY > LCD_Y_MAX){
						CoordY = LCD_Y_MAX;
					}
					lcdstring = txtviewStr.getText();
					
					/*构造一个新的帧*/
					ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,ctrl_prefixs.withoutack);
					ctrl_cmd = (short) (ctrlcmds.SET_LCD_SHOW_STRING);
					byte[] lcd_string_msg = new byte[frame_prefix+lcdstring.length()];
					lcd_string_msg[0] =  (byte)(CoordX & 0xff);
					lcd_string_msg[1] =  (byte)((CoordX >> 8)  & 0xff);
					lcd_string_msg[2] =  (byte)(CoordY & 0xff);
					lcd_string_msg[3] =  (byte)((CoordY >> 8)  & 0xff);
					lcd_string_msg[4] =  (byte)(font & 0xff);
					lcd_string_msg[5] =  (byte)(fontcolor & 0xff);
					
					if (lcdstring.length() > 0){
						for (int i = 0; i < lcdstring.length(); i++){
							lcd_string_msg[i+frame_prefix] = (byte) lcdstring.charAt(i);
						}
					}
					post_tcp_msg(ctrl_prefix, ctrl_cmd, lcd_string_msg);		
				} catch (Exception e) {
					if (e.getMessage() != null){
						Log.e(TAG,e.getMessage());
					}else if (e.toString() != null){
						Log.e(TAG,e.toString());
					}else{
						Log.e(TAG,"Error in send pic to lcd command");
					}
					Toast.makeText(view.getContext(), "设置液晶字符显示命令未发送!", Toast.LENGTH_SHORT).show();
					//disp_toast("设置液晶字符显示命令未发送!");
				}

				dialog.cancel();
			}
		}).create().show();
				

    }
    
    private void post_set_screen_with_pic_msg(){     
    	//if (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK()){
	    	Intent intent = new Intent(Intent.ACTION_GET_CONTENT); 
	    	intent.setType("image/*");
	    	int requestCode = REQ_SET_WITH_PIC;
			startActivityForResult(intent, requestCode );
    	//}else{
    	//	disp_toast("没有连接到TCP服务端,请退出后连接到相应无线网络!");
    	//}
     }
    
    @Override  
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {  
    	
    	switch (requestCode) {
			case REQ_SET_WITH_PIC:
				send_image2lcd(resultCode, data);
				break;
			case REQ_SYSTEM_SETTINGS:
				systemsettingchange(resultCode, data);
				break;
	
			default:
				break;
		}
    	super.onActivityResult(requestCode, resultCode, data);  
    }
    
    private boolean systemsettingchange(int resultCode, Intent data){
    	boolean ifSucess = true;

    	if (resultCode == RESULT_OK){
    		
    	}else{
    		Log.i(TAG,"None settings change");
    	}
    	return ifSucess;
    }
    
    private boolean send_image2lcd(int resultCode, Intent data){
    	boolean ifSucess = true;
    	short ctrl_cmd = 0 ;
    	short ctrl_prefix = 0;

		if (resultCode == RESULT_OK){
			try {
				Uri uri = data.getData();	
				ContentResolver cr = this.getContentResolver();
				Bitmap bitmap = BitmapFactory.decodeStream(cr.openInputStream(uri));
				Bitmap bitmap_cp = Bitmap.createScaledBitmap(bitmap, img_width, img_height, false);
				Bitmap bitmap_rgb = bitmap_cp.copy(img_cfg, true);
				Bitmap bitmap_bw = bitmap_cp.copy(img_cfg, true);
				byte[] grey_pixels = new byte[img_width*img_height];
				int threshold = 40;
				if ( (threshold = convertRGB2GreyScale(bitmap_rgb, grey_pixels)) !=  0){
					int imagerawsize = img_width*img_height;
					int datasize = imagerawsize;
					int frame_prefix = 9;
					int data_offset = 0;
					int frame_cnt = 0;
					int data_length ;
					while (datasize > 0){
						if (datasize > (tcp_small_frame_size - frame_prefix)){
							data_length =  tcp_small_frame_size - frame_prefix;
							datasize = datasize - data_length;
						}else{
							data_length = datasize;
							datasize = 0;
						}
						byte[] grey_pixels_msg = new byte[data_length+frame_prefix];
						grey_pixels_msg[0] =  (byte)(img_width & 0xff);
						grey_pixels_msg[1] =  (byte)((img_width >> 8)  & 0xff);
						grey_pixels_msg[2] =  (byte)(img_height & 0xff);
						grey_pixels_msg[3] =  (byte)((img_height >> 8)  & 0xff);
						grey_pixels_msg[4] =  (byte)(frame_cnt & 0xff);
						grey_pixels_msg[5] =  (byte)((frame_cnt >> 8)  & 0xff);
						grey_pixels_msg[6] =  (byte)(data_length & 0xff);
						grey_pixels_msg[7] =  (byte)((data_length >> 8)  & 0xff);
						grey_pixels_msg[8] =  (byte)(threshold & 0xff);
						System.arraycopy(grey_pixels, data_offset, grey_pixels_msg, frame_prefix, data_length);
						data_offset = data_offset + data_length;
						
						ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,ctrl_prefixs.withoutack);
						ctrl_cmd = (short) (ctrlcmds.SET_LCD_WITH_PIC);
						post_tcp_msg(ctrl_prefix, ctrl_cmd, grey_pixels_msg);
						Log.i(TAG, "Set LCD With Image Small Frame Index " + frame_cnt + " Send!");
						Thread.sleep(50);
						frame_cnt ++;
					}
					ImageView img = new ImageView(WifiRobotActivity.this); /*必须要用WifiRobotActivity.this 否则就会报错*/
					img.setMinimumWidth(320);
					img.setMinimumHeight(480);

					img.setScaleType(ScaleType.FIT_XY);
					img.setImageBitmap(bitmap_rgb);
					new AlertDialog.Builder(WifiRobotActivity.this)
						.setTitle("已经发送的图像灰度图如图所示")
						.setView(img)
						.setPositiveButton("确定", null)
						.show();
					
					if (convertRGB2BlackWhite(bitmap_bw, threshold)){
						ImageView img_wb = new ImageView(WifiRobotActivity.this); /*必须要用WifiRobotActivity.this 否则就会报错*/
						img_wb.setMinimumWidth(320);
						img_wb.setMinimumHeight(480);

						img_wb.setScaleType(ScaleType.FIT_XY);
						img_wb.setImageBitmap(bitmap_bw);
						new AlertDialog.Builder(WifiRobotActivity.this)
							.setTitle("已经发送的图像二值化后如图所示")
							.setView(img_wb)
							.setPositiveButton("确定", null)
							.show();
					}
					//img_camera.setImageBitmap(bitmap_rgb);
					Thread.sleep(30); /*等待ARM端处理完毕*/
				}else{
					ifSucess = false;
				}
			} catch (Exception e) {
				ifSucess = false;
				if (e.getMessage() != null){
					Log.e(TAG,e.getMessage());
				}else if (e.toString() != null){
					Log.e(TAG,e.toString());
				}else{
					Log.e(TAG,"Error in send pic to lcd command");
				}
			}
		}else{
			ifSucess = false;
		}
		
		if (!ifSucess){
			disp_toast("采用图像来设置液晶命令未发送!");
		}else{
			disp_toast("采用图像来设置液晶命令已经全部发送!");
		}
		
		return ifSucess;
    }
    
    
    /*将图像转换为灰度图,并且如果图像存在就将其灰度(不为0)返回,不存在返回灰度为0*/
    private int convertRGB2GreyScale(Bitmap bitmap, byte[] picPixels){
		int threshold = 0;
		if (bitmap == null){
			threshold = 0;
		}else{
			if (picPixels.length >= (bitmap.getHeight()*bitmap.getWidth())){ /*如果返回灰度值的数组的大小足够了才进行转换*/
				int width = bitmap.getWidth();
				int height = bitmap.getHeight();
				long gray_sum = 0;
				int gray_mean = 0;
				int grayfrontmean = 0;
				int graybackmean = 0;
				int front = 0;
				int back = 0;
				int u = 0;// 灰度平均值
				long area = (height*(long)width);
				for (int i = 0; i < height; i++){
					for (int j = 0; j < width; j++){
					   int col = bitmap.getPixel(j, i);  
					   int alpha = col&0xFF000000;  
		               int red = (col&0x00FF0000)>>16;  
		               int green = (col&0x0000FF00)>>8;  
		               int blue = (col&0x000000FF);  
		               int gray = (int)((float)red*0.3+(float)green*0.59+(float)blue*0.11);  
		               int newColor = alpha|(gray<<16)|(gray<<8)|gray; 
		               bitmap.setPixel(j, i, newColor);
		               gray_sum = gray_sum + gray;
		               picPixels[(i * width) + j] = (byte)gray;
					}
				}	
				gray_mean = (int) (gray_sum / area);// 整个图的灰度平均值
				u = gray_mean;
				Log.i(TAG, "整个图的灰度平均值:" + (u & 0xff));
				/*for (int i = 0; i < height; i++) // 计算整个图的二值化阈值
				{
					for (int j = 0; j < width; j++) {
						if (picPixels[(i * width) + j] < gray_mean) {
							graybackmean += picPixels[(i * width) + j];
							back++;
						} else {
							grayfrontmean += picPixels[(i * width) + j];
							front++;
						}
					}
				}
				int frontvalue = (int) (grayfrontmean / front);// 前景中心
				int backvalue = (int) (graybackmean / back);// 背景中心
				float G[] = new float[frontvalue - backvalue + 1];// 方差数组
				int s = 0;
				Log.i(TAG, "前景中心:" + (frontvalue & 0xff));
				Log.i(TAG, "背景中心:" + (backvalue & 0xff));

				for (int i1 = backvalue; i1 < frontvalue + 1; i1++)// 以前景中心和背景中心为区间采用大津法算法
				{
					back = 0;
					front = 0;
					grayfrontmean = 0;
					graybackmean = 0;
					for (int i = 0; i < height; i++) {
						for (int j = 0; j < width; j++) {
							if (picPixels[(i * width) + j] < (i1 + 1)) {
								graybackmean += picPixels[(i * width) + j];
								back++;
							} else {
								grayfrontmean += picPixels[(i * width) + j];
								front++;
							}
						}
					}
					grayfrontmean = (int) (grayfrontmean / front);
					graybackmean = (int) (graybackmean / back);
					G[s] = (((float) back / area) * (graybackmean - u)
							* (graybackmean - u) + ((float) front / area)
							* (grayfrontmean - u) * (grayfrontmean - u));
					s++;
				}
				float max = G[0];
				int index = 0;
				for (int i = 1; i < frontvalue - backvalue + 1; i++) {
					if (max < G[i]) {
						max = G[i];
						index = i;
					}
				}
				
				threshold = index + backvalue; */
				threshold = u;
				if (threshold == 0){
					threshold += 1;
				}
				Log.i(TAG, "最终计算出来的阈值是:" + (threshold&0xff));
			}else{
				threshold = 0;
			}
		}
    	return threshold;
    }
    
    private boolean convertRGB2BlackWhite(Bitmap bitmap, int threshold){
		boolean ercd = true;

		if (bitmap == null){
			ercd = false;
		}else{
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			for (int i = 0; i < height; i++){
				for (int j = 0; j < width; j++){
				   int col = bitmap.getPixel(j, i);  
				   int alpha = col&0xFF000000;  
	               int red = (col&0x00FF0000)>>16;  
	               int green = (col&0x0000FF00)>>8;  
	               int blue = (col&0x000000FF);  
	               int gray = (int)((float)red*0.3+(float)green*0.59+(float)blue*0.11);  
	               if (gray > threshold){
	            	   gray = 255;
	               }else{
	            	   gray = 0;
	               }
	               int newColor = alpha|(gray<<16)|(gray<<8)|gray; 
	               bitmap.setPixel(j, i, newColor);
				}		
			}
		}
    	return ercd;
    }
    
    private void disp_toast(String msg){
		Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
	}
    
    class DrawVideo extends Thread{
    	private String m_video_addr = "http://115.156.219.39:8080/?action=stream"; 
    	private URL m_video_url;
    	private HttpURLConnection m_video_conn;
    	private InputStream m_InputStream;
    	private Handler 		video_Handler;
    	private boolean exit_flag = false;
    	
    	public DrawVideo(String url_addr, Handler handler){
    		m_video_addr = url_addr;
    		video_Handler = handler;
    	}

    	public void exit_thread(){
    		exit_flag = true;
    	}
    	
    	public boolean testconnection(){
    		boolean flag = false;
    		try{
				m_video_url = new URL(m_video_addr);
				m_video_conn = (HttpURLConnection)m_video_url.openConnection();
				m_video_conn.connect();
				m_InputStream = m_video_conn.getInputStream();
				Bitmap bmp = BitmapFactory.decodeStream(m_InputStream);//从获取的流中构建出BMP图像
				if (bmp == null){
					flag = false;
				}else{
					flag = true;
				}
    		}catch (Exception e) {
				flag = false;
				Log.e(TAG, "Error In Get Video Msg:" + e.getMessage());
			}
    		if (m_video_conn != null){
    			m_video_conn.disconnect();
    		}
    		return flag;
    	}
    	
    	public void run(){
    		try {
				while(!exit_flag){
					m_video_url = new URL(m_video_addr);
					m_video_conn = (HttpURLConnection)m_video_url.openConnection();
					m_video_conn.connect();
					m_InputStream = m_video_conn.getInputStream();
					Bitmap bmp = BitmapFactory.decodeStream(m_InputStream);//从获取的流中构建出BMP图像
					//if (bmp != null){
					img_camera_bmp = bmp;
					//img_camera_bmp= Bitmap.createScaledBitmap(bmp, img_width, img_height, true);
					video_Handler.obtainMessage(MSG_VIDEO_UPDATE) .sendToTarget();
						//img_camera.setImageBitmap(img_camera_bmp);
					//}
					sleep(35);
				}
				exit_flag = false;
			} catch (Exception e) {
				video_flag = false;
				Log.e(TAG, "Error In Get Video Msg:" + e.getMessage());
				video_Handler.obtainMessage(MSG_VIDEO_ERROR) .sendToTarget();
			}
    	}
    }
}
