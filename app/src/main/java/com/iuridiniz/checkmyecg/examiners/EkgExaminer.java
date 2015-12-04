package com.iuridiniz.checkmyecg.examiners;

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
    private final Orientation normalOrientation;


    public final LinkedList<Integer> getPeaksPositions() {
        if (__peaksPositions != null)
            return __peaksPositions;
        /* parse peaks */
        __peaksPositions = new LinkedList<Integer>();
        int direction = 0;
        if (time.length < 3) {
            return __peaksPositions;
        }

        for (int i = 0; i < time.length; i++) {
            int new_direction;

            double volt_diff;
            if (i == 0) {
                /* fisrt */
            }

            if (i+1 == time.length) {
                /* last */
                if (direction == 1) {
                    __peaksPositions.add(i);
                }
                continue;
            }
            volt_diff = voltage[i+1] - voltage[i];

            new_direction = 0;
            if (volt_diff < 0 ) {
                new_direction = -1;
            } else if  (volt_diff > 0 ){
                new_direction = 1;
            }

            if (new_direction != 0 && new_direction != direction) {
                if (new_direction == 1) {
                    /* depression */
                } else if (new_direction == -1){
                    /* peak */
                    __peaksPositions.add(i);
                }
                direction = new_direction;
            }

        }
        /* return the median point */
        for (int i=0; i<__peaksPositions.size(); i++) {
            int start_pos = __peaksPositions.get(i);
            int end_pos = __peaksPositions.get(i);
            int pos = __peaksPositions.get(i);
            if (pos == 0 || end_pos == time.length - 1) {
                /* peek at start or at end */
            } else {
                /* backward start_pos to the first value lower point */
                while (start_pos > 0 && voltage[start_pos-1] >= voltage[pos]) {
                    start_pos--;
                };
                /* forward end_pos to the first value lower point */
                while (end_pos < time.length - 1 && voltage[end_pos+1] >= voltage[pos]) {
                    end_pos++;
                };

                /* change to median point */
                int point = (Integer) (end_pos - start_pos)/2 + start_pos;
                __peaksPositions.set(i, point);
            }
        }
        return __peaksPositions;
    }


    public final TreeMap<Integer, Pair<Double, Double>> getPeaksPositionsAndCoefficients(double smoothness, /* linear coefficient */
                                                                           double threshold) {

        LinkedList<Integer> peaks = getPeaksPositions();
        TreeMap<Integer, Pair<Double, Double>> result = new TreeMap<>();

        for (int pos: peaks) {
            /* get previous points */
            int startPos = pos;
            int endPos = pos;
            if (pos == 0 || pos == time.length - 1) {
                /* peek at start  or at end */
            } else {
                /* backward startPos to the first value greater than threshold */
                while (startPos > 0 && Math.abs(voltage[pos] - voltage[startPos]) < threshold) {
                    startPos--;
                };
                /* forward endPos to the first value greater than threshold */
                while (endPos < (time.length  - 1) && Math.abs(voltage[pos] - voltage[endPos]) < threshold) {
                    endPos++;
                };

            }
            if (startPos != endPos) {
                int pointLeft = startPos;
                int pointRight = endPos;

                double dxLeft, dyLeft;
                double dxRight, dyRight;
                double coefficientLeft, coefficientRight;
                int meanPointLeft, meanPointRight;
                double meanPoint;

                meanPoint = ((double)pointRight - (double)pointLeft)/2.0 + pointLeft;
                meanPointLeft = (int)Math.floor(meanPoint);
                meanPointRight = (int)Math.ceil(meanPoint);

                dxLeft = time[meanPointLeft] - time[pointLeft];
                dyLeft = voltage[meanPointLeft] - voltage[pointLeft];
                coefficientLeft = dyLeft/dxLeft;

                dxRight = time[pointRight] - time[meanPointRight];
                dyRight = voltage[pointRight] - voltage[meanPointRight];

                coefficientRight = dyRight/dxRight;

                if (coefficientLeft >= smoothness && -coefficientRight >=smoothness) {
                    result.put(pos, new Pair<Double, Double>(coefficientLeft, coefficientRight));
                }
            }
        }

        return result;
    }

    public final LinkedList<Integer> getPeaksPositions(double smoothness, /* linear coefficient */
                                                       double threshold) {

        TreeMap<Integer, Pair<Double, Double>> posAndCo = getPeaksPositionsAndCoefficients(smoothness, threshold);
        return  new LinkedList<>(posAndCo.keySet());
    }

    public final LinkedList<Integer> getAcutePeaksPositions() {
        return getPeaksPositions(1.0, ONE_SQUARE_Y);
    }

    public final TreeMap<Integer, Pair<Double, Double>> getAcutePeaksPositionsAndCoefficients() {
        return getPeaksPositionsAndCoefficients(1.0, ONE_SQUARE_Y);
    }


    public EkgExaminer(double[] signalX, double[] signalY) {
        this(signalX, signalY, Orientation.DOWN);
    }
    public EkgExaminer(double[] time, double[] voltage, Orientation o) {
        this.time = time;
        this.voltage = voltage;
        if (time.length != voltage.length) {
            throw new IllegalArgumentException("time.lenght must be equal to voltage.lenght");
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
            int score = 0;
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
                score += coefLeftScore >50?50: coefLeftScore;

                coefRightScore = (int) (1.0/ relationCoefRight);
                score += coefRightScore >50?50: coefRightScore;

                voltScore = (int) (5.0/diffVolt);
                score += voltScore >100?100: voltScore;

            }


            @Override
            public int compareTo(Object o) {
                return score - ((MyPair) o).score;
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
        //return 60/a.get(0).diffTime;

    }

}
