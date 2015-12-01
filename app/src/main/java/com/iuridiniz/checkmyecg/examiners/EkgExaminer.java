package com.iuridiniz.checkmyecg.examiners;

//import android.util.Log;

import org.apache.commons.math3.stat.StatUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

/**
 * Created by iuri on 27/11/15.
 */
public class EkgExaminer {
    private static final String TAG = "EkgExaminer";
    private final double[] time;
    private final double[] voltage;
    private int[] peaks;
    private int[] depressions;
    private LinkedList<Integer> __peaksPositions = null;
    private double voltageSignificancy;

    public EkgExaminer(double[] signalX, double[] signalY) {
        this(signalX, signalY, Orientation.DOWN);
    }

    private TreeMap<Integer, LinkedList<Integer>> getMapVoltage() {
        if (__mapVoltage.size() == 0) {
            /* try to be lazy */
            double max_voltage = StatUtils.max(voltage);
            double min_voltage = StatUtils.min(voltage);
            //Log.d(TAG, String.format("max_voltage: %.4f", max_voltage));
            //Log.d(TAG, String.format("min_voltage: %.4f", min_voltage));
            for (int i = 0; i < voltage.length; i++) {
                int key = (int) (voltage[i] * (1/ DEFAULT_VOLTAGE_RESOLUTION));
                List l = __mapVoltage.get(key);
                if (l == null) {
                    l = new LinkedList<Integer>();
                    __mapVoltage.put(key, (LinkedList<Integer>) l);
                }
                l.add(i);
            }
        }
        return __mapVoltage;
    }

    private TreeMap<Integer, LinkedList<Integer>> getMapTime() {
        return mapTime;
    }

    private final TreeMap<Integer, LinkedList<Integer>> __mapVoltage;
    private final TreeMap<Integer, LinkedList<Integer>> mapTime;

    boolean storeOnTemp = false;
    public final LinkedList<Integer> getPeaksPositions() {
        if (__peaksPositions != null)
            return __peaksPositions;
        /* parse peaks */
        __peaksPositions = new LinkedList<Integer>();
        int direction = 0;
        if (time.length < 3) {
            return __peaksPositions;
        }
        LinkedList<Integer> tempSerie = new LinkedList<Integer>();

        for (int i = 0; i < time.length; i++) {
            int new_direction;

            double volt_diff;
            if (i == 0) {
                /* fisrt */
            }

            if (i+1 == time.length) {
                /* last */
                if (direction == 1) {
                    int pos = i;
                    if (tempSerie.size() > 0) {
                        pos = (i - tempSerie.getFirst())/2 + tempSerie.getFirst();
                    }
                    __peaksPositions.add(pos);
                }
                continue;
            }
            volt_diff = voltage[i+1] - voltage[i];
            if (Math.abs(volt_diff) < this.voltageSignificancy) {
                new_direction = 0;
                tempSerie.add(i);
                //storeOnTemp = true;
            } else {

                if (volt_diff < 0 ) {
                    new_direction = -1;
                } else {
                    new_direction = 1;
                }
                if (new_direction == direction) {
                    tempSerie.clear();
                }
            }
            boolean flush = false;
            if (new_direction != 0 && new_direction != direction) {
                flush=true;
            }

            if (flush) {
                tempSerie.add(i);
                if (new_direction == 1) {
                    /* depression */
                } else if (new_direction == -1){
                    /* peak */
                    int pos = (tempSerie.getLast()- tempSerie.getFirst())/2 + tempSerie.getFirst() ;
                    __peaksPositions.add(pos);
                    //__peaksPositions.add(i);
                }
                direction = new_direction;
                tempSerie.clear();
                storeOnTemp = false;
            }

        }
        return __peaksPositions;
    }

    public void setVoltageSignificancy(double voltageSignificancy) {
        this.voltageSignificancy = voltageSignificancy;
        this.__peaksPositions = null;
    }


    public enum Orientation {
        UP, DOWN
    };
    /* 0.1v  is the equivalent of little square in voltage axis */
    /* 0.04s is the equivalent of little square in time axis */
    public static double DEFAULT_VOLTAGE_RESOLUTION = 0.05; /* half of a square */
    public static double DEFAULT_VOLTAGE_SIGNIFICANCY = 0.1; /* one little square */
    public static double DEFAULT_TIME_SIGNIFICANCY = 0.04; /* one little square */

    private final Orientation normalOrientation;

    public EkgExaminer(double[] time, double[] voltage, Orientation o) {
        this.time = time;
        this.voltage = voltage;
        if (time.length != voltage.length) {
            throw new IllegalArgumentException("time.leght must be equal to voltage.lenght");
        }
        this.normalOrientation = o;

        this.__mapVoltage = new TreeMap<>();
        this.mapTime = new TreeMap<>();

        this.voltageSignificancy = this.DEFAULT_VOLTAGE_SIGNIFICANCY;
    }


    public double getFrequency() {

        double diff_time = 0.0;
        Double last_time = null;
        Integer key = null;

        /* start from bottom values */
        /* FIXME: this algorithm does not catch two peeks with a little peak between them*/
        key = getMapVoltage().firstKey();
        while (diff_time < DEFAULT_TIME_SIGNIFICANCY && key != null) {
            List<Integer> list = getMapVoltage().get(key);
            Iterator<Integer> it = list.iterator();
            while (it.hasNext() && diff_time < DEFAULT_TIME_SIGNIFICANCY) {
                int v = it.next();
                double cur_time = this.time[v];
                double cur_voltage = this.voltage[v];
                //Log.d(TAG, String.format("Voltage: %.4f at %.4f", cur_voltage, cur_time));
                if (last_time != null) {
                    diff_time = Math.abs(cur_time - last_time);
                }
                /* update last_time */
                last_time = cur_time;
            }
            /* go to next higher key */
            key = getMapVoltage().higherKey(key);
        }
        double bottom_frequency = 0.0;
        if (diff_time > 0) {
            //Log.d(TAG, String.format("Bottom difference: %.4f", diff_time));

            bottom_frequency = 60/diff_time;
        }

        return bottom_frequency;
    }

}
