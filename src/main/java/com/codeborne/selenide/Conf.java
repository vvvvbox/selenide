package com.codeborne.selenide;

import com.codeborne.selenide.Configuration.FileDownloadMode;

public interface Conf {
  String browser();
  long timeout();
  long pollingInterval();

  boolean dismissModalDialogs();
  boolean captureJavascriptErrors();
  FileDownloadMode fileDownload();
}
