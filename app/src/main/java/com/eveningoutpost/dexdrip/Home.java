package com.eveningoutpost.dexdrip;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.Service;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.Models.ActiveBluetoothDevice;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.UtilityModels.IobCob;
import com.eveningoutpost.dexdrip.Services.WixelReader;
import com.eveningoutpost.dexdrip.UtilityModels.BgGraphBuilder;
import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.UtilityModels.Notifications;
import com.eveningoutpost.dexdrip.utils.DatabaseUtil;
import com.eveningoutpost.dexdrip.utils.ShareNotification;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;

import lecho.lib.hellocharts.ViewportChangeListener;
import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.util.Utils;
import lecho.lib.hellocharts.view.LineChartView;
import lecho.lib.hellocharts.view.PreviewLineChartView;


public class Home extends Activity implements NavigationDrawerFragment.NavigationDrawerCallbacks {
    private String menu_name = "DIYPanc";
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private LineChartView chart;
    private PreviewLineChartView previewChart;

    Viewport tempViewport = new Viewport();
    Viewport holdViewport = new Viewport();
    public float left;
    public float right;
    public float top;
    public float bottom;
    public boolean updateStuff;
    public boolean updatingPreviewViewport = false;
    public boolean updatingChartViewport = false;
    public boolean isShown = false;

    SharedPreferences prefs;

    public BgGraphBuilder bgGraphBuilder;
    BroadcastReceiver _broadcastReceiver;
    BgReading lastBgreading = BgReading.lastNoSenssor();

    private static Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_bg_notification, false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_data_sync, false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_wifi, false);


        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        checkEula();
        setContentView(R.layout.activity_home);

    }

    public void checkEula() {
        boolean IUnderstand = prefs.getBoolean("I_understand", false);
        if (!IUnderstand) {
            Intent intent = new Intent(getApplicationContext(), LicenseAgreementActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        checkEula();

        CollectionServiceStarter collectionServiceStarter = new CollectionServiceStarter();
        collectionServiceStarter.start(getApplicationContext());

        _broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent.getAction().compareTo(Intent.ACTION_TIME_TICK) == 0) {
                    updateCurrentBgInfo();
                }
            }
        };
        registerReceiver(_broadcastReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        mNavigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mNavigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout), menu_name, this);
        holdViewport.set(0, 0, 0, 0);
        setupCharts();
        updateCurrentBgInfo();
    }

    public static Context getContext() {
        return mContext;
    }

    public void setupCharts() {
        bgGraphBuilder = new BgGraphBuilder(this);
        updateStuff = false;
        chart = (LineChartView) findViewById(R.id.chart);
        chart.setZoomType(ZoomType.HORIZONTAL);

        previewChart = (PreviewLineChartView) findViewById(R.id.chart_preview);
        previewChart.setZoomType(ZoomType.HORIZONTAL);

        chart.setLineChartData(bgGraphBuilder.lineData());
        previewChart.setLineChartData(bgGraphBuilder.previewLineData());
        updateStuff = true;

        previewChart.setViewportCalculationEnabled(true);
        chart.setViewportCalculationEnabled(true);
        previewChart.setViewportChangeListener(new ViewportListener());
        chart.setViewportChangeListener(new ChartViewPortListener());
        setViewport();
    }

    private class ChartViewPortListener implements ViewportChangeListener {
        @Override
        public void onViewportChanged(Viewport newViewport) {
            if (!updatingPreviewViewport) {
                updatingChartViewport = true;
                previewChart.setZoomType(ZoomType.HORIZONTAL);
                previewChart.setCurrentViewport(newViewport, false);
                updatingChartViewport = false;
            }
        }
    }

    private class ViewportListener implements ViewportChangeListener {
        @Override
        public void onViewportChanged(Viewport newViewport) {
            if (!updatingChartViewport) {
                updatingPreviewViewport = true;
                chart.setZoomType(ZoomType.HORIZONTAL);
                chart.setCurrentViewport(newViewport, false);
                tempViewport = newViewport;
                updatingPreviewViewport = false;
            }
            if (updateStuff == true) {
                holdViewport.set(newViewport.left, newViewport.top, newViewport.right, newViewport.bottom);
            }
        }

    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        mNavigationDrawerFragment.swapContext(position);
    }

    public void setViewport() {
        if (tempViewport.left == 0.0 || holdViewport.left == 0.0 || holdViewport.right  >= (new Date().getTime())) {
            previewChart.setCurrentViewport(bgGraphBuilder.advanceViewport(chart, previewChart), false);
        } else {
            previewChart.setCurrentViewport(holdViewport, false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (_broadcastReceiver != null)
            unregisterReceiver(_broadcastReceiver);
    }

    public void updateCurrentBgInfo() {
        final TextView currentBgValueText = (TextView) findViewById(R.id.currentBgValueRealTime);
        final TextView currentWixelBatteryText = (TextView) findViewById(R.id.currentWixelBattery);
        final TextView notificationText = (TextView)findViewById(R.id.notices);
        notificationText.setText("");
        boolean isBTWixel = CollectionServiceStarter.isBTWixel(getApplicationContext());
        boolean isDexbridge = CollectionServiceStarter.isDexbridge(getApplicationContext());
        if(((isBTWixel || isDexbridge) && ActiveBluetoothDevice.first() != null) || ((!isBTWixel || !isDexbridge) && WixelReader.IsConfigured(getApplicationContext()))) {
            if (Sensor.isActive() && (Sensor.currentSensor().started_at + (60000 * 60 * 2)) < new Date().getTime()) {
                if (BgReading.latest(2).size() > 1) {
                    List<Calibration> calibrations = Calibration.latest(2);
                    if (calibrations.size() > 1) {
                        if (calibrations.get(0).possible_bad != null && calibrations.get(0).possible_bad == true && calibrations.get(1).possible_bad != null && calibrations.get(1).possible_bad == false) {
                            notificationText.setText("Possible bad calibration slope, please have a glass of water, wash hands, then recalibrate in a few!");
                        }
                        displayCurrentInfo();
                    } else {
                        notificationText.setText("Please enter two calibrations to get started!");
                    }
                } else {
                    notificationText.setText("Please wait, need 2 readings from transmitter first.");
                }
            } else if (Sensor.isActive() && ((Sensor.currentSensor().started_at + (60000 * 60 * 2))) >= new Date().getTime()) {
                double waitTime = ((Sensor.currentSensor().started_at + (60000 * 60 * 2)) - (new Date().getTime())) / (60000) ;
                notificationText.setText("Please wait while sensor warms up! ("+ String.format("%.2f", waitTime)+" minutes)");
            } else {
                notificationText.setText("Now start your sensor");
            }
        } else {
            if((isBTWixel || isDexbridge)) {
                if((android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)) {
                    notificationText.setText("First pair with your BT device");
                } else {
                    notificationText.setText("Your device has to be android 4.3 and up to support Bluetooth wixel");
                }
            } else {
                notificationText.setText("First configure your wifi wixel reader ip addresses");
            }
        }
        mNavigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mNavigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout), menu_name, this);

    }


    public void addTreatment(View view) {
        Log.w("Adding treatment", "MESSAGE");
        Intent myIntent = new Intent(this, AddTreatment.class);
        startActivity(myIntent);
    }

    public void displayCurrentInfo() {
        DecimalFormat df = new DecimalFormat("#");
        df.setMaximumFractionDigits(0);
        DecimalFormat obdf = new DecimalFormat("#.#");
        obdf.setMaximumFractionDigits(1);
        final TextView currentBgValueText = (TextView)findViewById(R.id.currentBgValueRealTime);
        final TextView notificationText = (TextView)findViewById(R.id.notices);
        final TextView currentWixelBatteryText = (TextView) findViewById(R.id.currentWixelBattery);
        final TextView displayIOB = (TextView) findViewById(R.id.iob);
        final TextView displayCOB = (TextView) findViewById(R.id.cob);

        if ((currentBgValueText.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) > 0) {
            currentBgValueText.setPaintFlags(currentBgValueText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            currentWixelBatteryText.setPaintFlags((currentWixelBatteryText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG)));
        }

        displayIOB.setText("IOB: " + obdf.format(IobCob.iob()) + "U");
        displayCOB.setText("COB: " + obdf.format(IobCob.cob()) + "g");

        if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("display_dd_batt", false) == false) {
            currentWixelBatteryText.setVisibility(View.INVISIBLE);
        } else {
            currentWixelBatteryText.setVisibility(View.VISIBLE);
        }

        if (prefs.getBoolean("preventSleep",false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }   else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        //if (Integer.parseInt(lastBgreading.getWixelBatteryLevel(getApplicationContext())) < 15 && PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("display_dd_batt", false) == true) {
        if ((lastBgreading.sensor.wixel_battery_level) < 15 && PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("display_dd_batt", false) == true) {
            if (!isShown) {
                isShown = true;
                AlertDialog wixelAlertDialog = new AlertDialog.Builder(this).create();
                wixelAlertDialog.setTitle("Warning");
                    wixelAlertDialog.setMessage("Wixel battery is less than 15%");
                    wixelAlertDialog.setButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                            int which) {
                            isShown = false;
                            dialog.dismiss();
                        }
                    });
                    wixelAlertDialog.show();
                }
            }

        if (Math.round((float) lastBgreading.sensor.wixel_battery_level) > 0){
            DecimalFormat fmt = new DecimalFormat();
            fmt.setMinimumFractionDigits(1);
            fmt.setMaximumFractionDigits(1);
            currentWixelBatteryText.setText("Bridge Power: " + fmt.format(lastBgreading.sensor.wixel_battery_level) + "%");}
        else{
            currentWixelBatteryText.setText("Bridge Power: 0%");}

        if ((lastBgreading.sensor.wixel_battery_level >= 15) && (lastBgreading.sensor.wixel_battery_level <= 50)) {
            currentWixelBatteryText.setTextColor(Color.YELLOW);
        } else if (lastBgreading.sensor.wixel_battery_level < 15) {
            currentWixelBatteryText.setTextColor(Color.RED);
        } else {
            currentWixelBatteryText.setTextColor(Color.GREEN);
        }

        boolean predictive = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("predictive_bg", false);
            if (lastBgreading != null) {
            double estimate = 0;
            Log.i("time:getTime(): ", String.valueOf((new Date().getTime() - (60000 * 16))));
            Log.i("time:timestamp: ", String.valueOf(lastBgreading.timestamp));
            if (new Date().getTime() - (60000 * 16) - lastBgreading.timestamp > 0) {
                notificationText.setText("Signal Missed");
                if(!predictive){
                    estimate=lastBgreading.calculated_value;
                } else {
                    estimate = BgReading.estimated_bg(lastBgreading.timestamp + (6000 * 7));
                }
                currentBgValueText.setText(bgGraphBuilder.unitized_string(estimate));
                currentBgValueText.setPaintFlags(currentBgValueText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                if(!predictive){
                    estimate=lastBgreading.calculated_value;
                    String stringEstimate = bgGraphBuilder.unitized_string(estimate);
                    currentBgValueText.setText( stringEstimate + " " + BgReading.slopeArrow(lastBgreading.staticSlope()));
                } else {
                    estimate = BgReading.activePrediction();
                    String stringEstimate = bgGraphBuilder.unitized_string(estimate);
                    currentBgValueText.setText( stringEstimate + " " + BgReading.slopeArrow());
                }
            }

            if(bgGraphBuilder.unitized(estimate) <= bgGraphBuilder.lowMark) {
                if (bgGraphBuilder.unitized(estimate) > bgGraphBuilder.urgentLowMark){
                    currentBgValueText.setTextColor(Utils.COLOR_ORANGE);
                } else {
                    currentBgValueText.setTextColor(Utils.COLOR_RED);
                }
            } else if(bgGraphBuilder.unitized(estimate) >= bgGraphBuilder.highMark) {
                if (bgGraphBuilder.unitized(estimate) < bgGraphBuilder.urgentHighMark) {
                    currentBgValueText.setTextColor(Utils.COLOR_ORANGE);
                } else {
                    currentBgValueText.setTextColor(Utils.COLOR_RED);
                }
            } else {
                currentBgValueText.setTextColor(Color.WHITE);
            }
        }
    setupCharts();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_export_database) {
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... params) {
                    return DatabaseUtil.saveSql(getBaseContext());
                }

                @Override
                protected void onPostExecute(String filename) {
                    super.onPostExecute(filename);

                    final Context ctx = getApplicationContext();

                    Toast.makeText(ctx, "Export stored at " + filename, Toast.LENGTH_SHORT).show();

                    final NotificationCompat.Builder n = new NotificationCompat.Builder(ctx);
                    n.setContentTitle("Export complete");
                    n.setContentText("Ready to be sent.");
                    n.setAutoCancel(true);
                    n.setSmallIcon(R.drawable.ic_action_communication_invert_colors_on);
                    ShareNotification.viewOrShare("application/octet-stream", Uri.fromFile(new File(filename)), n, ctx);

                    final NotificationManager manager = (NotificationManager) ctx.getSystemService(Service.NOTIFICATION_SERVICE);
                    manager.notify(Notifications.exportCompleteNotificationId, n.build());
                }
            }.execute();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
