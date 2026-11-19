package io.github.echotrigger.chatroam;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private AttachmentPreviewChromeClient attachmentPreview;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RoamApiKeyPlugin.class);
        super.onCreate(savedInstanceState);
        if (bridge != null) {
            attachmentPreview = new AttachmentPreviewChromeClient(this, bridge);
            bridge.getWebView().setWebChromeClient(attachmentPreview);
            bridge.getWebView().getSettings().setSupportMultipleWindows(true);
        }
    }

    @Override
    public void onDestroy() {
        if (attachmentPreview != null) {
            attachmentPreview.close();
        }
        super.onDestroy();
    }
}
