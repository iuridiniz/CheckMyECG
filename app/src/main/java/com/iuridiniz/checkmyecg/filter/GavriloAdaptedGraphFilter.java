package com.iuridiniz.checkmyecg.filter;

import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by iuri on 26/10/15.
 */
public class GavriloAdaptedGraphFilter extends GavriloGraphFilter {

    private static final String TAG = "GavriloGraphFilter[2]";


    /* countours */
    private final Size mKSize;
    protected Mat mRgbaOrig;
    protected Mat mBlurred;
    protected Mat mEdged;
    protected Mat mHierarchy;
    protected List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();


    public GavriloAdaptedGraphFilter(int rows, int cols, int slices, double areaPercent, double lightAdjust) {
        super(rows, cols, slices, areaPercent, lightAdjust);
        /* countours */
        mBlurred = new Mat(rows, cols, CvType.CV_8UC1);
        mEdged = new Mat(rows, cols, CvType.CV_8UC1);
        mRgbaOrig = new Mat(rows, cols, CvType.CV_8UC4);
        mKSize = new Size(11, 11);
        mHierarchy = new Mat();
    }

    public GavriloAdaptedGraphFilter(int rows, int cols) {
        this(rows, cols, DEFAULT_SLICES, DEFAULT_AREA_PERCENT, DEFAULT_LIGHT_ADJUST);
    }


    @Override
    public Mat apply(Mat rgba) {
        Mat ret = super.apply(rgba);
        if (ret == null) {
            return ret;
        }
        dilateContours();
        Imgproc.cvtColor(mCanvas, mRgbaDst, Imgproc.COLOR_GRAY2RGBA);
        return mRgbaDst;
    }

    public void dilateContours() {
        Imgproc.GaussianBlur(mCanvas, mBlurred, mKSize, 0);
        Imgproc.Canny(mBlurred, mEdged, 30, 150);

        mContours.clear();
        /* FIXME: there's a kind of memory leak here (findContours), We need to call gc.collect in order to free resources */

        Imgproc.findContours(mEdged, mContours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        if (mContours.size() == 0) {
            return;
        }
        /* sort the countours */
        Comparator<MatOfPoint> cmp = new Comparator<MatOfPoint>() {
            @Override
            public int compare(MatOfPoint lhs, MatOfPoint rhs) {
                return rhs.rows() - lhs.rows();
            }
        };
        Collections.sort(mContours, cmp);
        /* draw countours with more points */
        int max = 4;
        int thinkness = 2*max;
        for (int i = 0; i < max && i < mContours.size(); i++ ) {
            Scalar color = new Scalar(255);
            thinkness -= i*2;
            thinkness = thinkness < 1?1:thinkness;
            Log.d(TAG, String.format("Contour %d: size: %d", i, mContours.get(i).rows()));
            Imgproc.drawContours(mCanvas, mContours, i, color, thinkness);
        }
        /* erode and dilate */
        Mat kernelErode = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(max, max));
        Mat kernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, max));
        Imgproc.erode(mCanvas, mCanvas, kernelErode);
        Imgproc.dilate(mCanvas, mCanvas, kernelDilate);

        Log.d(TAG, String.format("Contours Total size: %d", mContours.size()));
    }


    @Override
    public Mat getResult() {
        return mRgbaDst;
    }

    public static int calcMeanPoint(List<Integer> points) {
        int sum = 0;
        for (int v: points) {
            sum += v;
        }
        /* mean point */
        return Math.round(sum/(float)points.size());
    }
}
