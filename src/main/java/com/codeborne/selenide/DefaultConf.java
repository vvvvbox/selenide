package com.codeborne.selenide;

import com.codeborne.selenide.Configuration.FileDownloadMode;

public class DefaultConf implements Conf {
  protected String browser;
  protected long timeout;
  protected long pollingInterval;
  protected boolean dismissModalDialogs;
  protected boolean captureJavascriptErrors;
  protected FileDownloadMode fileDownload;

  @Override
  public String browser() {
    return browser;
  }

  @Override
  public long timeout() {
    return timeout;
  }

  @Override
  public long pollingInterval() {
    return pollingInterval;
  }

  @Override
  public boolean dismissModalDialogs() {
    return dismissModalDialogs;
  }

  @Override
  public boolean captureJavascriptErrors() {
    return captureJavascriptErrors;
  }

  @Override
  public FileDownloadMode fileDownload() {
    return fileDownload;
  }
}
