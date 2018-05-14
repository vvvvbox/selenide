package com.codeborne.selenide.impl;

import com.codeborne.selenide.DefaultModule;
import com.codeborne.selenide.SelenideDriver;
import com.codeborne.selenide.proxy.SelenideProxyServer;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.events.WebDriverEventListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static com.codeborne.selenide.Configuration.closeBrowserTimeoutMs;
import static com.codeborne.selenide.Configuration.holdBrowserOpen;
import static java.lang.Thread.currentThread;
import static java.util.logging.Level.FINE;

public class WebDriverThreadLocalContainer implements WebDriverContainer {
  private static final Logger log = Logger.getLogger(WebDriverThreadLocalContainer.class.getName());

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

  /**
   * @return true iff webdriver is started in current thread
   */
  @Override
  public boolean hasWebDriverStarted() {
    SelenideDriver driver = THREAD_WEB_DRIVER.get(currentThread().getId());
    return driver != null && driver.hasWebDriverStarted();
  }

  @Override
  public WebDriver getWebDriver() {
    return getSelenideDriver().getWebDriver();
  }

  @Override
  public SelenideDriver getSelenideDriver() {
    SelenideDriver webDriver = THREAD_WEB_DRIVER.get(currentThread().getId());
    if (webDriver != null) {
      return webDriver;
    }

    log.info("No webdriver is bound to current thread: " + currentThread().getId() + " - let's create new webdriver");
    return setDriver(createDriver());
  }

  @Override
  public WebDriver getAndCheckWebDriver() {
    SelenideDriver selenideDriver = THREAD_WEB_DRIVER.get(currentThread().getId());
    if (selenideDriver == null) {
      selenideDriver = setDriver(createDriver());
    }
    return selenideDriver.getAndCheckWebDriver();
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
    SelenideDriver selenideDriver = new SelenideDriver(new LegacyConf(), new DefaultModule());
    if (proxy != null) {
      selenideDriver.setUserProvidedProxy(proxy);
    }
    markForAutoClose();
    return selenideDriver;
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
