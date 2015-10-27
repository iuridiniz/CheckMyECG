package com.iuridiniz.checkmyecg.filter;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by iuri on 26/10/15.
 */
public class GavriloGraphFilter implements Filter {

    protected Mat mRgb, mHsv, mHist, mCanvas, mHue, mSaturation, mValue,
            mKernelErode, mKernelDilate, mRgbaDst;;

    private static final String TAG = "GravriloGraphFilter";

    //protected int threshold = 150;
    //protected int slices = 18;
    //protected int

    public GavriloGraphFilter(int rows, int cols) {
        mHsv = new Mat(rows, cols, CvType.CV_8UC3);
        mRgb = new Mat(rows, cols, CvType.CV_8UC3);
        mHist = new Mat(rows, cols, CvType.CV_32F);

        //mHue = new Mat(rows, cols, CvType.CV_8U);
        //mSaturation = new Mat(rows, cols, CvType.CV_8U);
        //mValue = new Mat(rows, cols, CvType.CV_8U);

        //mCanvas = new Mat(rows, cols, CvType.CV_8U);

        mKernelErode = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        mKernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 3));

        mRgbaDst = new Mat(rows, cols, CvType.CV_8UC4);

    }


    @Override
    public Mat apply(Mat rgba) {
        /* convert to HSV */
        /* HSV first */
        Imgproc.cvtColor(rgba, mRgb, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(mRgb, mHsv, Imgproc.COLOR_RGB2HSV);
        /* Split channels and get only the value channel */
        List<Mat> mv = new ArrayList<Mat>();
        //Arrays.asList(mHue, mSaturation, mValue);
        Core.split(mHsv, mv);

        /* slices the array */
        Mat[] valueSlices = new Mat[18];
        Mat[] canvasSlices = new Mat[18];

        Mat valueChannel = mv.get(2);
        Mat canvas = Mat.zeros(valueChannel.size(), valueChannel.type());

        int step = valueChannel.width()/valueSlices.length;
        Log.d(TAG, String.format("valueChannel size: %s", valueChannel.size()));

        for (int i=0; i < valueSlices.length; i++) {
            int hStart, hEnd, wStart, wEnd;

            /* don't slice on y axis */
            hStart = 0;
            hEnd = valueChannel.height();

            /* slice on x axis */
            wStart = step * i;
            wEnd = (step*(i+1))>valueChannel.width()?
                        (step * valueSlices.length) + (valueChannel.width() - (step*(i+1))):
                        (step*(i+1));

            valueSlices[i] = valueChannel.submat(hStart, hEnd, wStart, wEnd);
            canvasSlices[i] = canvas.submat(hStart, hEnd, wStart, wEnd);

            //Log.d(TAG, String.format("Slice[%s] %s:%s:%s:%s", i, hStart, hEnd, wStart, wEnd));
            //Log.d(TAG, String.format("Slice[%s] size: %s", i, valueSlices[i].size()));
        }
        for (int i=0; i<valueSlices.length; i++) {
            Mat slice = valueSlices[i];
            Mat canv = canvasSlices[i];

            double area;
            double accu, bound, threshold;
            int upper;

            //List<Mat> images = new ArrayList<Mat>();
            //images.add(slice);
            //Arrays.asList(slice);
            Imgproc.calcHist(Arrays.asList(slice), new MatOfInt(0), new Mat(), mHist, new MatOfInt(256), new MatOfFloat(0, 256));

            //Log.d(TAG, String.format("mHist size: %s", mHist.size()));

            area =  slice.size().area();
            bound = area * 0.1;
            accu = 0;
            for (upper=0; upper<mHist.rows() && accu < bound; upper++) {
                accu += mHist.get(upper, 0)[0];
            }

            threshold = upper * (1 - upper * 0.00058);

            Imgproc.threshold(slice, canv, threshold, 255, Imgproc.THRESH_BINARY_INV);
            /* erode */
            Imgproc.erode(canv, canv, mKernelErode);
            /* dilate */
            Imgproc.dilate(canv, canv, mKernelDilate);

        }
        Imgproc.cvtColor(canvas, mRgbaDst, Imgproc.COLOR_GRAY2RGBA);
        return mRgbaDst;
    }
}
