package com.codeborne.selenide;

import com.codeborne.selenide.ex.DialogTextMismatch;
import com.codeborne.selenide.ex.JavaScriptErrorsFound;
import com.codeborne.selenide.impl.*;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.support.ui.FluentWait;

import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.codeborne.selenide.Configuration.captureJavascriptErrors;
import static com.codeborne.selenide.Configuration.dismissModalDialogs;
import static com.codeborne.selenide.Configuration.timeout;
import static com.codeborne.selenide.WebDriverRunner.*;
import static com.codeborne.selenide.impl.WebElementWrapper.wrap;
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

  public SelenideElement $(WebElement webElement) {
    return wrap(webElement);
  }

  public SelenideElement $(String cssSelector) {
    return getElement(By.cssSelector(cssSelector));
  }

  public SelenideElement $x(String xpathExpression) {
    return getElement(By.xpath(xpathExpression));
  }

  public SelenideElement $(By seleniumSelector) {
    return getElement(seleniumSelector);
  }

  public SelenideElement $(By seleniumSelector, int index) {
    return getElement(seleniumSelector, index);
  }

  public SelenideElement $(String cssSelector, int index) {
    return ElementFinder.wrap(null, By.cssSelector(cssSelector), index);
  }

  public ElementsCollection $$(Collection<? extends WebElement> elements) {
    return new ElementsCollection(new WebElementsCollectionWrapper(elements));
  }

  public ElementsCollection $$(String cssSelector) {
    return new ElementsCollection(new BySelectorCollection(By.cssSelector(cssSelector)));
  }

  public ElementsCollection $$x(String xpathExpression) {
    return new ElementsCollection(new BySelectorCollection(By.xpath(xpathExpression)));
  }

  public ElementsCollection $$(By seleniumSelector) {
    return new ElementsCollection(new BySelectorCollection(seleniumSelector));
  }

  public SelenideElement getElement(By criteria) {
    return ElementFinder.wrap(null, criteria, 0);
  }

  public SelenideElement getElement(By criteria, int index) {
    return ElementFinder.wrap(null, criteria, index);
  }

  public ElementsCollection getElements(By criteria) {
    return $$(criteria);
  }

  public SelenideElement getSelectedRadio(By radioField) {
    for (WebElement radio : $$(radioField)) {
      if (radio.getAttribute("checked") != null) {
        return wrap(radio);
      }
    }
    return null;
  }

  public void onConfirmReturn(boolean confirmReturnValue) {
    if (doDismissModalDialogs()) {
      executeJavaScript("window._selenide_modalDialogReturnValue = " + confirmReturnValue + ';');
    }
  }

  public String confirm() {
    return confirm(null);
  }

  public String confirm(String expectedDialogText) {
    if (!doDismissModalDialogs()) {
      Alert alert = switchTo().alert();
      String actualDialogText = alert.getText();
      alert.accept();
      checkDialogText(expectedDialogText, actualDialogText);
      return actualDialogText;
    }
    return null;
  }

  public String prompt() {
    return prompt(null, null);
  }

  public String prompt(String inputText) {
    return prompt(null, inputText);
  }

  public String prompt(String expectedDialogText, String inputText) {
    if (!doDismissModalDialogs()) {
      Alert alert = switchTo().alert();
      String actualDialogText = alert.getText();
      if (inputText != null)
        alert.sendKeys(inputText);
      alert.accept();
      checkDialogText(expectedDialogText, actualDialogText);
      return actualDialogText;
    }
    return null;
  }

  public String dismiss() {
    return dismiss(null);
  }

  public String dismiss(String expectedDialogText) {
    if (!doDismissModalDialogs()) {
      Alert alert = switchTo().alert();
      String actualDialogText = alert.getText();
      alert.dismiss();
      checkDialogText(expectedDialogText, actualDialogText);
      return actualDialogText;
    }
    return null;
  }

  private void checkDialogText(String expectedDialogText, String actualDialogText) {
    if (expectedDialogText != null && !expectedDialogText.equals(actualDialogText)) {
      Screenshots.takeScreenShot(Selenide.class.getName(), Thread.currentThread().getName());
      throw new DialogTextMismatch(actualDialogText, expectedDialogText);
    }
  }

  public SelenideTargetLocator switchTo() {
    return new SelenideTargetLocator(getWebDriver().switchTo());
  }

  public WebElement getFocusedElement() {
    return (WebElement) executeJavaScript("return document.activeElement");
  }
}
