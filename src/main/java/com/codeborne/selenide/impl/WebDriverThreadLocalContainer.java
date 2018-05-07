package com.codeborne.selenide.impl;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.DefaultModule;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideDriver;
import com.codeborne.selenide.proxy.SelenideProxyServer;
import com.codeborne.selenide.webdriver.WebDriverFactory;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.openqa.selenium.support.events.EventFiringWebDriver;
import org.openqa.selenium.support.events.WebDriverEventListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static com.codeborne.selenide.Configuration.FileDownloadMode.PROXY;
import static com.codeborne.selenide.Configuration.*;
import static com.codeborne.selenide.impl.Describe.describe;
import static java.lang.Thread.currentThread;
import static java.util.logging.Level.FINE;

public class WebDriverThreadLocalContainer implements WebDriverContainer {
  private static final Logger log = Logger.getLogger(WebDriverThreadLocalContainer.class.getName());

  protected WebDriverFactory factory = new WebDriverFactory();

  protected List<WebDriverEventListener> listeners = new ArrayList<>();
  protected Collection<Thread> ALL_WEB_DRIVERS_THREADS = new ConcurrentLinkedQueue<>();
  protected Map<Long, SelenideDriver> THREAD_WEB_DRIVER = new ConcurrentHashMap<>(4);
  protected Proxy proxy;

  protected final AtomicBoolean cleanupThreadStarted = new AtomicBoolean(false);

  protected void closeUnusedWebdrivers() {
    for (Thread thread : ALL_WEB_DRIVERS_THREADS) {
      if (!thread.isAlive()) {
        log.info("Thread " + thread.getId() + " is dead. Let's close webdriver " + THREAD_WEB_DRIVER.get(thread.getId()));
        closeWebDriver(thread);
      }
    }
  }

  @Override
  public void addListener(WebDriverEventListener listener) {
    listeners.add(listener);
  }

  @Override
  public WebDriver setWebDriver(WebDriver webDriver) {
    SelenideDriver selenideDriver = new SelenideDriver(new LegacyConf(), new DefaultModule());
    selenideDriver.setWebDriver(webDriver);
    setDriver(selenideDriver);
    return webDriver;
  }

  private SelenideDriver setDriver(SelenideDriver selenideDriver) {
    THREAD_WEB_DRIVER.put(currentThread().getId(), selenideDriver);
    return selenideDriver;
  }

  @Override
  public void setProxy(Proxy webProxy) {
    proxy = webProxy;
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

  /**
   * @return true iff webdriver is started in current thread
   */
  @Override
  public boolean hasWebDriverStarted() {
    return THREAD_WEB_DRIVER.containsKey(currentThread().getId());
  }

  @Override
  public WebDriver getWebDriver() {
    SelenideDriver webDriver = THREAD_WEB_DRIVER.get(currentThread().getId());
    if (webDriver != null) {
      return webDriver.getWebDriver();
    }

    log.info("No webdriver is bound to current thread: " + currentThread().getId() + " - let's create new webdriver");
    return setDriver(createDriver()).getWebDriver();
  }

  @Override
  public WebDriver getAndCheckWebDriver() {
    SelenideDriver webDriver = THREAD_WEB_DRIVER.get(currentThread().getId());
    if (webDriver != null) {
      if (!reopenBrowserOnFail || isBrowserStillOpen(webDriver.getWebDriver())) {
        return webDriver.getWebDriver();
      }
      else {
        log.info("Webdriver has been closed meanwhile. Let's re-create it.");
        closeWebDriver();
      }
    }
    return setDriver(createDriver()).getWebDriver();
  }

  @Override
  public SelenideProxyServer getProxyServer() {
    SelenideDriver webDriver = THREAD_WEB_DRIVER.get(currentThread().getId());
    return webDriver.getProxy();
  }

  @Override
  public void closeWebDriver() {
    closeWebDriver(currentThread());
  }

  protected void closeWebDriver(Thread thread) {
    ALL_WEB_DRIVERS_THREADS.remove(thread);
    SelenideDriver webdriver = THREAD_WEB_DRIVER.remove(thread.getId());

    if (webdriver != null && !holdBrowserOpen) {
      log.info("Close webdriver: " + thread.getId() + " -> " + webdriver);
      if (proxy != null) {
        log.info("Close proxy server: " + thread.getId() + " -> " + proxy);
      }

      long start = System.currentTimeMillis();

      Thread t = new Thread(new CloseBrowser(webdriver));
      t.setDaemon(true);
      t.start();

      try {
        t.join(closeBrowserTimeoutMs);
      } catch (InterruptedException e) {
        log.log(FINE, "Failed to close webdriver in " + closeBrowserTimeoutMs + " milliseconds", e);
      }

      long duration = System.currentTimeMillis() - start;
      if (duration >= closeBrowserTimeoutMs) {
        log.severe("Failed to close webdriver in " + closeBrowserTimeoutMs + " milliseconds");
      }
      else {
        log.info("Closed webdriver in " + duration + " ms");
      }
    }
  }

  private static class CloseBrowser implements Runnable {
    private final SelenideDriver driver;

    private CloseBrowser(SelenideDriver driver) {
      this.driver = driver;
    }

    @Override
    public void run() {
      driver.close();
    }
  }

  @Override
  public void clearBrowserCache() {
    SelenideDriver webdriver = THREAD_WEB_DRIVER.get(currentThread().getId());
    if (webdriver != null) {
      webdriver.deleteAllCookies();
    }
  }

  @Override
  public String getPageSource() {
    return getWebDriver().getPageSource();
  }

  @Override
  public String getCurrentUrl() {
    return getWebDriver().getCurrentUrl();
  }

  @Override
  public String getCurrentFrameUrl() {
    return ((JavascriptExecutor) getWebDriver()).executeScript("return window.location.href").toString();
  }

  protected SelenideDriver createDriver() {
    Proxy userProvidedProxy = proxy;
    SelenideDriver selenideDriver = new SelenideDriver(new LegacyConf(), new DefaultModule());

    if (Configuration.fileDownload == PROXY) {
      SelenideProxyServer selenideProxyServer = new SelenideProxyServer(proxy);
      selenideProxyServer.start();
      selenideDriver.setProxy(selenideProxyServer);
      userProvidedProxy = selenideProxyServer.createSeleniumProxy();
    }

    WebDriver webdriver = factory.createWebDriver(userProvidedProxy);
    addListeners(webdriver);

    log.info("Create webdriver in current thread " + currentThread().getId() + ": " +
            describe(webdriver) + " -> " + webdriver);

    selenideDriver.setWebDriver(webdriver);

    markForAutoClose();
    return selenideDriver;
  }

  protected void addListeners(WebDriver webdriver) {
    if (!listeners.isEmpty()) {
      EventFiringWebDriver wrapper = new EventFiringWebDriver(webdriver);
      for (WebDriverEventListener listener : listeners) {
        log.info("Add listener to webdriver: " + listener);
        wrapper.register(listener);
      }
    }
  }

  protected void markForAutoClose() {
    ALL_WEB_DRIVERS_THREADS.add(currentThread());

    if (!cleanupThreadStarted.get()) {
      synchronized (this) {
        if (!cleanupThreadStarted.get()) {
          new UnusedWebdriversCleanupThread().start();
          cleanupThreadStarted.set(true);
        }
      }
    }
    Runtime.getRuntime().addShutdownHook(new WebdriversFinalCleanupThread(currentThread()));
  }

  protected class WebdriversFinalCleanupThread extends Thread {
    private final Thread thread;

    public WebdriversFinalCleanupThread(Thread thread) {
      this.thread = thread;
    }

    @Override
    public void run() {
      closeWebDriver(thread);
    }
  }

  protected class UnusedWebdriversCleanupThread extends Thread {
    public UnusedWebdriversCleanupThread() {
      setDaemon(true);
      setName("Webdrivers killer thread");
    }

    @Override
    public void run() {
      while (true) {
        closeUnusedWebdrivers();
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
  }
}
