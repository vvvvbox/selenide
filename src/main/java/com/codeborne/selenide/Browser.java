package com.codeborne.selenide;

import static com.codeborne.selenide.Configuration.browser;
import static com.codeborne.selenide.WebDriverRunner.*;
import static org.openqa.selenium.remote.BrowserType.IE;

public class Browser {
  public final String name;

  public Browser(String name) {
    this.name = name;
  }

  public boolean isHeadless() {
    return isHtmlUnit() || isPhantomjs();
  }

  public boolean isChrome() {
    return CHROME.equalsIgnoreCase(name);
  }

  public boolean isFirefox() {
    return FIREFOX.equalsIgnoreCase(browser);
  }

  public boolean isLegacyFirefox() {
    return LEGACY_FIREFOX.equalsIgnoreCase(browser);
  }

  public boolean isIE() {
    return INTERNET_EXPLORER.equalsIgnoreCase(name) || IE.equalsIgnoreCase(name);
  }

  public boolean isEdge() {
    return EDGE.equalsIgnoreCase(name);
  }

  public boolean isSafari() {
    return SAFARI.equalsIgnoreCase(name);
  }

  public boolean isHtmlUnit() {
    return name != null && name.startsWith(HTMLUNIT);
  }

  public boolean supportsModalDialogs() {
    return !isHeadless() && !isSafari() || isHtmlUnit();
  }
}
