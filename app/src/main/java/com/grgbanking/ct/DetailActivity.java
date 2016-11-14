package com.grgbanking.ct;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.location.LocationClientOption.LocationMode;
import com.grgbanking.ct.cach.DataCach;
import com.grgbanking.ct.database.DBManager;
import com.grgbanking.ct.database.ExtractBoxs;
import com.grgbanking.ct.database.Person;
import com.grgbanking.ct.entity.PdaCashboxInfo;
import com.grgbanking.ct.entity.PdaGuardManInfo;
import com.grgbanking.ct.entity.PdaLoginMessage;
import com.grgbanking.ct.entity.PdaLoginMsg;
import com.grgbanking.ct.entity.PdaNetInfo;
import com.grgbanking.ct.entity.PdaNetPersonInfo;
import com.grgbanking.ct.http.FileLoadUtils;
import com.grgbanking.ct.http.HttpPostUtils;
import com.grgbanking.ct.http.ResultInfo;
import com.grgbanking.ct.http.UICallBackDao;
import com.grgbanking.ct.rfid.UfhData;
import com.grgbanking.ct.rfid.UfhData.UhfGetData;
import com.grgbanking.ct.scan.Recordnet;
import com.grgbanking.ct.scan.Waternet;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.grgbanking.ct.cach.DataCach.pdaLoginMsg;

@SuppressLint("NewApi")
public class DetailActivity extends Activity {

    private static final int SCAN_INTERVAL = 10;
    private static final int MSG_UPDATE_LISTVIEW = 0;
    private static final int IMAGE_REQUEST_CODE = 0; // 选择本地图片
    private static final int CAMERA_REQUEST_CODE = 1; // 拍照
    private static final String IMAGE_FILE_NAME = "faceImage.jpg";
    private static HashMap<String, Object> boxesMap1 = null;//保存正确款箱map
    private static HashMap<String, Object> boxesMap2 = null;//保存多处的款箱map
    private static HashMap<String, Object> boxesMap3 = null;//保存错误的款箱map
    private static PdaNetPersonInfo netPersonInfo = null;//保存网点人员
    private static PdaGuardManInfo guardManInfo = null;//保存押运人员
    TextView positionTextView = null;
    TextView branchNameTextView = null;
    Button commitYesButton = null;
    Button commitNoButton = null;
    TextView detailTitleTextView = null;
    Button connDeviceButton = null;
    Button startDeviceButton = null;
    TextView person1TextView = null;
    TextView person2TextView = null;
    ListView deviceListView;
    SimpleAdapter listItemAdapter;
    ArrayList<HashMap<String, Object>> listItem;
    private Context context;
    private EditText remarkEditView;
    private int tty_speed = 57600;
    private byte addr = (byte) 0xff;
    private Timer timer;
    private boolean Scanflag = false;
    private boolean isCanceled = true;
    private Handler mHandler;
    private Map<String, Integer> data;
    private String branchCode = null;
    private String branchId = null;
    private String imageUrl = null;// 上传成功后的图片URL
    private String address;
    private double latitude;
    private double longitude;
    private boolean uploadFlag = false;
    private ProgressDialog pd = null;
    OnClickListener click = new OnClickListener() {
        @Override
        public void onClick(View arg0) {
            String context = startDeviceButton.getText().toString();
            switch (arg0.getId()) {
                case R.id.detail_btn_back:
                    if (context.equals("Stop")) {
                        Toast.makeText(DetailActivity.this, "请先停止扫描", Toast.LENGTH_LONG).show();
                    } else {
                        backListPage();
                    }
                    break;
                //			case R.id.detail_btn_commit_y:
                //				doCommit("Y");
                //				break;
                case R.id.detail_btn_commit_n:
                    //				if (uploadFlag) {
                    //					doCommit("N");
                    //				} else {
                    //					Toast.makeText(DetailActivity.this, "图片正在上传中，请稍等。", Toast.LENGTH_LONG)
                    //							.show();
                    //				}
                    if (context.equals("Stop")) {
                        Toast.makeText(DetailActivity.this, "请先停止扫描", Toast.LENGTH_LONG).show();
                    } else {
                        dialog();
                    }
                    break;
                //			case R.id.add_photo:
                //				doAddPhoto();
                //				break;

                case R.id.button1:
                    //连接设备
                    connDevices();

                    break;
                case R.id.Button01:
                    //启动设备
                    if (!context.equals("Stop")) {
                        loadDevices();
                    }

                    startDevices();
                    break;
                default:
                    break;
            }
        }
    };
    private Person person = null;

    /**
     * 计算图片的缩放值
     *
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static int calculateInSampleSize(BitmapFactory.Options options,
                                            int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and
            // width
            final int heightRatio = Math.round((float) height
                    / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will
            // guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        return inSampleSize;
    }

    /**
     * 根据路径获得突破并压缩返回bitmap用于显示
     *
     * @param
     * @return
     */
    public static Bitmap getSmallBitmap(String filePath) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, 480, 800);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(filePath, options);
    }

    private void showWaitDialog(String msg) {
        if (pd == null) {
            pd = new ProgressDialog(this);
        }
        pd.setCancelable(false);
        pd.setMessage(msg);
        pd.show();
    }

    private void hideWaitDialog() {
        if (pd != null) {
            pd.cancel();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.detail);
        context = getApplicationContext();
        remarkEditView = (EditText) findViewById(R.id.detail_remark);
        commitNoButton = (Button) findViewById(R.id.detail_btn_commit_n);
        branchNameTextView = (TextView) findViewById(R.id.detail_branch_name);
        detailTitleTextView = (TextView) findViewById(R.id.detail_title_view);
        connDeviceButton = (Button) findViewById(R.id.button1);
        startDeviceButton = (Button) findViewById(R.id.Button01);
        person1TextView = (TextView) findViewById(R.id.textView2);
        person2TextView = (TextView) findViewById(R.id.textView_person2);
        deviceListView = (ListView) findViewById(R.id.ListView_boxs);

        // 生成动态数组，加入数据
        listItem = new ArrayList<HashMap<String, Object>>();
        // 生成适配器的Item和动态数组对应的元素
        listItemAdapter = new SimpleAdapter(this, listItem, R.layout.boxes_list_item, new String[]{"list_img", "list_title"}, new int[]{R.id.list_boxes_img, R.id.list_boxes_title});
        // 添加并且显示
        deviceListView.setAdapter(listItemAdapter);

        showWaitDialog("正在加载中，请稍后...");

        loadDevices();

        //启动RFID扫描功能刷新扫描款箱数据
        flashInfo();

        hideWaitDialog();
        // 点击返回按钮操作内容
        findViewById(R.id.detail_btn_back).setOnClickListener(click);

        commitNoButton.setOnClickListener(click);
        connDeviceButton.setOnClickListener(click);
        startDeviceButton.setOnClickListener(click);

        // 点击添加照片按钮操作内容
        //		findViewById(R.id.add_photo).setOnClickListener(click);
    }

    private void flashInfo() {
        mHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                if (isCanceled)
                    return;
                switch (msg.what) {
                    case MSG_UPDATE_LISTVIEW:

                        data = UfhData.scanResult6c;
                        String person1 = person1TextView.getText().toString();
                        String person2 = person2TextView.getText().toString();
                        Iterator it = data.keySet().iterator();
                        while (it.hasNext()) {
                            String key = (String) it.next();
                            Log.i("==key==", "" + key);
                            //判断是否是押运人员
                            if (key.indexOf(Constants.PRE_RFID_GUARD) != -1) {

                                if (person1.trim().equals("")) {
                                    PdaLoginMsg plm = DataCach.getPdaLoginMsg();
                                    Log.i("========", "+++====" + plm);
                                    //                                    PdaLoginMessage plm = DataCach.getPdaLoginMessage();
                                    if (plm != null) {
                                        List<PdaGuardManInfo> guardManInfoList = plm.getPdaGuardManInfo();
                                        Log.i("========", "=+++++++=" + guardManInfoList.size());
                                        if (guardManInfoList != null && guardManInfoList.size() > 0) {
                                            for (PdaGuardManInfo info : guardManInfoList) {
                                                if (info.getGuardManRFID().equals(key)) {
                                                    person1TextView.setText(info.getGuardManName());
                                                    guardManInfo = info;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            //判断是否是网点人员
                            else if (key.indexOf(Constants.PRE_RFID_BANKEMPLOYEE) != -1) {

                                if (person2.trim().equals("")) {
                                    Intent intent = getIntent();
                                    Bundle bundle = intent.getBundleExtra("bundle");
                                    int count = bundle.getInt("count");
                                    HashMap<String, Object> map = DataCach.taskMap.get(count + "");
                                    PdaNetInfo pni = (PdaNetInfo) map.get("data");

                                    if (pni != null) {
                                        List<PdaNetPersonInfo> netPersonInfoList = pni.getNetPersonInfoList();
                                        if (netPersonInfoList != null && netPersonInfoList.size() > 0) {
                                            for (PdaNetPersonInfo info : netPersonInfoList) {
                                                if (info.getNetPersonRFID().equals(key)) {
                                                    person2TextView.setText(info.getNetPersonName());
                                                    netPersonInfo = info;
                                                    break;
                                                }
                                            }
                                            //										if (person2TextView.getText().toString().equals("")) {
                                            //											Toast.makeText(context, "该网点人员不是本网点人员", Toast.LENGTH_LONG).show();
                                            //										}
                                        }
                                    }
                                }
                            }
                            //判断是否是正确款箱RFID
                            else if (boxesMap1.get(key) != null) {
                                Log.i("======", "here======");
                                HashMap<String, Object> map = DataCach.boxesMap.get(key);
                                map.put("list_img", R.drawable.boxes_list_status_1);// 图像资源的ID

                                HashMap<String, Object> map1 = (HashMap<String, Object>) boxesMap1.get(key);
                                //记录该款箱是否已扫描  0:未扫描;1:已扫描
                                map1.put("status", "1");
                            }
                            //判断是否是错误款箱RFID
                            else if (boxesMap2.get(key) == null) {
                                if (DataCach.netType.equals("0")) {
                                    Intent intent = getIntent();
                                    Bundle bundle = intent.getBundleExtra("bundle");
                                    int count = bundle.getInt("count");
                                    HashMap<String, Object> map = DataCach.taskMap.get(count + "");
                                    PdaNetInfo pni = (PdaNetInfo) map.get("data");
                                    DBManager dbManager = new DBManager(context);
                                    List<ExtractBoxs> extractBoxsList = dbManager.queryExtractBoxs();
                                    if (extractBoxsList != null) {
                                        for (ExtractBoxs extractBoxs : extractBoxsList) {
                                            if (extractBoxs.getRfidNum().equals(key)) {
                                                HashMap<String, Object> map1 = new HashMap<String, Object>();
                                                map1.put("list_img", R.drawable.boxes_list_status_1);
                                                map1.put("list_title", extractBoxs.getBoxSn());
                                                boxesMap2.put(key, map1);
                                                listItem.add(map1);
                                            }
                                        }
                                    }


                                    //                                    if (pni != null) {
                                    //                                        List<PdaCashboxInfo> pdaCashboxInfoList = pni.getCashBoxInfoList();
                                    //                                        if (pdaCashboxInfoList != null && pdaCashboxInfoList.size() > 0) {
                                    //                                            for (PdaCashboxInfo info : pdaCashboxInfoList) {
                                    //                                                if (info.getRfidNum().equals(key)) {
                                    //                                                    map.put("list_imag", R.drawable.boxes_list_status_1);
                                    //                                                    map.put("list_title", info.getBoxSn());
                                    //
                                    //                                                    DataCach.boxesMap.put(key, map);
                                    //                                                    boxesMap2.put(key, map);
                                    //                                                    listItem.add(map);
                                    //                                                }
                                    //                                            }
                                    //                                        }
                                    //                                    }

                                } else {
                                    //                                PdaLoginMessage pdaLoginMessage = DataCach.getPdaLoginMessage();
                                    pdaLoginMsg = DataCach.getPdaLoginMsg();
                                    Map<String, String> allPdaBoxsMap = pdaLoginMsg.getAllPdaBoxsMap();
                                    if (allPdaBoxsMap.get(key) != null) {
                                        HashMap<String, Object> map1 = new HashMap<String, Object>();
                                        map1.put("list_img", R.drawable.boxes_list_status_3);// 图像资源的ID
                                        map1.put("list_title", allPdaBoxsMap.get(key));

                                        DataCach.boxesMap.put(key, map1);
                                        boxesMap2.put(key, map1);
                                        listItem.add(map1);
                                    }
                                }

                            }
                        }
                        listItemAdapter.notifyDataSetChanged();
                        break;

                    default:
                        break;
                }
                super.handleMessage(msg);
            }

        };
    }

    private void connDevices() {
        int result = UhfGetData.OpenUhf(tty_speed, addr, 4, 1, null);
        if (result == 0) {
            UhfGetData.GetUhfInfo();
            Toast.makeText(context, "连接设备成功", Toast.LENGTH_LONG).show();
            //		mHandler.removeMessages(MSG_SHOW_PROPERTIES);
            //		mHandler.sendEmptyMessage(MSG_SHOW_PROPERTIES);
        } else {
            Toast.makeText(context, "连接设备失败，请关闭程序重新登录", Toast.LENGTH_LONG).show();
        }
    }

    private void startDevices() {
        if (!UfhData.isDeviceOpen()) {
            Toast.makeText(this, R.string.detail_title, Toast.LENGTH_LONG).show();
            return;
        }
        if (timer == null) {
            //			cdtion.setEnabled(false);
            ///////////声音开关初始化
            UfhData.Set_sound(true);
            UfhData.SoundFlag = false;
            //////////
            //			if (myAdapter != null) {
            //				if(mode.equals(MainActivity.TABLE_6B)){
            //					UfhData.scanResult6b.clear();
            //				}else if(mode.equals(MainActivity.TABLE_6C)){
            //					UfhData.scanResult6c.clear();
            //				}
            //				myAdapter.mList.clear();
            //				myAdapter.notifyDataSetChanged();
            //				mHandler.removeMessages(MSG_UPDATE_LISTVIEW);
            //				mHandler.sendEmptyMessage(MSG_UPDATE_LISTVIEW);
            //			}

            isCanceled = false;
            timer = new Timer();
            //
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (Scanflag)
                        return;
                    Scanflag = true;
                    UfhData.read6c();
                    mHandler.removeMessages(MSG_UPDATE_LISTVIEW);
                    mHandler.sendEmptyMessage(MSG_UPDATE_LISTVIEW);
                    Scanflag = false;
                }
            }, 0, SCAN_INTERVAL);
            startDeviceButton.setText("Stop");
        } else {
            cancelScan();
            UfhData.Set_sound(false);
        }

    }

    private void cancelScan() {
        isCanceled = true;
        //		cdtion.setEnabled(true);
        mHandler.removeMessages(MSG_UPDATE_LISTVIEW);
        if (timer != null) {
            timer.cancel();
            timer = null;
            startDeviceButton.setText("Scan");
            //			if(mode.equals(MainActivity.TABLE_6B)){
            //				UfhData.scanResult6b.clear();
            //			}else if(mode.equals(MainActivity.TABLE_6C)){
            //				UfhData.scanResult6c.clear();
            //			}
            UfhData.scanResult6c.clear();
            //			if (listItemAdapter != null) {
            //				listItem.clear();
            //				listItemAdapter.notifyDataSetChanged();
            //			}
            //			txNum.setText("0");
        }
    }

    private void loadDevices() {
        //加载数据要先把数据缓存清空
        person1TextView.setText("");
        person2TextView.setText("");
        DataCach.boxesMap = null;
        DataCach.boxesMap = new LinkedHashMap<String, HashMap<String, Object>>();
        boxesMap1 = null;
        boxesMap1 = new HashMap<String, Object>();
        boxesMap2 = null;
        boxesMap2 = new HashMap<String, Object>();
        boxesMap3 = null;
        boxesMap3 = new HashMap<String, Object>();
        netPersonInfo = null;
        guardManInfo = null;
        listItem.removeAll(listItem);


        Intent intent = getIntent();
        Bundle bundle = intent.getBundleExtra("bundle");
        int count = bundle.getInt("count");
        if (DataCach.taskMap.get(count + "") != null) {
            HashMap<String, Object> map = DataCach.taskMap.get(count + "");
            PdaNetInfo pni = (PdaNetInfo) map.get("data");
            detailTitleTextView.setText(pni.getBankName());

            List<PdaCashboxInfo> cashBoxInfoList = pni.getCashBoxInfoList();

            if (cashBoxInfoList != null && cashBoxInfoList.size() > 0) {

                for (PdaCashboxInfo pci : cashBoxInfoList) {

                    HashMap<String, Object> map1 = new HashMap<String, Object>();
                    map1.put("list_img", R.drawable.boxes_list_status_2);// 图像资源的ID
                    map1.put("list_title", pci.getBoxSn());

                    //记录该款箱是否已扫描  0:未扫描;1:已扫描
                    map1.put("status", "0");

                    DataCach.boxesMap.put(pci.getRfidNum(), map1);

                    if (DataCach.netType.equals("1")) {//网点入库item显示
                        boxesMap1.put(pci.getRfidNum(), map1);
                        listItem.add(map1);
                    } else {//网点出库item显示
                        //                        HashMap<String, Object> map2 = new HashMap<String, Object>();
                        //                        listItem.add(map2);
                    }


                }
            }
        }

        listItemAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    protected void dialog() {
        //	    通过AlertDialog.Builder这个类来实例化我们的一个AlertDialog的对象
        AlertDialog.Builder builder = new AlertDialog.Builder(DetailActivity.this);
        //        //    设置Title的图标
        //        builder.setIcon(R.drawable.ic_launcher);
        //    设置Title的内容
        builder.setTitle("提示");
        //    设置Content来显示一个信息
        builder.setMessage("确定保存？");
        //    设置一个PositiveButton
        builder.setPositiveButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        //    设置一个NegativeButton
        builder.setNegativeButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showWaitDialog("正在保存数据...");
                //开始保存数据到数据库
                DBManager db = new DBManager(context);
                //时间戳
                SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                //初始化实体类
                Recordnet recordnet = new Recordnet();
                //获取当前时间
                Date curDate = new Date(System.currentTimeMillis());
                String date = format.format(curDate);

                // FIXME: 2016/11/2 null
                //线路名称
                recordnet.setLineSn(pdaLoginMsg.getLineSn());

                //lineId
                recordnet.setLineId(pdaLoginMsg.getLineId());

                //日期（扫描时间）
                recordnet.setScanningDate(date);

                // 网点人员名称
                recordnet.setBankman(person2TextView.getText().toString());

                //押运人员名称
                recordnet.setGuardman(person1TextView.getText().toString());

                // FIXME: 2016/11/2 null
                //1:网点入库 ； 0：网点出库
                recordnet.setLineType(DataCach.netType);

                //判断款箱扫描状态
                String rightRfidNums = "";
                String missRfidNums = "";
                String errorRfidNums = "";
                if (boxesMap1 != null && boxesMap1.size() > 0) {
                    Iterator it = boxesMap1.keySet().iterator();
                    while (it.hasNext()) {
                        String key = (String) it.next();
                        HashMap<String, Object> map1 = (HashMap<String, Object>) boxesMap1.get(key);
                        if (map1.get("status").equals("1")) {
                            rightRfidNums += ";" + key;
                        } else {
                            missRfidNums += ";" + key;
                        }
                    }
                    if (rightRfidNums.length() > 0) {
                        rightRfidNums = rightRfidNums.substring(1);
                    }
                    if (missRfidNums.length() > 0) {
                        missRfidNums = missRfidNums.substring(1);
                    }
                }
                //0：扫描正确 ; 1：扫描错误
                if (rightRfidNums.length() > 0 && missRfidNums.length() == 0 && errorRfidNums.length() == 0) {
                    recordnet.setScanStatus(Constants.NET_COMMIT_STATUS_RIGHT);
                } else {
                    recordnet.setScanStatus(Constants.NET_COMMIT_STATUS_ERROR);
                }

                //备注信息
                recordnet.setNote(remarkEditView.getText().toString());

                //网点号
                // 得到跳转到该Activity的Intent对象
                Intent intent = getIntent();
                Bundle bundle = intent.getBundleExtra("bundle");
                int count = bundle.getInt("count");
                if (DataCach.taskMap.get(count + "") != null) {
                    HashMap<String, Object> map = DataCach.taskMap.get(count + "");
                    map.put("list_img", R.drawable.task_1);// 图像资源的ID
                    map.put("list_worktime", "已完成");
                }
                //                backListPage();
                HashMap<String, Object> map = DataCach.taskMap.get(count + "");
                PdaNetInfo pdanetinfo = (PdaNetInfo) map.get("data");
                recordnet.setBankId(pdanetinfo.getBankId());

                //网点人员ID
                try {
                    recordnet.setBankmanId(netPersonInfo.getNetPersonId());
                } catch (Exception e) {
                    Toast.makeText(context, "请重新扫描网点人员", Toast.LENGTH_SHORT).show();
                }


                //押运人员ID

                try {
                    recordnet.setGuardmanId(guardManInfo.getGuardManId());
                } catch (Exception e) {
                    Toast.makeText(context, "请重新扫描网点人员", Toast.LENGTH_SHORT).show();
                }


                //存入数据库
                db.addRecordnet(recordnet);
                //取当前流水表最大值
                int maxId = db.queryMaxRecordNet();

                //                //这个表b_scanning_recordnet 的外键
                //                waternet.setScanningNetid();


                //                //箱包名称  //箱包ID  //网点ID
                //                PdaNetInfo pni = (PdaNetInfo) map.get("data");
                //                List<PdaCashboxInfo> cashBoxInfoList = pni.getCashBoxInfoList();
                //                if (cashBoxInfoList != null && cashBoxInfoList.size() > 0) {
                //                    for (PdaCashboxInfo pci : cashBoxInfoList) {
                //                        waternet.setBoxSn(pci.getBoxSn());
                //                        waternet.setBoxId(pci.getRfidNum());
                //                        waternet.setBankId(pci.getBankId());
                //                    }
                //                }

                //日期
                //                waternet.setScanningDate(date);

                //0:正确入库   1：错误入库  2：遗漏

                if (boxesMap1 != null && boxesMap1.size() > 0) {
                    Iterator it2 = boxesMap1.keySet().iterator();
                    while (it2.hasNext()) {
                        Waternet net = new Waternet();
                        String key2 = (String) it2.next();
                        HashMap<String, Object> map1 = (HashMap<String, Object>) boxesMap1.get(key2);
                        if (map1.get("status").equals("1")) {
                            net.setStatus("2");
                        } else if (map1.get("status").equals("0")) {
                            net.setStatus("1");
                        }
                        String title = map.get("list_title").toString();
                        net.setBoxId(key2);
                        net.setBoxSn(title);
                        net.setScanningType((DataCach.netType));
                        net.setBankId(pdanetinfo.getBankId());
                        net.setScanningDate(date);
                        net.setScanningNetid(maxId);
                        db.addWaternet(net);
                    }
                }
                if (boxesMap2 != null && boxesMap2.size() > 0) {
                    Iterator it2 = boxesMap1.keySet().iterator();
                    while (it2.hasNext()) {
                        Waternet net = new Waternet();
                        String key2 = (String) it2.next();
                        HashMap<String, Object> map2 = (HashMap<String, Object>) boxesMap1.get(key2);
                        net.setStatus("1");
                        String title = map.get("list_title").toString();
                        net.setBoxId(key2);
                        net.setBoxSn(title);
                        net.setScanningType((DataCach.netType));
                        net.setBankId(pdanetinfo.getBankId());
                        net.setScanningDate(date);
                        net.setScanningNetid(maxId);
                        db.addWaternet(net);
                    }
                }

                hideWaitDialog();

                //                if (boxesMap1 != null && boxesMap1.size() > 0) {
                //
                //                    Iterator it2 = boxesMap1.keySet().iterator();
                //                    while (it2.hasNext()) {
                //                        String key2 = (String) it2.next();
                //                        HashMap<String, Object> map1 = (HashMap<String, Object>) boxesMap1.get(key2);
                //                        if (map1.get("status").equals("0")) {
                //                            waternet.setStatus("2");
                //                        }
                //                        break;
                //                    }
                //                    if (boxesMap2 != null && boxesMap2.size() > 0) {
                //                        waternet.setStatus("1");
                //                    } else {
                //                        waternet.setStatus("0");
                //                    }
                //                }
                //
                //
                //                //1:网点入库 ； 0：网点出库
                //                waternet.setScanningType((DataCach.netType));
                //
                //                //存入数据库
                //                db.addWaternet(waternet);


                //ResultInfo ri = this.getNetIncommitData();
                //if (ri.getCode().endsWith(ri.CODE_ERROR)) {
                //                    hideWaitDialog();
                //                    Toast.makeText(context, ri.getMessage(), Toast.LENGTH_LONG).show();
                //                    return;
                //                }
                //
                //
                //                List<NameValuePair> params = new ArrayList<NameValuePair>();
                //                params.add(new BasicNameValuePair("param", ri.getText()));
                //                new HttpPostUtils(Constants.URL_NET_IN_COMMIT, params, new UICallBackDao() {
                //                    @Override
                //                    public void callBack(ResultInfo resultInfo) {
                //                        if (resultInfo.getCode().equals(resultInfo.CODE_ERROR)) {
                //                            hideWaitDialog();
                //                            Toast.makeText(context, resultInfo.getMessage(), Toast.LENGTH_LONG).show();
                //                        } else {
                //                            hideWaitDialog();
                //                            Toast.makeText(context, resultInfo.getMessage(), Toast.LENGTH_LONG).show();
                //                        }
                //                    }
                //                }).execute();
                //                hideWaitDialog();

                //                // 得到跳转到该Activity的Intent对象
                //                Intent intent = getIntent();
                //                Bundle bundle = intent.getBundleExtra("bundle");
                //                int count = bundle.getInt("count");
                //                if (DataCach.taskMap.get(count + "") != null) {
                //                    HashMap<String, Object> map = DataCach.taskMap.get(count + "");
                //                    map.put("list_img", R.drawable.task_1);// 图像资源的ID
                //                    map.put("list_worktime", "已完成");
                //                }
                //                backListPage();
            }

            private ResultInfo getNetIncommitData() {
                ResultInfo ri = new ResultInfo();
                Map<String, String> dataMap = new HashMap<String, String>();
                if (guardManInfo == null) {
                    ri.setCode(ri.CODE_ERROR);
                    ri.setMessage("请扫描押运人员");
                    return ri;
                }
                if (netPersonInfo == null) {
                    ri.setCode(ri.CODE_ERROR);
                    ri.setMessage("请扫描网点人员");
                    return ri;
                }
                if ((boxesMap1 == null || boxesMap1.size() == 0) && (boxesMap2 == null || boxesMap2.size() == 0)) {
                    ri.setCode(ri.CODE_ERROR);
                    ri.setMessage("请扫描款箱");
                    return ri;
                }


                //开始组装数据
                PdaLoginMessage pdaLoginMessage = DataCach.getPdaLoginMessage();
                SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                dataMap.put("lineId", pdaLoginMessage.getLineId());
                dataMap.put("scanningDate", format.format(new Date()));
                dataMap.put("netPersonName", netPersonInfo.getNetPersonName());
                dataMap.put("netPersonId", netPersonInfo.getNetPersonId());
                dataMap.put("guardPersonName", guardManInfo.getGuardManName());
                dataMap.put("guradPersonId", guardManInfo.getGuardManId());
                dataMap.put("note", remarkEditView.getText().toString());


                Intent intent = getIntent();
                Bundle bundle = intent.getBundleExtra("bundle");
                int count = bundle.getInt("count");

                dataMap.put("scanningType", DataCach.netType);

                HashMap<String, Object> map = DataCach.taskMap.get(count + "");
                PdaNetInfo pni = (PdaNetInfo) map.get("data");
                dataMap.put("netId", pni.getBankId());

                JSONObject jsonObject = new JSONObject(dataMap);
                String data = jsonObject.toString();
                ri.setCode(ri.CODE_SUCCESS);
                ri.setText(data);
                return ri;
            }


        });
        //        //    设置一个NeutralButton
        //        builder.setNeutralButton("忽略", new DialogInterface.OnClickListener()
        //        {
        //            @Override
        //            public void onClick(DialogInterface dialog, int which)
        //            {
        //                Toast.makeText(DetailActivity.this, "neutral: " + which, Toast.LENGTH_SHORT).show();
        //            }
        //        });
        //    显示出该对话框
        builder.show();
    }

    void backListPage() {
        Intent intent = new Intent(DetailActivity.this, NetOutInActivity.class);
        startActivity(intent);
        finish();
    }

    void doCommit(String flag) {

        if (address == null || "".equals(address)) {
            Toast.makeText(DetailActivity.this, "错误提示：无法获取您当前的地理位置，请返回重新扫描二维码。", Toast.LENGTH_LONG)
                    .show();
            return;
        }

        showWaitDialog("正在处理中...");
        String remark = remarkEditView.getText().toString();
        String status = flag;
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        //        params.add(new BasicNameValuePair("userid", person.getUser_id()));
        params.add(new BasicNameValuePair("name", person.getUser_name()));

        params.add(new BasicNameValuePair("branchId", branchId));
        params.add(new BasicNameValuePair("remark", remark));
        params.add(new BasicNameValuePair("status", status));
        params.add(new BasicNameValuePair("imageUrl", imageUrl));
        params.add(new BasicNameValuePair("longitude", longitude + ""));
        params.add(new BasicNameValuePair("latitude", latitude + ""));
        params.add(new BasicNameValuePair("address", address));
        new HttpPostUtils(Constants.URL_SAVE_TASK, params, new UICallBackDao() {
            @Override
            public void callBack(ResultInfo resultInfo) {
                hideWaitDialog();

                if ("1".equals(resultInfo.getCode())) {
                    new AlertDialog.Builder(DetailActivity.this)
                            .setTitle("消息")
                            .setMessage("提交成功！")
                            .setPositiveButton("确定",
                                    new DialogInterface.OnClickListener() {// 设置确定按钮
                                        @Override
                                        // 处理确定按钮点击事件
                                        public void onClick(DialogInterface dialog,
                                                            int which) {
                                            backListPage();
                                        }
                                    }).show();
                } else {
                    Toast.makeText(DetailActivity.this, resultInfo.getMessage(), Toast.LENGTH_LONG).show();

                }


            }
        }).execute();
    }

    void doAddPhoto() {
        final CharSequence[] items = {"相册", "拍照"};
        AlertDialog dlg = new AlertDialog.Builder(DetailActivity.this)
                .setTitle("选择照片")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 这里item是根据选择的方式， 在items数组里面定义了两种方式，拍照的下标为1所以就调用拍照方法
                        if (which == 1) {
                            takePhoto();
                        } else {
                            pickPhoto();
                        }
                    }
                }).create();
        dlg.show();
    }

    /**
     * 拍照
     */
    private void takePhoto() {
        Intent intentFromCapture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 判断存储卡是否可以用，可用进行存�?
        String state = Environment.getExternalStorageState();
        if (state.equals(Environment.MEDIA_MOUNTED)) {
            intentFromCapture.putExtra(MediaStore.EXTRA_OUTPUT, Uri
                    .fromFile(new File(Environment
                            .getExternalStorageDirectory(), IMAGE_FILE_NAME)));
        }
        startActivityForResult(intentFromCapture, CAMERA_REQUEST_CODE);
    }

    /**
     * 选择本地图片
     */
    private void pickPhoto() {
        Intent intentFromGallery = new Intent();
        intentFromGallery.setType("image/*"); // 设置文件类型
        intentFromGallery.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intentFromGallery, IMAGE_REQUEST_CODE);
    }

    public String uri2filePath(Uri uri) {
        String path = "";
        if (DocumentsContract.isDocumentUri(this, uri)) {
            String wholeID = DocumentsContract.getDocumentId(uri);
            String id = wholeID.split(":")[1];
            String[] column = {MediaStore.Images.Media.DATA};
            String sel = MediaStore.Images.Media._ID + "=?";
            Cursor cursor = this.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, column, sel,
                    new String[]{id}, null);
            int columnIndex = cursor.getColumnIndex(column[0]);
            if (cursor.moveToFirst()) {
                path = cursor.getString(columnIndex);
            }
            cursor.close();
        } else {
            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor = this.getContentResolver().query(uri,
                    projection, null, null, null);
            int column_index = cursor
                    .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            path = cursor.getString(column_index);
        }
        return path;

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case CAMERA_REQUEST_CODE:
                    String state = Environment.getExternalStorageState();
                    if (state.equals(Environment.MEDIA_MOUNTED)) {
                        Bitmap bitmap = compressImage(getSmallBitmap(Environment
                                .getExternalStorageDirectory()
                                + "/"
                                + IMAGE_FILE_NAME));

                        //					addPhotoImageView.setImageBitmap(bitmap);
                        //					uploadStatusTextView.setText("图片上传中...");

                        new FileLoadUtils(Constants.URL_FILE_UPLOAD, new File(
                                Environment.getExternalStorageDirectory()
                                        + "/faceImage1.jpg"), new UICallBackDao() {
                            @Override
                            public void callBack(ResultInfo resultInfo) {
                                if (resultInfo != null
                                        && "200".equals(resultInfo.getCode())) {
                                    Toast.makeText(DetailActivity.this, "上传成功",
                                            Toast.LENGTH_LONG).show();
                                    //								uploadStatusTextView.setText("上传成功。");
                                    //								imageUrl = resultInfo.getMessage();
                                    uploadFlag = true;
                                }
                            }
                        }).execute();
                    } else {
                        Toast.makeText(this, getString(R.string.sdcard_unfound),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                case IMAGE_REQUEST_CODE:
                    try {
                        String path = uri2filePath(data.getData());
                        Bitmap bitmap = compressImage(getSmallBitmap(path));
                        //					addPhotoImageView.setImageBitmap(bitmap);
                        //					uploadStatusTextView.setText("图片上传中...");
                        new FileLoadUtils(Constants.URL_FILE_UPLOAD,
                                new File(path), new UICallBackDao() {

                            @Override
                            public void callBack(ResultInfo resultInfo) {
                                if (resultInfo != null
                                        && "200".equals(resultInfo
                                        .getCode())) {
                                    Toast.makeText(DetailActivity.this,
                                            "上传成功", Toast.LENGTH_LONG).show();
                                    //										uploadStatusTextView.setText("上传成功。");
                                    imageUrl = resultInfo.getMessage();
                                    uploadFlag = true;
                                }
                            }
                        }).execute();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }

    /**
     * 图片压缩方法实现
     *
     * @param srcPath
     * @return
     */
    private Bitmap getimage(String srcPath) {
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        // 开始读入图片，此时把options.inJustDecodeBounds 设回true了
        newOpts.inJustDecodeBounds = true;
        Bitmap bitmap = BitmapFactory.decodeFile(srcPath, newOpts);

        newOpts.inJustDecodeBounds = false;
        int w = newOpts.outWidth;
        int h = newOpts.outHeight;
        int hh = 800;// 这里设置高度为800f
        int ww = 480;// 这里设置宽度为480f
        // 缩放比。由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
        int be = 1;// be=1表示不缩放
        if (w > h && w > ww) {// 如果宽度大的话根据宽度固定大小缩放
            be = (int) (newOpts.outWidth / ww);
        } else if (w < h && h > hh) {// 如果高度高的话根据高度固定大小缩放
            be = (int) (newOpts.outHeight / hh);
        }
        if (be <= 0)
            be = 1;
        newOpts.inSampleSize = be;// 设置缩放比例
        // 重新读入图片，注意此时已经把options.inJustDecodeBounds 设回false了
        bitmap = BitmapFactory.decodeFile(srcPath, newOpts);
        return compressImage(bitmap);// 压缩好比例大小后再进行质量压缩
    }

    /**
     * 质量压缩
     *
     * @param image
     * @return
     */
    private Bitmap compressImage(Bitmap image) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 50, baos);// 质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
        int options = 100;
        while (baos.toByteArray().length * 3 / 1024 > 100) { // 循环判断如果压缩后图片是否大于100kb,大于继续压缩
            baos.reset();// 重置baos即清空baos
            options -= 10;// 每次都减少10
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);// 这里压缩options%，把压缩后的数据存放到baos中
        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());// 把压缩后的数据baos存放到ByteArrayInputStream中
        Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, null);// 把ByteArrayInputStream数据生成图片
        try {
            FileOutputStream out = new FileOutputStream(
                    Environment.getExternalStorageDirectory()
                            + "/faceImage1.jpg");
            bitmap.compress(Bitmap.CompressFormat.PNG, 40, out);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    public void initLocation(final Context context,
                             final DetailActivity detailActivity) {
        LocationClient locationClient = new LocationClient(context);
        // 设置定位条件
        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true); // 是否打开GPS
        option.setIsNeedAddress(true);
        option.setLocationMode(LocationMode.Hight_Accuracy);
        option.setScanSpan(0);// 设置定时定位的时间间隔。单位毫秒
        option.setAddrType("all");
        option.setCoorType("bd09ll");
        locationClient.setLocOption(option);
        // 注册位置监听器
        locationClient.registerLocationListener(new BDLocationListener() {
            @Override
            public void onReceiveLocation(BDLocation location) {
                //				gpsPositionTextView = (TextView) detailActivity
                //						.findViewById(R.id.gps_position);
                //				gpsPositionTextView.setText("我的位置：" + location.getAddrStr());
                address = location.getAddrStr();
                latitude = location.getLatitude();
                longitude = location.getLongitude();
            }
        });
        locationClient.start();
    }
}

// TODO: 2016/11/2  1、禁止自动停止扫描 2、保存数据库的时候保存所有扫描到的数据 3、组装数据并且提交两张表内的所有数据 4、新建出库任务表 5、网点出库任务列表展示 6、网点出库任务详情改动 7、保存提交 8、二维码扫描item数据展示 9、二维码扫描数据库创建添加修改提交 10、结束Activity的时候停止扫描
// FIXME: 2016/11/3 1、查询时候do while循环 2、提交完成后更改数据状态避免无法完成任务。3,提交时押运人员网点人员不能为空