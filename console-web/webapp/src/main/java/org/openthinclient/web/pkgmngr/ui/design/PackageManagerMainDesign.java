package org.openthinclient.web.pkgmngr.ui.design;

import com.vaadin.annotations.AutoGenerated;
import com.vaadin.annotations.DesignRoot;
import com.vaadin.ui.Label;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.declarative.Design;
import org.openthinclient.web.pkgmngr.ui.view.PackageListMasterDetailsView;

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
public class PackageManagerMainDesign extends TabSheet {
   
  protected PackageListMasterDetailsView availablePackages;
  protected PackageListMasterDetailsView installedPackages;

  public PackageManagerMainDesign() {
    Design.read(this);
  }
}
