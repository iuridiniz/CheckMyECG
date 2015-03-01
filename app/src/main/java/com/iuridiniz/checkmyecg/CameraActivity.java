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
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

interface Filter {
    public Mat apply(Mat src);
};

class GradeFilter implements Filter {

    private Mat mGray, mMaskRed1, mMaskRed2; /* CV_8UC1 */
    private Mat mRgb, mHsv; /* CV_8UC3 */
    private Mat mRgba; /* CV_8UC4 */

    private Scalar mLowerRed1, mUpperRed1;
    private Scalar mLowerRed2, mUpperRed2;

    public GradeFilter(int rows, int cols) {
        mGray = new Mat(rows, cols, CvType.CV_8UC1);
        mMaskRed1 = new Mat(rows, cols, CvType.CV_8UC1);
        mMaskRed2 = new Mat(rows, cols, CvType.CV_8UC1);

        mHsv = new Mat(rows, cols, CvType.CV_8UC3);
        mRgb = new Mat(rows, cols, CvType.CV_8UC3);

        mRgba = new Mat(rows, cols, CvType.CV_8UC4);

        mLowerRed1 = new Scalar(0, 100, 100);
        mUpperRed1 = new Scalar(15, 255, 255);

        mLowerRed2 = new Scalar(230, 100, 100);
        mUpperRed2 = new Scalar(255, 255, 255);

    }

    @Override
    public Mat apply(Mat rgba) {

        /* HSV first */
        Imgproc.cvtColor(rgba, mRgb, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(mRgb, mHsv, Imgproc.COLOR_RGB2HSV);

        /* get first red range */
        Core.inRange(mHsv, mLowerRed1, mUpperRed1, mMaskRed1);

        /* get second red range */
        Core.inRange(mHsv, mLowerRed1, mUpperRed1, mMaskRed1);
        Core.inRange(mHsv, mLowerRed2, mUpperRed2, mMaskRed2);

        /* merge it */
        Core.bitwise_or(mMaskRed1, mMaskRed2, mMaskRed1);

        /* invert it */
        Core.bitwise_not(mMaskRed1, mMaskRed1);

        /* convert back to colorspace expected */
        Imgproc.cvtColor(mMaskRed1, mRgba, Imgproc.COLOR_GRAY2RGBA);

        return mRgba;
    }
}

class GraphFilter implements Filter {

    private Mat mGray, mMatMaskBlack; /* CV_8UC1 */
    private Mat mRgb, mHsv; /* CV_8UC3 */
    private Mat mRgbaDst; /* CV_8UC4 */

    private Scalar mLowerBlack, mUpperBlack;

    public GraphFilter(int rows, int cols) {
        mGray = new Mat(rows, cols, CvType.CV_8UC1);
        mMatMaskBlack = new Mat(rows, cols, CvType.CV_8UC1);

        mHsv = new Mat(rows, cols, CvType.CV_8UC3);
        mRgb = new Mat(rows, cols, CvType.CV_8UC3);

        //mRgbaDst = new Mat(rows, cols, CvType.CV_8UC4, new Scalar(255));
        mRgbaDst = new Mat(rows, cols, CvType.CV_8UC4);

        mLowerBlack = new Scalar(0, 0, 0);
        mUpperBlack = new Scalar(255, 255, 100);

    }

    @Override
    public Mat apply(Mat rgba) {

        /* HSV first */
        Imgproc.cvtColor(rgba, mRgb, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(mRgb, mHsv, Imgproc.COLOR_RGB2HSV);

        /* get only black */
        Core.inRange(mHsv, mLowerBlack, mUpperBlack, mMatMaskBlack);

        /* invert it */
        Core.bitwise_not(mMatMaskBlack, mMatMaskBlack);

        /* convert back to colorspace expected */
        Imgproc.cvtColor(mMatMaskBlack, mRgbaDst, Imgproc.COLOR_GRAY2RGBA);

        return mRgbaDst;
    }
}

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
        UnivariateFunction f = si.interpolate(new double[]{0, mMin, mMax, 255},
                new double[]{0, 0, 255, 255});
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

    private Filter mGradeFilter;
    private Filter mGraphFilter;
    private Filter mNormalizeFilter;
    private Filter mContrastFilter;

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
        mGradeFilter = new GradeFilter(height, width);
        mGraphFilter = new GraphFilter(height, width);
        mNormalizeFilter = new NormalizerFilter(height, width, 100, 200);
        mContrastFilter = new ContrastFilter(height, width, 100, 140);
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        /* show previews */
        Mat src = null, bottomLeft = null, topLeft = null, topRight = null, bottomRight = null;

        src = inputFrame.rgba();

        Mat grade = mGradeFilter.apply(src);
        Mat graph = mGraphFilter.apply(src);

        Mat srcNormalized = mContrastFilter.apply(src);
        Mat gradeNormalized = mGradeFilter.apply(srcNormalized);
        Mat graphNormalized = mGraphFilter.apply(srcNormalized);

        topLeft = grade;
        bottomLeft = gradeNormalized;
        topRight = graph;
        bottomRight = graphNormalized;

        drawMini(src, topLeft, bottomLeft, topRight, bottomRight);

        return src;
    }

    private void drawMini(Mat dst, Mat topLeft, Mat bottomLeft, Mat topRight, Mat bottomRight) {
        int dstHeight = dst.rows();
        int dstWidth = dst.cols();

        int dstRoiHeight = dstHeight / 3;
        int dstRoiWidth = dstWidth / 3;

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