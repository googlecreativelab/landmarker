package com.androidexperiments.landmarker.widget;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.creativelabs.androidexperiments.typecompass.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

/**
 * View with about info and licenses
 */
public class InfoView extends RelativeLayout {

    @InjectView(R.id.info_licenses_text_view)    TextView mLicensesTextView;
    @InjectView(R.id.info_licenses_web_view)    WebView mLicenseWebview;

    public InfoView(Context context) {
        super(context);
    }

    public InfoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InfoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        ButterKnife.inject(this, this);

        setupText();
        setupWebView();
    }

    private void setupWebView() {
        InputStream is = getResources().openRawResource(R.raw.licenses_landmarker);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder builder = new StringBuilder();
        String line = null;
        try {
            while((line = reader.readLine()) != null){
                builder.append(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mLicenseWebview.loadData(builder.toString(), "text/html", null);

        //is my system fucked? this wont work AT ALL yet works fine in
        //lipflip - maybe something with using schema from inside a view rather than
        //inside activity?
//        mLicenseWebview.loadUrl("file:///android_res/raw/licenses_landmarker.html");
    }

    private void setupText() {
        mLicensesTextView.setPaintFlags(mLicensesTextView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
    }

    @OnClick(R.id.info_close_btn)
    public void onClickClose() {
        this.setVisibility(GONE);
        hideWebView();
    }

    @OnClick(R.id.info_licenses_text_view)
    public void onClickLicensesButton() {
        mLicenseWebview.setVisibility(VISIBLE);
    }

    public boolean isWebViewShowing() {
        return mLicenseWebview.getVisibility() == VISIBLE;
    }

    public void hideWebView() {
        mLicenseWebview.setVisibility(GONE);
    }
}
