package org.hustcse.wifirobot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
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
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.RawContacts.Entity;
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
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.MobileAnarchy.Android.Widgets.Joystick.JoystickMovedListener;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickView;

public class WifiRobotActivity extends Activity {
	private static String TAG = "WifiHRRobot";

	private static String CAR_VIDEO_ADDR = "http://192.168.1.100:8080/?action=snapshot";
	private static String ARM_VIDEO_ADDR = "http://192.168.1.100:8090/?action=snapshot";
	private static String OPENCV_VIDEO_ADDR = "http://192.168.1.100/detection.jpg";

	private static String DIST_TCPIPADDR = "192.168.1.100";
	private static int DIST_TCPPORT = 1234;

	final static boolean D = true;
	private SharedPreferences preferences;
	MediaPlayer vlcmediaPlayer;
	SurfaceView surface_vlc;
	SurfaceHolder surfaceholder_vlc;
	DrawVideo m_DrawVideo;

	private String dist_tcp_addr;
	private int dist_tcp_port;

	final static int MSG_VIDEO_UPDATE = 1;
	final static int MSG_VIDEO_ERROR = 2;
	final static int MSG_VIDEO_END = 3;

	final static int MSG_DATA_REC = 1;
	final static int MSG_DISPLAY_TOAST = 100;
	final static int MSG_FIX_PREFERENCE = 1000;
	final static int FIX_IP_PREFERENCE = 0;

	ProgressDialog mDialog_Connect, mDialog_ImageCap, mDialog_VideoCap;
	private static final int CONNECT_DIALOG_KEY = 0;
	private static final int IMGCAP_DIALOG_KEY = 1;
	private static final int VIDEOCAP_DIALOG_KEY = 2;

	Button btn_image;
	Button btn_video_srcsel;
	Button btn_video;
	Button btn_follow_road_mode_ctrl;
	Button btn_set_camera2LCD;
	Button btn_control_mode;
	Button btn_connect;
	Button btn_laser_ctrl;
	Button btn_arm_ctrl;

	ImageView img_camera;

	/* seekbar objects */

	SeekBar skb_angle[];

	private boolean follow_road_flag = false;
	private boolean show_camera2LCD_flag = false;
	private boolean need_lock_button = true; /* 是否需要在进入巡线模式后让Button不可用 */
	private boolean auto_control_mode = false;

	/* vlc video mode */
	private boolean vlc_video_mode = false;

	private int video_source_sel = 0;
	final static int MAX_VIDEO_SRC_CNT = 3;
	final static int CAR_VIDEO_SRC = 0;
	final static int ARM_VIDEO_SRC = 1;
	final static int OPENCV_VIDEO_SRC = 2;

	private String[] video_addr = new String[MAX_VIDEO_SRC_CNT];
	private String cur_video_addr; /* 当前视频源地址 */

	private boolean vlc_video_flag = false;
	private boolean player_sel = false;
	private boolean image_ready_flag = false;
	private boolean video_ready_flag = false;

	private String target_ipaddr;

	short tcp_ctrl_code;
	short tcp_data_length;
	byte[] tcp_ctrl_data = new byte[1024];
	tcp_ctrl tcp_ctrl_obj;
	udp_ctrl udp_ctrl_obj;
	byte[] image_binary_data = new byte[12800];

	static final int tcp_small_frame_size = 512;

	int img_height = 100;
	int img_width = 128;
	int video_cnt = 0;
	int img_size = img_height * img_width;
	int[] img_pixels = new int[img_size];
	Config img_cfg = Config.ARGB_8888;
	// Bitmap img_org;
	Bitmap img_camera_bmp;
	boolean video_flag = false;
	JoystickView joystick;
	
	JoystickView joystickArm;
	
	TextView txtAngle, txtSpeed, txtTCPState;
	int operate_angle_last = 0;
	int operate_speed_last = 0;
	int operate_angle = 0;
	int operate_speed = 0;
	
	/* 机械臂的X,Y偏移 */
	int arm_x_offset = 0;
	int arm_y_offset = 0;
	int arm_x_offset_last = 0;
	int arm_y_offset_last = 0;
	private final static int MAX_ARM_UNIT = 10;
	private final static int ARM_X_SCALE = 9;
	private final static int ARM_Y_SCALE = 9;

	int[] arm_angle_now = new int[5];
	int[] arm_angle_last = new int[5];
	private static final int ARM_ANGLE_MAX = 180;

	private final static int MAX_SPEED_UNIT = 10;
	private final static int SPEED_SCALE = 5;

	private Context mContext;

	private final static int LCD_X_MAX = 255;
	private final static int LCD_Y_MAX = 63;
	private static final int REQ_SYSTEM_SETTINGS = 0x0;
	private static final int REQ_SET_WITH_PIC = 0x1;

	Display display;
	private int screen_Width = 0;
	private int screen_Height = 0;
	private int screen_Orientation = 0;
	private float joystick_scale = (float) 3;
	LayoutParams joyviewParams;
	LayoutParams joyviewParamsArm;	
	
	private float btn_scale = (float) 60;
	private float txtview_scale = (float) 42;

	private long start_time = 0;
	private long end_time = 0;
	private int gl_video_state = 0;

	private long benchmark_start = 0;
	private long benchmark_end = 0;

	private static int MAX_PASS = 40;
	private long[] time_pass = new long[MAX_PASS + 1];
	private String[] pass_log = new String[MAX_PASS + 1];

	private static int MAX_INFO_CNT = 40;

	private String[] SystemInfo = new String[MAX_INFO_CNT];

	private int SystemInfoCnt = 0;

	private int pass_cnt = 0;
	private String MYLOG_PATH_SD = "hrrobotlog";
	private boolean need_sd_log = false;
	
	private final static int LASER_OFF = 0;
	private final static int LASER_ON = 1;
	private int laser_ctrl = LASER_OFF;
	
	private final static int MHE_ARM_OPEN = 1;
	private final static int MHE_ARM_CLOSE = 0;
	private int machine_arm_ctrl = MHE_ARM_CLOSE;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "program startup");
		init_log_time();

		start_time = System.currentTimeMillis();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		log_pass_time("Set ContentView OK");
		preferences = PreferenceManager.getDefaultSharedPreferences(this);

		log_pass_time("surfaceholder_vlc ok");

		/* 获取屏幕的宽度长度 */
		display = getWindowManager().getDefaultDisplay();
		screen_Orientation = display.getOrientation();
		if ((screen_Orientation == Surface.ROTATION_0)
				|| (screen_Orientation == Surface.ROTATION_180)) {
			screen_Width = display.getHeight();
			screen_Height = display.getWidth();
		} else {
			screen_Width = display.getWidth();
			screen_Height = display.getHeight();
		}

		Log.i(TAG, "Screen Resolution:" + screen_Height + " X " + screen_Width);
		log_pass_time("screen info ok");

		// btn_image = (Button)findViewById(R.id.button_image);
		btn_video_srcsel = (Button) findViewById(R.id.button_video_src);
		btn_video = (Button) findViewById(R.id.button_video);
		btn_control_mode = (Button) findViewById(R.id.button_control);
		btn_connect = (Button) findViewById(R.id.button_connect);
		btn_laser_ctrl = (Button) findViewById(R.id.button_laser_ctrl);
		btn_arm_ctrl = (Button) findViewById(R.id.button_arm_ctrl);
		
		img_camera = (ImageView) findViewById(R.id.imageView_camera);

		joystick = (JoystickView) findViewById(R.id.joystickView);   /* control joystick */
		joystickArm = (JoystickView) findViewById(R.id.joystickARM); /* arm joystick */
		
		txtAngle = (TextView) findViewById(R.id.TextViewX);
		txtSpeed = (TextView) findViewById(R.id.TextViewY);
		txtTCPState = (TextView) findViewById(R.id.TextViewTCPState);

		/* Seekbar object get */
		skb_angle = new SeekBar[5];
		skb_angle[0] = (SeekBar) findViewById(R.id.seekbar_angle1);
		skb_angle[1] = (SeekBar) findViewById(R.id.seekbar_angle2);
		skb_angle[2] = (SeekBar) findViewById(R.id.seekbar_angle3);
		skb_angle[3] = (SeekBar) findViewById(R.id.seekbar_angle4);
		skb_angle[4] = (SeekBar) findViewById(R.id.seekbar_angle5);

		// btn_image.getBackground().setAlpha(100); /*设置透明度为半透明 alpha 0-255*/
		btn_video_srcsel.getBackground().setAlpha(100); /* 设置透明度为半透明 */
		btn_video.getBackground().setAlpha(100); /* 设置透明度为半透明 */
		btn_control_mode.getBackground().setAlpha(100);
		btn_connect.getBackground().setAlpha(100);
		btn_laser_ctrl.getBackground().setAlpha(100);
		btn_arm_ctrl.getBackground().setAlpha(100);

		for (int i = 0; i < 5; i++) {
			skb_angle[i].setOnSeekBarChangeListener(skb_change_listener); /* 设置seekbar改变的listener */
			skb_angle[i].setMax(ARM_ANGLE_MAX);
			skb_angle[i].setProgress(ARM_ANGLE_MAX / 2);
		}

		// btn_image.setTextSize(screen_Width / btn_scale);
		btn_video_srcsel.setTextSize(screen_Width / btn_scale);
		btn_video.setTextSize(screen_Width / btn_scale);
		btn_control_mode.setTextSize(screen_Width / btn_scale);
		btn_connect.setTextSize(screen_Width / btn_scale);
		btn_laser_ctrl.setTextSize(screen_Width / btn_scale);
		btn_arm_ctrl.setTextSize(screen_Width / btn_scale);

		((TextView) findViewById(R.id.TextViewAngle)).setTextSize(screen_Width
				/ txtview_scale);
		((TextView) findViewById(R.id.TextViewSpeed)).setTextSize(screen_Width
				/ txtview_scale);
		((TextView) findViewById(R.id.TextViewTCPStateTxt))
				.setTextSize(screen_Width / txtview_scale);
		txtAngle.setTextSize(screen_Width / txtview_scale);
		txtSpeed.setTextSize(screen_Width / txtview_scale);
		txtTCPState.setTextSize(screen_Width / txtview_scale);

		// btn_image.setOnClickListener(image_acquire_listener);
		btn_video_srcsel.setOnClickListener(video_src_acquire_listener);
		btn_video.setOnClickListener(video_acquire_listener);

		btn_control_mode.setOnClickListener(ctrl_btn_listener);
		btn_connect.setOnClickListener(connect_listener);
		btn_laser_ctrl.setOnClickListener(btn_ctrl_listener);
		btn_arm_ctrl.setOnClickListener(btn_ctrl_listener);

		log_pass_time("all objects init ok");

		update_preference();
		tcp_ctrl_obj = new tcp_ctrl(getApplicationContext(),
				mHandler_UDP_SEND_MSG, dist_tcp_addr, dist_tcp_port);
		log_pass_time("tcp ok");
		// udp_ctrl_obj = new udp_ctrl(getApplicationContext(),
		// mHandler_UDP_MSG);
		// log_pass_time("udp ok");

		mContext = getApplicationContext();
		if (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK()) {
			txtTCPState.setText(R.string.tcpstate_online);
		} else {
			txtTCPState.setText(R.string.tcpstate_offline);
		}

		img_camera_bmp = Bitmap.createBitmap(img_width, img_height, img_cfg);

		/* 设置 遥控小车的界面的摇杆大小 */
		joyviewParams = joystick.getLayoutParams();
		joyviewParams.width = (int) (screen_Width / joystick_scale);
		joyviewParams.height = (int) (screen_Width / joystick_scale);
		joystick.setLayoutParams(joyviewParams);
		joystick.setOnJostickMovedListener(joystickctrl_listener);
		
		/* 设置 操作机械臂的界面的摇杆大小 */
		joyviewParams = joystickArm.getLayoutParams();
		joyviewParams.width = (int) (screen_Width / joystick_scale);
		joyviewParams.height = (int) (screen_Width / joystick_scale);
		joystickArm.setLayoutParams(joyviewParams);
		joystickArm.setOnJostickMovedListener(joystickarm_listener);

		log_pass_time("joystick ok");

		end_time = System.currentTimeMillis();

		Log.i(TAG, "startup use " + (end_time - start_time) + " ms");
		log_pass_time("program started");
		end_log_time();
		log_system_info();
		write_log2file("hrrobotup");
	}

	@Override
	protected void onResume() {
		/**
		 * 设置为横屏
		 */
		if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
		super.onResume();
	}

	public void log_pass_time(String Tag) {
		long pass_time;

		benchmark_end = System.currentTimeMillis();
		if (true) {
			if (pass_cnt > MAX_PASS) {
				return;
			} else {
				pass_time = benchmark_end - benchmark_start;
				time_pass[pass_cnt] = pass_time;
				pass_log[pass_cnt] = "PASS " + pass_cnt + " : " + Tag + ":"
						+ "costs " + pass_time + "ms";
				pass_cnt++;
			}
		}
		benchmark_start = System.currentTimeMillis();
	}

	public void init_log_time() {
		benchmark_start = System.currentTimeMillis();
		benchmark_end = System.currentTimeMillis();
		start_time = System.currentTimeMillis();
		end_time = System.currentTimeMillis();
	}

	public void end_log_time() {
		end_time = System.currentTimeMillis();

		if (true) {
			if (pass_cnt > (MAX_PASS + 1)) {
				return;
			} else {
				long pass_time = end_time - start_time;
				time_pass[pass_cnt] = pass_time;
				pass_log[pass_cnt] = "Program Startup Costs " + pass_time
						+ "ms";
				pass_cnt++;
			}
		}
	}

	public void log_system_info() {
		String ScreenInfo = "Screen Resolution:" + screen_Height + " X "
				+ screen_Width;
		String CpuInfo = "";
		String VersionInfo = "";

		CpuInfo = readfile2str("/proc/cpuinfo");
		VersionInfo = readfile2str("proc/version");

		SystemInfo[0] = CpuInfo;
		SystemInfo[1] = VersionInfo;
		SystemInfo[2] = ScreenInfo;
		SystemInfo[3] = getphoneinfo();

		SystemInfoCnt = 4;
	}

	public String getphoneinfo() {
		String phoneInfo = "Product: " + android.os.Build.PRODUCT;
		phoneInfo += "\n CPU_ABI: " + android.os.Build.CPU_ABI;
		phoneInfo += "\n TAGS: " + android.os.Build.TAGS;
		phoneInfo += "\n VERSION_CODES.BASE: "
				+ android.os.Build.VERSION_CODES.BASE;
		phoneInfo += "\n MODEL: " + android.os.Build.MODEL;
		phoneInfo += "\n SDK: " + android.os.Build.VERSION.SDK;
		phoneInfo += "\n VERSION.RELEASE: " + android.os.Build.VERSION.RELEASE;
		phoneInfo += "\n DEVICE: " + android.os.Build.DEVICE;
		phoneInfo += "\n DISPLAY: " + android.os.Build.DISPLAY;
		phoneInfo += "\n BRAND: " + android.os.Build.BRAND;
		phoneInfo += "\n BOARD: " + android.os.Build.BOARD;
		phoneInfo += "\n FINGERPRINT: " + android.os.Build.FINGERPRINT;
		phoneInfo += "\n ID: " + android.os.Build.ID;
		phoneInfo += "\n MANUFACTURER: " + android.os.Build.MANUFACTURER;
		phoneInfo += "\n USER: " + android.os.Build.USER;

		return phoneInfo;
	}

	public String readfile2str(String file_path) {
		String res = "";

		File file = new File(file_path);
		if (file.exists()) {
			try {
				String temp;
				FileReader fileReader = new FileReader(file);
				BufferedReader bufferedReader = new BufferedReader(fileReader);
				while (((temp = bufferedReader.readLine()) != null)) {
					res = res + "\n" + temp;
				}
				fileReader.close();
				bufferedReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return res;
	}

	public void write_log2file(String log_file_name) {
		update_preference();
		if (need_sd_log) {
			SimpleDateFormat formatter = new SimpleDateFormat(
					"yyyy-MM-dd HH_mm_ss");
			Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
			String log_file_date = formatter.format(curDate);
			String full_log_filename = log_file_name + "_" + log_file_date
					+ ".txt";
			String log_file_path = "";

			String sd_status = Environment.getExternalStorageState();
			if (!(sd_status.equals(Environment.MEDIA_MOUNTED))) {
				log_file_path = "/mnt/flash" + File.separator + MYLOG_PATH_SD;
				disp_toast("SD卡没有挂载,日志文件将写入到" + log_file_path + "目录下!");
			} else {
				log_file_path = Environment.getExternalStorageDirectory()
						+ File.separator + MYLOG_PATH_SD;
			}
			File log_file_Dir = new File(log_file_path);
			File log_file = new File(log_file_path, full_log_filename);
			try {
				if (!log_file_Dir.exists()) {/* 文件或者不存在就创建目录和文件 */
					if (!log_file_Dir.mkdir()) {
						disp_toast("创建启动日志文件目录失败!");
						return;
					} else {
						if (!log_file.exists()) {
							log_file.createNewFile(); /* 创建文件 */
						}
					}
				}
				// 后面这个参数代表是不是要接上文件中原来的数据，不进行覆盖
				FileWriter filerWriter = new FileWriter(log_file, true);
				BufferedWriter bufWriter = new BufferedWriter(filerWriter);
				for (int cnt = 0; cnt < pass_cnt; cnt++) {
					bufWriter.write(pass_log[cnt]);
					bufWriter.newLine();
				}
				bufWriter.newLine();

				for (int cnt = 0; cnt < SystemInfoCnt; cnt++) {
					bufWriter.write(SystemInfo[cnt]);
					bufWriter.newLine();
				}
				bufWriter.close();
				filerWriter.close();
				disp_toast("启动日志文件已生成位于" + log_file.getAbsolutePath());
			} catch (Exception e) {
				Log.e(TAG, "Write Log File Failed! " + e.getMessage());
			}
		}
	}

	public class SHCallback implements SurfaceHolder.Callback {
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
		}

		public void surfaceCreated(SurfaceHolder holder) {
			Log.v(TAG, "surfaceCreated");
			try {
				vlcmediaPlayer = new MediaPlayer();
				vlcmediaPlayer.setDisplay(surfaceholder_vlc);
			} catch (Exception e) {
				Log.e(TAG, "error: " + e.getMessage());
			}
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	public void update_preference() {
		int temp;
		try {
			video_addr[0] = preferences.getString(
					getResources().getString(R.string.videoaddr1),
					CAR_VIDEO_ADDR);
			video_addr[1] = preferences.getString(
					getResources().getString(R.string.videoaddr2),
					ARM_VIDEO_ADDR);
			video_addr[2] = preferences.getString(
					getResources().getString(R.string.videoaddr3),
					OPENCV_VIDEO_ADDR);
			dist_tcp_addr = preferences.getString(
					getResources().getString(R.string.distipaddr),
					DIST_TCPIPADDR);

			try {
				temp = Integer.parseInt((preferences.getString(getResources()
						.getString(R.string.disttcpport), String
						.valueOf(DIST_TCPPORT))));
				dist_tcp_port = temp;
			} catch (Exception e) {
				SharedPreferences.Editor editor = preferences.edit();
				editor.putString(
						getResources().getString(R.string.disttcpport),
						String.valueOf(dist_tcp_port));
				editor.commit();
			}
			vlc_video_mode = true;// preferences.getBoolean(getResources().getString(R.string.vlcvideostate),
									// false);
			player_sel = false;// preferences.getBoolean(getResources().getString(R.string.playersel),
								// false);
			need_sd_log = false;// preferences.getBoolean(getResources().getString(R.string.needsdlog),
								// false);
		} catch (Exception e) {
			Log.d(TAG, e.toString());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.Settings) {
			startActivityForResult(new Intent(this, Preferences.class),
					REQ_SYSTEM_SETTINGS);
		} else {
			if (show_camera2LCD_flag) {
				disp_toast("正在动态显示摄像头图像到液晶,请先退出该模式,再进行操作!");
				return true;
			}
			switch (item.getItemId()) {
			/*
			 * case R.id.SetLCDClrScreen: post_clr_screen_msg(); break; case
			 * R.id.SetLCDString: post_set_screen_with_string_msg(); break; case
			 * R.id.SetLCDWithPic: post_set_screen_with_pic_msg(); break;
			 */
			case R.id.Settings:

				break;
			default:
				break;
			}
		}
		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			// 按下的如果是BACK，同时没有重复
			Log.d(TAG, "Program Exit!");
			System.exit(0);
		}
		return super.onKeyDown(keyCode, event);
	}

	private JoystickMovedListener joystickctrl_listener = new JoystickMovedListener() {

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
			Thread.yield(); /* 进程主动让出 控制权，这样的话 在操作摇杆时 还是可以显示动态图像的虽然效果不好 */
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

	private JoystickMovedListener joystickarm_listener = new JoystickMovedListener() {

		@Override
		public void OnMoved(int pan, int tilt) {
			int operate_x = 0;
			int operate_y = 0;

			operate_x = pan;
			operate_y = -tilt;
			calc_arm_xy(operate_x, operate_y);
			
			checkSendOperateArmMsg();
			Thread.yield(); /* 进程主动让出 控制权，这样的话 在操作摇杆时 还是可以显示动态图像的虽然效果不好 */
		}

		@Override
		public void OnReleased() {
			Thread.yield();
		}

		public void OnReturnedToCenter() {
			arm_x_offset = 0;
			arm_y_offset = 0;
			checkSendOperateArmMsg();
			Thread.yield();
		};
	};
	
	/* 测试是否角度和速度没有改变 并发送控制小车命令 */
	private void checkSendOperateCarMsg() {
		// tcp_ctrl_obj.mTcp_ctrl_client.tcp_connect();
		if (!((operate_angle == operate_angle_last) && (operate_speed == operate_speed_last))) {
			if ((!follow_road_flag)
					&& (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK())) { /*
																		 * 非巡线模式才可以进行遥控
																		 * 并且当前socket可用才进行数据发送
																		 */
				postOperateCarMessage(operate_angle, operate_speed);
			}
			operate_angle_last = operate_angle;
			operate_speed_last = operate_speed;
		}
	}

	
	
	/* 发送获取角度控制和速度控制的命令 */
	private void postOperateCarMessage(int angle, int speed) {
		short ctrl_cmd;
		short ctrl_prefix;
		byte[] msg = new byte[4];

		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		ctrl_cmd = ctrlcmds.OPERATE_CAR;
		msg[0] = (byte) (angle & 0xff);
		msg[1] = (byte) ((angle >> 8) & 0xff);
		msg[2] = (byte) (speed & 0xff);
		msg[3] = (byte) ((speed >> 8) & 0xff);

		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}

	/* 通过当前坐标计算角度和速度信息 */
	private void calc_speed_and_angle(int operate_x, int operate_y) {
		operate_speed = (int) Math.sqrt((operate_x * operate_x)
				+ (operate_y * operate_y));

		if (operate_y < 0) {
			operate_speed = -operate_speed;
		}

		if (operate_x == 0) {
			if (operate_y == 0) {
				operate_angle = 0;
			} else if (operate_y > 0) {
				operate_angle = 90;
			} else {
				operate_angle = -90;
			}
		} else if (operate_y == 0) {
			if (operate_x == 0) {
				operate_angle = 0;
			} else if (operate_x > 0) {
				operate_angle = 0;
			} else {
				operate_angle = 180;
			}
		} else {
			operate_angle = (int) ((Math.atan2(operate_y, operate_x) / Math.PI) * 180);
		}

		if (operate_speed == 0) {
			operate_angle = 0;
		} else if (operate_speed > 0) {
			operate_angle = 90 - operate_angle;
		} else {
			operate_angle = operate_angle + 90;
		}

		if (operate_speed > MAX_SPEED_UNIT) {
			operate_speed = MAX_SPEED_UNIT;
		} else if (operate_speed < -MAX_SPEED_UNIT) {
			operate_speed = -MAX_SPEED_UNIT;
		}
		operate_speed = operate_speed * SPEED_SCALE;

	}
	
	/* 计算机械臂的X,Y偏移值 */
	private void calc_arm_xy(int operate_x, int operate_y) {
		if (operate_x > MAX_ARM_UNIT){
			operate_x = MAX_ARM_UNIT;
		}else if (operate_x < -MAX_ARM_UNIT){
			operate_x = -MAX_ARM_UNIT;
		}
		
		if (operate_y > MAX_ARM_UNIT){
			operate_y = MAX_ARM_UNIT;
		}else if (operate_y < -MAX_ARM_UNIT){
			operate_y = -MAX_ARM_UNIT;
		}
		
		arm_x_offset = operate_x * ARM_X_SCALE;
		arm_y_offset = operate_y * ARM_Y_SCALE;		
	}
	
	/* 测试机械臂的XY是否没有改变 并发送控制机械臂命令 */
	private void checkSendOperateArmMsg() {
		if (!((arm_x_offset == arm_x_offset_last) && (arm_y_offset == arm_y_offset_last))) {
			if ((tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK())) { 
				/*
				 * 当前socket可用才进行数据发送
				 */
				postOperateArmMessage(arm_x_offset, arm_y_offset);
			}
			arm_x_offset_last = arm_x_offset;
			arm_y_offset_last = arm_y_offset;
		}
	}
	
	/* 发送获取机械臂XY控制的命令 */
	private void postOperateArmMessage(int x, int y) {
		short ctrl_cmd;
		short ctrl_prefix;
		byte[] msg = new byte[4];

		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		ctrl_cmd = ctrlcmds.OPERATE_ARM;
		msg[0] = (byte) (x & 0xff);
		msg[1] = (byte) ((x >> 8) & 0xff);
		msg[2] = (byte) (y & 0xff);
		msg[3] = (byte) ((y >> 8) & 0xff);

		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}

	private void adjustArmAngle(int skb_id, int progress) {
		int arm_id = 0;
		int arm_angle = 0;

		switch (skb_id) {
		case R.id.seekbar_angle1:
			arm_id = 0;
			break;
		case R.id.seekbar_angle2:
			arm_id = 1;
			break;
		case R.id.seekbar_angle3:
			arm_id = 2;
			break;
		case R.id.seekbar_angle4:
			arm_id = 3;
			break;
		case R.id.seekbar_angle5:
			arm_id = 4;
			break;
		default:
			return;
		}
		arm_angle = progress;
		arm_angle_now[arm_id] = arm_angle;
		checkSendAdjustARMMsg(arm_id);
		// Log.d(TAG, "Adjust ARM " + arm_id + " to angle " + arm_angle);

	}

	/* 检查是否可以发送机械臂调节数据 */
	private void checkSendAdjustARMMsg(int arm_id) {
		if (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK()) { /* 当前socket可用才进行数据发送 */
			if (arm_angle_now[arm_id] != arm_angle_last[arm_id]) {
				arm_angle_last[arm_id] = arm_angle_now[arm_id];
				postAdjustARMMsg(arm_id, arm_angle_last[arm_id]);
			}
		}
	}

	/* 发送调节机械臂的指令 */
	private void postAdjustARMMsg(int arm_id, int arm_angle) {
		short ctrl_cmd;
		short ctrl_prefix;
		byte[] msg = new byte[4];

		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		ctrl_cmd = ctrlcmds.ADJUST_ARM_ANGLE;
		msg[0] = (byte) (arm_id & 0xff);
		msg[1] = (byte) ((arm_id >> 8) & 0xff);
		msg[2] = (byte) (arm_angle & 0xff);
		msg[3] = (byte) ((arm_angle >> 8) & 0xff);

		Log.d(TAG, "Adjust ARM " + arm_id + " to angle " + arm_angle);
		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}

	/* seekbar 改变时的listener */
	private OnSeekBarChangeListener skb_change_listener = new OnSeekBarChangeListener() {

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			// Log.d(TAG, seekBar.getId() + ":" + progress + ":" + fromUser);
			if (fromUser) {
				adjustArmAngle(seekBar.getId(), seekBar.getProgress());
			}
			Thread.yield();
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			Thread.yield();
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			// Log.d(TAG, seekBar.getId() + ":" + seekBar.getProgress());
			// adjustArmAngle(seekBar.getId(), seekBar.getProgress());
			Thread.yield();
		}
	};

	private final Handler mHandler_UDP_SEND_MSG = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_DATA_REC:
				short ctrlcmd = (Short) (msg.obj);
				switch (ctrlcmd) {
				case ctrlcmds.ACQUIRE_CAMERA_IMAGE:
					try {
						udp_ctrl_obj.send_image_frames(0, image_binary_data);
						for (int i = 0; i < img_size; i++) {
							image_binary_data[i] = (byte) (image_binary_data[i] + 10);
						}
					} catch (InterruptedException e) {
						Log.e(TAG,
								"Error in acquire image command :"
										+ e.getMessage());
					}
					break;
				case ctrlcmds.ACQUIRE_CAMERA_VIDEO_START:
					while (video_cnt < 20) {
						video_cnt += 1;
						for (int i = 0; i < img_size; i++) {
							image_binary_data[i] = (byte) (image_binary_data[i] + 10);
						}
						try {
							udp_ctrl_obj.send_image_frames(video_cnt,
									image_binary_data);
							Thread.yield();
						} catch (InterruptedException e) {
							Log.e(TAG,
									"Error in acquire video command :"
											+ e.getMessage());
						}
					}
					video_cnt = 0;
					break;

				default:
					break;
				}
				break;

			case MSG_DISPLAY_TOAST:
				disp_toast((String) msg.obj);
				break;
			case (MSG_FIX_PREFERENCE + FIX_IP_PREFERENCE):
				String ip = (String) msg.obj;
				SharedPreferences.Editor editor = preferences.edit();
				editor.putString(getResources().getString(R.string.distipaddr),
						ip);
				editor.commit();
				break;
			default:
				break;
			}

		}
	};

	private final Handler mHandler_video_process = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_VIDEO_UPDATE:
				img_camera.setImageBitmap(img_camera_bmp);
				break;
			case MSG_VIDEO_ERROR:
				((Button) findViewById(R.id.button_video))
						.setText(R.string.button_video_start);
				disp_toast("Getting remote video failed,please check the video address!");
				// disp_toast("获取视频数据失败,请确认视频网址是否正确!");
				img_camera.setImageResource(R.drawable.zynq_logo);
				break;
			case MSG_VIDEO_END:
				if (!video_flag){ /*如果此时没有在显示视频数据才显示结束按钮以及切换LOGO显示*/
					((Button) findViewById(R.id.button_video))
						.setText(R.string.button_video_start);
					// disp_toast("Getting remote video failed,please check the video address!");
					img_camera.setImageResource(R.drawable.zynq_logo);
				}
				
				// disp_toast("获取视频数据失败,请确认视频网址是否正确!");
				break;
			default:
				break;
			}
		}
	};

	private final Handler mHandler_UDP_MSG = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			byte[] img_pixels_data = (byte[]) (msg.obj);
			for (int i = 0; i < img_size; i++) {
				img_pixels[i] = (0xFF000000) | (img_pixels_data[i] << 0)
						| (img_pixels_data[i] << 8)
						| (img_pixels_data[i] << 16);
			}
			try {
				Canvas m_canvas = new Canvas(img_camera_bmp);
				Paint m_paint = new Paint();
				ColorMatrix cm = new ColorMatrix();
				cm.setSaturation(0);
				ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
				m_paint.setColorFilter(f);
				m_canvas.drawBitmap(img_pixels, 0, (img_width >> 2) << 2, 0, 0,
						img_width, img_height, false, m_paint);
				// img_camera_bmp.setPixels(img_pixels, 0, (img_width>>2)<<2, 0,
				// 0, img_width, img_height);
				// img_camera_bmp.setDensity(Bitmap.DENSITY_NONE);
				// img_camera.setAlpha(255);
				img_camera.setImageBitmap(img_camera_bmp);

			} catch (Exception e) {
				Log.d(TAG, e.toString());
			}
		}
	};

	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case CONNECT_DIALOG_KEY:
			mDialog_Connect = new ProgressDialog(WifiRobotActivity.this);
			mDialog_Connect.setMessage("Trying to connect to TCP server ....");
			mDialog_Connect.setCancelable(false);
			return mDialog_Connect;
		case IMGCAP_DIALOG_KEY:
			mDialog_ImageCap = new ProgressDialog(WifiRobotActivity.this);
			mDialog_ImageCap
					.setMessage("Trying to capture image from car camera ....");
			mDialog_ImageCap.setCancelable(false);
			return mDialog_ImageCap;
		case VIDEOCAP_DIALOG_KEY:
			mDialog_VideoCap = new ProgressDialog(WifiRobotActivity.this);
			mDialog_VideoCap
					.setMessage("Trying to capture video from car camera ....");
			mDialog_VideoCap.setCancelable(false);
			return mDialog_VideoCap;
		default:
			return null;
		}
	}

	private OnClickListener connect_listener = new OnClickListener() {
		public void onClick(View v) {
			update_preference();
			showDialog(CONNECT_DIALOG_KEY);
		}
	};

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case CONNECT_DIALOG_KEY:
			ConnectProgressThread mConnectProgressThread = new ConnectProgressThread(
					progress_handler);
			mConnectProgressThread.start();
			break;
		case IMGCAP_DIALOG_KEY:
			ImageCapProgressThread mCapProgressThread = new ImageCapProgressThread(
					progress_handler, IMGCAP_DIALOG_KEY);
			mCapProgressThread.start();
			break;
		case VIDEOCAP_DIALOG_KEY:
			ImageCapProgressThread mCapProgressThread2 = new ImageCapProgressThread(
					progress_handler, VIDEOCAP_DIALOG_KEY);
			mCapProgressThread2.start();
			break;
		default:
			break;
		}

	}

	// Define the Handler that receives messages from the thread and update the
	// progress
	final Handler progress_handler = new Handler() {
		public void handleMessage(Message msg) {

			if (msg.what <= VIDEOCAP_DIALOG_KEY) {
				dismissDialog(msg.what);
			}

			switch (msg.what) {
			case CONNECT_DIALOG_KEY:
				if (msg.obj != null) {
					disp_toast((String) msg.obj);
				}
				if (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK()) {
					txtTCPState.setText(R.string.tcpstate_online);
				} else {
					txtTCPState.setText(R.string.tcpstate_offline);
				}
				break;
			case IMGCAP_DIALOG_KEY:
				image_ready_flag = (Boolean) (msg.obj);

				if (image_ready_flag == true) {
					img_camera.setImageBitmap(img_camera_bmp);
				} else {
					disp_toast("Getting remote image failed,please check the video address!");
				}
				break;
			case VIDEOCAP_DIALOG_KEY:
				video_ready_flag = (Boolean) (msg.obj);
				if (video_ready_flag == true) {
					img_camera.setImageBitmap(img_camera_bmp);
					m_DrawVideo = new DrawVideo(cur_video_addr,
							mHandler_video_process);
					m_DrawVideo.start();
					btn_video.setText(R.string.button_video_stop);
					video_flag = true;
				} else {
					disp_toast("Getting remote video failed,please check the video address!");
				}
				break;
			case MSG_DISPLAY_TOAST:
				break;

			default:
				break;
			}
		}
	};

	/**  */
	private class ConnectProgressThread extends Thread {
		Handler mHandler;
		String msg = null;

		ConnectProgressThread(Handler h) {
			mHandler = h;
		}

		public void run() {
			if (tcp_ctrl_obj.mTcp_ctrl_client.updateIPandPort(dist_tcp_addr,
					dist_tcp_port)
					|| (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK() == false)) { 
				/*
				 * if ip or port updated or socket not opened
				 */
				tcp_ctrl_obj.mTcp_ctrl_client.tcp_connect(true);
			} else {
				msg = new String("Already Connectted to TCP Server @"
						+ dist_tcp_addr + ":" + dist_tcp_port);
				// mHandler.obtainMessage(MSG_DISPLAY_TOAST).sendToTarget();
			}
			mHandler.obtainMessage(CONNECT_DIALOG_KEY, msg).sendToTarget();
		}
	}

	private class ImageCapProgressThread extends Thread {
		Handler mHandler;
		boolean image_ok = false;
		int dialog_key;

		ImageCapProgressThread(Handler h, int id) {
			mHandler = h;
			dialog_key = id;
		}

		public void run() {
			image_ok = get_remote_image(cur_video_addr);
			mHandler.obtainMessage(dialog_key, image_ok).sendToTarget();

		}
	}


	private OnClickListener image_acquire_listener = new OnClickListener() {
		public void onClick(View v) {
			post_ctrl_btnclk_msg(v.getId());
		}
	};

	private OnClickListener video_src_acquire_listener = new OnClickListener() {
		public void onClick(View v) {
			process_video_src_select(v.getId());

		}
	};

	private OnClickListener video_acquire_listener = new OnClickListener() {
		public void onClick(View v) {
			post_ctrl_btnclk_msg(v.getId());
		}
	};

	private OnClickListener ctrl_btn_listener = new OnClickListener() {
		public void onClick(View v) {
			/* 根据小车控制按钮的id来处理事件 */
			post_ctrl_btnclk_msg(v.getId());
		}
	};
	
	private OnClickListener btn_ctrl_listener = new OnClickListener() {
		public void onClick(View v) {
			/* 发送激光控制命令 */
			postBtnCtrlMsg(v.getId());
		}
	};
	
	private void postBtnCtrlMsg(int btn_id) {
		short ctrl_cmd;
		short ctrl_prefix;
		byte[] msg = new byte[1];
		Button btn;

		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		
		btn = (Button) findViewById(btn_id);

		switch (btn_id){
			case R.id.button_laser_ctrl:
				ctrl_cmd = ctrlcmds.LASER_CTRL;
				if (laser_ctrl == LASER_OFF){
					btn.setText(R.string.button_laser_off);
					laser_ctrl = LASER_ON;
				}else{
					btn.setText(R.string.button_laser_on);
					laser_ctrl = LASER_OFF;
				}
				msg[0] = (byte) (laser_ctrl & 0xff);
				Log.d(TAG, "Switch Laser Ctrl "  + " to  " + laser_ctrl);
				break;
			case R.id.button_arm_ctrl:
				ctrl_cmd = ctrlcmds.ARM_OC_CTRL;
				if (machine_arm_ctrl == MHE_ARM_CLOSE){
					btn.setText(R.string.button_arm_close);
					machine_arm_ctrl = MHE_ARM_OPEN;
				}else{
					btn.setText(R.string.button_arm_open);
					machine_arm_ctrl = MHE_ARM_CLOSE;
				}
				msg[0] = (byte) (machine_arm_ctrl & 0xff);
				Log.d(TAG, "Switch Arm Open_Close Ctrl "  + " to  " + machine_arm_ctrl);
				break;
			default:
				return;
		}
		
		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}

	private OnSharedPreferenceChangeListener sys_set_chg_listener = new OnSharedPreferenceChangeListener() {

		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			if (key == getResources().getString(R.string.videoaddr1)) {
				video_addr[0] = preferences.getString(key, CAR_VIDEO_ADDR);
			} else if (key == getResources().getString(R.string.videoaddr2)) {
				video_addr[1] = preferences.getString(key, ARM_VIDEO_ADDR);
			} else if (key == getResources().getString(R.string.videoaddr3)) {
				video_addr[2] = preferences.getString(key, OPENCV_VIDEO_ADDR);
			} else if (key == getResources().getString(R.string.vlcvideostate)) {

			} else if ((key == getResources().getString(R.string.distipaddr))
					|| (key == getResources().getString(R.string.disttcpport))) {
				dist_tcp_addr = preferences.getString(
						getResources().getString(R.string.distipaddr),
						DIST_TCPIPADDR);
				dist_tcp_port = Integer.parseInt((preferences.getString(
						getResources().getString(R.string.disttcpport),
						String.valueOf(DIST_TCPPORT))));
				tcp_ctrl_obj.mTcp_ctrl_client.tcpreconnect(dist_tcp_addr,
						dist_tcp_port);
			}
		}
	};

	/* 检查是否可以发送切换视频模式 */
	private void checkSendSwitchVideoModeMsg(int videomode) {
		if (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK()) { /* 当前socket可用才进行数据发送 */
			postSwitchVideoModeMsg(videomode);
		}
	}

	/* 发送切换视频模式的指令 */
	private void postSwitchVideoModeMsg(int videomode) {
		short ctrl_cmd;
		short ctrl_prefix;
		byte[] msg = new byte[1];

		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		ctrl_cmd = ctrlcmds.ADJUST_VIDEO_MODE;
		msg[0] = (byte) (videomode & 0xff);

		Log.d(TAG, "Switch Video Mode "  + " to  " + videomode);
		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}
	
	/* 处理选择视频源的消息 */
	private void process_video_src_select(int btn_id) {
		Button btn;
		String toast_str;

		switch (btn_id) {
		case R.id.button_video_src:
			update_preference();
			btn = (Button) findViewById(R.id.button_video_src);
			video_source_sel += 1;
			if (video_source_sel >= MAX_VIDEO_SRC_CNT) {
				video_source_sel = 0;
			}
			cur_video_addr = video_addr[video_source_sel]; /* 选择正确视频源 */
			toast_str = new String(" Address : " + cur_video_addr);
			switch (video_source_sel) {
			case CAR_VIDEO_SRC:
				btn.setText(R.string.button_video_src_car);
				toast_str = "Switch to car video ," + toast_str; 
				break;
			case ARM_VIDEO_SRC:
				btn.setText(R.string.button_video_src_arm);
				toast_str = "Switch to arm video ," + toast_str;
				break;
			case OPENCV_VIDEO_SRC:
				btn.setText(R.string.button_video_src_opencv);
				toast_str = "Switch to openCV video ," + toast_str;
				break;
			}
			/*测试并发送切换的视频模式*/
			checkSendSwitchVideoModeMsg(video_source_sel);
			
			//disp_toast(toast_str);
			
			/*
			 * 如果当前的正在采集视频数据 就需要进行切换, 并且先要将之前的视频掐掉
			 */
			if (video_flag == true) {
				/* 先退出之前的视频源 */
				if (m_DrawVideo != null) {
					m_DrawVideo.exit_thread();
					//m_DrawVideo.stop();
					//while(m_DrawVideo.poll_thread_state()); /*等待线程结束 测试会卡死*/
				}
				
				
				btn_video.setText(R.string.button_video_start);
				img_camera.setImageResource(R.drawable.zynq_logo);
				video_flag = false;
				/* 切换为新的视频源 */
				showDialog(VIDEOCAP_DIALOG_KEY);
			}

			break;
		default:
			return;
		}

	}

	/* 处理对小车控制按钮的消息 */
	private void post_ctrl_btnclk_msg(int btn_id) {
		short ctrl_cmd = 0;
		short ctrl_prefix = 0;
		byte[] msg = null;
		Button btn;
		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		switch (btn_id) {
		case R.id.button_image:
			update_preference();
			if (vlc_video_mode == false) {
				ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
						ctrl_prefixs.read_request,
						ctrl_prefixs.mass_data_request, ctrl_prefixs.withack);
				ctrl_cmd = (short) (ctrlcmds.ACQUIRE_CAMERA_IMAGE);
			} else {
				showDialog(IMGCAP_DIALOG_KEY);
				return;
			}
			break;

		case R.id.button_video:
			btn = (Button) findViewById(R.id.button_video);
			update_preference();
			if (vlc_video_mode == false) {
				if (video_flag == false) {
					ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
							ctrl_prefixs.read_request,
							ctrl_prefixs.mass_data_request,
							ctrl_prefixs.withack);
					ctrl_cmd = (short) (ctrlcmds.ACQUIRE_CAMERA_VIDEO_START);
					btn.setText(R.string.button_video_stop);
					video_flag = true;
				} else {
					ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
							ctrl_prefixs.read_request,
							ctrl_prefixs.mass_data_request,
							ctrl_prefixs.withack);
					ctrl_cmd = (short) (ctrlcmds.ACQUIRE_CAMERA_VIDEO_STOP);
					video_flag = false;
					btn.setText(R.string.button_video_start);
				}
			} else {
				if (player_sel) {
					play_stream_withotherplayer();
				} else {
					if (video_flag == false) {
						cur_video_addr = video_addr[video_source_sel]; /* 选择正确视频源 */
						showDialog(VIDEOCAP_DIALOG_KEY);
					} else {
						if (m_DrawVideo != null) {
							m_DrawVideo.exit_thread();
							//m_DrawVideo.stop();
							//while(m_DrawVideo.poll_thread_state()); /*等待线程结束*/
						}
						btn.setText(R.string.button_video_start);
						img_camera.setImageResource(R.drawable.zynq_logo);
						video_flag = false;
					}
				}

				return;
			}
			break;

		case R.id.button_control:
			if (auto_control_mode == false) {
				ctrl_cmd = (short) (ctrlcmds.ENTER_AUTO_NAV_MODE);
				btn_control_mode.setText(R.string.button_realcontrol);
				auto_control_mode = true;
			} else {
				ctrl_cmd = (short) (ctrlcmds.ENTER_REAL_CONTROL_MODE);
				btn_control_mode.setText(R.string.button_autocontrol);
				auto_control_mode = false;
			}
			break;

		default:
			return;
		}

		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}

	public boolean get_remote_image(String url_addr) {
		boolean flag = false;

		String m_video_addr = CAR_VIDEO_ADDR;
		HttpURLConnection m_video_conn = null;
		InputStream m_InputStream = null;
		HttpGet httpRequest;
		HttpClient httpclient = null;
		HttpResponse httpResponse;

		try {
			m_video_addr = url_addr;
			Log.d(TAG, "start get url");
			httpRequest = new HttpGet(m_video_addr);

			Log.d(TAG, "open connection");
			httpclient = new DefaultHttpClient();

			Log.d(TAG, "begin connect");
			httpResponse = httpclient.execute(httpRequest);
			Log.d(TAG, "get InputStream");
			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				Log.d(TAG, "decodeStream");
				m_InputStream = httpResponse.getEntity().getContent();
				img_camera_bmp = BitmapFactory.decodeStream(m_InputStream);// 从获取的流中构建出BMP图像
			}
			Log.d(TAG, "decodeStream end");

			flag = true;
		} catch (Exception e) {
			Log.e(TAG, "Error In Get Image Msg:" + e.getMessage());
			flag = false;
		} finally {
			if (m_video_conn != null) {
				m_video_conn.disconnect();
			}
			if ((httpclient != null)
					&& (httpclient.getConnectionManager() != null)) {
				httpclient.getConnectionManager().shutdown(); /* 及时关闭httpclient释放资源 */
			}
		}

		return flag;
	}

	public boolean vlc_video_process() {
		boolean play_state = false;
		try {
			if (vlcmediaPlayer.isPlaying()) {
				vlcmediaPlayer.stop();
			}
			vlcmediaPlayer.reset();
			vlcmediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			vlcmediaPlayer.setWakeMode(getApplicationContext(),
					PowerManager.PARTIAL_WAKE_LOCK);
			vlcmediaPlayer.setDataSource(cur_video_addr);
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

	public void play_stream_withotherplayer() {
		try {
			Intent it = new Intent(Intent.ACTION_VIEW);
			Uri uri = Uri.parse(cur_video_addr);
			it.setDataAndType(uri, "video/*");
			startActivity(it);
		} catch (Exception e) {
			Log.e(TAG, "Error In Play With Other Player");
		}
	}

	private void post_tcp_msg(short ctrl_prefix, short ctrl_cmd, byte[] msg) {
		ctrl_frame mCtrl_frame = new ctrl_frame(ctrl_prefix, ctrl_cmd, msg);
		byte[] tcp_msg = new byte[4 + mCtrl_frame.datalength];
		mCtrl_frame.encode_frametobytes(tcp_msg);
		tcp_ctrl_obj.mTcp_ctrl_client.post_msg(tcp_msg);
		if (D) {
			Log.d(TAG, "The Send TCP Message is As Follows:");
			mCtrl_frame.display_ctrl_frame();
		}
	}

	private void post_clr_screen_msg() {
		short ctrl_cmd = 0;
		short ctrl_prefix = 0;
		byte[] msg = null;

		ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
				ctrl_prefixs.write_request, ctrl_prefixs.less_data_request,
				ctrl_prefixs.withoutack);
		ctrl_cmd = (short) (ctrlcmds.SET_LCD_CLR_SCREEN);
		post_tcp_msg(ctrl_prefix, ctrl_cmd, msg);
	}

	private void post_set_screen_with_string_msg() {

		LayoutInflater inflater = (LayoutInflater) (WifiRobotActivity.this)
				.getSystemService(LAYOUT_INFLATER_SERVICE); /*
															 * 必须使用WifiRobotActivity
															 * .this
															 */
		final View view = inflater.inflate(R.layout.dialog, null);
		AlertDialog.Builder builder2 = new AlertDialog.Builder(
				WifiRobotActivity.this);
		builder2.setView(view);
		builder2.setTitle("设置LCD何处显示何字符")
				.setPositiveButton("确定", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						short CoordX;
						short CoordY;
						short font;
						short fontcolor;
						short frame_prefix = 6;
						CharSequence lcdstring;
						short ctrl_cmd = 0;
						short ctrl_prefix = 0;
						TextView txtviewX = (TextView) view
								.findViewById(R.id.editTextCoordX);
						TextView txtviewY = (TextView) view
								.findViewById(R.id.editTextCoordY);
						TextView txtviewStr = (TextView) view
								.findViewById(R.id.editTextLCDString);
						Spinner spinner_fontset = (Spinner) view
								.findViewById(R.id.spinnerFontSet);
						Spinner spinner_fontcolorset = (Spinner) view
								.findViewById(R.id.spinnerFontColorSet);

						try {
							/* 获取X,Y,以及即将写入液晶的字符 */
							try {
								CoordX = (short) Integer
										.parseInt((String) (txtviewX.getText()
												.toString()));
							} catch (Exception e) {
								CoordX = 0;
							}
							try {
								CoordY = (short) Integer
										.parseInt((String) (txtviewY.getText()
												.toString()));
							} catch (Exception e) {
								CoordY = 0;
							}
							try {
								font = (short) spinner_fontset
										.getFirstVisiblePosition();
							} catch (Exception e) {
								font = 0;
							}
							font = (short) (font + 1); /* adjust to ARM */

							try {
								fontcolor = (short) spinner_fontcolorset
										.getFirstVisiblePosition();
							} catch (Exception e) {
								fontcolor = 0;
							}

							if (CoordX > LCD_X_MAX) {
								CoordX = LCD_X_MAX;
							}
							if (CoordY > LCD_Y_MAX) {
								CoordY = LCD_Y_MAX;
							}
							lcdstring = txtviewStr.getText();

							/* 构造一个新的帧 */
							ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
									ctrl_prefixs.write_request,
									ctrl_prefixs.less_data_request,
									ctrl_prefixs.withoutack);
							ctrl_cmd = (short) (ctrlcmds.SET_LCD_SHOW_STRING);
							byte[] lcd_string_msg = new byte[frame_prefix
									+ lcdstring.length()];
							lcd_string_msg[0] = (byte) (CoordX & 0xff);
							lcd_string_msg[1] = (byte) ((CoordX >> 8) & 0xff);
							lcd_string_msg[2] = (byte) (CoordY & 0xff);
							lcd_string_msg[3] = (byte) ((CoordY >> 8) & 0xff);
							lcd_string_msg[4] = (byte) (font & 0xff);
							lcd_string_msg[5] = (byte) (fontcolor & 0xff);

							if (lcdstring.length() > 0) {
								for (int i = 0; i < lcdstring.length(); i++) {
									lcd_string_msg[i + frame_prefix] = (byte) lcdstring
											.charAt(i);
								}
							}
							post_tcp_msg(ctrl_prefix, ctrl_cmd, lcd_string_msg);
						} catch (Exception e) {
							if (e.getMessage() != null) {
								Log.e(TAG, e.getMessage());
							} else if (e.toString() != null) {
								Log.e(TAG, e.toString());
							} else {
								Log.e(TAG, "Error in send pic to lcd command");
							}
							Toast.makeText(view.getContext(), "设置液晶字符显示命令未发送!",
									Toast.LENGTH_SHORT).show();
							// disp_toast("设置液晶字符显示命令未发送!");
						}

						dialog.cancel();
					}
				}).create().show();

	}

	private void post_set_screen_with_pic_msg() {
		// if (tcp_ctrl_obj.mTcp_ctrl_client.isSocketOK()){
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("image/*");
		int requestCode = REQ_SET_WITH_PIC;
		startActivityForResult(intent, requestCode);
		// }else{
		// disp_toast("没有连接到TCP服务端,请退出后连接到相应无线网络!");
		// }
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

	private boolean systemsettingchange(int resultCode, Intent data) {
		boolean ifSucess = true;

		if (resultCode == RESULT_OK) {

		} else {
			Log.i(TAG, "None settings change");
		}
		return ifSucess;
	}

	private boolean send_image2lcd(int resultCode, Intent data) {
		boolean ifSucess = true;
		short ctrl_cmd = 0;
		short ctrl_prefix = 0;

		if (resultCode == RESULT_OK) {
			try {
				Uri uri = data.getData();
				ContentResolver cr = this.getContentResolver();
				Bitmap bitmap = BitmapFactory.decodeStream(cr
						.openInputStream(uri));
				Bitmap bitmap_cp = Bitmap.createScaledBitmap(bitmap, img_width,
						img_height, false);
				Bitmap bitmap_rgb = bitmap_cp.copy(img_cfg, true);
				Bitmap bitmap_bw = bitmap_cp.copy(img_cfg, true);
				byte[] grey_pixels = new byte[img_width * img_height];
				int threshold = 40;
				if ((threshold = convertRGB2GreyScale(bitmap_rgb, grey_pixels)) != 0) {
					int imagerawsize = img_width * img_height;
					int datasize = imagerawsize;
					int frame_prefix = 9;
					int data_offset = 0;
					int frame_cnt = 0;
					int data_length;
					while (datasize > 0) {
						if (datasize > (tcp_small_frame_size - frame_prefix)) {
							data_length = tcp_small_frame_size - frame_prefix;
							datasize = datasize - data_length;
						} else {
							data_length = datasize;
							datasize = 0;
						}
						byte[] grey_pixels_msg = new byte[data_length
								+ frame_prefix];
						grey_pixels_msg[0] = (byte) (img_width & 0xff);
						grey_pixels_msg[1] = (byte) ((img_width >> 8) & 0xff);
						grey_pixels_msg[2] = (byte) (img_height & 0xff);
						grey_pixels_msg[3] = (byte) ((img_height >> 8) & 0xff);
						grey_pixels_msg[4] = (byte) (frame_cnt & 0xff);
						grey_pixels_msg[5] = (byte) ((frame_cnt >> 8) & 0xff);
						grey_pixels_msg[6] = (byte) (data_length & 0xff);
						grey_pixels_msg[7] = (byte) ((data_length >> 8) & 0xff);
						grey_pixels_msg[8] = (byte) (threshold & 0xff);
						System.arraycopy(grey_pixels, data_offset,
								grey_pixels_msg, frame_prefix, data_length);
						data_offset = data_offset + data_length;

						ctrl_prefix = ctrl_prefixs.encode_ctrlprefix(
								ctrl_prefixs.write_request,
								ctrl_prefixs.less_data_request,
								ctrl_prefixs.withoutack);
						ctrl_cmd = (short) (ctrlcmds.SET_LCD_WITH_PIC);
						post_tcp_msg(ctrl_prefix, ctrl_cmd, grey_pixels_msg);
						Log.i(TAG, "Set LCD With Image Small Frame Index "
								+ frame_cnt + " Send!");
						Thread.sleep(50);
						frame_cnt++;
					}
					ImageView img = new ImageView(WifiRobotActivity.this); /*
																			 * 必须要用WifiRobotActivity
																			 * .
																			 * this
																			 * 否则就会报错
																			 */
					img.setMinimumWidth(320);
					img.setMinimumHeight(480);

					img.setScaleType(ScaleType.FIT_XY);
					img.setImageBitmap(bitmap_rgb);
					new AlertDialog.Builder(WifiRobotActivity.this)
							.setTitle("已经发送的图像灰度图如图所示").setView(img)
							.setPositiveButton("确定", null).show();

					if (convertRGB2BlackWhite(bitmap_bw, threshold)) {
						ImageView img_wb = new ImageView(WifiRobotActivity.this); /*
																				 * 必须要用WifiRobotActivity
																				 * .
																				 * this
																				 * 否则就会报错
																				 */
						img_wb.setMinimumWidth(320);
						img_wb.setMinimumHeight(480);

						img_wb.setScaleType(ScaleType.FIT_XY);
						img_wb.setImageBitmap(bitmap_bw);
						new AlertDialog.Builder(WifiRobotActivity.this)
								.setTitle("已经发送的图像二值化后如图所示").setView(img_wb)
								.setPositiveButton("确定", null).show();
					}
					// img_camera.setImageBitmap(bitmap_rgb);
					Thread.sleep(30); /* 等待ARM端处理完毕 */
				} else {
					ifSucess = false;
				}
			} catch (Exception e) {
				ifSucess = false;
				if (e.getMessage() != null) {
					Log.e(TAG, e.getMessage());
				} else if (e.toString() != null) {
					Log.e(TAG, e.toString());
				} else {
					Log.e(TAG, "Error in send pic to lcd command");
				}
			}
		} else {
			ifSucess = false;
		}

		if (!ifSucess) {
			disp_toast("采用图像来设置液晶命令未发送!");
		} else {
			disp_toast("采用图像来设置液晶命令已经全部发送!");
		}

		return ifSucess;
	}

	/* 将图像转换为灰度图,并且如果图像存在就将其灰度(不为0)返回,不存在返回灰度为0 */
	private int convertRGB2GreyScale(Bitmap bitmap, byte[] picPixels) {
		int threshold = 0;
		if (bitmap == null) {
			threshold = 0;
		} else {
			if (picPixels.length >= (bitmap.getHeight() * bitmap.getWidth())) { /* 如果返回灰度值的数组的大小足够了才进行转换 */
				int width = bitmap.getWidth();
				int height = bitmap.getHeight();
				long gray_sum = 0;
				int gray_mean = 0;
				int grayfrontmean = 0;
				int graybackmean = 0;
				int front = 0;
				int back = 0;
				int u = 0;// 灰度平均值
				long area = (height * (long) width);
				for (int i = 0; i < height; i++) {
					for (int j = 0; j < width; j++) {
						int col = bitmap.getPixel(j, i);
						int alpha = col & 0xFF000000;
						int red = (col & 0x00FF0000) >> 16;
						int green = (col & 0x0000FF00) >> 8;
						int blue = (col & 0x000000FF);
						int gray = (int) ((float) red * 0.3 + (float) green
								* 0.59 + (float) blue * 0.11);
						int newColor = alpha | (gray << 16) | (gray << 8)
								| gray;
						bitmap.setPixel(j, i, newColor);
						gray_sum = gray_sum + gray;
						picPixels[(i * width) + j] = (byte) gray;
					}
				}
				gray_mean = (int) (gray_sum / area);// 整个图的灰度平均值
				u = gray_mean;
				Log.i(TAG, "整个图的灰度平均值:" + (u & 0xff));

				threshold = u;
				if (threshold == 0) {
					threshold += 1;
				}
				Log.i(TAG, "最终计算出来的阈值是:" + (threshold & 0xff));
			} else {
				threshold = 0;
			}
		}
		return threshold;
	}

	private boolean convertRGB2BlackWhite(Bitmap bitmap, int threshold) {
		boolean ercd = true;

		if (bitmap == null) {
			ercd = false;
		} else {
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			for (int i = 0; i < height; i++) {
				for (int j = 0; j < width; j++) {
					int col = bitmap.getPixel(j, i);
					int alpha = col & 0xFF000000;
					int red = (col & 0x00FF0000) >> 16;
					int green = (col & 0x0000FF00) >> 8;
					int blue = (col & 0x000000FF);
					int gray = (int) ((float) red * 0.3 + (float) green * 0.59 + (float) blue * 0.11);
					if (gray > threshold) {
						gray = 255;
					} else {
						gray = 0;
					}
					int newColor = alpha | (gray << 16) | (gray << 8) | gray;
					bitmap.setPixel(j, i, newColor);
				}
			}
		}
		return ercd;
	}

	private void disp_toast(String msg) {
		Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
	}

	class DrawVideo extends Thread {
		private String m_video_addr = CAR_VIDEO_ADDR;
		private URL m_video_url;
		private HttpURLConnection m_video_conn;
		private InputStream m_InputStream;
		private Handler video_Handler;
		HttpGet httpRequest;
		HttpClient httpclient = null;
		HttpResponse httpResponse;
		Bitmap bmp = null;
		private boolean exit_flag = false;
		private boolean thread_state = false;

		public DrawVideo(String url_addr, Handler handler) {
			m_video_addr = url_addr;
			video_Handler = handler;
		}

		public void exit_thread() {
			exit_flag = true;
		}
		
		public boolean poll_thread_state(){
			return thread_state;
		}

		public boolean testconnection() {
			boolean flag = false;
			try {

				httpRequest = new HttpGet(m_video_addr);
				httpclient = new DefaultHttpClient();
				httpResponse = httpclient.execute(httpRequest);
				if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					m_InputStream = httpResponse.getEntity().getContent();
					bmp = BitmapFactory.decodeStream(m_InputStream);// 从获取的流中构建出BMP图像
				}
				if (bmp == null) {
					flag = false;
				} else {
					flag = true;
				}
			} catch (Exception e) {
				flag = false;
				Log.e(TAG, "Error In Get Video Msg:" + e.getMessage());
			}
			if (m_video_conn != null) {
				m_video_conn.disconnect();
			}
			if ((httpclient != null)
					&& (httpclient.getConnectionManager() != null)) {
				httpclient.getConnectionManager().shutdown(); /* 及时关闭httpclient释放资源 */
			}
			return flag;
		}

		public void run() {
			try {
				httpRequest = new HttpGet(m_video_addr);
				httpclient = new DefaultHttpClient();
				
				thread_state = true;
				while (!exit_flag) {
					httpResponse = httpclient.execute(httpRequest);
					
					if (!exit_flag){ /* 由于上面的语句执行的时间很长,因此这里还需要判断一次是否退出 */
						if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
							m_InputStream = httpResponse.getEntity().getContent();
							bmp = BitmapFactory.decodeStream(m_InputStream);// 从获取的流中构建出BMP图像
						}
	
						if ((bmp != null) && (!exit_flag) ) {
							img_camera_bmp = bmp;
							video_Handler.obtainMessage(MSG_VIDEO_UPDATE)
									.sendToTarget();
						}
						sleep(20);
					}
				}
				exit_flag = false;
			} catch (Exception e) {
				video_flag = false;
				Log.e(TAG, "Error In Get Video Msg:" + e.getMessage());
				video_Handler.obtainMessage(MSG_VIDEO_ERROR).sendToTarget();
			} finally {
				if (m_video_conn != null) {
					m_video_conn.disconnect();
				}
				if ((httpclient != null)
						&& (httpclient.getConnectionManager() != null)) {
					httpclient.getConnectionManager().shutdown(); /* 及时关闭httpclient释放资源 */
				}
				video_Handler.obtainMessage(MSG_VIDEO_END).sendToTarget(); /*正常结束无需操作 避免显示时串了*/
				Log.e(TAG, "End of Get Video");
			}
			thread_state = false;
		}
	}
}
