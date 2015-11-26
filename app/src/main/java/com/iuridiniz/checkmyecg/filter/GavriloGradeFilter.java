package com.iuridiniz.checkmyecg.filter;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by iuri on 26/11/15.
 */
public class GavriloGradeFilter implements Filter{
    private static final String TAG = "GavriloGradeFilter";
    private Mat mBinary;
    private Mat mDst;
    private Mat mGray;
    private Mat mHist;
    private Mat mFilled;
    private Mat mLines = new Mat();

    public GavriloGradeFilter(int rows, int cols) {
        mGray = new Mat(rows, cols, CvType.CV_8UC1);
        mHist = new Mat(rows, cols, CvType.CV_8UC1);
        mDst = new Mat(rows, cols, CvType.CV_8UC4);
        mBinary = new Mat(rows, cols, CvType.CV_8UC1);
        mFilled = new Mat(rows, cols, CvType.CV_8UC1);
        mFilled.setTo(new Scalar(255));
    }
    @Override
    public Mat apply(Mat rgba) {
        rgba.copyTo(mDst);
        Imgproc.cvtColor(rgba, mGray, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.equalizeHist(mGray, mHist);
        Imgproc.adaptiveThreshold(mHist, mBinary, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 13, 13);
        Core.subtract(mFilled, mBinary, mBinary);
        Imgproc.HoughLines(mBinary, mLines, 5, Math.PI, 60);

        drawLines(mDst);
        return mDst;
    }

    public double getResolution() {
        int qtd = mLines.cols();
        if (qtd == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double[] distances = new double[qtd];
        for (int i = 0; i < qtd; i++) {
            distances[i] = mLines.get(0, i)[0];
        }
        Arrays.sort(distances);
        double acc = 0.0;
        double prev = 0.0;

        for (double d:distances) {
            double dif = d - prev;
            acc += dif;
            prev = d;
        }

        return acc/qtd;
    }

    public void drawLines(Mat dst) {
        /* draw lines */
        for (int i = 0; i < mLines.cols(); i++) {
            double rho = mLines.get(0, i)[0];
            double theta = mLines.get(0, i)[1];

            double a = Math.cos(theta), b = Math.sin(theta);
            double x0 = a * rho, y0 = b*rho;
            Point pt1 = new Point(Math.round(x0 + 1000*(-b)), Math.round(y0 + 1000*(a)));
            Point pt2 = new Point(Math.round(x0 - 1000*(-b)), Math.round(y0 - 1000*(a)));
            Core.line(dst, pt1, pt2, new Scalar(0, 0, 0xFF, 0xFF), 3, Core.LINE_8, 0);
        }
    }
    @Override
    public Mat getResult() {
        return null;
    }
}
