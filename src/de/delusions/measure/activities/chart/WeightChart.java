/*
   Copyright 2011 Sonja Pieper

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package de.delusions.measure.activities.chart;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;
import de.delusions.measure.MeasureActivity;
import de.delusions.measure.MeasureTabs;
import de.delusions.measure.R;
import de.delusions.measure.activities.bmi.BMI;
import de.delusions.measure.activities.bmi.StatisticsFactory;
import de.delusions.measure.activities.prefs.PrefItem;
import de.delusions.measure.activities.prefs.UserPreferences;
import de.delusions.measure.database.SqliteHelper;
import de.delusions.measure.ment.MeasureType;
import de.delusions.measure.ment.Measurement;

public class WeightChart extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int TEXTSIZE = 16;
    private static final int PADDING = 30;
    private static final int MIN_IMAGE_WIDTH = 400;
    private static final int MIN_IMAGE_HEIGHT = 400;
    public static final int LEFT = 0;
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 3;

    private static final int SEGMENTS = 5;

    private static final int MONTH = 30;
    private static final int MONTH_3 = 90;
    private static final int MONTH_6 = 180;
    private static final int WEEK_2 = 14;
    private static final int YEAR_1 = 360;
    private static final int ALL = -1;

    private static final SimpleDateFormat DATE_LABEL_FORMAT = new SimpleDateFormat("dd/MM");

    private static ChartCoordinates COORDS;
    private static Paint BACKGROUND = createPaint(Color.WHITE, Paint.Style.FILL);
    private static Paint GRID = createPaint(Color.GRAY, Paint.Style.STROKE);

    private int days;
    private int imageWidth;
    private int imageHeight;
    private MeasureType displayField;
    private MeasurePath trackedValuePath;
    private boolean showAll;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(MeasureActivity.TAG,"onCreate WeightChart");
        getWindow().setFormat(PixelFormat.RGBA_8888);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DITHER);
        setContentView(R.layout.activity_chart);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        refreshDisplayField();

        calculateImageDimensions();

        this.trackedValuePath = new MeasurePath(this, this.displayField, this.days);

        final int[] drawSizes = createCoords(this.imageWidth - PADDING, this.imageHeight - PADDING, GRID, calculateMeasureLabel(0));
        COORDS = new ChartCoordinates(this.days, drawSizes);

        this.days = MONTH;

        refresh();

        final Button month1 = (Button) findViewById(R.id.months_1);
        month1.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {
                WeightChart.this.days = MONTH;
                refresh();
            }
        });

        final Button month3 = (Button) findViewById(R.id.months_3);
        month3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                WeightChart.this.days = MONTH_3;
                refresh();
            }
        });
        final Button month6 = (Button) findViewById(R.id.months_6);
        month6.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {
                WeightChart.this.days = MONTH_6;
                refresh();
            }
        });
        final Button week2 = (Button) findViewById(R.id.week_2);
        week2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                WeightChart.this.days = WEEK_2;
                refresh();
            }
        });

        final Button year1 = (Button) findViewById(R.id.year_1);
        year1.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {
                WeightChart.this.days = YEAR_1;
                refresh();
            }
        });

        final Button all = (Button) findViewById(R.id.all);
        all.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {
                final SqliteHelper db = new SqliteHelper(WeightChart.this);
                db.open();
                final Cursor cursor = db.fetchFirst(WeightChart.this.displayField);
                final Measurement first = MeasureType.WEIGHT.createMeasurement(cursor);
                WeightChart.this.days = new Long((System.currentTimeMillis()-first.getTimestamp().getTime())/(1000*60*60*24)).intValue();
                cursor.close();
                db.close();
                refresh();
            }
        });

        final ToggleButton showbutton = (ToggleButton) findViewById(R.id.showall);
        showbutton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {
                WeightChart.this.showAll = showbutton.isChecked();
                refresh();
            }
        });

    }

    private void refreshDisplayField() {
        this.displayField = UserPreferences.getDisplayField(this);
        final TextView chartTitle = (TextView) findViewById(R.id.chart_title);
        chartTitle.setText(this.displayField.getLabel(this));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(MeasureActivity.TAG, "onConfigurationChanged");
        refreshGraph();
    }


    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PrefItem.DISPLAY_MEASURE.getKey())) {
            Log.d(MeasureActivity.TAG, "onSharedPreferenceChanged " + key);
            refreshDisplayField();
            refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return MeasureTabs.basicMenu(this, item) || super.onMenuItemSelected(featureId, item);
    }

    public void refresh() {
        Log.d(MeasureActivity.TAG, "refreshing Graph");
        COORDS.setDays(this.days);
        this.trackedValuePath.refreshData(this.displayField, this.days);
        refreshGraph();
    }

    public Point calculatePoint(double x, double y) {
        return COORDS.calculatePoint(x, y, this.trackedValuePath.getCeiling(), this.trackedValuePath.getFloor());
    }

    private void refreshGraph() {
        final ImageView image = (ImageView) findViewById(R.id.testy_img);
        final Bitmap charty = Bitmap.createBitmap(this.imageWidth, this.imageHeight, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(charty);
        drawBackgroundAndGrid(canvas);

        if (this.displayField == MeasureType.WEIGHT && !this.showAll) {
            drawBmiLines(canvas);
            drawGoalLine(canvas);
        }
        this.trackedValuePath.fillPath(COORDS);
        canvas.drawPath(this.trackedValuePath, createGraphPaint(this.displayField, 4));
        if (this.showAll) {
            for (final MeasureType type : MeasureType.getEnabledTypes(this)) {
                if (type != this.displayField) {
                    Log.d(MeasureActivity.TAG, "Adding line for " + type);
                    final MeasurePath path = new MeasurePath(this, type, this.days);
                    path.fillPath(COORDS);
                    canvas.drawPath(path, createGraphPaint(type, 4));
                }
            }
            drawLegend(canvas);
        }
        image.setImageBitmap(charty);
    }

    private Paint createGraphPaint(MeasureType type, int strokeWidth) {
        final Paint paint = createPaint(type.getColor(), Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        return paint;
    }

    private void drawBackgroundAndGrid(final Canvas canvas) {
        canvas.drawRect(COORDS.getLeft(), COORDS.getTop(), COORDS.getLeft() + COORDS.getRight(), COORDS.getTop() + COORDS.getBottom(), BACKGROUND);
        for (int segment = 0; segment <= SEGMENTS; segment++) {
            drawGridLine(canvas, GRID, segment, false);
            drawGridLine(canvas, GRID, segment, true);
            if (!this.showAll) {
                drawGridLabel(canvas, GRID, segment, false, calculateMeasureLabel(segment));
            }
            drawGridLabel(canvas, GRID, segment, true, calculateDateLabel(segment));
        }
    }

    private void drawLegend(Canvas canvas) {
        final float x = COORDS.getLeft() + 10;
        float y = COORDS.getTop() + 10 + TEXTSIZE;
        for (final MeasureType type : MeasureType.getEnabledTypes(this)) {
            final Paint paint = createPaint(type.getColor(), Style.FILL);
            canvas.drawText(getResources().getString(type.getLabelId()), x, y, paint);
            y = y + TEXTSIZE + 5;
        }
    }

    private void drawBmiLines(final Canvas canvas) {
        final Measurement height = UserPreferences.getHeight(this);
        final Paint paint = createPaint(Color.WHITE, Paint.Style.STROKE);
        for (final BMI bmi : BMI.values()) {
            final int bmiValue = bmi.getCeiling(this);
            final Measurement bmiWeight = StatisticsFactory.calculateBmiWeight(bmiValue, height);
            final float value = bmiWeight.getValue(UserPreferences.isMetric(this));
            if (value < this.trackedValuePath.getCeiling() && value > this.trackedValuePath.getFloor()) {
                paint.setColor(bmi.getColor(this));

                final Path bmiPath = createHorizontalPath(value);
                canvas.drawPath(bmiPath, paint);

                final String labelStr = "BMI " + bmiValue;
                final Point label = createStartPointForHorizontalLabel(paint, labelStr, value);
                canvas.drawText(labelStr, label.x, label.y, paint);
            }
        }
    }

    private void drawGoalLine(Canvas canvas) {
        final String labelStr = "Goal";
        final float value = UserPreferences.getGoal(this).getValue(UserPreferences.isMetric(this));
        final Paint paint = createPaint(getResources().getColor(R.color.yourgoal), Paint.Style.STROKE);
        final Path goalPath = createHorizontalPath(value);
        final Point label = createStartPointForHorizontalLabel(paint, labelStr, value);
        canvas.drawPath(goalPath, paint);
        canvas.drawText(labelStr, label.x, label.y, paint);
    }

    private Path createHorizontalPath(final float value) {
        final Point start = calculatePoint(0, value);
        final Point end = calculatePoint(this.days, value);
        final Path bmiLine = new Path();
        bmiLine.moveTo(start.x, start.y);
        bmiLine.lineTo(end.x, end.y);
        return bmiLine;
    }

    private Point createStartPointForHorizontalLabel(Paint paint, String label, float value) {
        final int textWidth = Math.round(calculateTextWidth(paint, label));
        final Point lineEnd = calculatePoint(this.days, value);
        final Point result = new Point();
        result.x = lineEnd.x - textWidth;
        result.y = lineEnd.y - 2;
        return result;
    }

    private String calculateMeasureLabel(int segment) {
        final int perSegment = (this.trackedValuePath.getCeiling() - this.trackedValuePath.getFloor()) / SEGMENTS;
        final int labelValue = segment * perSegment;
        return this.trackedValuePath.getCeiling() - labelValue + this.displayField.getUnit().retrieveUnitName(this);
    }

    private String calculateDateLabel(int segment) {
        final Calendar cal = (Calendar) this.trackedValuePath.getStartingDate().clone();
        final int daysPerSegment = this.days / SEGMENTS;
        final int days = segment * daysPerSegment;
        cal.add(Calendar.DAY_OF_MONTH, days);
        return DATE_LABEL_FORMAT.format(cal.getTime());
    }

    private void calculateImageDimensions() {
        final DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        final int orientation = getWindowManager().getDefaultDisplay().getOrientation();
        final int metricWidth = metrics.widthPixels - PADDING;
        final int metricHeight = metrics.heightPixels - 350; // tabhost and buttons)
        this.imageWidth = Math.max(MIN_IMAGE_WIDTH, metricWidth);
        this.imageHeight = Math.max(MIN_IMAGE_HEIGHT, metricHeight);
        // final ImageView image = (ImageView) findViewById(R.id.testy_img);
        Log.d(MeasureActivity.TAG, "calculateImageDimensions " + " orientation:" + orientation + " width:" + this.imageWidth + " height:"
                + this.imageHeight);
    }

    public static void drawGridLine(Canvas canvas, final Paint paint, int segment, boolean vertical) {
        final float startX = COORDS.getLeft() + (vertical ? segment * COORDS.getRight() / SEGMENTS : 0);
        final float startY = COORDS.getTop() + (vertical ? 0 : segment * COORDS.getBottom() / SEGMENTS);
        final float stopX = COORDS.getLeft() + (vertical ? segment * COORDS.getRight() / SEGMENTS : COORDS.getRight());
        final float stopY = COORDS.getTop() + (vertical ? COORDS.getBottom() : segment * COORDS.getBottom() / SEGMENTS);
        canvas.drawLine(startX, startY, stopX, stopY, paint);
    }

    public static void drawGridLabel(Canvas canvas, final Paint paint, int segment, boolean vertical, String label) {
        final float textSize = paint.getTextSize();
        final float textWidth = calculateTextWidth(paint, label);
        final float x = COORDS.getLeft() + (vertical ? segment * COORDS.getRight() / SEGMENTS : -textWidth - 2);
        final float y = COORDS.getTop() + (vertical ? COORDS.getBottom() + textSize : segment * COORDS.getBottom() / SEGMENTS);

        if (vertical) {
            canvas.rotate(60, x, y);
            canvas.drawText(label, x, y, paint);
            canvas.rotate(-60, x, y);
        } else {
            canvas.drawText(label, x, y, paint);
        }

    }

    public static Paint createPaint(int color, Paint.Style style) {
        final Paint paint = new Paint();
        paint.setTextSize(TEXTSIZE);
        paint.setStyle(style);
        paint.setColor(color);
        paint.setAntiAlias(true);
        return paint;
    }

    /**
     * Rounded_max is the max value we want to plot and therefore the longest label on the y-axis
     * 
     * @param paint
     * @param rounded_max
     * 
     * @return
     */
    public static int[] createCoords(int width, int height, Paint paint, String maxLabel) {
        final int leftMargin = calculateTextWidth(paint, maxLabel);
        final int bottomMargin = calculateTextWidth(paint, "01/01");
        final int left = 2 + leftMargin;
        final int top = 25;
        final int right = width - 2 - leftMargin;
        final int bottom = height - top - bottomMargin;
        final int[] result = { left, top, right, bottom };
        return result;
    }

    public static int calculateTextWidth(final Paint paint, String label) {
        final float[] widths = new float[label.length()];
        paint.getTextWidths(label, widths);
        float sum = 0;
        for (final float w : widths) {
            sum += w;
        }
        return (int) sum;
    }

}