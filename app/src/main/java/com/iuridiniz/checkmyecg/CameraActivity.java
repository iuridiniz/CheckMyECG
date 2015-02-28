package com.iuridiniz.checkmyecg;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;

interface Filter {
    public Mat apply(Mat src);
};


class NormalizerFilter implements Filter {

    private Mat mRgbaDst; /* CV_8UC4 */
    private double mAlpha, mBeta;
    private int mNormType;

    NormalizerFilter(int cols, int rows, double mAlpha, double mBeta, int mNormType) {
        mRgbaDst = new Mat(rows, cols, CvType.CV_8UC4);
        setAlpha(mAlpha);
        setBeta(mBeta);
        setNormType(mNormType);
    }

    NormalizerFilter(int cols, int rows, double mAlpha, double mBeta) {
        this(cols, rows, mAlpha, mBeta, Core.NORM_MINMAX);
    }

    NormalizerFilter(int cols, int rows) {
        this(cols, rows, 0.0, 255.0);
    }

    @Override
    public Mat apply(Mat src) {
        Core.normalize(src, mRgbaDst, mAlpha, mBeta, mNormType);
        return mRgbaDst;
    }

    public double getAlpha() {
        return mAlpha;
    }

    public void setAlpha(double mAlpha) {
        this.mAlpha = mAlpha;
    }

    public double getBeta() {
        return mBeta;
    }

    public void setBeta(double mBeta) {
        this.mBeta = mBeta;
    }

    public int getNormType() {
        return mNormType;
    }

    public void setNormType(int mNormType) {
        this.mNormType = mNormType;
    }
}

class ContrastFilter implements Filter {
    private final Mat mRgbaDst;
    private final Mat mLut;

    public ContrastFilter(int rows, int cols, int mMin, int mMax) {
        mRgbaDst = new Mat(rows, cols, CvType.CV_8UC4);
        mLut = new MatOfInt();

        SplineInterpolator si = new SplineInterpolator();
        UnivariateFunction f = si.interpolate(new double[] {0, mMin, mMax, 255},
                                              new double[] {0, 0, 255, 255});
        /* create my mLut */
        mLut.create(256, 1, CvType.CV_8UC4);

        for (int i = 0; i < 256; i++) {
            final double v = f.value(i); /* r, g, b */
            mLut.put(i, 0, v, v, v, i); /* alpha not touched */
        }
    }

    @Override
    public Mat apply(Mat src) {
        Core.LUT(src, mLut, mRgbaDst);
        return mRgbaDst;
    }
}

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "ActivityCamera";
    private boolean mOpenCvLoaded = false;
    private CameraBridgeViewBase mPreview;

    private Filter mNormalizeFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        /* Keep the screen on */
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        /* start openCV */
        /* try static initialization */
        if (OpenCVLoader.initDebug()) {

            Log.i(TAG, "OpenCV loaded successfully (static initialization)");
            mOpenCvLoaded = true;
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
                                createCameraPreview();
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

    private void createCameraPreview() {
        /* Create our Preview */
        if (mPreview == null) {
            mPreview = (CameraBridgeViewBase) findViewById(R.id.camera_view);
            mPreview.setVisibility(SurfaceView.VISIBLE);
            mPreview.setCvCameraViewListener(this);
        }
        if (mOpenCvLoaded) {
            mPreview.enableView();
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        createCameraPreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.disableView();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mPreview != null) {
            mPreview.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

        mNormalizeFilter = new NormalizerFilter(height, width, 100, 200);
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        /* show previews */
        Mat src=null, bottomLeft=null, topLeft=null, topRight=null, bottomRight=null;

        src = inputFrame.rgba();

        topRight = mNormalizeFilter.apply(src);

        drawMini(src, bottomLeft, topLeft, topRight, bottomRight);

        return src;
    }

    private void drawMini(Mat dst, Mat bottomLeft, Mat topLeft, Mat topRight, Mat bottomRight) {
        int dstHeight = dst.rows();
        int dstWidth = dst.cols();

        int dstRoiHeight = dstHeight/3;
        int dstRoiWidth  = dstWidth/3;

        Mat dstRoi;

        if (topLeft != null) {
            /* draw topLeft into top left corner */
            dstRoi = dst.submat(0, dstRoiHeight, 0, dstRoiWidth);
            Imgproc.resize(topLeft, dstRoi, dstRoi.size());
        }
        if (bottomLeft != null) {
            /* draw bottomLeft into bottom left corner */
            dstRoi = dst.submat(dstHeight - dstRoiHeight, dstHeight, 0, dstRoiWidth);
            Imgproc.resize(bottomLeft, dstRoi, dstRoi.size());
        }
        if (topRight != null) {
            /* draw topRight into top right corner */
            dstRoi = dst.submat(0, dstRoiHeight, dstWidth - dstRoiWidth, dstWidth);
            Imgproc.resize(topRight, dstRoi, dstRoi.size());
        }
        if (bottomRight != null) {
            /* draw bottomRight into bottom right corner */
            dstRoi = dst.submat(dstHeight - dstRoiHeight, dstHeight, dstWidth - dstRoiWidth, dstWidth);
            Imgproc.resize(bottomRight, dstRoi, dstRoi.size());
        }
    }
}