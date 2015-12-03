package com.iuridiniz.checkmyecg;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.net.Uri;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYStepMode;
import com.iuridiniz.checkmyecg.examiners.EkgExaminer;
import com.iuridiniz.checkmyecg.filter.GraphFilter2;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.KeyPoint;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class ResultEkgActivity extends ActionBarActivity {

    public static final String EXTRA_PHOTO_URI =
            "com.iuridiniz.checkmyecg.ResultEkgActivity.extra.PHOTO_URI;";
    public static final String EXTRA_PHOTO_DATA_PATH =
            "com.iuridiniz.checkmyecg.ResultEkgActivity.extra.DATA_PATH;";

    public static final String EXTRA_SERIES_X =
            "com.iuridiniz.checkmyecg.ResultEkgActivity.extra.SERIES_X;";
    public static final String EXTRA_SERIES_Y =
            "com.iuridiniz.checkmyecg.ResultEkgActivity.extra.SERIES_Y;";

    static final String TAG = "ResultEkg";

    private ShareActionProvider mShareActionProvider;
    private Uri mImageUri;
    private String mImageDataPath;
    private ImageView mImageContent;
    private TextView mTextContent;
    private boolean mOpenCvLoaded = false;
    private XYPlot mPlot;

    private double[] mSeriesX;
    private double[] mSeriesY;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result_ekg);

        final Intent intent = getIntent();


        mSeriesX = intent.getDoubleArrayExtra(EXTRA_SERIES_X);
        mSeriesY = intent.getDoubleArrayExtra(EXTRA_SERIES_Y);


        mPlot = (XYPlot) findViewById(R.id.result_plot);

        mPlot.setVisibility(View.INVISIBLE);

        mTextContent = (TextView) findViewById(R.id.result_text);

        mTextContent.setText("");

        /* init openCV */
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV loaded successfully (static initialization)");
            mOpenCvLoaded = true;
            onReady(savedInstanceState);
            return;
        }

        /* binaries not included, use OpenCV manager */
        Log.i(TAG, "OpenCV libs not included on APK, trying async initialization");
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_10, this, new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                switch (status) {
                    case BaseLoaderCallback.SUCCESS:
                        Log.i(TAG, "OpenCV loaded successfully (async initialization)");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mOpenCvLoaded = true;
                                onReady(savedInstanceState);
                            }
                        });
                        break;
                    default:
                        super.onManagerConnected(status);
                        break;
                }
            }
        });

    }

    private void onReady(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pick_derivation)
                .setItems(R.array.derivations_array, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, final int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ResultEkgActivity.this.compareWithDerivation(which);
                                ResultEkgActivity.this.showEKG();
                            }
                        });
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        ResultEkgActivity.this.finish();
                    }
                });
        Dialog d = builder.create();
        d.show();
    }

    private void showEKG() {
        // Create a couple arrays of y-values to plot:
        Number[] seriesX = {0, 1, 2, 3, 4, 5}; /* sample data */
        Number[] seriesY = {1, 8, -5, 2, 7, 4}; /* sample data */

        if (mSeriesX != null && mSeriesY != null) {
            seriesX = convertDoubleArrayToNumberArray(mSeriesX);
            seriesY = convertDoubleArrayToNumberArray(mSeriesY);
        }
        // Turn the above arrays into XYSeries':
        XYSeries series1 = new SimpleXYSeries(
                Arrays.asList(seriesX),
                Arrays.asList(seriesY),
                "EKG");

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter series1Format = new LineAndPointFormatter(Color.BLACK, null, null, null);

        // add a new series' to the xyplot:
        mPlot.addSeries(series1, series1Format);
        mPlot.setVisibility(View.VISIBLE);
        mPlot.invalidate();

    }



    private void compareWithDerivation(int derivationIndex) {
        String[] derivations = getResources().getStringArray(R.array.derivations_array);
        String derivation = derivations[derivationIndex];
        Log.i(TAG, String.format("Comparing the EKG derivation '%s' with a pathological base", derivation));

        String ret = null;
        switch (derivation.toUpperCase()) {
            case "DI":
                ret = this.examinerDi();
                break;
            case "DII":
                ret = this.examinerDii();
                break;
            case "DIII":
                ret = this.examinerDiii();
                break;
            case "AVR":
                ret = this.examinerAvr();
                break;
            case "AVL":
                ret = this.examinerAvl();
                break;
            case "AVF":
                ret = this.examinerAvf();
                break;
            case "V1":
                ret = this.examinerV1();
                break;
            case "V2":
                ret = this.examinerV2();
                break;
            case "V3":
                ret = this.examinerV3();
                break;
            case "V4":
                ret = this.examinerV4();
                break;
            case "V5":
                ret = this.examinerV5();
                break;
            case "V6":
                ret = this.examinerV6();
                break;
            default:
                this.finish();
                Log.d(TAG, "unknow derivation");
        }
        if (ret == null) {
            ret = "";
        }
        mTextContent.setText(ret);
    }

    private String examinerDi() {
        //EkgExaminer e = new DiExaminer(mSeriesX, mSeriesY);
        EkgExaminer e = new EkgExaminer(mSeriesX, mSeriesY, EkgExaminer.Orientation.DOWN);
        double freq = e.getFrequency();
        if (freq >= 10 && freq < 60) {
            return String.format(getString(R.string.result_bradycardia), freq);
        } else if (freq >= 60 && freq < 90) {
            return String.format(getString(R.string.result_normacardia), freq);
        } else if (freq >= 90 && freq < 300) {
            return String.format(getString(R.string.result_tachycardia), freq);
        }
        return String.format(getString(R.string.result_unknown, freq));
    }

    private String examinerDii() {
        return examinerDi();
    }

    private String examinerDiii() {
        return examinerDi();
    }

    private String examinerAvr() {
        return examinerDi();
    }

    private String examinerAvl() {
        return examinerDi();
    }

    private String examinerAvf() {
        return examinerDi();
    }

    private String examinerV1() {
        return examinerDi();
    }

    private String examinerV2() {
        return examinerDi();
    }

    private String examinerV3() {
        return examinerDi();
    }

    private String examinerV4() {
        return examinerDi();
    }

    private String examinerV5() {
        return examinerDi();
    }

    private String examinerV6() {
        return examinerDi();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_result_ekg, menu);

        // Set up ShareActionProvider's default share intent
        MenuItem shareItem = menu.findItem(R.id.action_share);
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(shareItem);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, mTextContent.getText());
        mShareActionProvider.setShareIntent(intent);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

    public static Number[] convertDoubleArrayToNumberArray(double[] a) {
        Number[] r = new Number[a.length];
        for (int i=0; i<a.length; i++) {
            r[i] = a[i];
        }
        return r;
    }

    public static double[] convertNumberListToDoubleArray(List<Number> l) {

        double[] r = new double[l.size()];
        for (int i=0; i<r.length; i++) {
            r[i] = (double) l.get(i);
        }
        return r;
    }
}
