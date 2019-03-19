package com.example.asus.musicplayer;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.PropertyBean;
import data.Music;
import data.MusicList;

import static com.example.asus.musicplayer.R.id.listview_tv_artist_item;

public class MainActivity extends AppCompatActivity {

    // 显示组件
    private ImageButton imgBtn_Previous;
    private ImageButton imgBtn_PlayOrPause;
    private ImageButton imgBtn_Stop;
    private ImageButton imgBtn_Next;
    private ListView list;
    private TextView title;


    private MediaPlayer player = new MediaPlayer();


    //歌曲列表对象
    private ArrayList<Music> musicArrayList;

    // 当前歌曲的序号，下标从0开始
    private int number = 0;

    //播放状态
    private int status;

    //广播接收器
    private StatusChangedReceiver receiver;

    private RelativeLayout root_Layout;
    private TextView text_Current;
    private TextView text_Duration;
    private SeekBar seekBar;
    private TextView textView;
    private Handler seekBarHandler;

    //当前歌曲的持续时间和当前位置，作用于进度条
    private int duration;
    private int time;

    //进度条控制常量
    private static final int PROGRESS_INCREASE = 0;
    private static final int PROGRESS_PAUSE = 1;
    private static final int PROGRESS_RESET = 2;
    private Menu menu;

    /**
     * Called when the activity is first created.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViews();
        registerListeners();
        initMusicList();
        initListView();
        checkMusicfile();
        duration=0;
        time=0;
        // 绑定广播接收器，可以接收广播
        bindStatusChangedReceiver();
        initSeekBarHandler();
        startService(new Intent(this, MusicService.class));
        status = MusicService.COMMAND_STOP;
    }

    /**
     * 绑定广播接收器
     */
    private void bindStatusChangedReceiver() {
        receiver = new StatusChangedReceiver();
        IntentFilter filter = new IntentFilter(
                MusicService.BROADCAST_MUSICSERVICE_UPDATE_STATUS);
        registerReceiver(receiver, filter);
    }

    /**
     * 获取显示组件
     */
    private void findViews() {
        imgBtn_Previous = (ImageButton) findViewById(R.id.imageButton1);
        imgBtn_PlayOrPause = (ImageButton) findViewById(R.id.imageButton2);
        imgBtn_Stop = (ImageButton) findViewById(R.id.imageButton3);
        imgBtn_Next = (ImageButton) findViewById(R.id.imageButton4);
        list = (ListView) findViewById(R.id.listView1);
        textView=(TextView)findViewById(R.id.textView);
        seekBar = (SeekBar) findViewById(R.id.seekBar1);
        text_Current = (TextView) findViewById(R.id.textView1);
        text_Duration = (TextView) findViewById(R.id.textView2);
        root_Layout = (RelativeLayout) findViewById(R.id.relativeLayout1);
        title = (TextView)findViewById(R.id.listview_tv_title_item);
    }


    /**
     * 为显示组件注册监听器
     */
    private void registerListeners() {
        imgBtn_Previous.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                sendBroadcastOnCommand(MusicService.COMMAND_PREVIOUS);
            }
        });
        imgBtn_PlayOrPause.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                switch (status) {
                    case MusicService.STATUS_PLAYING:
                        sendBroadcastOnCommand(MusicService.COMMAND_PAUSE);
                        break;
                    case MusicService.STATUS_PAUSED:
                        sendBroadcastOnCommand(MusicService.COMMAND_RESUME);
                        break;
                    case MusicService.COMMAND_STOP:
                        sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
                    default:
                        break;
                }
            }
        });
        imgBtn_Stop.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                sendBroadcastOnCommand(MusicService.COMMAND_STOP);
                list.setCacheColorHint(1);

            }
        });
        imgBtn_Next.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
            }
        });
//        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                //改变选中状态
//                adapter.setCurrentItem(position);
//                //通知ListView改变状态
//                adapter.notifyDataSetChanged();
//            }
//        });
        list.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                number = position;
                sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(status != MusicService.STATUS_STOPPED){
                    time =seekBar.getProgress();
                    //更新文本
                    text_Current.setText(formatTime(time));
                    //发送广播给Musicservice,执行跳转
                    sendBroadcastOnCommand(MusicService.COMMAND_SEEK_TO);
                }
                if(status == MusicService.STATUS_PLAYING){
                    //发送广播给MusicService，执行跳转
                    sendBroadcastOnCommand(MusicService.COMMAND_SEEK_TO);
                    //进度条恢复移动
                    seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE,1000);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //进度条暂停移动
                seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
            }
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }
        });
    }



    /**
     * 初始化音乐列表对象
     */
    private void initMusicList() {
        musicArrayList = MusicList.getMusicList();
        //避免重复添加音乐
        if (musicArrayList.isEmpty()) {
            Cursor mMusicCursor = this.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null,
                    MediaStore.Audio.AudioColumns.TITLE);
            //标题
            int indexTitle = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE);
            //艺术家
            int indexArtist = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST);
            //总时长
            int indexTotalTime = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION);
            //路径
            int indexPath = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.DATA);

            /**通过mMusicCursor游标遍历数据库，并将Music类对象加载到ArrayList中*/
            for (mMusicCursor.moveToFirst(); !mMusicCursor.isAfterLast(); mMusicCursor
                    .moveToNext()) {
                String strTitle = mMusicCursor.getString(indexTitle);
                String strArtist = mMusicCursor.getString(indexArtist);
                String strTotoalTime = mMusicCursor.getString(indexTotalTime);
                String strPath = mMusicCursor.getString(indexPath);

                if (strArtist.equals("<unknown>")) {
                    strArtist = "无艺术家";
                    Music music = new Music(strTitle, strArtist, strPath, strTotoalTime);
                    musicArrayList.add(music);
                } else {
                    Music music = new Music(strTitle, strArtist, strPath, strTotoalTime);
                    musicArrayList.add(music);
                }
            }
        }
    }

    /**
     * 设置适配器并初始化listView
     */
    private void initListView() {
        List<Map<String, String>> list_map = new ArrayList<Map<String, String>>();
        HashMap<String, String> map;
        SimpleAdapter simpleAdapter;
        for (Music music : musicArrayList) {
            map = new HashMap<String, String>();
            map.put("musicName", music.getmusicName());
            map.put("musicArtist", music.getmusicArtist());
            list_map.add(map);
        }

        String[] from = new String[]{"musicName", "musicArtist"};
        int[] to = {R.id.listview_tv_title_item, listview_tv_artist_item};

        simpleAdapter = new SimpleAdapter(this, list_map, R.layout.listview, from, to);
        list.setAdapter(simpleAdapter);
    }

    /**
     * 如果列表没有歌曲，则播放按钮不可用，并提醒用户
     */
    private void checkMusicfile() {
        if (musicArrayList.isEmpty()) {
            imgBtn_Next.setEnabled(false);
            imgBtn_PlayOrPause.setEnabled(false);
            imgBtn_Previous.setEnabled(false);
            imgBtn_Stop.setEnabled(false);
            Toast.makeText(getApplicationContext(), "当前没有歌曲文件", Toast.LENGTH_SHORT).show();
        } else {
            imgBtn_Next.setEnabled(true);
            imgBtn_PlayOrPause.setEnabled(true);
            imgBtn_Previous.setEnabled(true);
            imgBtn_Stop.setEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        sendBroadcastOnCommand(MusicService.COMMAND_CHECK_IS_PLAYING);
        PropertyBean propetry = new PropertyBean(MainActivity.this);
        String theme = propetry.getTheme();
        //设置activity的主题
        setTheme(theme);
    }

    private String formatTime(int msec) {
        int minute = msec / 1000 / 60;
        int second = msec / 1000 % 60;
        String minuteString;
        String secondString;
        if (minute < 10) {
            minuteString = "0" + minute;
        } else {
            minuteString = "" + minute;
        }
        if (second < 10) {
            secondString = "0" + second;
        } else {
            secondString = "" + second;
        }
        return minuteString + ":" + secondString;
    }

    /**
     * 发送命令，控制音乐播放。参数定义在MusicService类中
     */
    private void sendBroadcastOnCommand(int command) {

        Intent intent = new Intent(MusicService.BROADCAST_MUSICSERVICE_CONTROL);
        intent.putExtra("command", command);
        //根据不同命令，封装不同的数据
        switch (command) {
            case MusicService.COMMAND_PLAY:
                intent.putExtra("number", number);
                break;
            case MusicService.COMMAND_SEEK_TO:
                intent.putExtra("time",time);
                break;
            case MusicService.COMMAND_PREVIOUS:
            case MusicService.COMMAND_NEXT:
            case MusicService.COMMAND_PAUSE:
            case MusicService.COMMAND_STOP:
            case MusicService.COMMAND_RESUME:
            default:
                break;
        }
        sendBroadcast(intent);
    }

    /**
     * 内部类，用于播放器状态更新的接收广播
     */
    class StatusChangedReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String musicName = intent.getStringExtra("musicName");
            String musicArtist = intent.getStringExtra("musicArtist");
            //获取播放器状态
            status = intent.getIntExtra("status", -1);
            switch (status) {
                case MusicService.STATUS_PLAYING:
                    seekBarHandler.removeMessages(PROGRESS_INCREASE);
                    time = intent.getIntExtra("time", 0);
                    duration = intent.getIntExtra("duration", 0);
                    number = intent.getIntExtra("number", number);
                    list.setSelection(number);
                    seekBar.setProgress(time);
                    seekBar.setMax(duration);
                    seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE, 1000L);
                    text_Duration.setText(formatTime(duration));
                    imgBtn_PlayOrPause.setBackgroundResource(R.drawable.pause);
                    //设置textview文字，提示已经播放的歌曲
                    MainActivity.this.setTitle("正在播放:" + musicName + " - " + musicArtist);
                    MainActivity.this.textView.setText(musicName + " - " + musicArtist);
                    break;
                case MusicService.STATUS_PAUSED:
                    seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
                    imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
                    break;
                case MusicService.STATUS_STOPPED:
                    time = 0;
                    duration = 0;
                    text_Current.setText(formatTime(time));
                    text_Duration.setText(formatTime(duration));
                    seekBarHandler.sendEmptyMessage(PROGRESS_RESET);
                    MainActivity.this.setTitle("MusicPlayer");
                    MainActivity.this.textView.setText("");
                    imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
                    break;
                case MusicService.STATUS_COMPLETED:
                    number = intent.getIntExtra("number", 0);
                    if (number == MusicList.getMusicList().size() - 1)
                        sendBroadcastOnCommand(MusicService.STATUS_STOPPED);
                    else
                        sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
                    seekBarHandler.sendEmptyMessage(PROGRESS_RESET);
                    MainActivity.this.setTitle("MusicPlayer");
                    imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        if (status == MusicService.STATUS_STOPPED) {
            stopService(new Intent(this, MusicService.class));
        }
        super.onDestroy();
    }

    //设置Activity的主题，包括修改背景图片等等
    private void setTheme(String theme) {
        if ("彩色".equals(theme)) {
            root_Layout.setBackgroundResource(R.drawable.bg_color);
        } else if ("花朵".equals(theme)) {
            root_Layout.setBackgroundResource(R.drawable.bg_digit_flower);
        } else if ("群山".equals(theme)) {
            root_Layout.setBackgroundResource(R.drawable.bg_mountain);
        } else if ("小狗".equals(theme)) {
            root_Layout.setBackgroundResource(R.drawable.bg_running_dog);
        } else if ("冰雪".equals(theme)) {
            root_Layout.setBackgroundResource(R.drawable.bg_snow);
        } else if ("女孩".equals(theme)) {
            root_Layout.setBackgroundResource(R.drawable.bg_music_girl);
        } else if ("朦胧".equals(theme)) {
            root_Layout.setBackgroundResource(R.drawable.bg_blur);
        }
    }

    /*创建菜单*/
    public boolean onCreateOptionsMenu(Menu menu) {
        this.getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /*处理菜单点击事件*/
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_theme:
                new AlertDialog.Builder(this)
                        .setTitle("请选择主题").
                        setItems(R.array.theme,
                                new android.content.DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        String theme = PropertyBean.THEMES[which];
                                        MainActivity.this.setTheme(theme);
                                        PropertyBean property = new PropertyBean(MainActivity.this);
                                        property.setAndSaveTheme(theme);
                                    }
                                }).show();
                break;
            case R.id.menu_about:
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("GRacePlayer")
                        .setMessage(R.string.about2).show();
                break;
            case R.id.menu_quit:
                //退出程序
                new AlertDialog.Builder(this)
                        .setTitle("提示").
                        setMessage(R.string.quit).setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        System.exit(0);
                    }
                }).setNegativeButton("取消", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                    }
                }).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initSeekBarHandler() {
        seekBarHandler = new Handler() {
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case PROGRESS_INCREASE:
                        if (seekBar.getProgress() < duration) {
                            //进度条前进一秒
                            seekBar.setProgress(time);
                            seekBar.incrementProgressBy(1000);
                            seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE, 1000);
                            //修改显示当前进度的文本
                            text_Current.setText(formatTime(time));
                            time += 1000;
                        }
                        break;
                    case PROGRESS_PAUSE:
                        seekBarHandler.removeMessages(PROGRESS_INCREASE);
                        break;
                    case PROGRESS_RESET:
                        //重置进度条画面
                        seekBarHandler.removeMessages(PROGRESS_INCREASE);
                        seekBar.setProgress(0);
                        text_Current.setText("00:00");
                        break;
                }
            }
        };
    }
}
