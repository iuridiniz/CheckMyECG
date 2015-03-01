package com.iuridiniz.checkmyecg.filter;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;

/**
 * Created by iuri on 01/03/15.
 */
public class ContrastFilter implements Filter {
    private final Mat mRgbaDst;
    private final Mat mLut;
    private boolean mHasResult;

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
        mHasResult = true;
        return mRgbaDst;
    }

    public Mat getResult() {
        if (mHasResult)
            return mRgbaDst.clone();
        return null;
    }
}
