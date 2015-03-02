package com.iuridiniz.checkmyecg;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import com.iuridiniz.checkmyecg.filter.GraphFilter2;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;


public class ShowEkgActivity extends ActionBarActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;


    public static final String EXTRA_PHOTO_URI =
            "com.iuridiniz.checkmyecg.ShowEkgActivity.extra.PHOTO_URI";
    public static final String EXTRA_PHOTO_DATA_PATH =
            "com.iuridiniz.checkmyecg.ShowEkgActivity.extra.PHOTO_DATA_PATH";

    public static final String TAG = "EkgShow";
    private boolean mOpenCvLoaded = false;
    private Uri mUri;
    private String mDataPath;

    private GraphFilter2 mGraphFilter;
    private ImageView mImageContent;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_ekg);

        final Intent intent = getIntent();
        mUri = intent.getParcelableExtra(EXTRA_PHOTO_URI);
        mDataPath = intent.getStringExtra(EXTRA_PHOTO_DATA_PATH);

        /* init openCV */
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV loaded successfully (static initialization)");
            mOpenCvLoaded = true;
            onReady(savedInstanceState);
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
                                onReady(savedInstanceState);
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


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_show_ekg, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        //if (id == R.id.action_settings) {
        //    return true;
        //}

        return super.onOptionsItemSelected(item);
    }


    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        List<MatOfPoint> mContours;
        Mat mImage;

        protected SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        public SectionsPagerAdapter(FragmentManager supportFragmentManager, Mat rgbaImage, List<MatOfPoint> contours) {
            this(supportFragmentManager);
            mImage = rgbaImage;
            mContours = contours;
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position, mImage, mContours.get(position));
        }

        @Override
        public int getCount() {
            return mContours.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return null;
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";
        private MatOfPoint mContour;
        private Mat mImage;
        private Mat mImageRoi;
        private ImageView mImageContent;
        private Button mSelectButton;

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber, Mat img, MatOfPoint contour) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            //Bundle args = new Bundle();
            //args.putInt(ARG_SECTION_NUMBER, sectionNumber);

            //fragment.setArguments(args);
            fragment.mImage = img;
            fragment.mContour = contour;
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                                 Bundle savedInstanceState) {
            final View rootView = inflater.inflate(R.layout.fragment_show_ekg, container, false);
            mImageContent = (ImageView) rootView.findViewById(R.id.image_content);
            mSelectButton = (Button) rootView.findViewById(R.id.select_button);

            /* show image */
            Rect r = Imgproc.boundingRect(mContour);
            Bitmap bmp = Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888);
            mImageRoi = mImage.submat(r.y, r.y + r.height, r.x, r.x + r.width);
            Utils.matToBitmap(mImageRoi, bmp);

            mImageContent.setImageBitmap(bmp);

            /* setup button */
            mSelectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Select Button clicked");
                    ((Activity)rootView.getContext()).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            takePhoto();
                        }
                    });
                }
            });

            return rootView;
        }

        private void takePhoto() {
            Activity activity = (Activity) mImageContent.getContext();
            TakePhoto takePhoto = TakePhoto.invoke(activity, mImageRoi);
            if (takePhoto.hasError()) {
                activity.finish();
                Log.e(TAG, takePhoto.getErrorString(), takePhoto.getException());
                return;
            }
            Uri uri = takePhoto.getUri();
            String photoPath = takePhoto.getPhotoPath();

            /* Open the photo on result */
            final Intent intent = new Intent(activity, ResultEkgActivity.class);
            intent.putExtra(ResultEkgActivity.EXTRA_PHOTO_URI, uri);
            intent.putExtra(ResultEkgActivity.EXTRA_PHOTO_DATA_PATH, photoPath);
            startActivity(intent);
        }
    }


    private void onReady(Bundle savedInstanceState) {

        /* load image */
        Log.d(TAG, String.format("Loading '%s' as image", mDataPath));
        Mat imageBgr = Highgui.imread(mDataPath);

        if (!(imageBgr.width() > 0)) {
            Log.e(TAG, "Unable to load image");
            finish();
            imageBgr.release();
            imageBgr = null;
        }

        Log.d(TAG, String.format("Image loaded (%s), channels: %d, type: %s",
                imageBgr.size(),
                imageBgr.channels(),
                CvType.typeToString(imageBgr.type())));
        mGraphFilter = new GraphFilter2(imageBgr.rows(), imageBgr.cols());

        final Mat imageRgba = new Mat(imageBgr.rows(), imageBgr.cols(), CvType.CV_8UC4);

        Imgproc.cvtColor(imageBgr, imageRgba, Imgproc.COLOR_BGR2RGBA);

        final Mat result = mGraphFilter.apply(imageRgba);

        /* show image */
        //Bitmap bm = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888);
        //Utils.matToBitmap(result, bm);
        //mImageContent.setImageBitmap(bm);

        imageBgr.release();
        imageBgr = null;

//        final ArrayList<Bitmap> bmps = new ArrayList<Bitmap>(10);
//
//        for (MatOfPoint mp : mGraphFilter.getContours(10)) {
//            Rect r = Imgproc.boundingRect(mp);
//            Bitmap bmp = Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888);
//
//            Mat roi = imageRgba.submat(r.y, r.y + r.height, r.x, r.x + r.width);
//
//            Utils.matToBitmap(roi, bmp);
//            bmps.add(bmp);
//
//        }

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), imageRgba, mGraphFilter.getContours(10));


        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

    }

}
