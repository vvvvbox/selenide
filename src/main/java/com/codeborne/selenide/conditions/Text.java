package com.codeborne.selenide.conditions;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.impl.Html;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

public class Text extends Condition {
  protected final String text;
  private String lastActualValue;
  public Text(final String text) {
    super("text");
    this.text = text;
  }

  @Override
  public boolean apply(WebElement element) {
    String elementText = "select".equalsIgnoreCase(element.getTagName()) ?
        getSelectedOptionsTexts(element) :
        element.getText();
    lastActualValue = elementText;
    return Html.text.contains(elementText, this.text.toLowerCase());
  }

  @Override
  public String actualValue(WebElement element) {
    return lastActualValue;
  }

  private String getSelectedOptionsTexts(WebElement element) {
    List<WebElement> selectedOptions = new Select(element).getAllSelectedOptions();
    StringBuilder sb = new StringBuilder();
    for (WebElement selectedOption : selectedOptions) {
      sb.append(selectedOption.getText());
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return name + " '" + text + '\'';
  }
}
