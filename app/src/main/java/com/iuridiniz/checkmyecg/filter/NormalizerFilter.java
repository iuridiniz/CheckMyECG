package com.iuridiniz.checkmyecg.filter;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Created by iuri on 01/03/15.
 */
public class NormalizerFilter implements Filter {

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
