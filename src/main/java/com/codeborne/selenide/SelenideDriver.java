package com.codeborne.selenide;

import com.codeborne.selenide.ex.DialogTextMismatch;
import com.codeborne.selenide.ex.JavaScriptErrorsFound;
import com.codeborne.selenide.impl.BySelectorCollection;
import com.codeborne.selenide.impl.Cleanup;
import com.codeborne.selenide.impl.ElementFinder;
import com.codeborne.selenide.impl.Navigator;
import com.codeborne.selenide.impl.ScreenShotLaboratory;
import com.codeborne.selenide.impl.SelenideFieldDecorator;
import com.codeborne.selenide.impl.WebElementsCollectionWrapper;
import com.codeborne.selenide.inject.Module;
import com.codeborne.selenide.proxy.SelenideProxyServer;
import com.codeborne.selenide.webdriver.WebDriverFactory;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.openqa.selenium.support.events.EventFiringWebDriver;
import org.openqa.selenium.support.events.WebDriverEventListener;
import org.openqa.selenium.support.ui.FluentWait;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.codeborne.selenide.Configuration.FileDownloadMode.PROXY;
import static com.codeborne.selenide.Configuration.reopenBrowserOnFail;
import static com.codeborne.selenide.impl.Describe.describe;
import static com.codeborne.selenide.impl.WebElementWrapper.wrap;
import static java.lang.Thread.currentThread;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.logging.Level.FINE;

public class SelenideDriver {
  private static final Logger log = Logger.getLogger(SelenideDriver.class.getName());

  private final Conf conf;
  private final Module module;
  private final ScreenShotLaboratory screenshots;
  private final Navigator navigator;
  private final WebDriverFactory factory;
  private final List<WebDriverEventListener> listeners = new ArrayList<>();
  private WebDriver webDriver;
  private SelenideProxyServer proxy;
  private Proxy userProvidedProxy;

  public SelenideDriver(Conf conf, Module module) {
    this.conf = conf;
    this.module = module;
    this.screenshots = module.instance(ScreenShotLaboratory.class);
    this.navigator = module.instance(Navigator.class);
    this.factory = module.instance(WebDriverFactory.class);
  }

  public void setWebDriver(WebDriver webDriver) {
    this.webDriver = webDriver;
  }

  public void setProxy(SelenideProxyServer proxy) {
    this.proxy = proxy;
  }

  public void setUserProvidedProxy(Proxy proxy) {
    this.userProvidedProxy = proxy;
  }

  public WebDriver getAndCheckWebDriver() {
    if (hasWebDriverStarted()) {
      if (!reopenBrowserOnFail || isBrowserStillOpen(webDriver)) {
        return webDriver;
      } else {
        log.info("Webdriver has been closed meanwhile. Let's re-create it.");
        close();
      }
    }
    createDriver();
    return webDriver;
  }

  protected boolean isBrowserStillOpen(WebDriver webDriver) {
    try {
      webDriver.getTitle();
      return true;
    } catch (UnreachableBrowserException e) {
      log.log(FINE, "Browser is unreachable", e);
      return false;
    } catch (NoSuchWindowException e) {
      log.log(FINE, "Browser window is not found", e);
      return false;
    } catch (NoSuchSessionException e) {
      log.log(FINE, "Browser session is not found", e);
      return false;
    }
  }

  public WebDriver getWebDriver() {
    if (!hasWebDriverStarted()) {
      throw new IllegalStateException("WebDriver not initialized. Open a page before calling any other methods.");
    }
    return webDriver;
  }

  public boolean hasWebDriverStarted() {
    return webDriver != null;
  }

  public SelenideProxyServer getProxy() {
    return proxy;
  }

  protected void createDriver() {
    Proxy seleniumProxy = userProvidedProxy;
    if (conf.fileDownload() == PROXY) {
      SelenideProxyServer selenideProxyServer = new SelenideProxyServer(userProvidedProxy);
      selenideProxyServer.start();
      setProxy(selenideProxyServer);
      seleniumProxy = selenideProxyServer.createSeleniumProxy();
    }

    WebDriver webdriver = factory.createWebDriver(seleniumProxy);
    addListeners(webdriver);

    log.info("Create webdriver in current thread " + currentThread().getId() + ": " +
      describe(webdriver) + " -> " + webdriver);

    setWebDriver(webdriver);
  }

  protected WebDriver addListeners(WebDriver webdriver) {
    if (listeners.isEmpty()) {
      return webdriver;
    }

    EventFiringWebDriver wrapper = new EventFiringWebDriver(webdriver);
    for (WebDriverEventListener listener : listeners) {
      log.info("Add listener to webdriver: " + listener);
      wrapper.register(listener);
    }
    return wrapper;
  }

  public void open(String relativeOrAbsoluteUrl) {
    navigator.open(this, relativeOrAbsoluteUrl);
  }

  public void open(URL absoluteUrl) {
    open(absoluteUrl, "", "", "");
  }

  public void open(String relativeOrAbsoluteUrl, String domain, String login, String password) {
    navigator.open(this, relativeOrAbsoluteUrl, domain, login, password);
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

  public void deleteAllCookies() {
    if (hasWebDriverStarted()) {
      getWebDriver().manage().deleteAllCookies();
    }
  }

  public void close() {
    if (webDriver != null) {
      try {
        log.info("Trying to close the browser " + describe(webDriver) + " ...");
        webDriver.quit();
      } catch (UnreachableBrowserException e) {
        // It happens for Firefox. It's ok: browser is already closed.
        log.log(FINE, "Browser is unreachable", e);
      } catch (WebDriverException cannotCloseBrowser) {
        log.severe("Cannot close browser normally: " + Cleanup.of.webdriverExceptionMessage(cannotCloseBrowser));
      }
      webDriver = null;
    }

    if (proxy != null) {
      log.info("Trying to shutdown " + proxy + " ...");
      proxy.shutdown();
      proxy = null;
    }
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

  public Browser browser() {
    return new Browser(conf.browser());
  }

  private boolean supportsModalDialogs() {
    return browser().supportsModalDialogs();
  }

  private boolean doDismissModalDialogs() {
    return !supportsModalDialogs() || conf.dismissModalDialogs();
  }

  @SuppressWarnings("unchecked")
  public <T> T executeJavaScript(String jsCode, Object... arguments) {
    return (T) ((JavascriptExecutor) getWebDriver()).executeScript(jsCode, arguments);
  }

  public void refresh() {
    navigator.open(url());
  }

  public String url() {
    return getWebDriver().getCurrentUrl();
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
        .withTimeout(Duration.of(conf.timeout(), MILLIS))
        .pollingEvery(Duration.of(conf.pollingInterval(), MILLIS));
  }

  public Actions actions() {
    return new Actions(getWebDriver());
  }

  public List<String> getJavascriptErrors() {
    if (!conf.captureJavascriptErrors()) {
      return emptyList();
    }
    else if (!hasWebDriverStarted()) {
      return emptyList();
    }
    else if (!(webDriver instanceof JavascriptExecutor)) {
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

  private List<LogEntry> getLogEntries(String logType, Level logLevel) {
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

  public boolean atBottom() {
    return executeJavaScript("return window.pageYOffset + window.innerHeight >= document.body.scrollHeight");
  }
}
