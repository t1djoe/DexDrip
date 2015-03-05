package com.eveningoutpost.dexdrip.Tables;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.View;
import android.widget.SimpleCursorAdapter;

import com.activeandroid.Cache;
import com.eveningoutpost.dexdrip.NavigationDrawerFragment;
import com.eveningoutpost.dexdrip.R;

import java.util.ArrayList;


public class TreatmentDataTable extends ListActivity implements NavigationDrawerFragment.NavigationDrawerCallbacks {
    private String menu_name = "Treatment Data Table";
    private NavigationDrawerFragment mNavigationDrawerFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.raw_data_list);
    }

    @Override
    protected void onResume(){
        super.onResume();
        mNavigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mNavigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout), menu_name, this);
        getData();
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        mNavigationDrawerFragment.swapContext(position);
    }

    private void getData() {
        //Cursor cursor = Cache.openDatabase().rawQuery("Select * from Treatments order by treatment_time desc", null);
        Log.w("Cursor cursor", "MESSAGE");
        Cursor cursor = Cache.openDatabase().rawQuery("Select * from Treatments order by _ID desc limit 50", null);
        Log.w("adapter", "MESSAGE");
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                R.layout.treatment_data_list_item,
                cursor,
                new String[] { "event_type", "bg", "reading_type", "carbs", "insulin", "eating_time", "notes", "entered_by", "treatment_time" },
                new int[] { R.id.event_type, R.id.bg, R.id.reading_type, R.id.carbs, R.id.insulin, R.id.eating_time, R.id.notes, R.id.entered_by, R.id.treatment_time });
        this.setListAdapter(adapter);
    }

}
