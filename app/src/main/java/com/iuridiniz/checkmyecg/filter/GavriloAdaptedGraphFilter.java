package com.iuridiniz.checkmyecg.filter;

import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by iuri on 26/10/15.
 */
public class GavriloAdaptedGraphFilter extends GavriloGraphFilter {

    private static final String TAG = "GavriloGraphFilter[2]";


    /* countours */
    private final Size mKSize;
    protected Mat mRgbaOrig;
    protected Mat mBlurred;
    protected Mat mEdged;
    protected Mat mHierarchy;
    protected List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();


    public GavriloAdaptedGraphFilter(int rows, int cols, int slices, double areaPercent, double lightAdjust) {
        super(rows, cols, slices, areaPercent, lightAdjust);
        /* countours */
        mBlurred = new Mat(rows, cols, CvType.CV_8UC1);
        mEdged = new Mat(rows, cols, CvType.CV_8UC1);
        mRgbaOrig = new Mat(rows, cols, CvType.CV_8UC4);
        mKSize = new Size(11, 11);
        mHierarchy = new Mat();
    }

    public GavriloAdaptedGraphFilter(int rows, int cols) {
        this(rows, cols, DEFAULT_SLICES, DEFAULT_AREA_PERCENT, DEFAULT_LIGHT_ADJUST);
    }


    @Override
    public Mat apply(Mat rgba) {
        Mat ret = super.apply(rgba);
        if (ret == null) {
            return ret;
        }
        dilateContours();
        Imgproc.cvtColor(mCanvas, mRgbaDst, Imgproc.COLOR_GRAY2RGBA);
        return mRgbaDst;
    }

    public void dilateContours() {
        Imgproc.GaussianBlur(mCanvas, mBlurred, mKSize, 0);
        Imgproc.Canny(mBlurred, mEdged, 30, 150);

        mContours.clear();
        /* FIXME: there's a kind of memory leak here (findContours), We need to call gc.collect in order to free resources */

        Imgproc.findContours(mEdged, mContours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        if (mContours.size() == 0) {
            return;
        }
        /* sort the countours */
        Comparator<MatOfPoint> cmp = new Comparator<MatOfPoint>() {
            @Override
            public int compare(MatOfPoint lhs, MatOfPoint rhs) {
                return rhs.rows() - lhs.rows();
            }
        };
        Collections.sort(mContours, cmp);
        /* draw countours with more points */
        int max = 4;
        int thinkness = 2*max;
        for (int i = 0; i < max && i < mContours.size(); i++ ) {
            Scalar color = new Scalar(255);
            thinkness -= i*2;
            thinkness = thinkness < 1?1:thinkness;
            Log.d(TAG, String.format("Contour %d: size: %d", i, mContours.get(i).rows()));
            Imgproc.drawContours(mCanvas, mContours, i, color, thinkness);
        }
        /* erode and dilate */
        Mat kernelErode = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(max, max));
        Mat kernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, max));
        Imgproc.erode(mCanvas, mCanvas, kernelErode);
        Imgproc.dilate(mCanvas, mCanvas, kernelDilate);

        Log.d(TAG, String.format("Contours Total size: %d", mContours.size()));
    }

    @Override
    public double getPoints(double resolution, List<Number> outSeriesX, List<Number> outSeriesY) {
        int cols = mCanvas.cols();
        int rows = mCanvas.rows();

        boolean flag = false;

        int wellConnectedPoints = 0;

        List<Number> seriesX = new ArrayList<Number>();
        List<Number> seriesY = new ArrayList<Number>();

        /* find points analyzing each column */
        double time = 0.04/resolution;
        double voltage = 0.1/resolution;
        int sufficientPoints = 10;
        int strictClosePointsCount = sufficientPoints/5;
        //int limitDistance = Integer.MAX_VALUE; /* dinamic */
        final int limitDistance = 80;

        LinkedList<Integer> lastPoints = new LinkedList<Integer>();

        for (int col = 0; col < cols; col++) {

            /* find white points in each row of a column */
            boolean is_connected = false;
            List<Integer> whitePointsConnected = null;
            List<List> whitePointsCandidates = new ArrayList<List>();
            for (int row=0; row < rows; row++) {
                int value = (int)(mCanvas.get(row, col)[0]);

                boolean save_set = false;
                if (value == 255) {
                    /* new white point */
                    if (! is_connected) {
                        assert(whitePointsConnected == null);
                        /* new set of points (not connected to previous) */
                        whitePointsConnected = new ArrayList<Integer>();
                    }
                    whitePointsConnected.add(row);
                    is_connected = true;
                    if (row + 1 == rows) {
                        /* last row */
                        save_set = true;
                    }
                } else {
                    if (is_connected) {
                        /* end of connected points, save it */
                        save_set = true;
                        is_connected = false;
                    }

                }
                if (save_set) {
                    //assert(whitePointsConnected != null);
                    whitePointsCandidates.add(whitePointsConnected);
                    whitePointsConnected = null;
                }

            }

            int meanLastPoints = -1;

            if (whitePointsCandidates.size() > 0) {
                if (lastPoints.size() > 0) {
                    meanLastPoints = calcMeanPoint(lastPoints);
                }
                /* if we have sufficient points and many candidates, sort by the most close to previous points */


                if (lastPoints.size() >= sufficientPoints && whitePointsCandidates.size() > 1) {
                    assert(meanLastPoints >= 0);
                    final int mean = meanLastPoints;
                    final int limit = limitDistance;
                    Comparator<List> cmpMoreClosed = new Comparator<List>() {
                        @Override
                        public int compare(List lhs, List rhs) {
                            int rhsDistance = Math.abs(calcMeanPoint(rhs) - mean);
                            int lhsDistance = Math.abs(calcMeanPoint(lhs) - mean);

                            if (rhsDistance <= limit && lhsDistance <= limit) {
                                /* both are close, select based on more points */
                                //return rhs.size() - lhs.size();
                            }
                            return lhsDistance - rhsDistance;
                        }
                    };
                    Collections.sort(whitePointsCandidates, cmpMoreClosed);
                    /* get the candidate with more closed to last points */
                    whitePointsConnected = whitePointsCandidates.get(0);
                } else if (whitePointsCandidates.size() > 1) {
                    /* else sort by the candidate with more points */
                    Comparator<List> cmpQtd = new Comparator<List>() {
                        @Override
                        public int compare(List lhs, List rhs) {
                            return rhs.size() - lhs.size();
                        }
                    };
                    Collections.sort(whitePointsCandidates, cmpQtd);
                    /* get the candidate with more points if not too far away*/
                    if (meanLastPoints >= 0) {
                        for (int i = 0; i < whitePointsCandidates.size(); i++) {
                            whitePointsConnected = whitePointsCandidates.get(i);
                            int value = calcMeanPoint(whitePointsConnected);
                            int distance = Math.abs(meanLastPoints - value);

                            if (distance <= limitDistance) {
                                break;
                            }

                        }
                    } else {
                        whitePointsConnected = whitePointsCandidates.get(0);
                    }
                } else {
                    whitePointsConnected = whitePointsCandidates.get(0);
                }


                int value = calcMeanPoint(whitePointsConnected);
                boolean addToSeries = true;
                boolean addToLastPoints = true;
                boolean removeOldestPointFromLastPoints = false;

                if (lastPoints.size() >= sufficientPoints) {
                    removeOldestPointFromLastPoints = true;
                }


                int distance = Math.abs(meanLastPoints - value);
                if (meanLastPoints >= 0 && distance > limitDistance) {
                    /* current value is far away from mean of last values */
                    addToSeries = false;
                    addToLastPoints = false;
                    Log.d(TAG,
                            String.format("(col:%d) Point with value %d (mean of last points: %d) is far away [distance: %d, limit %d, candidate size: %d]",
                                    col, value, meanLastPoints, distance, limitDistance, whitePointsConnected.size())
                    );
                    /* verify if it is close of any  strictClosePointsCount */
                    if (lastPoints.size() > strictClosePointsCount) {
                        for (int i = 0; i < strictClosePointsCount; i++) {
                            int diff = Math.abs(value - lastPoints.get(lastPoints.size() - (1+i)));
                            if (diff <= limitDistance) {
                                addToSeries = true;
                                //removeOldestPointFromLastPoints = true;
                                addToLastPoints = true;
                                Log.d(TAG, "Point was resgated because it is close from one of the last points");
                                break;
                            }
                        }
                    }
                } else {
                    wellConnectedPoints += 1;
                }
                if (addToSeries) {
                    double x, y;
                    x = time * col;
                    y = (rows - value) * voltage;
                    seriesX.add(x);
                    seriesY.add(y);
                }
                if (addToLastPoints) {
                    lastPoints.add(value);
                }

                if (lastPoints.size() > 0 && removeOldestPointFromLastPoints) {
                    lastPoints.removeFirst();
                }
            } else {
//                /* no white points */
//                lastPoints = new LinkedList<Integer>();
            }
        }

        double ratio_total = 0;
        double ratio_considered = 0;
        if (cols > 0) {
            ratio_total = wellConnectedPoints/(double)cols;
        }
        if (seriesX.size() > 0) {
            ratio_considered = wellConnectedPoints / (double) seriesX.size();
        }
        Log.d(TAG, String.format("Well connected points: %d [considered points: %d (ratio: %.4f)|total points: %d (ratio: %.4f)]",
                wellConnectedPoints, seriesX.size(), ratio_considered, cols, ratio_total
        ));

        if (outSeriesX != null) {
            outSeriesX.addAll(seriesX);
        }

        if (outSeriesY != null) {
            outSeriesY.addAll(seriesY);
        }

        return (1.0 * ratio_considered + 1.5 * ratio_total)/2.5;
    }

    @Override
    public Mat getResult() {
        return mRgbaDst;
    }

    public static int calcMeanPoint(List<Integer> points) {
        int sum = 0;
        for (int v: points) {
            sum += v;
        }
        /* mean point */
        return Math.round(sum/(float)points.size());
    }
}
