package com.eveningoutpost.dexdrip.Interfaces;

import com.eveningoutpost.dexdrip.Models.Treatments;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.POST;
import retrofit.http.Path;

/**
 * Created by jlg on 1/19/15.
 */
public interface TreatmentInterface {

    @POST("/api/v1/users/{user_uuid}/Treatments/new")
    void createReading(@Path("user_uuid") String user_uuid, @Body Treatments treatment, Callback callback);

}