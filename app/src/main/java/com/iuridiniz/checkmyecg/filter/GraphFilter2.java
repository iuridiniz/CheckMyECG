package com.iuridiniz.checkmyecg.filter;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by iuri on 01/03/15.
 */
public class GraphFilter2 extends GraphFilter {
    private final Size mKSize;
    protected Mat mBlurred;
    protected Mat mEdged;
    protected Mat mHierarchy;
    protected List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();

    private boolean mNeedSort =true;

    public GraphFilter2(int rows, int cols) {
        super(rows, cols);
        mBlurred = new Mat(rows, cols, CvType.CV_8UC1);
        mEdged = new Mat(rows, cols, CvType.CV_8UC1);
        mKSize = new Size(11, 11);
        mHierarchy = new Mat();
    }

    @Override
    public Mat apply(Mat rgba) {
        super.apply(rgba);
        mRgbaDst = rgba.clone();
        /* get mask black and find contours */
        Imgproc.GaussianBlur(mMatMaskBlackInv, mBlurred, mKSize, 0);
        Imgproc.Canny(mBlurred, mEdged, 30, 150);

        mContours.clear();
        /* FIXME: there's a kind of memory leak here (findContours), We need to call gc.collect in order to free resources */
        Imgproc.findContours(mEdged, mContours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        Imgproc.drawContours(mRgbaDst, mContours, -1, new Scalar(0, 255, 0), 2);

        mNeedSort = true;
        return mRgbaDst;
    }

    public List<MatOfPoint> getContours(int max, boolean sortIt) {
        if (max < 0) {
            max = mContours.size();
        }
        ArrayList<MatOfPoint> ret = new ArrayList<MatOfPoint>(max);

        if (sortIt && mNeedSort) {
            Comparator<MatOfPoint> cmp = new Comparator<MatOfPoint>() {
                @Override
                public int compare(MatOfPoint lhs, MatOfPoint rhs) {
                    return rhs.rows() - lhs.rows();
                }
            };
            Collections.sort(mContours, cmp);
            mNeedSort = false;
        }

        for (int i = 0; i < max; i++)  {
            MatOfPoint mp = mContours.get(i);
            //ret.set(max-i-1, mp);
            ret.add(mp);
        }
        return ret;
    }

    public List<MatOfPoint> getContours(int max) {
        return getContours(max, true);
    }

    public List<MatOfPoint> getContours() {
        return getContours(-1);
    }
}
