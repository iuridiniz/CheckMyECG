package com.iuridiniz.checkmyecg.filter;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by iuri on 26/10/15.
 */
public class GavriloGraphFilter implements Filter {

    private static final String TAG = "GravriloGraphFilter";
    public static final int DEFAULT_SLICES = 18;
    public static final double DEFAULT_AREA_PERCENT = 0.1;
    public static final double DEFAULT_LIGHT_ADJUST = 0.00058;

    protected MatOfInt mSplitFromTo;
    protected Scalar mZeroScalar;
    protected double mAreaPercent;
    protected double mLightAdjust;
    protected int mSlices;
    protected MatOfInt mHistogramSize;
    protected MatOfFloat mHistogramRanges;
    protected Mat mRgb, mHsv, mHist, mKernelErode, mKernelDilate, mRgbaDst;

    protected Mat mValueChannel, mHueSaturationChannels;
    protected Mat[] mValueSlices;
    protected Mat[] mCanvasSlices;
    protected MatOfInt mHistogramChannels;
    protected Mat mHistogramMask;
    protected Mat mCanvas;

    /* countours */
    private final Size mKSize;
    protected Mat mRgbaOrig;
    protected Mat mBlurred;
    protected Mat mEdged;
    protected Mat mHierarchy;
    protected List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();

    public GavriloGraphFilter(int rows, int cols) {
        this(rows, cols, DEFAULT_SLICES, DEFAULT_AREA_PERCENT, DEFAULT_LIGHT_ADJUST);
    }

    public GavriloGraphFilter(int rows, int cols, int slices, double areaPercent, double lightAdjust) {
        mSlices = slices;
        mAreaPercent = areaPercent;
        mLightAdjust = lightAdjust;

        mHsv = new Mat(rows, cols, CvType.CV_8UC3);
        mRgb = new Mat(rows, cols, CvType.CV_8UC3);

        mHist = new Mat(rows, cols, CvType.CV_32F);

        mKernelErode = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        mKernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 3));

        mRgbaDst = new Mat(rows, cols, CvType.CV_8UC4);

        /* slices the array */

        mValueSlices = new Mat[mSlices];
        mCanvasSlices = new Mat[mSlices];

        mHistogramChannels = new MatOfInt(0);

        mHistogramMask = new Mat();
        mHistogramSize = new MatOfInt(256);
        mHistogramRanges = new MatOfFloat(0, 256);
        mSplitFromTo = new MatOfInt(2, 2);

        mHueSaturationChannels = new Mat(rows, cols, CvType.CV_8UC2);
        mValueChannel = new Mat(rows, cols, CvType.CV_8U);
        mCanvas = new Mat(rows, cols, CvType.CV_8U);

        mZeroScalar = Scalar.all(0);


        /* countours */

        mBlurred = new Mat(rows, cols, CvType.CV_8UC1);
        mEdged = new Mat(rows, cols, CvType.CV_8UC1);
        mRgbaOrig = new Mat(rows, cols, CvType.CV_8UC4);
        mKSize = new Size(11, 11);
        mHierarchy = new Mat();
    }

    @Override
    public Mat apply(Mat rgba) {
        /* convert to HSV */
        /* HSV first */
        Imgproc.cvtColor(rgba, mRgb, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(mRgb, mHsv, Imgproc.COLOR_RGB2HSV);
        /* Split channels and get only the value channel */

        Core.mixChannels(Arrays.asList(mHsv), Arrays.asList(mHueSaturationChannels, mValueChannel), mSplitFromTo);

        /* zeroing canvas */
        mCanvas = Mat.zeros(mValueChannel.size(), mValueChannel.type());
        mCanvas.setTo(mZeroScalar);

        int step = mValueChannel.width()/ mValueSlices.length;
        //Log.d(TAG, String.format("valueChannel size: %s", mValueChannel.size()));

        /* Slice the graph in order to handle with contraste differences */
        for (int i=0; i < mValueSlices.length; i++) {
            int hStart, hEnd, wStart, wEnd;

            /* don't slice on y axis */
            hStart = 0;
            hEnd = mValueChannel.height();

            /* slice on x axis */
            wStart = step * i;
            wEnd = (step*(i+1))> mValueChannel.width()?
                        (step * mValueSlices.length) + (mValueChannel.width() - (step*(i+1))):
                        (step*(i+1));

            mValueSlices[i] = mValueChannel.submat(hStart, hEnd, wStart, wEnd);
            mCanvasSlices[i] = mCanvas.submat(hStart, hEnd, wStart, wEnd);

            //Log.d(TAG, String.format("Slice[%s] %s:%s:%s:%s", i, hStart, hEnd, wStart, wEnd));
            //Log.d(TAG, String.format("Slice[%s] size: %s", i, valueSlices[i].size()));
        }
        for (int i=0; i< mValueSlices.length; i++) {
            Mat slice = mValueSlices[i];
            Mat canv = mCanvasSlices[i];

            double area;
            double accu, bound, threshold;
            int upper;

            //List<Mat> images = new ArrayList<Mat>();
            //images.add(slice);
            //Arrays.asList(slice);

            Imgproc.calcHist(Arrays.asList(slice), mHistogramChannels, mHistogramMask, mHist, mHistogramSize, mHistogramRanges);

            //Log.d(TAG, String.format("mHist size: %s", mHist.size()));

            area =  slice.size().area();
            bound = area * mAreaPercent;
            accu = 0;
            for (upper=0; upper<mHist.rows() && accu < bound; upper++) {
                accu += mHist.get(upper, 0)[0];
            }

            threshold = upper * (1 - upper * mLightAdjust);

            Imgproc.threshold(slice, canv, threshold, 255, Imgproc.THRESH_BINARY_INV);
            /* erode */
            Imgproc.erode(canv, canv, mKernelErode);
            /* dilate */
            Imgproc.dilate(canv, canv, mKernelDilate);

        }

        dilateCountours();
        Imgproc.cvtColor(mCanvas, mRgbaDst, Imgproc.COLOR_GRAY2RGBA);
        return mRgbaDst;
    }

    public void dilateCountours() {
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

            Imgproc.drawContours(mCanvas, mContours, i, color, thinkness);
        }
        /* erode and dilate */
        Mat kernelErode = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(max, max));
        Mat kernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, max));
        Imgproc.erode(mCanvas, mCanvas, kernelErode);
        Imgproc.dilate(mCanvas, mCanvas, kernelDilate);

    }

    public void getPoints(double resolution, List<Number> outSeriesX, List<Number> outSeriesY) {
        int cols = mCanvas.cols();
        int rows = mCanvas.rows();

        //List<Number> seriesX = new ArrayList<Number>();
        //List<Number> seriesY = new ArrayList<Number>();
        /* find points analyzing each column */
        double time = 0.04/resolution;
        double voltage = 0.1/resolution;
        for (int col = 0; col < cols; col++) {

            /* find white points in each row of a column */
            boolean is_connected = false;
            List<Integer> whitePointsConnected = null;
            List<List> whitePointsCandidates = new ArrayList<List>();
            int max_pos = -1; /* pos of white set with more white points */
            int max_qtd = 0; /* qtd of set with more white points */
            for (int row=0; row < rows; row++) {
                int value = (int)(mCanvas.get(row, col)[0]);

                boolean save_set = false;
                if (value == 255) {
                    /* new white point */
                    if (! is_connected) {
                        assert(whitePointsConnected == null);
                        /* new set of points (not connected to previous) */
                        whitePointsConnected = new ArrayList<Integer>();
                    }
                    whitePointsConnected.add(row);
                    is_connected = true;
                    if (row + 1 == rows) {
                        /* last row */
                        save_set = true;
                    }
                } else {
                    if (is_connected) {
                        /* end of connected points, save it */
                        save_set = true;
                        is_connected = false;
                    }

                }
                if (save_set) {
                    int qtd = whitePointsConnected.size();
                    if (qtd > max_qtd) {
                        /* a new max */
                        max_qtd = qtd;
                        max_pos = whitePointsCandidates.size();
                    }
                    //assert(whitePointsConnected != null);
                    whitePointsCandidates.add(whitePointsConnected);
                    whitePointsConnected = null;
                }

            }
            if (max_pos >= 0) {
                whitePointsConnected = whitePointsCandidates.get(max_pos);
                int sum = 0;
                for (int v: whitePointsConnected) {
                    sum += v;
                }
                /* mean point */
                int value = Math.round(sum/(float)max_qtd);
                double x, y;
                x = time * col;
                y = (rows - value) * voltage;
                outSeriesX.add(x);
                outSeriesY.add(y);
            }
        }
    }

    @Override
    public Mat getResult() {
        return mRgbaDst;
    }
}
