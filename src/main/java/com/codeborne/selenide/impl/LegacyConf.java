package com.codeborne.selenide.impl;

import com.codeborne.selenide.Conf;
import com.codeborne.selenide.Configuration;

public class LegacyConf implements Conf {
  @Override
  public String browser() {
    return Configuration.browser;
  }

  @Override
  public long timeout() {
    return Configuration.timeout;
  }

  @Override
  public long pollingInterval() {
    return Configuration.pollingInterval;
  }

  @Override
  public boolean dismissModalDialogs() {
    return Configuration.dismissModalDialogs;
  }

  @Override
  public boolean captureJavascriptErrors() {
    return Configuration.captureJavascriptErrors;
  }

  @Override
  public Configuration.FileDownloadMode fileDownload() {
    return Configuration.fileDownload;
  }
}
