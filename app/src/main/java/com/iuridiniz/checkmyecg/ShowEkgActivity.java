package com.iuridiniz.checkmyecg;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.internal.view.menu.ActionMenuItemView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.iuridiniz.checkmyecg.filter.Filter;
import com.iuridiniz.checkmyecg.filter.GavriloGradeFilter;
import com.iuridiniz.checkmyecg.filter.GavriloGraphFilter;
import com.iuridiniz.checkmyecg.filter.GraphFilter;
import com.iuridiniz.checkmyecg.filter.GraphFilter2;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class ShowEkgActivity extends ActionBarActivity implements View.OnTouchListener {

    public static final String EXTRA_PHOTO_URI =
            "com.iuridiniz.checkmyecg.ShowEkgActivity.extra.PHOTO_URI";
    public static final String EXTRA_PHOTO_DATA_PATH =
            "com.iuridiniz.checkmyecg.ShowEkgActivity.extra.PHOTO_DATA_PATH";

    public static final String TAG = "EkgShow";
    private boolean mOpenCvLoaded = false;
    private Uri mUri;
    private String mDataPath;

    private ImageView mImageContent;
    private Bitmap mBitmap;

    private int mX1 = 0, mX2 = 0, mY1 = 0, mY2 = 0;
    private Mat mImageRgba;
    private boolean mDrawing = false;
    private boolean needDrawECG = false;
    private Filter mFilter;
    private Mat ekgEclosed;
    private Mat mEkgOriginal = null;
    private MenuItem mSelectButton;
    private List<Number> mSeriesX;
    private List<Number> mSeriesY;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_ekg);

        final Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Log.d(TAG, String.format("Intent action: %s and type: %s", action, type));

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("image/")) {
                mUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        } else {
            mUri = intent.getParcelableExtra(EXTRA_PHOTO_URI);
            mDataPath = intent.getStringExtra(EXTRA_PHOTO_DATA_PATH);
        }

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
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        if (result) {
            mSelectButton = menu.findItem(R.id.action_select_ekg);
            mSelectButton.setVisible(false);
        }
        return result;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch(id) {
            case R.id.action_select_ekg:
                openResultEkg();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }




    private void onReady(Bundle savedInstanceState) {

        /* load image */
        Mat imageBgr = null;
        Log.d(TAG, String.format("Loading '%s'(URI: '%s') as image", mDataPath, mUri));
        if (mDataPath != null) {
            imageBgr = Highgui.imread(mDataPath);
        } else if (mUri != null) {
            /* XXX: uri is complicated */
            /* FROM: http://answers.opencv.org/question/31855/java-api-loading-image-from-any-java-inputstream/ */

            byte[] temporaryImageInMemory;
            try {
                InputStream is = getContentResolver().openInputStream(mUri);
                // Copy content of the image to byte-array
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[16384];

                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }

                buffer.flush();
                temporaryImageInMemory = buffer.toByteArray();
                buffer.close();
                is.close();

                imageBgr = Highgui.imdecode(new MatOfByte(temporaryImageInMemory), Highgui.IMREAD_COLOR);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Unable to load image from URI", e);
            }
        }

        if (imageBgr == null || !(imageBgr.width() > 0)) {
            Log.e(TAG, "Unable to load image");
            finish();

            if (imageBgr != null)
                imageBgr.release();
            return;
        }

        Log.d(TAG, String.format("Image loaded (%s), channels: %d, type: %s",
                imageBgr.size(),
                imageBgr.channels(),
                CvType.typeToString(imageBgr.type())));

        mImageRgba = new Mat(imageBgr.rows(), imageBgr.cols(), CvType.CV_8UC4);

        Imgproc.cvtColor(imageBgr, mImageRgba, Imgproc.COLOR_BGR2RGBA);

        /* show image */
        mBitmap = Bitmap.createBitmap(mImageRgba.cols(), mImageRgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mImageRgba, mBitmap);
        mImageContent.setImageBitmap(mBitmap);

        imageBgr.release();

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

    private void openResultEkg() {
        final Intent intent = new Intent(this, ResultEkgActivity.class);

        intent.putExtra(ResultEkgActivity.EXTRA_SERIES_X,
                ResultEkgActivity.convertNumberListToDoubleArray(mSeriesX));
        intent.putExtra(ResultEkgActivity.EXTRA_SERIES_Y,
                ResultEkgActivity.convertNumberListToDoubleArray(mSeriesY));

        startActivity(intent);
    }

    private void drawRectangle() {
        Mat modifiedImageRgba = mImageRgba.clone();
        int maxX = modifiedImageRgba.cols();
        int maxY = modifiedImageRgba.rows();

        int x1 = mX1, x2 = mX2, y1 = mY1, y2 = mY2;

        int w = mImageContent.getWidth();
        int h = mImageContent.getHeight();

        if(mSelectButton != null) { mSelectButton.setVisible(false); }

        if (w == 0 || h == 0) {
            return;
        }


        if (needDrawECG) {
            /* transform coordinates (imageX:viewX <--> imageW:viewW) */
            x1 = Math.round(maxX / (float) w * x1);
            x2 = Math.round(maxX / (float) w * x2);

            y1 = Math.round(maxY / (float) h * y1);
            y2 = Math.round(maxY / (float) h * y2);

            if (x1 < 0) x1 = 0;
            if (x2 < 0) x2 = 0;
            if (y1 < 0) y1 = 0;
            if (y2 < 0) y2 = 0;
            if (x1 > maxX) x1 = maxX;
            if (x2 > maxX) x2 = maxX;
            if (y1 > maxY) y1 = maxY;
            if (y2 > maxY) y2 = maxY;

            Rect rect = new Rect(new Point(x1, y1), new Point(x2, y2));
            Mat roi = modifiedImageRgba.submat(rect);

            mFilter = new GavriloGraphFilter(roi.rows(), roi.cols());
            /* save original */
            mEkgOriginal = roi.clone();

            Mat result = mFilter.apply(roi);
            if (result != null && mEkgOriginal != null) {

                AsyncTask<Void, Void, Double> task = new AsyncTask<Void, Void, Double>() {
                    private ProgressDialog dialog = new ProgressDialog(ShowEkgActivity.this);

                    @Override
                    protected void onPreExecute() {
                        dialog.setMessage(getResources().getString(R.string.processing));
                        dialog.show();
                    }

                    @Override
                    protected Double doInBackground(Void... voids) {

                        GavriloGradeFilter gradeFilter = new GavriloGradeFilter(mEkgOriginal.rows(), mEkgOriginal.cols());
                        gradeFilter.apply(mEkgOriginal);
                        double ekgRatio = 0;
                        double resolution = gradeFilter.getResolution();
                        if (resolution != Double.POSITIVE_INFINITY) {
                            mSeriesX = new ArrayList<Number>();
                            mSeriesY = new ArrayList<Number>();
                            ekgRatio = ((GavriloGraphFilter)mFilter).getPoints(resolution, mSeriesX, mSeriesY);
                            Log.d(TAG, String.format("EkgRatio: %.2f", ekgRatio));

                            if(mSelectButton != null) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mSelectButton.setVisible(true);
                                    }
                                });
                            }
                        }

                        return ekgRatio;
                    }

                    @Override
                    protected void onPostExecute(Double result) {

                        if (result < 0.8) {
                            Toast.makeText(ShowEkgActivity.this, R.string.ekg_looks_like, Toast.LENGTH_SHORT).show();
                        }
                        // after completed finished the progressbar
                        dialog.dismiss();
                    }
                };
                task.execute();
                result.copyTo(roi);

            }
            mBitmap = Bitmap.createBitmap(modifiedImageRgba.cols(), modifiedImageRgba.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(modifiedImageRgba, mBitmap);
            mImageContent.setImageBitmap(mBitmap);
        } else {
            if (x1 < 0) x1 = 0;
            if (x2 < 0) x2 = 0;
            if (y1 < 0) y1 = 0;
            if (y2 < 0) y2 = 0;
            if (x1 > w) x1 = w;
            if (x2 > w) x2 = w;
            if (y1 > h) y1 = h;
            if (y2 > h) y2 = h;

            Size size = new Size(w, h);
            Mat thumbnail = new Mat(size, mImageRgba.type());

            Imgproc.resize(mImageRgba, thumbnail, size);

            Rect rect = new Rect(new Point(x1, y1), new Point(x2, y2));
            Mat roi = thumbnail.submat(rect);

            /* From: http://stackoverflow.com/questions/24480751/how-to-create-a-semi-transparent-shape */
            Mat color = new Mat(roi.size(), CvType.CV_8UC4, new Scalar(0xFF, 0xFF, 0xFF, 0x00));
            double alpha = 0.3;
            Core.addWeighted(color, alpha, roi, 1 - alpha, 0, roi);
            Core.rectangle(roi, new Point(0, 0), new Point(roi.cols(), roi.rows()), new Scalar(0xFF, 0xFF, 0x00, 0x00), 5);
            mBitmap = Bitmap.createBitmap(thumbnail.cols(), thumbnail.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(thumbnail, mBitmap);
            mImageContent.setImageBitmap(mBitmap);
        }

    }
}
