package com.codeborne.selenide;

import com.codeborne.selenide.impl.LegacyConf;
import com.codeborne.selenide.inject.Module;
import com.codeborne.selenide.webdriver.WebDriverFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.UnreachableBrowserException;

import static com.codeborne.selenide.Configuration.FileDownloadMode.HTTPGET;
import static com.codeborne.selenide.Configuration.FileDownloadMode.PROXY;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SelenideDriverTest {
  WebDriverFactory factory = mock(WebDriverFactory.class);
  WebDriver webdriver = mock(WebDriver.class);
  SelenideDriver driver;

  @Before
  public void setUp() {
    Module module = mock(Module.class);
    when(module.instance(WebDriverFactory.class)).thenReturn(factory);
    driver = new SelenideDriver(new LegacyConf(), module);
    doReturn(mock(WebDriver.class)).when(factory).createWebDriver(any());
    doReturn(mock(WebDriver.class)).when(factory).createWebDriver(null);
  }

  @Test
  public void createWebDriverWithoutProxy() {
    Configuration.fileDownload = HTTPGET;

    driver.createDriver();

    verify(factory).createWebDriver(null);
  }

  @Test
  public void createWebDriverWithSelenideProxyServer() {
    Configuration.fileDownload = PROXY;

    driver.createDriver();

    ArgumentCaptor<Proxy> captor = ArgumentCaptor.forClass(Proxy.class);
    verify(factory).createWebDriver(captor.capture());
    assertThat(captor.getValue().getHttpProxy(), is(notNullValue()));
    assertThat(captor.getValue().getSslProxy(), is(notNullValue()));
  }

  @Test
  public void checksIfBrowserIsStillAlive() {
    Configuration.reopenBrowserOnFail = true;
    driver.setWebDriver(webdriver);
    driver = spy(driver);

    assertSame(webdriver, driver.getAndCheckWebDriver());

    verify(driver).isBrowserStillOpen(any());
  }

  @Test
  public void doesNotReopenBrowserIfItFailed() {
    Configuration.reopenBrowserOnFail = false;
    driver.setWebDriver(webdriver);
    driver = spy(driver);

    assertSame(webdriver, driver.getAndCheckWebDriver());
    verify(driver, never()).isBrowserStillOpen(any());
  }

  @Test
  public void checksIfBrowserIsStillAlive_byCallingGetTitle() {
    WebDriver webdriver = mock(WebDriver.class);
    doReturn("blah").when(webdriver).getTitle();

    assertThat(driver.isBrowserStillOpen(webdriver), is(true));
  }

  @Test
  public void isBrowserStillOpen_UnreachableBrowserException() {
    WebDriver webdriver = mock(WebDriver.class);
    doThrow(UnreachableBrowserException.class).when(webdriver).getTitle();

    assertThat(driver.isBrowserStillOpen(webdriver), is(false));
  }

  @Test
  public void isBrowserStillOpen_NoSuchWindowException() {
    WebDriver webdriver = mock(WebDriver.class);
    doThrow(NoSuchWindowException.class).when(webdriver).getTitle();

    assertThat(driver.isBrowserStillOpen(webdriver), is(false));
  }

  @Test
  public void isBrowserStillOpen_NoSuchSessionException() {
    WebDriver webdriver = mock(WebDriver.class);
    doThrow(NoSuchSessionException.class).when(webdriver).getTitle();

    assertThat(driver.isBrowserStillOpen(webdriver), is(false));
  }
}
