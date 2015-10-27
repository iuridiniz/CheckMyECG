package com.iuridiniz.checkmyecg.filter;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;

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

    //protected int threshold = 150;
    //protected int slices = 18;
    //protected int

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

        //mHue = new Mat(rows, cols, CvType.CV_8U);
        //mSaturation = new Mat(rows, cols, CvType.CV_8U);
        //mValue = new Mat(rows, cols, CvType.CV_8U);


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
        Log.d(TAG, String.format("valueChannel size: %s", mValueChannel.size()));

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
        Imgproc.cvtColor(mCanvas, mRgbaDst, Imgproc.COLOR_GRAY2RGBA);
        return mRgbaDst;
    }
}
