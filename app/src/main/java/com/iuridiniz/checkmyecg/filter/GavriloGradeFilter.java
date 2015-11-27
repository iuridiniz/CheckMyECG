package com.iuridiniz.checkmyecg.filter;

import android.util.Log;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by iuri on 26/11/15.
 */
public class GavriloGradeFilter implements Filter{

    public enum Stats {
        MEAN, VARIANCE, STD_DEVIATION, MODE, MIN, MAX
    };

    private static final String TAG = "GavriloGradeFilter";
    public static final int THICKNESS = 2;
    public static final Scalar COLOR = new Scalar(0x00, 0xFF, 0xFF, 0xFF); /* RGBA */
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
        if (rgba.rows() < GavriloGraphFilter.DEFAULT_SLICES * 2 || rgba.cols() < GavriloGraphFilter.DEFAULT_SLICES * 2) {
            /* insufficient data */
            return null;
        }
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

    public double getStats(List<Number> outStats) {
        int qtd = mLines.cols();
        if (qtd == 0) {
            return 0;
        }
        Number[] stats = new Number[Stats.values().length];
        double[] distance_to_origin = new double[qtd];
        double[] distances = new double[qtd-1];
        for (int i = 0; i < qtd; i++) {
            distance_to_origin[i] = mLines.get(0, i)[0];
        }
        Arrays.sort(distance_to_origin);
        for (int i=1; i < qtd; i++) {
            double cur = distance_to_origin[i];
            double prev = distance_to_origin[i-1];
            distances[i-1] = cur - prev;
            Log.d(TAG, String.format("[distances] [%d]: %.2f", i-1, distances[i-1]));
        }

        double[] mode = StatUtils.mode(distances);

        double mean = StatUtils.mean(distances);
        double most_frequency = 0;
        double max = StatUtils.max(distances);
        double min = StatUtils.min(distances);
        double variance = StatUtils.variance(distances, mean);

        if (mode.length > 0) {
            int i = 0;
            /* use the bigger */
            for (i = 0; i < mode.length; i++) {
                Log.d(TAG, String.format("[distances] %dº most frequent number: %.2f", i+1, mode[i]));
                most_frequency = mode[i];
            }


        }
        Log.d(TAG, String.format("[distances] total: %d, mean %.2f, mode: %.2f, vari: %.2f, std dev: %.2f,  min: %.2f, max: %.2f",
                                    qtd-1, mean, most_frequency, variance, Math.sqrt(variance),  min, max)
        );

        if (outStats != null) {
            stats[Stats.MEAN.ordinal()] = mean;
            stats[Stats.VARIANCE.ordinal()] = variance;
            stats[Stats.STD_DEVIATION.ordinal()] = variance;
            stats[Stats.MODE.ordinal()] = most_frequency;
            stats[Stats.MIN.ordinal()] = min;
            stats[Stats.MAX.ordinal()] = max;
            outStats.addAll(Arrays.asList(stats));
        }
        double ratio = ((most_frequency/mean)*2 + (min/mean) * 0.5 + (max/mean) * 0.5) / 3.0;
        Log.d(TAG, String.format("[distances] ratio: %.4f", ratio));
        return ratio;
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
            Core.line(dst, pt1, pt2, COLOR, THICKNESS, Core.LINE_8, 0);
        }
    }
    @Override
    public Mat getResult() {
        return null;
    }
}
