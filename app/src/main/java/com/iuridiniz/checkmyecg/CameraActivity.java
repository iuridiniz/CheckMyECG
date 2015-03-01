package com.iuridiniz.checkmyecg;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import com.iuridiniz.checkmyecg.filter.ContrastFilter;
import com.iuridiniz.checkmyecg.filter.GraphFilter2;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.File;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "ActivityCamera";
    private boolean mOpenCvLoaded = false;
    private CameraBridgeViewBase mPreview;

    private GraphFilter2 mGraphFilter2;
    private ContrastFilter mContrastFilter;
    private ImageButton mCaptureButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mCaptureButton = (ImageButton) findViewById(R.id.capture_button);
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Capture Button clicked");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        takePhoto();
                    }
                });
            }
        });

        /* Keep the screen on */
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        /* start openCV */
        /* try static initialization */
        if (OpenCVLoader.initDebug()) {

            Log.i(TAG, "OpenCV loaded successfully (static initialization)");
            mOpenCvLoaded = true;
            return;
        }

        /* binaries not included, use OpenCV manager */
        Log.i(TAG, "OpenCV libs not included on APK, trying async initialization");
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_10, this, new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                switch (status) {
                    case BaseLoaderCallback.SUCCESS:
                        Log.i(TAG, "OpenCV loaded successfully (async initialization)");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mOpenCvLoaded = true;
                                createCameraPreview();
                            }
                        });
                        break;
                    default:
                        super.onManagerConnected(status);
                        break;
                }
            }
        });

    }

    private void takePhoto() {
        /* save current image */
        Mat rgba = mContrastFilter.getResult();
        Mat bgra = new Mat();

        if (rgba == null) {
            Log.e(TAG, "There's no photo");
            finish();
            return;
        }
        /* Determine the path and metadata for the photo. */
        final long currentTimeMillis = System.currentTimeMillis();
        final String appName = getString(R.string.app_name);
        final String galleryPath =
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES).toString();
        final String albumPath = galleryPath + "/" + appName;
        final String photoPath = albumPath + "/"
                + currentTimeMillis + ".png";
        final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, photoPath);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.TITLE, appName);
        values.put(MediaStore.Images.Media.DESCRIPTION, appName);
        values.put(MediaStore.Images.Media.DATE_TAKEN, currentTimeMillis);

        /* Ensure that the album directory exists. */
        File album = new File(albumPath);
        if (!album.isDirectory() && !album.mkdirs()) {
            Log.e(TAG, String.format("Failed to create album directory at '%s'",
                    albumPath));
            finish();
            return;
        }

        /* Try to create the photo. */
        Imgproc.cvtColor(rgba, bgra, Imgproc.COLOR_RGBA2BGRA);
        if (!Highgui.imwrite(photoPath, bgra)) {
            Log.e(TAG, String.format("Failed to save photo to '%s'", photoPath));
            finish();
            return;
        }
        Log.d(TAG, String.format("Photo saved successfully to '%s", photoPath));

        /* Try to insert the photo into the MediaStore. */
        Uri uri;
        try {
            uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (final Exception e) {
            Log.e(TAG, "Failed to insert photo into MediaStore", e);

            // Since the insertion failed, delete the photo.
            File photo = new File(photoPath);
            if (!photo.delete()) {
                Log.e(TAG, "Failed to delete non-inserted photo");
            }

            finish();
            return;
        }

        /* Open the photo */
        final Intent intent = new Intent(this, ShowEkg.class);
        intent.putExtra(ShowEkg.EXTRA_PHOTO_URI, uri);
        intent.putExtra(ShowEkg.EXTRA_PHOTO_DATA_PATH, photoPath);
        startActivity(intent);
    }

    private void createCameraPreview() {
        /* Create our Preview */
        if (mPreview == null) {
            mPreview = (CameraBridgeViewBase) findViewById(R.id.camera_view);
            mPreview.setVisibility(SurfaceView.VISIBLE);
            mPreview.setCvCameraViewListener(this);
        }
        if (mOpenCvLoaded) {
            mPreview.enableView();
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        createCameraPreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.disableView();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mPreview != null) {
            mPreview.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGraphFilter2 = new GraphFilter2(height, width);
        mContrastFilter = new ContrastFilter(height, width, 100, 140);
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        /* show previews */
        Mat src = null, bottomLeft = null, topLeft = null, topRight = null, bottomRight = null;

        src = inputFrame.rgba();

        Mat srcNormalized = mContrastFilter.apply(src);
        Mat graph = mGraphFilter2.apply(srcNormalized);

        topLeft = src;

        drawMini(graph, topLeft, bottomLeft, topRight, bottomRight);

        return graph;
    }

    private void drawMini(Mat dst, Mat topLeft, Mat bottomLeft, Mat topRight, Mat bottomRight) {
        int dstHeight = dst.rows();
        int dstWidth = dst.cols();

        int dstRoiHeight = dstHeight / 4;
        int dstRoiWidth = dstWidth / 4;

        Mat dstRoi;

        if (topLeft != null) {
            /* draw topLeft into top left corner */
            dstRoi = dst.submat(0, dstRoiHeight, 0, dstRoiWidth);
            Imgproc.resize(topLeft, dstRoi, dstRoi.size());
        }
        if (bottomLeft != null) {
            /* draw bottomLeft into bottom left corner */
            dstRoi = dst.submat(dstHeight - dstRoiHeight, dstHeight, 0, dstRoiWidth);
            Imgproc.resize(bottomLeft, dstRoi, dstRoi.size());
        }
        if (topRight != null) {
            /* draw topRight into top right corner */
            dstRoi = dst.submat(0, dstRoiHeight, dstWidth - dstRoiWidth, dstWidth);
            Imgproc.resize(topRight, dstRoi, dstRoi.size());
        }
        if (bottomRight != null) {
            /* draw bottomRight into bottom right corner */
            dstRoi = dst.submat(dstHeight - dstRoiHeight, dstHeight, dstWidth - dstRoiWidth, dstWidth);
            Imgproc.resize(bottomRight, dstRoi, dstRoi.size());
        }
    }
}