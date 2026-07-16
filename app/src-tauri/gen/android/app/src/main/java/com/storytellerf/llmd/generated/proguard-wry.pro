# THIS FILE IS AUTO-GENERATED. DO NOT MODIFY!!

# Copyright 2020-2023 Tauri Programme within The Commons Conservancy
# SPDX-License-Identifier: Apache-2.0
# SPDX-License-Identifier: MIT

-keep class com.storytellerf.llmd.* {
  native <methods>;
}

-keep class com.storytellerf.llmd.WryActivity {
  public <init>(...);

  void setWebView(com.storytellerf.llmd.RustWebView);
  java.lang.Class getAppClass(...);
  int getId();
  java.lang.String getVersion();
  int startActivity(...);
}

-keep class com.storytellerf.llmd.Ipc {
  public <init>(...);

  @android.webkit.JavascriptInterface public <methods>;
}

-keep class com.storytellerf.llmd.RustWebView {
  public <init>(...);

  void loadUrlMainThread(...);
  void loadHTMLMainThread(...);
  void evalScript(...);
}

-keep class com.storytellerf.llmd.RustWebChromeClient,com.storytellerf.llmd.RustWebViewClient {
  public <init>(...);
}
