package io.github.echotrigger.chatroam;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebChromeClient;

/** Handles local attachment links opened with target="_blank". */
final class AttachmentPreviewChromeClient extends BridgeWebChromeClient {
    private final MainActivity activity;
    private final Bridge bridge;
    private final String attachmentPrefix;
    private WebView popup;
    private Dialog preview;

    AttachmentPreviewChromeClient(MainActivity activity, Bridge bridge) {
        super(bridge);
        this.activity = activity;
        this.bridge = bridge;
        Uri appUrl = Uri.parse(bridge.getAppUrl());
        attachmentPrefix = "blob:" + appUrl.getScheme() + "://" + appUrl.getEncodedAuthority() + "/";
    }

    @Override
    public boolean onCreateWindow(WebView opener, boolean isDialog, boolean userGesture, Message result) {
        if (!userGesture || preview != null || !(result.obj instanceof WebView.WebViewTransport transport)) {
            return false;
        }
        close();

        // Chromium delivers the clicked blob URL through the new window's navigation.
        WebView child = new WebView(activity);
        popup = child;
        child.setVisibility(View.INVISIBLE);
        child.getSettings().setAllowFileAccess(false);
        child.getSettings().setAllowContentAccess(false);
        child.getSettings().setBlockNetworkLoads(true);
        child.getSettings().setBuiltInZoomControls(true);
        child.getSettings().setDisplayZoomControls(false);
        child.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.isForMainFrame() || handleNavigation(opener, view, request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (handleNavigation(opener, view, Uri.parse(url))) {
                    view.stopLoading();
                }
            }
        });

        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(child, 1, 1);
        transport.setWebView(child);
        result.sendToTarget();
        return true;
    }

    private boolean handleNavigation(WebView opener, WebView child, Uri url) {
        if (child != popup) {
            return true;
        }
        if (url.toString().startsWith(attachmentPrefix)) {
            if (preview == null) {
                showPreview(child);
            }
            return false;
        }
        if (preview != null) {
            return true;
        }
        if ("about:blank".equals(url.toString())) {
            return false;
        }

        // Use the same Capacitor routing for normal web links as the chat WebView.
        String scheme = url.getScheme();
        if ("https".equals(scheme) || "http".equals(scheme) || "mailto".equals(scheme)) {
            if (!bridge.launchIntent(url)) {
                opener.loadUrl(url.toString());
            }
        }
        child.post(() -> {
            if (child == popup && preview == null) {
                close();
            }
        });
        return true;
    }

    private void showPreview(WebView child) {
        ((ViewGroup) child.getParent()).removeView(child);
        child.setVisibility(View.VISIBLE);

        Dialog dialog = new Dialog(activity);
        preview = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        Button closeButton = new Button(activity);
        closeButton.setText(R.string.close_attachment_preview);
        closeButton.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams closeLayout = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        closeLayout.gravity = Gravity.END;
        content.addView(closeButton, closeLayout);
        content.addView(child, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        dialog.setContentView(content);
        dialog.setOnDismissListener(ignored -> {
            if (dialog == preview) {
                preview = null;
                destroyPopup();
            }
        });
        dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    void close() {
        if (preview != null) {
            Dialog dialog = preview;
            preview = null;
            dialog.dismiss();
        }
        destroyPopup();
    }

    private void destroyPopup() {
        if (popup == null) {
            return;
        }
        WebView child = popup;
        popup = null;
        child.stopLoading();
        if (child.getParent() instanceof ViewGroup parent) {
            parent.removeView(child);
        }
        child.destroy();
    }
}
