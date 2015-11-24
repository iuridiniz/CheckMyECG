package com.iuridiniz.checkmyecg.filter;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Created by iuri on 27/10/15.
 */
public class NullFilter implements Filter{
    private Mat mRgbaDst;

    public NullFilter(int rows, int cols) {
        mRgbaDst = new Mat(rows, cols, CvType.CV_8UC4);
    }
    @Override
    public Mat apply(Mat src) {
        src.copyTo(mRgbaDst);
        return mRgbaDst;
    }

    @Override
    public Mat getResult() {
        return mRgbaDst;
    }
}
