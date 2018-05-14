package com.codeborne.selenide.impl;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideDriver;
import com.codeborne.selenide.WebDriverRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import static com.codeborne.selenide.Configuration.FileDownloadMode.PROXY;
import static com.codeborne.selenide.Selenide.close;
import static java.lang.Thread.currentThread;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class WebDriverThreadLocalContainerTest {
  private final WebDriverThreadLocalContainer container = spy(new WebDriverThreadLocalContainer());
  private static final Logger log =
      Logger.getLogger(WebDriverThreadLocalContainer.class.getName()); // matches the logger in the affected class
  private static OutputStream logCapturingStream;
  private static StreamHandler customLogHandler;

  private static String getTestCapturedLog() {
    customLogHandler.flush();
    return logCapturingStream.toString();
  }

  @Before
  public void setUp() {
    WebDriverRunner.setProxy(null);
    logCapturingStream = new ByteArrayOutputStream();
    Handler[] handlers = log.getParent().getHandlers();
    customLogHandler = new StreamHandler(logCapturingStream, handlers[0].getFormatter());
    log.addHandler(customLogHandler);

  }

  @After
  public void tearDown() {
    WebDriverRunner.setProxy(null);
    close();
  }

  @Test
  public void checksIfBrowserIsStillAlive() {
    Configuration.reopenBrowserOnFail = true;
    SelenideDriver webdriver = mock(SelenideDriver.class);
    container.THREAD_WEB_DRIVER.put(currentThread().getId(), webdriver);

    assertSame(webdriver, container.getAndCheckWebDriver());
    verify(webdriver).getAndCheckWebDriver();
  }

  @Test
  public void closeWebDriverLoggingWhenProxyIsAdded() {
    Configuration.holdBrowserOpen = false;
    Configuration.fileDownload = PROXY;

    Proxy mockedProxy = Mockito.mock(Proxy.class);
    when(mockedProxy.getHttpProxy()).thenReturn("selenide:0");
    container.setProxy(mockedProxy);
    container.createDriver();

    ChromeDriver mockedWebDriver = Mockito.mock(ChromeDriver.class);
    container.setWebDriver(mockedWebDriver);

    container.closeWebDriver();

    String capturedLog = getTestCapturedLog();
    String currentThreadId = String.valueOf(currentThread().getId());
    assertThat(capturedLog, containsString(String.format("Close webdriver: %s -> %s", currentThreadId, mockedWebDriver.toString())));
    assertThat(capturedLog, containsString(String.format("Close proxy server: %s ->", currentThreadId)));
  }

}
