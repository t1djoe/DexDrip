package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Treatments;

import java.util.List;

/**
 * Created by stephenblack on 11/7/14.
 */
@Table(name = "TreatmentSendQueue", id = BaseColumns._ID)
public class TreatmentSendQueue extends Model {

    @Column(name = "treatment", index = true)
    public Treatments treatment;

    @Column(name = "success", index = true)
    public boolean success;

    @Column(name = "mongo_success", index = true)
    public boolean mongo_success;

    @Column(name = "operation_type")
    public String operation_type;

    public static TreatmentSendQueue nextTreatmentJob() {
        return  new Select()
                .from(TreatmentSendQueue.class)
                .where("success = ?", false)
                .orderBy("_ID desc")
                .limit(1)
                .executeSingle();
    }

    public static List<TreatmentSendQueue> queue() {
        return new Select()
                .from(TreatmentSendQueue.class)
                .where("success = ?", false)
                .orderBy("_ID asc")
                .limit(20)
                .execute();
    }
    public static List<TreatmentSendQueue> mongoQueue() {
        return new Select()
                .from(TreatmentSendQueue.class)
                .where("mongo_success = ?", false)
                .where("operation_type = ?", "create")
                .orderBy("_ID asc")
                .limit(10)
                .execute();
    }

    public static void addToQueue(Treatments treatment, String operation_type, Context context) {
        TreatmentSendQueue treatmentSendQueue = new TreatmentSendQueue();
        treatmentSendQueue.operation_type = operation_type;
        treatmentSendQueue.treatment = treatment;
        treatmentSendQueue.success = false;
        treatmentSendQueue.mongo_success = false;

        treatmentSendQueue.save();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (prefs.getBoolean("cloud_storage_mongodb_enable", false) || prefs.getBoolean("cloud_storage_api_enable", false)) {
            Log.w("TREATMENT QUEUE:", String.valueOf(treatmentSendQueue.operation_type));
            if (operation_type == "create") {
                MongoSendTask task = new MongoSendTask(context, treatmentSendQueue);
                task.execute();
            }
        }
    }

    public void markMongoSuccess() {
        mongo_success = true;
        save();
    }
}