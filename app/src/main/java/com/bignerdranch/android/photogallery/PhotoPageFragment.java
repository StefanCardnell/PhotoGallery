package com.bignerdranch.android.photogallery;


import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class PhotoPageFragment extends VisibleFragment implements onBackPressed {
    private static final String ARG_URI = "photo_page_url";

    private static final String TAG = "PhotoPageFragment";

    private Uri mUri;
    private WebView mWebView;
    private ProgressBar mProgressBar;

    public static PhotoPageFragment newInstance(Uri uri) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, uri);

        PhotoPageFragment fragment = new PhotoPageFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUri = getArguments().getParcelable(ARG_URI);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_page, container, false);

        mProgressBar = v.findViewById(R.id.progress_bar);
        mProgressBar.setMax(100);

        mWebView = v.findViewById(R.id.web_view);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setWebChromeClient(new WebChromeClient(){
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if(newProgress == 100){
                    mProgressBar.setVisibility(View.GONE);
                } else {
                    mProgressBar.setVisibility(View.VISIBLE);
                    mProgressBar.setProgress(newProgress);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                activity.getSupportActionBar().setSubtitle(title);
            }
        });
        mWebView.setWebViewClient(new WebViewClient(){

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                Log.i(TAG, "Got URL: " + url);
                if(!uri.getScheme().startsWith("http")){
                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    PackageManager packageManager = getActivity().getPackageManager();
                    if(packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                        startActivity(i);
                    }
                    else{
                        Toast.makeText(getActivity(), "No available application", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                } else {
                    return false;
                }

            }
        });
        mWebView.loadUrl(mUri.toString());

        return v;
    }

    @Override
    public boolean onBackPress() {
        if(mWebView != null && mWebView.canGoBack()){
            mWebView.goBack();
            return true;
        }
        return false;
    }
}