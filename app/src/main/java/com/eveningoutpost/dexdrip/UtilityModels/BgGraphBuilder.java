package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.Treatments;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.util.Utils;
import lecho.lib.hellocharts.view.Chart;

/**
 * Created by stephenblack on 11/15/14.
 */
public class BgGraphBuilder {
    public double  end_time = new Date().getTime() + (60000 * 10);
    public double  start_time = end_time - (60000 * 60 * 24);
    public Context context;
    public SharedPreferences prefs;
    public double urgentHighMark;
    public double highMark;
    public double lowMark;
    public double urgentLowMark;
    public double defaultMinY;
    public double defaultMaxY;
    public boolean doMgdl;
    final int pointSize;
    final int infoSize;
    final int axisTextSize;
    final int previewAxisTextSize;
    final int hoursPreviewStep;

    private double endHour;
    private final int numValues =(60/5)*120;
    private final List<BgReading> bgReadings = BgReading.latestForGraph( numValues, start_time);
    private final List<Treatments> treatments = Treatments.latestForGraph( numValues, start_time);
    private final List<Calibration> calibrations = Calibration.latestForGraph( numValues, start_time);
    private List<PointValue> inRangeValues = new ArrayList<PointValue>();
    private List<PointValue> urgentHighValues = new ArrayList<PointValue>();
    private List<PointValue> highValues = new ArrayList<PointValue>();
    private List<PointValue> lowValues = new ArrayList<PointValue>();
    private List<PointValue> urgentLowValues = new ArrayList<PointValue>();
    private List<PointValue> treatmentValues = new ArrayList<PointValue>();
    private List<PointValue> calibrationValues = new ArrayList<PointValue>();
    public Viewport viewport;


    public BgGraphBuilder(Context context){
        this.context = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.urgentHighMark = Double.parseDouble(prefs.getString("urgentHighValue", "220"));
        this.highMark = Double.parseDouble(prefs.getString("highValue", "170"));
        this.lowMark = Double.parseDouble(prefs.getString("lowValue", "70"));
        this.urgentLowMark = Double.parseDouble(prefs.getString("urgentLowValue", "60"));
        this.doMgdl = (prefs.getString("units", "mgdl").compareTo("mgdl") == 0);
        defaultMinY = unitized(40);
        defaultMaxY = unitized(250);
        pointSize = isXLargeTablet() ? 5 : 3;
        infoSize = isXLargeTablet() ? 8 : 5;
        axisTextSize = isXLargeTablet() ? 20 : Axis.DEFAULT_TEXT_SIZE_SP;
        previewAxisTextSize = isXLargeTablet() ? 12 : 5;
        hoursPreviewStep = isXLargeTablet() ? 2 : 1;
    }

    public LineChartData lineData() {
        LineChartData lineData = new LineChartData(defaultLines());
        lineData.setAxisYLeft(yAxis());
        lineData.setAxisXBottom(xAxis());
        return lineData;
    }

    public LineChartData previewLineData() {
        LineChartData previewLineData = new LineChartData(lineData());
        previewLineData.setAxisYLeft(yAxis());
        previewLineData.setAxisXBottom(previewXAxis());
        previewLineData.getLines().get(4).setPointRadius(2);
        previewLineData.getLines().get(5).setPointRadius(2);
        previewLineData.getLines().get(6).setPointRadius(2);
        return previewLineData;
    }

    public List<Line> defaultLines() {
        addBgReadingValues();
        addTreatmentValues();
        addCalibrationValues();
        List<Line> lines = new ArrayList<Line>();
        lines.add(minShowLine());
        lines.add(maxShowLine());
        lines.add(urgentHighLine());
        lines.add(highLine());
        lines.add(lowLine());
        lines.add(urgentLowLine());
        lines.add(inRangeValuesLine());
        lines.add(urgentLowValuesLine());
        lines.add(lowValuesLine());
        lines.add(highValuesLine());
        lines.add(urgentHighValuesLine());
        lines.add(treatmentValuesLine());
        lines.add(calibrationValuesLine());
        return lines;
    }

    public Line highValuesLine() {
        Line highValuesLine = new Line(highValues);
        highValuesLine.setColor(Utils.COLOR_ORANGE);
        highValuesLine.setHasLines(false);
        highValuesLine.setPointRadius(pointSize);
        highValuesLine.setHasPoints(true);
        return highValuesLine;
    }

    public Line urgentHighValuesLine() {
        Line urgentHighValuesLine = new Line(urgentHighValues);
        urgentHighValuesLine.setColor(Utils.COLOR_RED);
        urgentHighValuesLine.setHasLines(false);
        urgentHighValuesLine.setPointRadius(pointSize);
        urgentHighValuesLine.setHasPoints(true);
        return urgentHighValuesLine;
    }

    public Line lowValuesLine() {
        Line lowValuesLine = new Line(lowValues);
        lowValuesLine.setColor(Utils.COLOR_ORANGE);
        lowValuesLine.setHasLines(false);
        lowValuesLine.setPointRadius(pointSize);
        lowValuesLine.setHasPoints(true);
        return lowValuesLine;
    }

    public Line urgentLowValuesLine() {
        Line urgentLowValuesLine = new Line(urgentLowValues);
        urgentLowValuesLine.setColor(Utils.COLOR_RED);
        urgentLowValuesLine.setHasLines(false);
        urgentLowValuesLine.setPointRadius(pointSize);
        urgentLowValuesLine.setHasPoints(true);
        return urgentLowValuesLine;
    }

    public Line inRangeValuesLine() {
        Line inRangeValuesLine = new Line(inRangeValues);
        inRangeValuesLine.setColor(Utils.COLOR_GREEN);
        inRangeValuesLine.setHasLines(false);
        inRangeValuesLine.setPointRadius(pointSize);
        inRangeValuesLine.setHasPoints(true);
        return inRangeValuesLine;
    }

    public Line treatmentValuesLine() {
        Line treatmentValuesLine = new Line(treatmentValues);
        treatmentValuesLine.setColor(Utils.COLOR_BLUE);
        treatmentValuesLine.setHasLines(false);
        treatmentValuesLine.setPointRadius(infoSize);
        treatmentValuesLine.setHasPoints(true);
        return treatmentValuesLine;
    }

    public Line calibrationValuesLine() {
        Line calibrationValuesLine = new Line(calibrationValues);
        calibrationValuesLine.setColor(Utils.COLOR_RED);
        calibrationValuesLine.setHasLines(false);
        calibrationValuesLine.setPointRadius(infoSize);
        calibrationValuesLine.setHasPoints(true);
        return calibrationValuesLine;
    }

    private void addBgReadingValues() {
        for (BgReading bgReading : bgReadings) {
            if (unitized(bgReading.calculated_value) >= highMark) {
                if (unitized(bgReading.calculated_value) >= urgentHighMark) {
                    urgentHighValues.add(new PointValue((float) bgReading.timestamp, (float) unitized(bgReading.calculated_value)));
                } else if (unitized(bgReading.calculated_value) >= 400) {
                    urgentHighValues.add(new PointValue((float) bgReading.timestamp, (float) unitized(400)));
                } else {
                    highValues.add(new PointValue((float) bgReading.timestamp, (float) unitized(bgReading.calculated_value)));
                }
            } else if (unitized(bgReading.calculated_value) <= lowMark) {
                if (unitized(bgReading.calculated_value) <= urgentLowMark) {
                    urgentLowValues.add(new PointValue((float)bgReading.timestamp, (float) unitized(bgReading.calculated_value)));
                } else if (unitized(bgReading.calculated_value) <= 40) {
                    urgentLowValues.add(new PointValue((float)bgReading.timestamp, (float) unitized(40)));
                } else {
                    lowValues.add(new PointValue((float)bgReading.timestamp, (float) unitized(bgReading.calculated_value)));
                }
            } else {
                inRangeValues.add(new PointValue((float) bgReading.timestamp, (float) unitized(bgReading.calculated_value)));
            }
        }
    }

    private void addTreatmentValues() {
        for (Treatments treatment : treatments) {
            treatmentValues.add(new PointValue((float) treatment.treatment_time, (float) unitized(100)));
        }
    }

    private void addCalibrationValues() {
        for (Calibration calibration : calibrations) {
            calibrationValues.add(new PointValue((float) calibration.timestamp, (float) unitized(100)));
        }
    }

    public Line urgentHighLine() {
        List<PointValue> urgentHighLineValues = new ArrayList<PointValue>();
        urgentHighLineValues.add(new PointValue((float)start_time, (float)urgentHighMark));
        urgentHighLineValues.add(new PointValue((float)end_time, (float)urgentHighMark));
        Line urgentHighLine = new Line(urgentHighLineValues);
        urgentHighLine.setHasPoints(false);
        urgentHighLine.setStrokeWidth(1);
        urgentHighLine.setColor(Utils.COLOR_RED);
        return urgentHighLine;
    }

    public Line highLine() {
        List<PointValue> highLineValues = new ArrayList<PointValue>();
        highLineValues.add(new PointValue((float)start_time, (float)highMark));
        highLineValues.add(new PointValue((float)end_time, (float)highMark));
        Line highLine = new Line(highLineValues);
        highLine.setHasPoints(false);
        highLine.setStrokeWidth(1);
        highLine.setColor(Utils.COLOR_ORANGE);
        return highLine;
    }

    public Line lowLine() {
        List<PointValue> lowLineValues = new ArrayList<PointValue>();
        lowLineValues.add(new PointValue((float)start_time, (float)lowMark));
        lowLineValues.add(new PointValue((float)end_time, (float)lowMark));
        Line lowLine = new Line(lowLineValues);
        lowLine.setHasPoints(false);
        lowLine.setAreaTransparency(50);
        lowLine.setColor(Utils.COLOR_ORANGE);
        lowLine.setStrokeWidth(1);
        lowLine.setFilled(true);
        return lowLine;
    }

    public Line urgentLowLine() {
        List<PointValue> urgentLowLineValues = new ArrayList<PointValue>();
        urgentLowLineValues.add(new PointValue((float)start_time, (float)urgentLowMark));
        urgentLowLineValues.add(new PointValue((float)end_time, (float)urgentLowMark));
        Line urgentLowLine = new Line(urgentLowLineValues);
        urgentLowLine.setHasPoints(false);
        urgentLowLine.setAreaTransparency(50);
        urgentLowLine.setColor(Utils.COLOR_RED);
        urgentLowLine.setStrokeWidth(1);
        urgentLowLine.setFilled(true);
        return urgentLowLine;
    }

    public Line maxShowLine() {
        List<PointValue> maxShowValues = new ArrayList<PointValue>();
        maxShowValues.add(new PointValue((float)start_time, (float)defaultMaxY));
        maxShowValues.add(new PointValue((float)end_time, (float)defaultMaxY));
        Line maxShowLine = new Line(maxShowValues);
        maxShowLine.setHasLines(false);
        maxShowLine.setHasPoints(false);
        return maxShowLine;
    }

    public Line minShowLine() {
        List<PointValue> minShowValues = new ArrayList<PointValue>();
        minShowValues.add(new PointValue((float)start_time, (float)defaultMinY));
        minShowValues.add(new PointValue((float)end_time, (float)defaultMinY));
        Line minShowLine = new Line(minShowValues);
        minShowLine.setHasPoints(false);
        minShowLine.setHasLines(false);
        return minShowLine;
    }

    /////////AXIS RELATED//////////////
    public Axis yAxis() {
        Axis yAxis = new Axis();
        yAxis.setAutoGenerated(false);
        List<AxisValue> axisValues = new ArrayList<AxisValue>();

        for(int j = 1; j <= 12; j += 1) {
            if (doMgdl) {
                axisValues.add(new AxisValue(j * 50));
            } else {
                axisValues.add(new AxisValue(j*2));
            }
        }
        yAxis.setValues(axisValues);
        yAxis.setHasLines(true);
        yAxis.setMaxLabelChars(5);
        yAxis.setInside(true);
        yAxis.setTextSize(axisTextSize);
        return yAxis;
    }

    public Axis xAxis() {
        Axis xAxis = new Axis();
        xAxis.setAutoGenerated(false);
        List<AxisValue> xAxisValues = new ArrayList<AxisValue>();
        GregorianCalendar now = new GregorianCalendar();
        GregorianCalendar today = new GregorianCalendar(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        final java.text.DateFormat timeFormat = hourFormat();
        timeFormat.setTimeZone(TimeZone.getDefault());
        double start_hour = today.getTime().getTime();
        double timeNow = new Date().getTime();
        for(int l=0; l<=24; l++) {
            if ((start_hour + (60000 * 60 * (l))) <  timeNow) {
                if((start_hour + (60000 * 60 * (l + 1))) >=  timeNow) {
                    endHour = start_hour + (60000 * 60 * (l));
                    l=25;
                }
            }
        }
        for(int l=0; l<=24; l++) {
            double timestamp = endHour - (60000 * 60 * l);
            xAxisValues.add(new AxisValue((long)(timestamp), (timeFormat.format(timestamp)).toCharArray()));
        }
        xAxis.setValues(xAxisValues);
        xAxis.setHasLines(true);
        xAxis.setTextSize(axisTextSize);
        return xAxis;
    }

    private SimpleDateFormat hourFormat() {
        return new SimpleDateFormat(DateFormat.is24HourFormat(context) ? "HH" : "h a");
    }

    private boolean isXLargeTablet() {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    public Axis previewXAxis(){
        List<AxisValue> previewXaxisValues = new ArrayList<AxisValue>();
        final java.text.DateFormat timeFormat = hourFormat();
        timeFormat.setTimeZone(TimeZone.getDefault());
        for(int l=0; l<=24; l+=hoursPreviewStep) {
            double timestamp = endHour - (60000 * 60 * l);
            previewXaxisValues.add(new AxisValue((long)(timestamp), (timeFormat.format(timestamp)).toCharArray()));
        }
        Axis previewXaxis = new Axis();
        previewXaxis.setValues(previewXaxisValues);
        previewXaxis.setHasLines(true);
        previewXaxis.setTextSize(previewAxisTextSize);
        return previewXaxis;
    }

    /////////VIEWPORT RELATED//////////////
    public Viewport advanceViewport(Chart chart, Chart previewChart) {
        viewport = new Viewport(previewChart.getMaximumViewport());
        viewport.inset((float)(86400000 / 2.5), 0);
        double distance_to_move = (new Date().getTime()) - viewport.left - (((viewport.right - viewport.left) /2));
        viewport.offset((float) distance_to_move, 0);
        return viewport;
    }

    public double unitized(double value) {
        if(doMgdl) {
            return value;
        } else {
            return mmolConvert(value);
        }
    }

    public String unitized_string(double value) {
        value = Math.round(value);
        DecimalFormat df = new DecimalFormat("#");
        df.setMaximumFractionDigits(0);
        if (value >= 400) {
            return "HIGH";
        } else if (value >= 40) {
            if(doMgdl) {
                df.setMaximumFractionDigits(0);
                df.setMinimumFractionDigits(0);
                return df.format(value);
            } else {
                df.setMaximumFractionDigits(1);
                df.setMinimumFractionDigits(1);
                return df.format(mmolConvert(value));
            }
        } else {
            return "LOW";
        }
    }

    public double mmolConvert(double mgdl) {
        return mgdl * Constants.MGDL_TO_MMOLL;
    }

    public String unit() {
        if(doMgdl){
            return "mg/dl";
        } else {
            return "mmol";
        }

    }
}
