package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.Treatments;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.WriteConcern;

import org.apache.http.Header;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * THIS CLASS WAS BUILT BY THE NIGHTSCOUT GROUP FOR THEIR NIGHTSCOUT ANDROID UPLOADER
 * https://github.com/nightscout/android-uploader/
 * I have modified this class to make it fit my needs
 * Modifications include field remappings and lists instead of arrays
 * A DTO would probably be a better future implementation
 * -Stephen Black
 */
public class NightscoutUploader {
        private static final String TAG = NightscoutUploader.class.getSimpleName();
        private static final int SOCKET_TIMEOUT = 60000;
        private static final int CONNECTION_TIMEOUT = 30000;
        private Context mContext;
        private Boolean enableRESTUpload;
        private Boolean enableMongoUpload;
        private SharedPreferences prefs;

        public NightscoutUploader(Context context) {
            mContext = context;
            prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            enableRESTUpload = prefs.getBoolean("cloud_storage_api_enable", false);
            enableMongoUpload = prefs.getBoolean("cloud_storage_mongodb_enable", false);
        }

        public boolean upload(BgReading glucoseDataSet, Calibration meterRecord, Calibration calRecord, Treatments treatmentRecord) {
            List<BgReading> glucoseDataSets = new ArrayList<BgReading>();
            glucoseDataSets.add(glucoseDataSet);
            List<Calibration> meterRecords = new ArrayList<Calibration>();
            meterRecords.add(meterRecord);
            List<Calibration> calRecords = new ArrayList<Calibration>();
            calRecords.add(calRecord);
            List<Treatments> treatmentRecords = new ArrayList<Treatments>();
            treatmentRecords.add(treatmentRecord);
            return upload(glucoseDataSets, meterRecords, calRecords, treatmentRecords);
        }

        public boolean upload(List<BgReading> glucoseDataSets, List<Calibration> meterRecords, List<Calibration> calRecords, List<Treatments> treatmentRecords) {
            boolean mongoStatus = false;
            boolean apiStatus = false;

            if (enableRESTUpload) {
                long start = System.currentTimeMillis();
                Log.i(TAG, String.format("Starting upload of %s record using a REST API", (glucoseDataSets.size() + meterRecords.size() + calRecords.size() + treatmentRecords.size())));
                apiStatus = doRESTUpload(prefs, glucoseDataSets, meterRecords, calRecords, treatmentRecords);
                Log.i(TAG, String.format("Finished upload of %s record using a REST API in %s ms", (glucoseDataSets.size() + meterRecords.size() + calRecords.size() + treatmentRecords.size()), System.currentTimeMillis() - start));
            }

            if (enableMongoUpload) {
                double start = new Date().getTime();
                Log.i(TAG, String.format("Starting upload of %s record using a Mongo", (glucoseDataSets.size() + meterRecords.size() + calRecords.size() + treatmentRecords.size())));
                mongoStatus = doMongoUpload(prefs, glucoseDataSets, meterRecords, calRecords, treatmentRecords);
                Log.i(TAG, String.format("Finished upload of %s record using a Mongo in %s ms", (glucoseDataSets.size() + meterRecords.size() + calRecords.size() + treatmentRecords.size()), System.currentTimeMillis() - start));
            }

            return apiStatus || mongoStatus;
        }

        private boolean doRESTUpload(SharedPreferences prefs, List<BgReading> glucoseDataSets, List<Calibration> meterRecords, List<Calibration> calRecords, List<Treatments> treatmentRecords) {
            String baseURLSettings = prefs.getString("cloud_storage_api_base", "");
            ArrayList<String> baseURIs = new ArrayList<String>();

            try {
                for (String baseURLSetting : baseURLSettings.split(" ")) {
                    String baseURL = baseURLSetting.trim();
                    if (baseURL.isEmpty()) continue;
                    baseURIs.add(baseURL + (baseURL.endsWith("/") ? "" : "/"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to process API Base URL");
                return false;
            }

            for (String baseURI : baseURIs) {
                try {
                    doRESTUploadTo(baseURI, glucoseDataSets, meterRecords, calRecords, treatmentRecords);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to do REST API Upload");
                    return false;
                }
            }
            return true;
        }

        private void doRESTUploadTo(String baseURI, List<BgReading> glucoseDataSets, List<Calibration> meterRecords, List<Calibration> calRecords, List<Treatments> treatmentRecords) {
            try {
                int apiVersion = 0;
                if (baseURI.endsWith("/v1/")) apiVersion = 1;

                String baseURL = null;
                String secret = null;
                String[] uriParts = baseURI.split("@");

                if (uriParts.length == 1 && apiVersion == 0) {
                    baseURL = uriParts[0];
                } else if (uriParts.length == 1 && apiVersion > 0) {
                    throw new Exception("Starting with API v1, a pass phase is required");
                } else if (uriParts.length == 2 && apiVersion > 0) {
                    secret = uriParts[0];
                    baseURL = uriParts[1];
                } else {
                    throw new Exception("Unexpected baseURI");
                }

                String postURL = baseURL + "entries";
                Log.i(TAG, "postURL: " + postURL);

                HttpParams params = new BasicHttpParams();
                HttpConnectionParams.setSoTimeout(params, SOCKET_TIMEOUT);
                HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);

                DefaultHttpClient httpclient = new DefaultHttpClient(params);

                HttpPost post = new HttpPost(postURL);

                Header apiSecretHeader = null;

                if (apiVersion > 0) {
                    if (secret == null || secret.isEmpty()) {
                        throw new Exception("Starting with API v1, a pass phase is required");
                    } else {
                        MessageDigest digest = MessageDigest.getInstance("SHA-1");
                        byte[] bytes = secret.getBytes("UTF-8");
                        digest.update(bytes, 0, bytes.length);
                        bytes = digest.digest();
                        StringBuilder sb = new StringBuilder(bytes.length * 2);
                        for (byte b: bytes) {
                            sb.append(String.format("%02x", b & 0xff));
                        }
                        String token = sb.toString();
                        apiSecretHeader = new BasicHeader("api-secret", token);
                    }
                }

                if (apiSecretHeader != null) {
                    post.setHeader(apiSecretHeader);
                }

                for (BgReading record : glucoseDataSets) {
                    JSONObject json = new JSONObject();

                    try {
                        if (apiVersion >= 1)
                            populateV1APIBGEntry(json, record);
                        else
                            populateLegacyAPIEntry(json, record);
                    } catch (Exception e) {
                        Log.w(TAG, "Unable to populate entry");
                        continue;
                    }

                    String jsonString = json.toString();

                    Log.i(TAG, "SGV JSON: " + jsonString);

                    try {
                        StringEntity se = new StringEntity(jsonString);
                        post.setEntity(se);
                        post.setHeader("Accept", "application/json");
                        post.setHeader("Content-type", "application/json");

                        ResponseHandler responseHandler = new BasicResponseHandler();
                        httpclient.execute(post, responseHandler);
                    } catch (Exception e) {
                        Log.w(TAG, "Unable to populate entry");
                    }
                }

                if (apiVersion >= 1) {
                    for (Calibration record : meterRecords) {
                        JSONObject json = new JSONObject();

                        try {
                            populateV1APIMeterReadingEntry(json, record);
                        } catch (Exception e) {
                            Log.w(TAG, "Unable to populate entry");
                            continue;
                        }

                        String jsonString = json.toString();
                        Log.i(TAG, "MBG JSON: " + jsonString);

                        try {
                            StringEntity se = new StringEntity(jsonString);
                            post.setEntity(se);
                            post.setHeader("Accept", "application/json");
                            post.setHeader("Content-type", "application/json");

                            ResponseHandler responseHandler = new BasicResponseHandler();
                            httpclient.execute(post, responseHandler);
                        } catch (Exception e) {
                            Log.w(TAG, "Unable to post data");
                        }
                    }
                }

                if (apiVersion >= 1) {
                    for (Calibration calRecord : calRecords) {

                        JSONObject json = new JSONObject();

                        try {
                            populateV1APICalibrationEntry(json, calRecord);
                        } catch (Exception e) {
                            Log.w(TAG, "Unable to populate entry");
                            continue;
                        }

                        String jsonString = json.toString();
                        Log.i(TAG, "CAL JSON: " + jsonString);

                        try {
                            StringEntity se = new StringEntity(jsonString);
                            post.setEntity(se);
                            post.setHeader("Accept", "application/json");
                            post.setHeader("Content-type", "application/json");

                            ResponseHandler responseHandler = new BasicResponseHandler();
                            httpclient.execute(post, responseHandler);
                        } catch (Exception e) {
                            Log.w(TAG, "Unable to post data");
                        }
                    }
                }

                String treatPostURL = baseURL + "treatments";
                Log.i(TAG, "treatPostURL: " + treatPostURL);
                HttpPost treatPost = new HttpPost(treatPostURL);

                if (apiVersion >= 1) {
                    for (Treatments treatmentRecord : treatmentRecords) {
                        Log.i(TAG, "treatmentRecord REST Upload");
                        JSONObject json = new JSONObject();

                        try {
                            populateV1APITreatmentEntry(json, treatmentRecord);
                            Log.i(TAG, "treatmentEntry populated");
                        } catch (Exception e) {
                            Log.w(TAG, "Unable to populate entry");
                            continue;
                        }

                        String jsonString = json.toString();
                        Log.i(TAG, "TREATMENT JSON: " + jsonString);

                        try {
                            StringEntity se = new StringEntity(jsonString);
                            Log.i(TAG, "Stringentity se" + se);
                            treatPost.setEntity(se);
                            Log.i(TAG, "treatPost.setEntity");
                            treatPost.setHeader("Accept", "application/json");
                            Log.i(TAG, "treatPost.setHeader Accept");
                            treatPost.setHeader("Content-type", "application/json");
                            Log.i(TAG, "treatPost.setHeader Content-type");

                            ResponseHandler responseHandler = new BasicResponseHandler();
                            Log.i(TAG, "responseHandler");
                            httpclient.execute(treatPost, responseHandler);
                            Log.i(TAG, "post.setHeader Accept");
                        } catch (Exception e) {
                            Log.w(TAG, "Unable to post treatment data");
                        }
                    }
                }

                // TODO: this is a quick port from the original code and needs to be checked before release
                postDeviceStatus(baseURL, apiSecretHeader, httpclient);

            } catch (Exception e) {
            Log.w(TAG, "Unable to post data");
            }
        }

        private void populateV1APIBGEntry(JSONObject json, BgReading record) throws Exception {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
            format.setTimeZone(TimeZone.getDefault());
            json.put("device", "dexcom");
            json.put("date", record.timestamp);
            json.put("dateString", format.format(record.timestamp));
            json.put("sgv", (int)record.calculated_value);
            json.put("direction", record.slopeName());
            json.put("type", "sgv");
            json.put("filtered", record.filtered_data);
            json.put("unfiltered", record.raw_data);
            json.put("rssi", "100");
        }

        private void populateLegacyAPIEntry(JSONObject json, BgReading record) throws Exception {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
            format.setTimeZone(TimeZone.getDefault());
            json.put("device", "dexcom");
            json.put("date", record.timestamp);
            json.put("dateString", format.format(record.timestamp));
            json.put("sgv", (int)record.calculated_value);
            json.put("direction", record.slopeName());
        }

        private void populateV1APIMeterReadingEntry(JSONObject json, Calibration record) throws Exception {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
            format.setTimeZone(TimeZone.getDefault());
            json.put("device", "dexcom");
            json.put("type", "mbg");
            json.put("date", record.timestamp);
            json.put("dateString", format.format(record.timestamp));
            json.put("mbg", record.bg);
        }

        private void populateV1APICalibrationEntry(JSONObject json, Calibration record) throws Exception {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
            format.setTimeZone(TimeZone.getDefault());

            json.put("device", "dexcom");
            json.put("type", "cal");
            json.put("date", record.timestamp);
            json.put("dateString", format.format(record.timestamp));
            json.put("slope", (int)(record.slope * 1000));
            json.put("intercept", (int) record.intercept);
            json.put("scale", 1000);
        }


        private void populateV1APITreatmentEntry(JSONObject json, Treatments record) throws Exception {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
            format.setTimeZone(TimeZone.getDefault());

            json.put("enteredBy", record.entered_by);
            json.put("eventType", record.event_type);
            json.put("glucose", record.bg);
            json.put("glucoseType", record.reading_type);
            json.put("carbs", record.carbs);
            json.put("insulin", record.insulin);
            json.put("notes", record.notes);
            json.put("preBolus", record.eating_time);
            json.put("created_at", format.format(record.treatment_time));
        }

        // TODO: this is a quick port from original code and needs to be refactored before release
        private void postDeviceStatus(String baseURL, Header apiSecretHeader, DefaultHttpClient httpclient) throws Exception {
            String devicestatusURL = baseURL + "devicestatus";
            Log.i(TAG, "devicestatusURL: " + devicestatusURL);

            JSONObject json = new JSONObject();
            json.put("uploaderBattery", getBatteryLevel());
            String jsonString = json.toString();

            HttpPost post = new HttpPost(devicestatusURL);

            if (apiSecretHeader != null) {
                post.setHeader(apiSecretHeader);
            }

            StringEntity se = new StringEntity(jsonString);
            post.setEntity(se);
            post.setHeader("Accept", "application/json");
            post.setHeader("Content-type", "application/json");

            ResponseHandler responseHandler = new BasicResponseHandler();
            httpclient.execute(post, responseHandler);
        }

        private boolean doMongoUpload(SharedPreferences prefs, List<BgReading> glucoseDataSets, List<Calibration> meterRecords,  List<Calibration> calRecords, List<Treatments> treatmentRecords) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
            format.setTimeZone(TimeZone.getDefault());

            String dbURI = prefs.getString("cloud_storage_mongodb_uri", null);
            String collectionName = prefs.getString("cloud_storage_mongodb_collection", null);
            String dsCollectionName = prefs.getString("cloud_storage_mongodb_device_status_collection", "devicestatus");
            String tCollectionName = prefs.getString("cloud_storage_mongodb_treatments_collection", "treatments");

            if (dbURI != null && collectionName != null) {
                try {

                    // connect to db
                    MongoClientURI uri = new MongoClientURI(dbURI.trim());
                    MongoClient client = new MongoClient(uri);

                    // get db
                    DB db = client.getDB(uri.getDatabase());

                    // get collection
                    DBCollection dexcomData = db.getCollection(collectionName.trim());
                    Log.i(TAG, "The number of EGV records being sent to MongoDB is " + glucoseDataSets.size());
                    for (BgReading record : glucoseDataSets) {
                        // make db object
                        BasicDBObject testData = new BasicDBObject();
                        testData.put("device", "dexcom");
                        testData.put("date", record.timestamp);
                        testData.put("dateString", format.format(record.timestamp));
                        testData.put("sgv", Math.round(record.calculated_value));
                        testData.put("direction", record.slopeName());
                        testData.put("type", "sgv");
                        testData.put("filtered", record.filtered_data);
                        testData.put("unfiltered", record.raw_data);
                        testData.put("rssi", "100");
                        dexcomData.update(testData, testData, true, false, WriteConcern.UNACKNOWLEDGED);
                    }

                    Log.i(TAG, "The number of MBG records being sent to MongoDB is " + meterRecords.size());
                    for (Calibration meterRecord : meterRecords) {
                        // make db object
                        BasicDBObject testData = new BasicDBObject();
                        testData.put("device", "dexcom");
                        testData.put("type", "mbg");
                        testData.put("date", meterRecord.timestamp);
                        testData.put("dateString", format.format(meterRecord.timestamp));
                        testData.put("mbg", meterRecord.bg);
                        dexcomData.update(testData, testData, true, false, WriteConcern.UNACKNOWLEDGED);
                    }

                    for (Calibration calRecord : calRecords) {
                        // make db object
                        BasicDBObject testData = new BasicDBObject();
                        testData.put("device", "dexcom");
                        testData.put("date", calRecord.timestamp);
                        testData.put("dateString", format.format(calRecord.timestamp));
                        testData.put("slope", (int)(calRecord.slope * 1000));
                        testData.put("intercept", (int) calRecord.intercept);
                        testData.put("scale", 1000);
                        testData.put("type", "cal");
                        dexcomData.update(testData, testData, true, false, WriteConcern.UNACKNOWLEDGED);
                    }

                    Log.i(TAG, "The number of treatment records being sent to MongoDB is " + treatmentRecords.size());
                    for (Treatments treatRecord : treatmentRecords) {
                        // make db object
                        DBCollection treatCollection = db.getCollection(tCollectionName.trim());
                        Log.w(TAG, "db object created");
                        BasicDBObject treatData = new BasicDBObject();
                        Log.w(TAG, "treatData created");

                        treatData.put("enteredBy", treatRecord.entered_by);
                        treatData.put("eventType", treatRecord.event_type);
                        if (treatRecord.eating_time == 0){
                            if (treatRecord.carbs != 0){
                                treatData.put("carbs", treatRecord.carbs);}}
                        if (treatRecord.bg != 0) {
                            treatData.put("glucose", treatRecord.bg);
                            treatData.put("glucoseType", treatRecord.reading_type);}
                        if (treatRecord.insulin != 0){
                            treatData.put("insulin", treatRecord.insulin);}
                        if (treatRecord.notes.length() > 0){
                            treatData.put("notes", treatRecord.notes);}
                        if (treatRecord.entered_by != ""){
                        }
                        treatData.put("created_at", format.format(treatRecord.treatment_time));
                        if (treatRecord.eating_time > 0){
                            treatData.put("preBolus", treatRecord.eating_time);
                            Log.w(TAG, "Prebolus data populated");
                            treatCollection.insert(treatData, WriteConcern.UNACKNOWLEDGED);
                            Log.w(TAG, "Prebolus data inserted");
                            BasicDBObject eatData = new BasicDBObject();
                            eatData.put("created_at", format.format((treatRecord.treatment_time + (treatRecord.eating_time * 60000))));
                            eatData.put("eventType", treatRecord.event_type);
                            eatData.put("carbs", treatRecord.carbs);
                            Log.w(TAG, "Eating data populated");
                            treatCollection.insert(eatData, WriteConcern.UNACKNOWLEDGED);
                            Log.w(TAG, "Eating data inserted");}
                        else{
                            Log.w(TAG, "Treatment data populated");
                            treatCollection.insert(treatData, WriteConcern.UNACKNOWLEDGED);
                            Log.w(TAG, "Treatment data inserted");
                        }
                    }

                    DBCollection dsCollection = db.getCollection(dsCollectionName);
                    BasicDBObject devicestatus = new BasicDBObject();
                    devicestatus.put("uploaderBattery", getBatteryLevel());
                    devicestatus.put("created_at", new Date());
                    dsCollection.insert(devicestatus, WriteConcern.UNACKNOWLEDGED);

                    client.close();

                    return true;

                } catch (Exception e) {
                    Log.e(TAG, "Unable to upload data to mongo");
                }
            }
            return false;
        }


    public int getBatteryLevel() {
        Intent batteryIntent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if(level == -1 || scale == -1) {
            return 50;
        }
        return (int)(((float)level / (float)scale) * 100.0f);
    }
}
