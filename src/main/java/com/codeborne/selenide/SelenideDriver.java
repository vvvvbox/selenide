package com.codeborne.selenide;

import com.codeborne.selenide.ex.JavaScriptErrorsFound;
import com.codeborne.selenide.impl.Navigator;
import com.codeborne.selenide.impl.ScreenShotLaboratory;
import com.codeborne.selenide.impl.SelenideFieldDecorator;
import org.openqa.selenium.JavascriptExecutor;

import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.codeborne.selenide.Configuration.captureJavascriptErrors;
import static com.codeborne.selenide.Configuration.dismissModalDialogs;
import static com.codeborne.selenide.Configuration.timeout;
import static com.codeborne.selenide.WebDriverRunner.*;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class SelenideDriver {
  private static final Logger log = Logger.getLogger(SelenideDriver.class.getName());

  @Inject
  private ScreenShotLaboratory screenshots;
  @Inject
  private Navigator navigator;

  public void open(String relativeOrAbsoluteUrl) {
    open(relativeOrAbsoluteUrl, "", "", "");
  }

  public void open(URL absoluteUrl) {
    open(absoluteUrl, "", "", "");
  }

  public void open(String relativeOrAbsoluteUrl, String domain, String login, String password) {
    navigator.open(relativeOrAbsoluteUrl, domain, login, password);
    mockModalDialogs();
  }

  public void open(URL absoluteUrl, String domain, String login, String password) {
    navigator.open(absoluteUrl, domain, login, password);
    mockModalDialogs();
  }

  public <PageObjectClass> PageObjectClass open(String relativeOrAbsoluteUrl,
                                                Class<PageObjectClass> pageObjectClassClass) {
    return open(relativeOrAbsoluteUrl, "", "", "", pageObjectClassClass);
  }

  public <PageObjectClass> PageObjectClass open(URL absoluteUrl,
                                                Class<PageObjectClass> pageObjectClassClass) {
    return open(absoluteUrl, "", "", "", pageObjectClassClass);
  }

  public <PageObjectClass> PageObjectClass open(String relativeOrAbsoluteUrl,
                                                       String domain, String login, String password,
                                                       Class<PageObjectClass> pageObjectClassClass) {
    open(relativeOrAbsoluteUrl, domain, login, password);
    return page(pageObjectClassClass);
  }

  public <PageObjectClass> PageObjectClass open(URL absoluteUrl, String domain, String login, String password,
                                                       Class<PageObjectClass> pageObjectClassClass) {
    open(absoluteUrl, domain, login, password);
    return page(pageObjectClassClass);
  }

  public <PageObjectClass> PageObjectClass page(Class<PageObjectClass> pageObjectClass) {
    try {
      Constructor<PageObjectClass> constructor = pageObjectClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      return page(constructor.newInstance());
    } catch (Exception e) {
      throw new RuntimeException("Failed to create new instance of " + pageObjectClass, e);
    }
  }

  public <PageObjectClass, T extends PageObjectClass> PageObjectClass page(T pageObject) {
    SelenidePageFactory.initElements(new SelenideFieldDecorator(getWebDriver()), pageObject);
    return pageObject;
  }

  public void close() {
    closeWebDriver();
  }

  public void updateHash(String hash) {
    String localHash = (hash.charAt(0) == '#') ? hash.substring(1) : hash;
    executeJavaScript("window.location.hash='" + localHash + "'");
  }

  private void mockModalDialogs() {
    if (doDismissModalDialogs()) {
      String jsCode =
          "  window._selenide_modalDialogReturnValue = true;\n" +
              "  window.alert = function(message) {};\n" +
              "  window.confirm = function(message) {\n" +
              "    return window._selenide_modalDialogReturnValue;\n" +
              "  };";
      try {
        executeJavaScript(jsCode);
      } catch (UnsupportedOperationException cannotExecuteJsAgainstPlainTextPage) {
        log.warning(cannotExecuteJsAgainstPlainTextPage.toString());
      }
    }
  }

  private boolean doDismissModalDialogs() {
    return !supportsModalDialogs() || dismissModalDialogs;
  }

  @SuppressWarnings("unchecked")
  public <T> T executeJavaScript(String jsCode, Object... arguments) {
    return (T) ((JavascriptExecutor) getWebDriver()).executeScript(jsCode, arguments);
  }

  public void refresh() {
    navigator.open(url());
  }

  public void back() {
    navigator.back();
  }

  public void forward() {
    navigator.forward();
  }

  public String screenshot(String fileName) {
    return screenshots.takeScreenShot(fileName);
  }

  public String title() {
    return getWebDriver().getTitle();
  }

  public FluentWait<WebDriver> Wait() {
    return new FluentWait<>(getWebDriver())
        .withTimeout(timeout, MILLISECONDS)
        .pollingEvery(Configuration.pollingInterval, MILLISECONDS);
  }

  public Actions actions() {
    return new Actions(getWebDriver());
  }

  public List<String> getJavascriptErrors() {
    if (!captureJavascriptErrors) {
      return emptyList();
    }
    else if (!hasWebDriverStarted()) {
      return emptyList();
    }
    else if (!supportsJavascript()) {
      return emptyList();
    }
    try {
      Object errors = executeJavaScript("return window._selenide_jsErrors");
      if (errors == null) {
        return emptyList();
      }
      else if (errors instanceof List) {
        return errorsFromList((List<Object>) errors);
      }
      else if (errors instanceof Map) {
        return errorsFromMap((Map<Object, Object>) errors);
      }
      else {
        return asList(errors.toString());
      }
    } catch (WebDriverException | UnsupportedOperationException cannotExecuteJs) {
      log.warning(cannotExecuteJs.toString());
      return emptyList();
    }
  }

  private static List<String> errorsFromList(List<Object> errors) {
    if (errors.isEmpty()) {
      return emptyList();
    }
    List<String> result = new ArrayList<>(errors.size());
    for (Object error : errors) {
      result.add(error.toString());
    }
    return result;
  }

  private static List<String> errorsFromMap(Map<Object, Object> errors) {
    if (errors.isEmpty()) {
      return emptyList();
    }
    List<String> result = new ArrayList<>(errors.size());
    for (Map.Entry error : errors.entrySet()) {
      result.add(error.getKey() + ": " + error.getValue());
    }
    return result;
  }

  public void assertNoJavascriptErrors() throws JavaScriptErrorsFound {
    List<String> jsErrors = getJavascriptErrors();
    if (jsErrors != null && !jsErrors.isEmpty()) {
      throw new JavaScriptErrorsFound(jsErrors);
    }
  }

  public void zoom(double factor) {
    executeJavaScript(
        "document.body.style.transform = 'scale(' + arguments[0] + ')';" +
            "document.body.style.transformOrigin = '0 0';",
        factor
    );
  }

  public List<String> getWebDriverLogs(String logType) {
    return getWebDriverLogs(logType, Level.ALL);
  }

  public List<String> getWebDriverLogs(String logType, Level logLevel) {
    return listToString(getLogEntries(logType, logLevel));
  }

  private static List<LogEntry> getLogEntries(String logType, Level logLevel) {
    try {
      return getWebDriver().manage().logs().get(logType).filter(logLevel);
    }
    catch (UnsupportedOperationException ignore) {
      return emptyList();
    }
  }

  private static <T> List<String> listToString(List<T> objects) {
    if (objects == null || objects.isEmpty()) {
      return emptyList();
    }
    List<String> result = new ArrayList<>(objects.size());
    for (T object : objects) {
      result.add(object.toString());
    }
    return result;
  }

  public void clearBrowserCookies() {
    getWebDriver().manage().deleteAllCookies();
  }

  public void clearBrowserLocalStorage() {
    executeJavaScript("localStorage.clear();");
  }

  public String getUserAgent() {
    return executeJavaScript("return navigator.userAgent;");
  }
}
