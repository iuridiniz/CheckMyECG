package com.iuridiniz.checkmyecg;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.iuridiniz.checkmyecg.filter.GavriloGraphFilter;
import com.iuridiniz.checkmyecg.filter.GraphFilter2;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;


public class ShowEkgActivity extends ActionBarActivity implements View.OnTouchListener {

    public static final String EXTRA_PHOTO_URI =
            "com.iuridiniz.checkmyecg.ShowEkgActivity.extra.PHOTO_URI";
    public static final String EXTRA_PHOTO_DATA_PATH =
            "com.iuridiniz.checkmyecg.ShowEkgActivity.extra.PHOTO_DATA_PATH";

    public static final String TAG = "EkgShow";
    private boolean mOpenCvLoaded = false;
    private Uri mUri;
    private String mDataPath;

    private GraphFilter2 mGraphFilter;
    private ImageView mImageContent;
    private Bitmap mBitmap;

    private int mX1 = 0, mX2 = 0, mY1 = 0, mY2 = 0;
    private Mat mImageRgba;
    private boolean mDrawing = false;
    private boolean needDrawECG = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_ekg);

        final Intent intent = getIntent();
        mUri = intent.getParcelableExtra(EXTRA_PHOTO_URI);
        mDataPath = intent.getStringExtra(EXTRA_PHOTO_DATA_PATH);

        mImageContent = (ImageView) findViewById(R.id.image_content);

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


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_show_ekg, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }


    private void onReady(Bundle savedInstanceState) {

        /* load image */
        Log.d(TAG, String.format("Loading '%s' as image", mDataPath));
        Mat imageBgr = Highgui.imread(mDataPath);

        if (!(imageBgr.width() > 0)) {
            Log.e(TAG, "Unable to load image");
            finish();
            imageBgr.release();
            imageBgr = null;
        }

        Log.d(TAG, String.format("Image loaded (%s), channels: %d, type: %s",
                imageBgr.size(),
                imageBgr.channels(),
                CvType.typeToString(imageBgr.type())));
        //mGraphFilter = new GraphFilter2(imageBgr.rows(), imageBgr.cols());

        mImageRgba = new Mat(imageBgr.rows(), imageBgr.cols(), CvType.CV_8UC4);

        Imgproc.cvtColor(imageBgr, mImageRgba, Imgproc.COLOR_BGR2RGBA);

        /* show image */
        mBitmap = Bitmap.createBitmap(mImageRgba.cols(), mImageRgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mImageRgba, mBitmap);
        mImageContent.setImageBitmap(mBitmap);

        imageBgr.release();
        imageBgr = null;

        mImageContent.setOnTouchListener(this);

    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int eventaction = motionEvent.getAction();

        int x = (int) motionEvent.getX();
        int y = (int) motionEvent.getY();


        boolean needDraw = false;

        switch (eventaction) {

            case MotionEvent.ACTION_DOWN:
                needDrawECG = false;
                mX1 = x;
                mY1 = y;
                break;
            case MotionEvent.ACTION_UP:
                needDrawECG = true;
            case MotionEvent.ACTION_MOVE:
                mX2 = x;
                mY2 = y;
                needDraw = true;
                break;
        }
        if (needDraw && !mDrawing) {
            mDrawing = true;
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //Log.e(TAG, "Drawing");
                            drawRectangle();
                            mDrawing = false;
                            needDrawECG = false;
                        }
                    });
                }
            }, 200);

        }

        return true;
    }

    private void drawRectangle() {
        Mat modifiedImageRgba = mImageRgba.clone();
        int maxX = modifiedImageRgba.cols();
        int maxY = modifiedImageRgba.rows();

        if (mX1 < 0) mX1 = 0;
        if (mX2 < 0) mX2 = 0;
        if (mY1 < 0) mY1 = 0;
        if (mY2 < 0) mY2 = 0;
        if (mX1 > maxX) mX1 = maxX;
        if (mX2 > maxX) mX2 = maxX;
        if (mY1 > maxY) mY1 = maxY;
        if (mY2 > maxY) mY2 = maxY;

        Rect rect = new Rect(new Point(mX1, mY1), new Point(mX2, mY2));
        Mat roi = modifiedImageRgba.submat(rect);
        if (needDrawECG) {
            GavriloGraphFilter filter = new GavriloGraphFilter(roi.rows(), roi.cols());
            Mat result = filter.apply(roi);
            result.copyTo(roi);
        } else {
            Mat color = new Mat(roi.size(), CvType.CV_8UC4, new Scalar(0xFF, 0xFF, 0xFF, 0x00));
            double alpha = 0.3;
            Core.addWeighted(color, alpha, roi, 1 - alpha, 0, roi);
            Core.rectangle(roi, new Point(0, 0), new Point(roi.cols(), roi.rows()), new Scalar(0xFF, 0xFF, 0x00, 0x00), 5);
        }
        mBitmap = Bitmap.createBitmap(modifiedImageRgba.cols(), modifiedImageRgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(modifiedImageRgba, mBitmap);
        mImageContent.setImageBitmap(mBitmap);
    }
}
