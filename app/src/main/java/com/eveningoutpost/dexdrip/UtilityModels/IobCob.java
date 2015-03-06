package com.eveningoutpost.dexdrip.UtilityModels;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Context;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Treatments;
import com.google.gson.annotations.Expose;

import java.util.Date;
import java.util.List;

/**
 * Created by joe on 3/3/15.
 */
@TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)
@Table(name = "IobCob", id = BaseColumns._ID)
public class IobCob extends Model {

    @Expose
    @Column(name = "iob", index = true)
    public double iob;

    @Expose
    @Column(name = "cob", index = true)
    public double cob;

    @Expose
    @Column(name = "bgi", index = true)
    public double bgi;

    private final static String TAG = IobCob.class.getSimpleName();

    public double calcIob;
    public double calcCob;
    public double carbImpact;
    public double initialCarbs;
    public double nextCarbTreatment;
    public Date decayedBy = new Date();
    public int isDecaying;
    public double iobContrib;
    public double activityContrib;
    private double activity;
    public double insulinActivity;

    BgReading lastBgreading = BgReading.lastNoSenssor();

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Home.getContext());
    double carbs_hr = Double.parseDouble(prefs.getString("carbs_hr", "0"));
    double sens = Double.parseDouble(prefs.getString("sensitivity", "0"));
    double dia = Double.parseDouble(prefs.getString("insulinDIA", "0"));
    double carbratio = Double.parseDouble(prefs.getString("carbRatio", "0"));

    public IobCob iobCob;

    public static double iob(){
        IobCob iobcob = new IobCob();
        iobcob.calcIobCob();
        return iobcob.iob;
    }

    public static double cob(){
        IobCob iobcob = new IobCob();
        iobcob.calcIobCob();
        return iobcob.cob;
    }

    public void calcIobCob() {
        Log.i("calcIobCob", "MESSAGE");

        int predict_hr = (int) dia;

        double cobDecay;
        bgi = lastBgreading.calculated_value;
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
        double scaleFactor = 3.0/dia;

        int peak = 75;

        Date treatDate = new Date();
        Date now = new Date();
        treatDate.setTime((treatDate.getTime() - (4 * 60 * 60 * 1000)));
        Log.i("iobCalc dia: " + dia, "INSULIN");

        Log.i("iobCalc insulin: " + treatment.insulin, "INSULIN");
        if (treatment.insulin > 0) {

            long minAgo=(long) (scaleFactor * (now.getTime() - treatment.treatment_time)/1000/60);
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
        Log.i("carbs_hr: " + carbs_hr, "CARBS");
        Log.i("carbs_min: " + carbs_min, "CARBS");
        if (treatment.carbs > 0) {
            Date decayedBy = new Date(treatment.treatment_time);
            Date now = new Date();
            long minutesToDecay = (long) (treatment.carbs / carbs_min);
            Log.i("minutesToDecay: " + minutesToDecay, "CARBS");
            long minutesSinceTreatment = (long) ((now.getTime() - decayedBy.getTime()) / 1000) / 60;
            Log.i("minutesSinceTreatment: " + minutesSinceTreatment, "CARBS");
            long minutesleft = minutesToDecay - minutesSinceTreatment;
            Log.i("lastDecayedBy: " + lastDecayedBy.getTime(), "CARBS");
            Log.i("treatment_time: " + treatment.treatment_time, "CARBS");
            Log.i("minutesleft: " + minutesleft, "CARBS");
            decayedBy.setMinutes((int) (decayedBy.getMinutes() + Math.max(delay, minutesleft) + (treatment.carbs / carbs_min)));
            Log.i("decayedBy: " + decayedBy.getTime(), "CARBS");
            if (delay > minutesSinceTreatment) {
                initialCarbs = treatment.carbs;
            } else {
                initialCarbs = (treatment.carbs / carbs_min) - minutesleft;
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
