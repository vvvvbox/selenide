package com.codeborne.selenide;

import com.codeborne.selenide.impl.WebDriverContainer;
import com.codeborne.selenide.impl.WebDriverThreadLocalContainer;
import com.codeborne.selenide.inject.Module;

public class DefaultModule extends Module {
  public DefaultModule() {
    bind(WebDriverContainer.class, WebDriverThreadLocalContainer.class);
  }
}
