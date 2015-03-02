package com.iuridiniz.checkmyecg;

import android.content.Intent;
import android.net.Uri;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.ShareActionProvider;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

public class ResultEkgActivity extends ActionBarActivity {

    public static final String EXTRA_PHOTO_URI =
            "com.iuridiniz.checkmyecg.ResultEkgActivity.extra.PHOTO_URI;";
    public static final String EXTRA_PHOTO_DATA_PATH =
            "com.iuridiniz.checkmyecg.ResultEkgActivity.extra.DATA_PATH;";

    private ShareActionProvider mShareActionProvider;
    private Uri mUri;
    private String mDataPath;
    private ImageView mImageContent;
    private TextView mTextContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result_ekg);

        final Intent intent = getIntent();
        mUri = intent.getParcelableExtra(EXTRA_PHOTO_URI);
        mDataPath = intent.getStringExtra(EXTRA_PHOTO_DATA_PATH);

        mImageContent = (ImageView) findViewById(R.id.result_image);
        mTextContent = (TextView) findViewById(R.id.result_text);

        mTextContent.setText(R.string.text_ekg_fail);

        mImageContent.setImageURI(mUri);
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
