package io.github.echotrigger.chatroam;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RoamApiKeyPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
