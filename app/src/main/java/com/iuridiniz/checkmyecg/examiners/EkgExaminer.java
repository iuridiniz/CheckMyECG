package com.iuridiniz.checkmyecg.examiners;

import android.support.annotation.NonNull;

import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.TreeMap;

/**
 * Created by iuri on 27/11/15.
 */
public class EkgExaminer {
    public enum Orientation {
        UP, DOWN
    };
    public static final double ONE_SQUARE_X = 0.04;
    public static final double ONE_SQUARE_Y = 0.1;

    private final double[] time;
    private final double[] voltage;
    private LinkedList<Integer> __peaksPositions = null;
    private LinkedList<Integer> __depressionsPositions = null;

    private final Orientation normalOrientation;


    public final LinkedList<Integer> getPeaksPositions() {
        if (__peaksPositions != null)
            return __peaksPositions;
        /* parse peaks */
        __peaksPositions = getMaxMin(time, voltage, true);
        return __peaksPositions;
    }

    public LinkedList<Integer> getDepressionsPositions() {
        if (__depressionsPositions != null)
            return __depressionsPositions;
        /* parse depressions */
        __depressionsPositions = getMaxMin(time, voltage, false);
        return __depressionsPositions;
    }

    @NonNull
    private static LinkedList<Integer> getMaxMin(double[] x, double[] y, boolean peaks) {

        int desiredDirection = 1;
        if (!peaks) {
            desiredDirection = -1;
        }
        if (x.length != y.length) {
            throw new IllegalArgumentException("x.length must be equal to y.length");
        }

        LinkedList<Integer> positions = new LinkedList<Integer>();
        int direction = 0;
        if (x.length < 3) {
            return positions;
        }

        for (int i = 0; i < x.length; i++) {
            int newDirection;

            double yDiff;

            if (i+1 == x.length) {
                /* last */
                if (direction == desiredDirection) {
                    positions.add(i);
                }
                continue;
            }
            yDiff = y[i+1] - y[i];

            newDirection = 0;
            if (yDiff < 0 ) {
                newDirection = -1;
            } else if  (yDiff > 0 ){
                newDirection = 1;
            }

            if (newDirection != 0 && newDirection != direction) {
                if (newDirection != desiredDirection) {
                    /* depression or peak found */
                    positions.add(i);
                }
                direction = newDirection;
            }

        }
        /* return the median point */
        for (int i=0; i<positions.size(); i++) {
            int startPos = positions.get(i);
            int endPos = positions.get(i);
            int pos = positions.get(i);
            if (pos == 0 || endPos == x.length - 1) {
                /* peek/depression at start or at end */
            } else {
                /* backward startPos to the first value lower/higher point */
                while (startPos > 0 && (y[startPos-1] - y[pos]) * desiredDirection >= 0) {
                    startPos--;
                };
                /* forward endPos to the first value lower/higher point */
                /* XXX: I think this code is never called */
                while (endPos < x.length - 1 && (y[endPos+1] - y[pos]) * desiredDirection >= 0) {
                    endPos++;
                };

                /* change it to median point */
                int point = (Integer) (endPos - startPos)/2 + startPos;
                positions.set(i, point);
            }
        }
        return positions;
    }

    private static final Pair<Double, Double> getCoefficients(int pos, final double[] x, final double[] y, double threshold) {
        int startPos = pos;
        int endPos = pos;

        if (pos > 0) {
            while (startPos > 0 && Math.abs(y[pos] - y[startPos]) < threshold) {
                startPos--;
            };
        }
        if (pos < x.length - 1) {
            while (endPos < (x.length - 1) && Math.abs(y[pos] - y[endPos]) < threshold) {
                endPos++;
            }

        }

        if (startPos != endPos) {

            double dxLeft, dyLeft;
            double dxRight, dyRight;
            double coefficientLeft, coefficientRight;
            int meanPointLeft, meanPointRight;
            double meanPoint;

            meanPoint = ((double) endPos - (double) startPos)/2.0 + startPos;
            meanPointLeft = (int)Math.floor(meanPoint);
            meanPointRight = (int)Math.ceil(meanPoint);

            dxLeft = x[meanPointLeft] - x[startPos];
            dyLeft = y[meanPointLeft] - y[startPos];
            coefficientLeft = dyLeft/dxLeft;

            dxRight = x[endPos] - x[meanPointRight];
            dyRight = y[endPos] - y[meanPointRight];

            coefficientRight = dyRight/dxRight;

            return new Pair<Double, Double>(coefficientLeft, coefficientRight);
        }
        return null;
    }

    @NonNull
    private static TreeMap<Integer, Pair<Double, Double>> getSmoothMaxMin(final LinkedList<Integer> points, final double[] x, final double[] y, double smoothness, double threshold) {
        TreeMap<Integer, Pair<Double, Double>> result = new TreeMap<>();

        int direction = 0;
        if (smoothness > 0) {
            direction = +1;
        } else if (smoothness < 0) {
            direction = -1;
        }
        for (int pos : points) {
            Pair<Double, Double> coefficients = getCoefficients(pos, x, y, threshold);

            if (coefficients == null) {
                continue;
            }
            double coefficientLeft = coefficients.getFirst();
            double coefficientRight = coefficients.getSecond();

            if ((direction * coefficientLeft) >= (direction * smoothness) && (-1 * direction * coefficientRight) >= (direction * smoothness)) {
                result.put(pos, coefficients);
            }
        }
        return result;
    }

    public final LinkedList<Integer> getAcutePeaksPositions() {
        TreeMap<Integer, Pair<Double, Double>> result;
        result = getSmoothMaxMin(getPeaksPositions(), time, voltage, 1.0, ONE_SQUARE_Y);
        return  new LinkedList<>(result.keySet());
    }

    public final TreeMap<Integer, Pair<Double, Double>> getAcutePeaksPositionsAndCoefficients() {
        TreeMap<Integer, Pair<Double, Double>> result;
        result = getSmoothMaxMin(getPeaksPositions(), time, voltage, 1.0, ONE_SQUARE_Y);
        return result;
    }

    public final LinkedList<Integer> getAcuteDepressionsPositions() {
        TreeMap<Integer, Pair<Double, Double>> result;
        result = getSmoothMaxMin(getDepressionsPositions(), time, voltage, -1.0, ONE_SQUARE_Y);
        return  new LinkedList<>(result.keySet());
    }

    public final TreeMap<Integer, Pair<Double, Double>> getAcuteDepressionsPositionsAndCoefficients() {
        TreeMap<Integer, Pair<Double, Double>> result;
        result = getSmoothMaxMin(getDepressionsPositions(), time, voltage, -1.0, ONE_SQUARE_Y);
        return result;
    }

    public EkgExaminer(double[] signalX, double[] signalY) {
        this(signalX, signalY, Orientation.DOWN);
    }

    public EkgExaminer(double[] time, double[] voltage, Orientation o) {
        this.time = time;
        this.voltage = voltage;
        if (time.length != voltage.length) {
            throw new IllegalArgumentException("time.length must be equal to voltage.length");
        }
        this.normalOrientation = o;
    }

    public double getFrequency() {

        final TreeMap<Integer, Pair<Double, Double>> coefficients = getAcutePeaksPositionsAndCoefficients();
        Integer[] peaks = coefficients.keySet().toArray(new Integer[0]);

        if (peaks.length < 4) {
            /* insufficient points to determine the frequency */
            return 0.0;
        }

        class MyPair extends Pair<Integer, Integer> implements Comparable{
            final double diffTime;
            final double relationCoefLeft;
            final double relationCoefRight;
            final double diffVolt;
            int cappedScore = 0;
            int coefLeftScore;
            int coefRightScore;
            final int voltScore;

            public MyPair(Integer o1, Integer o2) {
                super(o1, o2);
                diffTime = Math.abs(time[o1] - time[o2]);
                relationCoefLeft = Math.abs(1.0 - (coefficients.get(o1).getFirst()/coefficients.get(o2).getFirst()));
                relationCoefRight = Math.abs(1.0 - (coefficients.get(o1).getSecond()/coefficients.get(o2).getSecond()));

                diffVolt = Math.abs(voltage[o1] - voltage[o2]);

                coefLeftScore = (int) (1.0/ relationCoefLeft);
                cappedScore += coefLeftScore >50?50: coefLeftScore;

                coefRightScore = (int) (1.0/ relationCoefRight);
                cappedScore += coefRightScore >50?50: coefRightScore;

                voltScore = (int) (10.0/diffVolt);
                cappedScore += voltScore >100?100: voltScore;

            }

            @Override
            public int compareTo(@NonNull Object o) {
                /* minor is low score */
                int r = (Integer.valueOf (this.cappedScore)).compareTo(((MyPair) o).cappedScore);

                if (r == 0) {
                    /* minor is lower score on voltage */
                    r = (Double.valueOf (this.voltScore)).compareTo((double) ((MyPair) o).voltScore);
                }
                return r;
            }
        }
        ArrayList<MyPair> a = new ArrayList<>();

        for(int i = 0; i < peaks.length; i++) {
            for (int j=i + 1;j< peaks.length; j++) {
                MyPair p = new MyPair(peaks[i], peaks[j]);
                a.add(p);
            }
        }
        /* choose the best one */
        Collections.sort(a);
        Collections.reverse(a);

        return 60.0/a.get(0).diffTime;

    }

}
