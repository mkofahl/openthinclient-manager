package org.openthinclient.web.devices.ui.design;

import com.vaadin.annotations.AutoGenerated;
import com.vaadin.annotations.DesignRoot;
import com.vaadin.ui.Label;
import com.vaadin.ui.Link;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.declarative.Design;

import org.openthinclient.web.ui.ViewHeader;

/**
 * !! DO NOT EDIT THIS FILE !!
 *
 * This class is generated by Vaadin Designer and will be overwritten.
 *
 * Please make a subclass with logic and additional interfaces as needed,
 * e.g class LoginView extends LoginDesign implements View { }
 */
@DesignRoot
@AutoGenerated
@SuppressWarnings("serial")
public class ManageDevicesDesign extends VerticalLayout {
  protected ViewHeader header;
  protected Label labelTitle;
  protected Link linkOpen;
  protected Label labelDescription;

  public ManageDevicesDesign() {
    Design.read(this);
  }
}