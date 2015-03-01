package com.iuridiniz.checkmyecg;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.File;

/**
 * Created by iuri on 01/03/15.
 */
public class TakePhoto {
    private String photoPath = null;
    private Uri uri = null;
    private boolean result = false;
    private Exception e = null;

    private String errorString = null;

    protected TakePhoto() {
    }

    protected TakePhoto(Uri uri, String photoPath) {
        this.photoPath = photoPath;
        this.uri = uri;
        this.result = true;
    }

    static protected TakePhoto createWithError(String error, Exception e) {
        TakePhoto ret = new TakePhoto();
        ret.errorString = error;
        ret.e = e;
        return ret;
    }

    static protected TakePhoto createWithError(String error) {
        return createWithError(error, null);
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public Uri getUri() {
        return uri;
    }

    public boolean hasError() {
        return !result;
    }

    public String getErrorString() {
        return errorString;
    }

    public Exception getException() {
        return e;
    }

    public static TakePhoto invoke(Context ctx, Mat rgba) {

        Mat bgra = new Mat();

        /* Determine the path and metadata for the photo. */
        long currentTimeMillis = System.currentTimeMillis();
        String appName = ctx.getString(R.string.app_name);
        String galleryPath =
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES).toString();
        String albumPath = galleryPath + "/" + appName;
        String photoPath = albumPath + "/"
                + currentTimeMillis + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, photoPath);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.TITLE, appName);
        values.put(MediaStore.Images.Media.DESCRIPTION, appName);
        values.put(MediaStore.Images.Media.DATE_TAKEN, currentTimeMillis);

        /* Ensure that the album directory exists. */
        File album = new File(albumPath);
        if (!album.isDirectory() && !album.mkdirs()) {
            return createWithError(String.format("Failed to create album directory at '%s'",
                    albumPath));
        }

        /* Try to create the photo. */
        Imgproc.cvtColor(rgba, bgra, Imgproc.COLOR_RGBA2BGRA);

        if (!Highgui.imwrite(photoPath, bgra)) {
            return createWithError(String.format("Failed to save photo to '%s'", photoPath));
        }
        //Log.d(TAG, String.format("Photo saved successfully to '%s", photoPath));

        /* Try to insert the photo into the MediaStore. */
        Uri uri;
        try {
            uri = ctx.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (final Exception e) {
            String error = "Failed to insert photo into MediaStore";

            /* Since the insertion failed, delete the photo. */
            File photo = new File(photoPath);
            if (!photo.delete()) {
                error += "(Failed to delete non-inserted photo too)";
            }

            return createWithError(error, e);
        }

        return new TakePhoto(uri, photoPath);
    }

}
