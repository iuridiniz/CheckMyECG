package com.iuridiniz.checkmyecg;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.iuridiniz.checkmyecg.filter.GraphFilter2;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.features2d.DMatch;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.KeyPoint;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class ResultEkgActivity extends ActionBarActivity {

    public static final String EXTRA_PHOTO_URI =
            "com.iuridiniz.checkmyecg.ResultEkgActivity.extra.PHOTO_URI;";
    public static final String EXTRA_PHOTO_DATA_PATH =
            "com.iuridiniz.checkmyecg.ResultEkgActivity.extra.DATA_PATH;";
    static final String TAG = "ResultEkg";

    private ShareActionProvider mShareActionProvider;
    private Uri mImageUri;
    private String mImageDataPath;
    private ImageView mImageContent;
    private TextView mTextContent;
    private boolean mOpenCvLoaded = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result_ekg);

        final Intent intent = getIntent();
        mImageUri = intent.getParcelableExtra(EXTRA_PHOTO_URI);
        mImageDataPath = intent.getStringExtra(EXTRA_PHOTO_DATA_PATH);

        mImageContent = (ImageView) findViewById(R.id.result_image);
        mTextContent = (TextView) findViewById(R.id.result_text);

        mTextContent.setText("");

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

    private void onReady(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pick_derivation)
                .setItems(R.array.derivations_array, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, final int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ResultEkgActivity.this.compareWithDerivation(which);
                            }
                        });
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        ResultEkgActivity.this.finish();
                    }
                });
        Dialog d = builder.create();
        d.show();
    }

    private void compareWithDerivation(int derivationIndex) {
        String[] derivations = getResources().getStringArray(R.array.derivations_array);
        String derivation = derivations[derivationIndex];
        Log.i(TAG, String.format("Comparing the ekg derivation '%s' with pathological base", derivation));
        //mImageContent.setImageURI(mImageUri);
        //mTextContent.setText(R.string.text_ekg_fail);

        PathologicalDescriptor query = new PathologicalDescriptor();
        query.describe(mImageDataPath);

        //PathologicalDescriptor base = new PathologicalDescriptor("Normal");
        //try {
        //    base.describe(Utils.loadResource(this, R.drawable.test, Highgui.CV_LOAD_IMAGE_GRAYSCALE));
        //} catch (IOException e) {
        //    e.printStackTrace();
        //    finish();
        //}
        PathologicalMatcher matcher = null;
        {
            //DataBaseHelper db = new DataBaseHelper(this);
            DbAdapter db = new DbAdapter(this);
            db.initializeDatabase();
            db.open();
            matcher = db.getMatcher(derivation);
            db.close();
        } //catch (IOException e) {
            //e.printStackTrace();
            //Log.d(TAG, "Error while opening database", e);
        //}

        //matcher.append(base);

        List<PathologicalResult> search = matcher.search(query);
        for (PathologicalResult r: search) {
            Log.d(TAG, String.format("%s: %f", r.getText(), r.getScore()));
        }

        mImageContent.setImageURI(mImageUri);
        if (search.size() < 1) {
            mTextContent.setText(R.string.text_ekg_fail);
        } else {
            PathologicalResult best_result = search.get(0);
            if (best_result.getScore() < 0.5) {
                mTextContent.setText(R.string.text_ekg_fail);
            } else {
                mTextContent.setText(best_result.getText());
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_result_ekg, menu);

        // Set up ShareActionProvider's default share intent
        MenuItem shareItem = menu.findItem(R.id.action_share);
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(shareItem);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, mTextContent.getText());
        mShareActionProvider.setShareIntent(intent);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }
}

class PathologicalResult implements Comparable<PathologicalResult> {
    private Double mScore = -1.0;
    private String mText = "";

    public PathologicalResult(String text, double score) {
        this.mText = text;
        this.mScore = score;
    }

    @Override
    public int compareTo(PathologicalResult another) {
        return this.mScore.compareTo(another.mScore);
    }

    public Double getScore() {
        return mScore;
    }

    public String getText() {
        return mText;
    }
}

class PathologicalDescriptor {

    private final DescriptorExtractor mDescriptorExtractor;
    private final FeatureDetector mFeatureDetector;
    private GraphFilter2 graph = null;
    private final String mText;
    private MatOfKeyPoint mKeyPoints;

    private Mat mDescriptors;

    public PathologicalDescriptor(String text, int detectorType, int extractorType) {
        mText = text;
        mFeatureDetector = FeatureDetector.create(detectorType);
        mDescriptorExtractor = DescriptorExtractor.create(extractorType);
        mKeyPoints = new MatOfKeyPoint();
        mDescriptors = new Mat();
    }

    public PathologicalDescriptor() {
        this("");
    }

    public PathologicalDescriptor(String text) {
        this(text, FeatureDetector.STAR, DescriptorExtractor.FREAK);
    }

    public PathologicalDescriptor describe(String imagePath) {
        Mat imageGray = Highgui.imread(imagePath, Highgui.CV_LOAD_IMAGE_GRAYSCALE);
        return describe(imageGray);
    }

    public PathologicalDescriptor describe(Mat imageGray) {
        mFeatureDetector.detect(imageGray, mKeyPoints);
        mDescriptorExtractor.compute(imageGray, mKeyPoints, mDescriptors);

        Mat rgba = new Mat();
        Imgproc.cvtColor(imageGray, rgba, Imgproc.COLOR_GRAY2RGBA);
        graph = new GraphFilter2(rgba.rows(), rgba.cols());
        graph.apply(rgba);

        return this;
    }

    public List<MatOfPoint> getContours(Integer max, Boolean sortIt) {
        if (graph == null) {
            return null;
        }
        if (sortIt == null) {
            return graph.getContours(max);
        }
        if (max == null) {
            return graph.getContours();
        }
        return graph.getContours(max, sortIt);
    }

    public List<MatOfPoint> getContours(int max) {
        return getContours(max, null);
    }

    public List<MatOfPoint> getContours() {
        return getContours(null, null);
    }

    public Mat getDescriptors() {
        return mDescriptors;
    }

    public MatOfKeyPoint getKeyPoints() {
        return mKeyPoints;
    }

    public String getText() {
        return mText;
    }
}

class PathologicalMatcher {
    private Vector<PathologicalDescriptor> mBase;
    public PathologicalMatcher() {
        mBase = new Vector<PathologicalDescriptor>();
    }

    public void append(PathologicalDescriptor b) {
        mBase.add(b);
    }

    private double match(PathologicalDescriptor query, PathologicalDescriptor base) {

        return match(query.getKeyPoints().toList(), query.getDescriptors(),
                base.getKeyPoints().toList(), base.getDescriptors(), 0.7, 50);
    }

    private double match2(PathologicalDescriptor query, PathologicalDescriptor base) {

        return match2(query.getKeyPoints().toList(), query.getDescriptors(),
                base.getKeyPoints().toList(), base.getDescriptors());
    }

    private double matchContours(PathologicalDescriptor query, PathologicalDescriptor base) {
        List<MatOfPoint> queryContours = query.getContours(10);
        List<MatOfPoint> baseContours = base.getContours(10);
        int matchs_close = 0;
        int matchs = 0;
        int matchs_far = 0;
        double sum = 0.0;
        double sum_close = 0.0;
        int size = 0;
        for (int i = 0; i < queryContours.size(); i++) {
            for (int j = 0; j < baseContours.size(); j++) {
                double score = Imgproc.matchShapes(queryContours.get(i), baseContours.get(j),
                        Imgproc.CV_CONTOURS_MATCH_I1, 0);
                //Log.d(ResultEkgActivity.TAG, String.format("[%d,%d]Score: %f", i, j, score));
                if (score <= 0.0) {
                    matchs++;
                } else if (score > 0.0 && score < 1.0) {
                    matchs_close++;
                    sum_close += score;
                } else if (score > 10.0) {
                    matchs_far++;
                }
                size++;
                sum += score;

            }
        }
        double mean = -1.0;
        double score = -1.0;
        double mean_close = -1.0;
        if (size > 0) {
            mean = sum / size;
            score = ((double)matchs_close) / size;
            if (matchs_close > 0) {
                mean_close = sum_close / matchs_close;
            }
            /* has equals? impossible: penalty 5% for each, max: 50%*/
            score = score * (1 - (0.05 * (matchs<10?matchs:10)));

            /* mean elevated */
            score = score * (mean < 10 ? 1.0 : 0.5);
        }
        Log.d(ResultEkgActivity.TAG,
                String.format("Size: %d, Matchs: %d, close: %d, far: %d, Mean %f, close: %f, score: %f",
                        size, matchs, matchs_close, matchs_far, mean, mean_close, score));

        return score;
    }

    public List<PathologicalResult> search(PathologicalDescriptor query) {
        Vector<PathologicalResult> result = new Vector<PathologicalResult>();
        for (PathologicalDescriptor pathology: mBase) {
            double score = matchContours(query, pathology);
            result.add(new PathologicalResult(pathology.getText(), score));
        }
        Collections.sort(result);
        Collections.reverse(result);
        return result;
    }

    private double match2(List<KeyPoint> keyPointsQuery, Mat descriptionsQuery,
                          List<KeyPoint> keyPointsBase, Mat descriptionsBase) {
        DescriptorMatcher descriptorMatcher =
                DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        MatOfDMatch matches = new MatOfDMatch();
        descriptorMatcher.match(descriptionsQuery, descriptionsBase, matches);

        List<DMatch> matchesList = matches.toList();

        if (matchesList.size() < 4) {
            return -1.0;
        }

        double maxDist = 0.0;
        double minDist = Double.MAX_VALUE;

        /* calculate distances */
        for (DMatch match: matchesList) {
            double dist = match.distance;
            if (dist < minDist) {
                minDist = dist;
            }
            if (dist > maxDist) {
                maxDist = dist;
            }
        }

        if (minDist > 25.0) {
            return -1.0;
        }

        /* good points */
        ArrayList<Point> goodQueryPointsList = new ArrayList<Point>();
        ArrayList<Point> goodBasePointsList = new ArrayList<Point>();

        double maxGoodMatch = 1.75 * minDist;

        for (DMatch match: matchesList) {
            if (match.distance < maxGoodMatch) {
                goodQueryPointsList.add(keyPointsQuery.get(match.queryIdx).pt);
                goodBasePointsList.add(keyPointsBase.get(match.trainIdx).pt);
            }
        }

        if (goodQueryPointsList.size() < 4 || goodBasePointsList.size() < 4) {
            return -1.0;
        }

        MatOfPoint2f queryPoints = new MatOfPoint2f();
        queryPoints.fromList(goodQueryPointsList);

        MatOfPoint2f basePoints = new MatOfPoint2f();
        basePoints.fromList(goodBasePointsList);

        Mat status = new Mat();
        Calib3d.findHomography(basePoints, queryPoints, Calib3d.RANSAC, 4.0, status);

        return Core.sumElems(status).val[0]/status.total();
    }

    private double match(List<KeyPoint> keyPointsQuery, Mat descriptionsQuery,
                         List<KeyPoint> keyPointsBase, Mat descriptionsBase,
                         double ratio, int minMatches) {
        DescriptorMatcher descriptorMatcher =
                DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

        Vector<MatOfDMatch> rawMatches = new Vector<MatOfDMatch>();
        Vector<Point> matchesQueryPoints = new Vector<Point>();
        Vector<Point> matchesTrainPoints = new Vector<Point>();

        //descriptorMatcher.knnMatch(descriptionsBase, descriptionsQuery, rawMatches, 2);

        for (MatOfDMatch m: rawMatches) {
            List<DMatch> l = m.toList();
            if (l.size() == 2 && l.get(0).distance < l.get(1).distance * ratio) {
                // ptsA
                matchesQueryPoints.add(keyPointsQuery.get(l.get(0).trainIdx).pt);
                // ptsB
                matchesTrainPoints.add(keyPointsBase.get(l.get(0).queryIdx).pt);
            }
        }

        if (matchesQueryPoints.size() > minMatches) {
            MatOfPoint2f ptsQuery = new MatOfPoint2f();
            ptsQuery.fromList(matchesQueryPoints);

            MatOfPoint2f ptsTrain = new MatOfPoint2f();
            ptsQuery.fromList(matchesTrainPoints);

            Mat status = new Mat();
            Calib3d.findHomography(ptsQuery, ptsTrain, Calib3d.RANSAC, 4.0, status);

            return Core.sumElems(status).val[0]/status.total();
        }
        return -1.0;
    }
}

/* http://stackoverflow.com/questions/2605555/android-accessing-assets-folder-sqlite-database-file-with-sqlite-extension# */
/* http://www.xatik.com/2012/03/19/android-preloaded-database/ */
class DbAdapter extends SQLiteOpenHelper {

    private String DATABASE_PATH = "/data/data/YOUR_PACKAGE/";
    public static final String DATABASE_NAME = "pathological.db";

    private SQLiteDatabase mDb;

    private final Context mContext;

    private boolean mCreateDatabase = false;
    private boolean mUpgradeDatabase = false;

    /**
     * Constructor
     * Takes and keeps a reference of the passed context in order to access
     * the application's assets and resources
     * @param context
     */
    public DbAdapter(Context context) {
        super(context, DATABASE_NAME, null, 1);

        mUpgradeDatabase = true; /* always delete */
        mContext = context;
    }

    public void initializeDatabase() {
        DATABASE_PATH = mContext.getApplicationInfo().dataDir +"/databases/";
        getWritableDatabase();

        if(mUpgradeDatabase) {
            mContext.deleteDatabase(DATABASE_NAME);
        }

        if(mCreateDatabase || mUpgradeDatabase) {
            try {
                copyDatabase();
            } catch (IOException e) {
                throw new Error("Error copying database");
            }
        }
    }

    private void copyDatabase() throws IOException {
        close();

        InputStream input = mContext.getAssets().open(DATABASE_NAME);

        String outFileName = DATABASE_PATH + DATABASE_NAME;

        OutputStream output = new FileOutputStream(outFileName);

        // Transfer bytes from the input file to the output file
        byte[] buffer = new byte[1024];
        int length;
        while ((length = input.read(buffer)) > 0) {
            output.write(buffer, 0, length);
        }

        output.flush();
        output.close();
        input.close();

        getWritableDatabase().close();
    }

    public DbAdapter open() throws SQLException {
        mDb = getReadableDatabase();
        return this;
    }

    public void CleanUp() {
        mDb.close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        mCreateDatabase = true;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        mUpgradeDatabase = true;
    }

    /**
     * Public helper methods
     */

    public PathologicalMatcher getMatcher(String derivation) {
        PathologicalMatcher matcher = new PathologicalMatcher();

        /* support locales */
        String lang = mContext.getResources().getConfiguration().locale.getLanguage();
        String column_desc = "description";
        if (lang.compareTo("pt") == 0) {
            column_desc = "description_pt";
        } else if (lang.compareTo("es") == 0) {
            column_desc = "description_es";
        }
        Cursor c = mDb.rawQuery(String.format("SELECT %s,image FROM pathology WHERE derivation = ?", column_desc),
                new String[] { derivation });
        if (c == null) {
            return matcher;
        }

        while (c.moveToNext()) {
            String desc = c.getString(0);
            byte[] blob = c.getBlob(1);
            /* save to file */

            try
            {
                File outputDir = mContext.getCacheDir(); // context being the Activity pointer
                File blobFile = File.createTempFile("temp", ".png", outputDir);

                FileOutputStream outStream  = new FileOutputStream(blobFile);
                InputStream inStream = new ByteArrayInputStream(blob);

                int     length  = -1;
                int     size    = blob.length;
                byte[]  buffer  = new byte[size];

                while ((length = inStream.read(buffer)) != -1)
                {
                    outStream.write(buffer, 0, length);
                    outStream.flush();
                }

                inStream.close();
                outStream.close();

                PathologicalDescriptor patho = new PathologicalDescriptor(desc);
                patho.describe(blobFile.getAbsolutePath());

                matcher.append(patho);
            }
            catch (Exception e)
            {
                Log.d(ResultEkgActivity.TAG, "Error while opening database", e);
            }
            finally
            {
            }
        };


        return matcher;
    }
}