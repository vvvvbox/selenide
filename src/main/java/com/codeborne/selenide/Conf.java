package com.codeborne.selenide;

public interface Conf {
  String browser();
  long timeout();
  long pollingInterval();

  boolean dismissModalDialogs();
  boolean captureJavascriptErrors();
}
