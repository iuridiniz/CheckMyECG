package com.iuridiniz.checkmyecg.filter;

import org.opencv.core.Mat;

/**
 * Created by iuri on 01/03/15.
 */
public interface Filter {
    public Mat apply(Mat src);
}
