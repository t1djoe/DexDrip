package com.eveningoutpost.dexdrip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.eveningoutpost.dexdrip.Models.ActiveBluetoothDevice;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.Treatments;
import com.eveningoutpost.dexdrip.Services.WixelReader;
import com.eveningoutpost.dexdrip.UtilityModels.BgGraphBuilder;
import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import lecho.lib.hellocharts.ViewportChangeListener;
import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.view.LineChartView;
import lecho.lib.hellocharts.view.PreviewLineChartView;


public class Home extends Activity implements NavigationDrawerFragment.NavigationDrawerCallbacks {
    private String menu_name = "DIYPanc";
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private LineChartView chart;
    private PreviewLineChartView previewChart;
    SharedPreferences prefs;
    Viewport tempViewport = new Viewport();
    Viewport holdViewport = new Viewport();
    public float left;
    public float right;
    public float top;
    public float bottom;
    public boolean updateStuff;
    public boolean updatingPreviewViewport = false;
    public boolean updatingChartViewport = false;
    public double iob;
    public double cob;
    public double calcIob;
    public double calcCob;
    public double carbImpact;
    public double initialCarbs;
    public double nextCarbTreatment;
    public Date decayedBy = new Date();
    public int isDecaying;
    public double iobContrib;
    public double activityContrib;
    public double activity;
    public double insulinActivity;
    public boolean isShown = false;

    public BgGraphBuilder bgGraphBuilder;
    BroadcastReceiver _broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        BgReading lastBgreading = BgReading.lastNoSenssor();

        calcIobCob(lastBgreading.calculated_value);
        displayIOB.setText("IOB: " + obdf.format(iob) + "U");
        displayCOB.setText("COB: " + obdf.format(cob) + "g");

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
            if ((new Date().getTime()) - (60000 * 11) - lastBgreading.timestamp > 0) {
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
                currentBgValueText.setTextColor(Color.parseColor("#C30909"));
            } else if(bgGraphBuilder.unitized(estimate) >= bgGraphBuilder.highMark) {
                currentBgValueText.setTextColor(Color.parseColor("#FFBB33"));
            } else {
                currentBgValueText.setTextColor(Color.WHITE);
            }
        }
    setupCharts();
    }

    public void calcIobCob(double bgi) {
        Log.i("calcIobCob", "MESSAGE");
        double carbs_hr = Double.parseDouble(prefs.getString("carbs_hr", "0"));
        double sens = Double.parseDouble(prefs.getString("sensitivity", "0"));
        double dia = Double.parseDouble(prefs.getString("insulinDIA", "0"));
        double carbratio = Double.parseDouble(prefs.getString("carbRatio", "0"));

        int predict_hr = (int) dia;

        double cobDecay;
        iob = 0;
        cob = 0;

        cobTotal();

        Date endtime=new Date();
        endtime.setHours(endtime.getHours() + predict_hr);
        Log.i("calcIobCob calcCob:" + calcCob, "CARBS");
        Log.i("calcIobCob isDecaying:" + isDecaying, "CARBS");
        if ((calcCob > 0) && (isDecaying==1)) {
            // calculate carbImpact and change in cob as a function of insulinActivity
            Log.i("carbImpact", "CARBS");
            carbImpact=0;
            carbImpact = (carbs_hr/60 - Math.min(0,dia*sens));
            Log.i("calcIobCob carbImpact:" + carbImpact, "CARBS");
            cobDecay = carbImpact/carbratio;
            Log.i("calcIobCob cobDecay:" + cobDecay, "CARBS");
            cob = calcCob - cobDecay;}
        else {
            // if there is a new carb treatment after cob=0, or we're in the 20m delay, recalculate everything
            if (endtime.getTime() > nextCarbTreatment || cob > 0) {
                cobTotal();
            }
            // otherwise, no need to do anything until the nextCarbTreatment
            else {
                carbImpact = 0;
                cob = 0;
            }
        }

        // re-run iobTotal() to get latest insulinActivity
        iobTotal();
        iob = calcIob;
        insulinActivity = activity;


        // use totalImpact to calculate predBG[]
        double totalImpact = carbImpact-insulinActivity;
        bgi = bgi + totalImpact;

        return;
    }

    public void iobTotal() {
        Log.i("iobTotal", "INSULIN");
        List<Treatments> latestTreatments = Treatments.latest();
        int listLength = latestTreatments.size();

        Date treatDate = new Date();
        treatDate.setTime((treatDate.getTime() - (4*60*60*1000)));

        if (listLength == 0) return;

        calcIob = 0;
        activity = 0;

        for (int i = 0; i < listLength; i++) {
            Treatments element = latestTreatments.get(i);

            if(element.treatment_time >= treatDate.getTime()) {
                Log.i("Launching iobCalc", "INSULIN");
                iobCalc(element);
                Log.i("iobCalc iobContrib: " + iobContrib, "INSULIN");
                if (iobContrib>0) calcIob += iobContrib;
                Log.i("iobCalc activityContrib: " + activityContrib, "INSULIN");
                if (activityContrib>0) activity += activityContrib;
            }
        }

        return;
    }

    public void iobCalc(Treatments treatment) {
        Log.i("iobCalc", "INSULIN");
        double sens = Double.parseDouble(prefs.getString("sensitivity", "0"));
        double dia = Double.parseDouble(prefs.getString("insulinDIA", "0"));
        double basal = Double.parseDouble(prefs.getString("basal", "0"));

        iobContrib = 0;
        activityContrib = 0;

        int peak = 0;

        Date treatDate = new Date();
        Date now = new Date();
        treatDate.setTime((treatDate.getTime() - (4 * 60 * 60 * 1000)));
        Log.i("iobCalc dia: " + dia, "INSULIN");
        if (dia == 3) {
            peak = 75;
        }else if (dia == 4){
            peak = 90;
        } else {
            Log.i("DIA of " + dia + "not supported", "INSULIN");
        }
        Log.i("iobCalc insulin: " + treatment.insulin, "INSULIN");
        if (treatment.insulin > 0) {

            long minAgo=(now.getTime() - treatment.treatment_time)/1000/60;
            Log.i("iobCalc minAgo: " + minAgo, "INSULIN");

            if (minAgo < 0) {
                iobContrib=0;
                activityContrib=0;
            }
            if (minAgo < peak) {
                double x = minAgo/5+1;
                iobContrib=treatment.insulin*(1-0.001852*x*x+0.001852*x);
                activityContrib=sens*treatment.insulin*((2/dia/60/peak)*minAgo);

            }
            else if (minAgo < dia) {
                double x = (minAgo-peak);
                iobContrib=treatment.insulin*(0.001323*x*x - .054233*x + .55556);
                activityContrib=sens*treatment.insulin*((2/dia/60-(minAgo-peak)*2/dia/60/(60*dia-peak)));
            }
            else {
                iobContrib=0;
                activityContrib=0;
            }

            return;
        }
        else return;
    }

    public void cobTotal() {
        List<Treatments> latestTreatments = Treatments.latest();
        int listLength = latestTreatments.size();

        double carbs_hr = Double.parseDouble(prefs.getString("carbs_hr", "0"));
        double carbratio = Double.parseDouble(prefs.getString("carbRatio", "0"));
        double sens = Double.parseDouble(prefs.getString("sensitivity", "0"));

        Log.w("listLength: " + listLength, "CARBS");

        if (latestTreatments.size() == 0) return;

        Date treatDate = new Date();
        treatDate.setTime((treatDate.getTime() - (4 * 60 * 60 * 1000)));

        int isDecaying = 1;
        Date lastDecayedBy = new Date("1/1/1970");

        for (int i = 0; i < latestTreatments.size(); i++) {
            Log.w("element i: " + i, "CARBS");
            Treatments element = latestTreatments.get(i);
            if(element.carbs > 0) {
                Log.i("cobTotal carbs: " + element.carbs, "CARBS");
                if (element.treatment_time >= treatDate.getTime()) {
                    boolean ccalc = cobCalc(element, lastDecayedBy, carbs_hr);
                    lastDecayedBy = decayedBy;
                    if (ccalc) {
                        //if (cCalc.carbsleft) {
                        //    var carbsleft = + cCalc.carbsleft;
                        //}
                    }

                    double decaysin_hr = (decayedBy.getTime() - element.treatment_time)/1000/60/60;
                    Log.i("cobTotal decaysin_hr: " + decaysin_hr, "CARBS");
                    if (decaysin_hr > 0) {
                        calcCob = Math.min(initialCarbs, decaysin_hr * carbs_hr);
                    }
                    else {
                        calcCob = 0;
                    }
                    Log.i("cobTotal calcCob: " + calcCob, "CARBS");
                }
                else {
                    nextCarbTreatment = new Date(element.treatment_time).getTime();
                }
            }
        };

        carbImpact = isDecaying*sens/carbratio*carbs_hr/60;
        return;
    }

    public boolean cobCalc(Treatments treatment, Date lastDecayedBy, double carbs_hr) {

        int delay = 20;
        double carbs_min = carbs_hr / 60;
        Log.i("treatment.carbs: " + treatment.carbs, "CARBS");
        if (treatment.carbs > 0) {
            Date decayedBy = new Date(treatment.treatment_time);

            long minutesleft = ((lastDecayedBy.getTime() - treatment.treatment_time) / 1000) / 60;
            Log.i("minutesleft: " + minutesleft, "CARBS");
            decayedBy.setMinutes((int) (decayedBy.getMinutes() + Math.max(delay, minutesleft) + (treatment.carbs / carbs_min)));
            Log.i("decayedBy: " + decayedBy.getTime(), "CARBS");
            if (delay > minutesleft) {
                initialCarbs = treatment.carbs;
            } else {
                initialCarbs = treatment.carbs + minutesleft * carbs_min;
            }
            Log.i("initialCarbs: " + initialCarbs, "CARBS");
            Log.i("treatment.treatment_time: " + treatment.treatment_time, "CARBS");
            Log.i("lastDecayedBy.getTime(): " + lastDecayedBy.getTime(), "CARBS");
            Log.i("startDecay.getTime(): " + treatment.treatment_time, "CARBS");
            Log.i("isDecaying if: " + (treatment.treatment_time < lastDecayedBy.getTime() || treatment.treatment_time > treatment.treatment_time), "CARBS");
            if (treatment.treatment_time < lastDecayedBy.getTime() || treatment.treatment_time > treatment.treatment_time) {
                isDecaying = 1;
            } else {
                isDecaying = 0;
            }

            return true;
        } else {
            return false;
        }
    }
}
