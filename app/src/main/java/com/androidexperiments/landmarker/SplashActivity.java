package com.androidexperiments.landmarker;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.androidexperiments.landmarker.widget.InfoView;
import com.google.creativelabs.androidexperiments.typecompass.R;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

/**
 * Splash shit
 */
public class SplashActivity extends BaseActivity
{
    private static final String TAG = SplashActivity.class.getSimpleName();

    @InjectView(R.id.splash_info_view) InfoView mInfoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_splash);

        ButterKnife.inject(this);

        mInfoView.setVisibility(View.GONE);
    }

    @OnClick(R.id.btn_begin)
    public void onBeginClick()
    {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);

        this.finish();
    }

    @OnClick(R.id.btn_view_tutorial)
    public void onViewTutorialClick()
    {
        Intent intent = new Intent(this, TutorialActivity.class);
        startActivity(intent);

        this.finish();
    }

    @OnClick(R.id.btn_info)
    public void onInfoClick() {
        mInfoView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if(mInfoView.getVisibility() == View.VISIBLE)
        {
            if(mInfoView.isWebViewShowing())
                mInfoView.hideWebView();
            else
                mInfoView.setVisibility(View.GONE);
        }
        else
            super.onBackPressed();
    }
}
