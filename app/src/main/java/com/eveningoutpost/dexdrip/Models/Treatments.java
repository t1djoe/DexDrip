package com.eveningoutpost.dexdrip.Models;

import android.content.Context;
import android.provider.BaseColumns;
import android.util.Log;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.eveningoutpost.dexdrip.Sensor;
import com.eveningoutpost.dexdrip.UtilityModels.Notifications;
import com.eveningoutpost.dexdrip.UtilityModels.TreatmentSendQueue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.internal.bind.DateTypeAdapter;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

/**
 * Created by stephenblack on 10/29/14.
 */
@Table(name = "Treatments", id = BaseColumns._ID)
public class Treatments extends Model {


    @Expose
    @Column(name = "event_type")
    public String event_type;

    @Expose
    @Column(name = "bg")
    public double bg;

    @Expose
    @Column(name = "reading_type")
    public String reading_type;

    @Expose
    @Column(name = "carbs")
    public double carbs;

    @Expose
    @Column(name = "insulin")
    public double insulin;

    @Expose
    @Column(name = "eating_time")
    public long eating_time;

    @Expose
    @Column(name = "notes")
    public String notes;

    @Expose
    @Column(name = "entered_by")
    public String entered_by;

    @Expose
    @Column(name = "treatment_time", index = true)
    public long treatment_time;

    public static Treatments create(String enteredBy, String eventType, double bg, String readingType, double carbs, double insulin, long eatTime, String notes, long treatTime, Context context) {

        Treatments treatment = new Treatments();

        treatment.entered_by = enteredBy;
        treatment.event_type = eventType;
        treatment.bg = bg;
        treatment.reading_type = readingType;
        treatment.carbs = carbs;
        treatment.insulin = insulin;
        treatment.eating_time = eatTime;
        treatment.notes = notes;
        treatment.treatment_time = treatTime;
        treatment.save();

        TreatmentSendQueue.addToQueue(treatment, "create", context);
        return(treatment);
    }

}