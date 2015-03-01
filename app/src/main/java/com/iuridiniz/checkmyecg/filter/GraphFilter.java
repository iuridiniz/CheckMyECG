package com.iuridiniz.checkmyecg.filter;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Created by iuri on 01/03/15.
 */
public class GraphFilter implements Filter {

    protected Mat mSrc;
    protected Mat mGray;
    protected Mat mMatMaskBlack;
    protected Mat mMatMaskBlackInv; /* CV_8UC1 */
    protected Mat mRgb, mHsv; /* CV_8UC3 */
    protected Mat mRgbaDst; /* CV_8UC4 */

    protected Scalar mLowerBlack, mUpperBlack;

    public GraphFilter(int rows, int cols) {
        mGray = new Mat(rows, cols, CvType.CV_8UC1);
        mMatMaskBlack = new Mat(rows, cols, CvType.CV_8UC1);
        mMatMaskBlackInv = new Mat(rows, cols, CvType.CV_8UC1);

        mHsv = new Mat(rows, cols, CvType.CV_8UC3);
        mRgb = new Mat(rows, cols, CvType.CV_8UC3);

        //mRgbaDst = new Mat(rows, cols, CvType.CV_8UC4, new Scalar(255));
        mRgbaDst = new Mat(rows, cols, CvType.CV_8UC4);
        mSrc = new Mat(rows, cols, CvType.CV_8UC4);

        mLowerBlack = new Scalar(0, 0, 0);
        mUpperBlack = new Scalar(255, 255, 50);

    }

    @Override
    public Mat apply(Mat rgba) {
        /* save a copy */
        rgba.copyTo(mSrc);

        /* HSV first */
        Imgproc.cvtColor(rgba, mRgb, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(mRgb, mHsv, Imgproc.COLOR_RGB2HSV);

        /* get only black */
        Core.inRange(mHsv, mLowerBlack, mUpperBlack, mMatMaskBlack);

        /* invert it */
        Core.bitwise_not(mMatMaskBlack, mMatMaskBlackInv);

        /* convert back to colorspace expected */
        Imgproc.cvtColor(mMatMaskBlackInv, mRgbaDst, Imgproc.COLOR_GRAY2RGBA);

        return mRgbaDst;
    }
}
