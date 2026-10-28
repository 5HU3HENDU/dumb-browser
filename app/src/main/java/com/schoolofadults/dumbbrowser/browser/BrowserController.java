package com.schoolofadults.dumbbrowser.browser;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

import com.schoolofadults.dumbbrowser.database.Record;
import com.schoolofadults.dumbbrowser.view.AdapterRecord;

public interface BrowserController {
    void updateProgress(int progress);
    void showAlbum(AlbumController albumController);
    void removeAlbum(AlbumController albumController);

    void showFileChooser(Context context, ValueCallback<Uri[]> filePathCallback);

    void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback);
    void hideOverview();
    void hideOverflow();
    void hideSearch();
    void onHideCustomView();
    void showDialogFastToggle(String title, String url, FloatingActionButton floatingActionButton);
    void setProfileIcon (FloatingActionButton floatingActionButton, String url);
    void showOverflow(Dialog dialog, View view, int hideMenu, String title, String url, final AdapterRecord adapterRecord, List<Record> recordList, int location);
}