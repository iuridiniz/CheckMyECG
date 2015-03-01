package com.iuridiniz.checkmyecg.filter;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Created by iuri on 01/03/15.
 */
public class GradeFilter implements Filter {

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
