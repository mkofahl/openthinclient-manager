package org.openthinclient.web.pkgmngr.ui.presenter;

import static java.util.stream.Stream.concat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openthinclient.pkgmgr.PackageManager;
import org.openthinclient.pkgmgr.db.Package;
import org.openthinclient.pkgmgr.op.PackageManagerOperation;
import org.openthinclient.pkgmgr.op.PackageManagerOperationReport;
import org.openthinclient.pkgmgr.progress.ListenableProgressFuture;
import org.openthinclient.util.dpkg.PackageReference;
import org.openthinclient.web.pkgmngr.ui.InstallationPlanSummaryDialog;
import org.openthinclient.web.pkgmngr.ui.view.PackageDetailsView;
import org.openthinclient.web.pkgmngr.ui.view.PackageListContainer;
import org.openthinclient.web.progress.ProgressReceiverDialog;
import org.vaadin.viritin.button.MButton;

import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Component;
import com.vaadin.ui.ComponentContainer;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TreeTable;
import com.vaadin.ui.VerticalLayout;

public class PackageDetailsListPresenter {

    private final View view;
    private final PackageManager packageManager;

    public PackageDetailsListPresenter(View view, PackageManager packageManager) {
        this.view = view;
        this.packageManager = packageManager;
    }
    
    public void setPackages(Collection<org.openthinclient.pkgmgr.db.Package> otcPackages) {

      if (otcPackages != null) {
          view.show();
          view.clearPackageList();
          view.getActionBar().removeAllComponents();
          
          List<Component> installable = new ArrayList<>();
          List<Component> uninstallable = new ArrayList<>();
          
          for (Package otcPackage : otcPackages) {
            
            PackageDetailsView detailsView = new PackageDetailsView();
            
            detailsView.setName(otcPackage.getName());
            detailsView.setVersion(otcPackage.getVersion().toString());
            detailsView.setDescription(otcPackage.getDescription());
            detailsView.setShortDescription(otcPackage.getShortDescription());
            
            detailsView.clearPackageList();
            // Check available and existing packages to match package-reference of current package, sorted to use first matching package
            List<Package> installableAndExistingPackages = concat(
                packageManager.getInstalledPackages().stream(),
                packageManager.getInstallablePackages().stream()
            ).sorted()
             .collect(Collectors.toList());

            List<String> usedPackages = new ArrayList<>();
            for (PackageReference pr : otcPackage.getDepends()) {
              boolean isReferenced = false;
              for (Package _package : installableAndExistingPackages) {
                if (pr.matches(_package) && !usedPackages.contains(_package.getName())) {
                  detailsView.addDependency(_package);
                  isReferenced = true;
                  usedPackages.add(_package.getName());
                }
              }
              if (!isReferenced) {
                detailsView.addMissingPackage(pr);
              }
            }
            // -- 
            
            view.addPackageDetails(detailsView);
            
            
            // summary headline
            if (packageManager.isInstallable(otcPackage)) {
              installable.add(createLabel(otcPackage));
            }
            if (packageManager.isInstalled(otcPackage)) {
              uninstallable.add(createLabel(otcPackage));
            }
            
          }
          
          //  attach summary header and action button    
          if (!installable.isEmpty()) {
            HorizontalLayout bar = new HorizontalLayout();
            bar.setSpacing(true);
            
            VerticalLayout vl = new VerticalLayout();
            vl.addComponent(new Label("Install selected package" + (installable.size() > 1 ? "s" : "") + "."));
            vl.addComponent(new MButton("Install").withIcon(FontAwesome.DOWNLOAD).withListener(e -> {
                doInstallPackage(otcPackages);
            }));
            bar.addComponent(vl);
            
            // the installable list
            PackageListContainer packageListContainer = new PackageListContainer();
            TreeTable packagesTable = new TreeTable();
            packageListContainer.addAll(otcPackages);
            // TODO: magic numbers
            packagesTable.setWidth("100%");
            packagesTable.setHeight(39 + (otcPackages.size() * 38) + "px");
            packagesTable.setContainerDataSource(packageListContainer);
            packagesTable.setVisibleColumns("name", "version");
            bar.addComponent(packagesTable);
            bar.setExpandRatio(packagesTable, 3.0f); // TreeTable should use as much space as it can - but doesn't
            
            view.getActionBar().addComponent(bar);
          }
          
          if (!uninstallable.isEmpty()) {
            HorizontalLayout bar = new HorizontalLayout();
            bar.setSpacing(true);
            
            VerticalLayout vl = new VerticalLayout();
            vl.addComponent(new Label("Uninstall selected package" + (uninstallable.size() > 1 ? "s" : "") + "."));
            vl.addComponent(new MButton("Uninstall").withIcon(FontAwesome.TRASH_O).withListener(e -> {
                  doUninstallPackage(otcPackages);
            }));
            bar.addComponent(vl);
            
            // the uninstallable list
            PackageListContainer packageListContainer = new PackageListContainer();
            TreeTable packagesTable = new TreeTable();
            packageListContainer.addAll(otcPackages);
            // TODO: magic numbers
            packagesTable.setWidth("100%");
            packagesTable.setHeight(39 + (otcPackages.size() * 38) + "px");
            packagesTable.setContainerDataSource(packageListContainer);
            packagesTable.setVisibleColumns("name", "version");
            bar.addComponent(packagesTable);
            bar.setExpandRatio(packagesTable, 3.0f); // TreeTable should use as much space as it can - but doesn't            
            
            view.getActionBar().addComponent(bar);
          }

      } else {
          view.hide();
      }

  }
    
    private HorizontalLayout createLabel(Package otcPackage) {
      Label name = new Label(otcPackage.getName());
      name.setStyleName("huge");
      Label version = new Label(otcPackage.getVersion().toString());
      version.setStyleName("tiny");
      return new HorizontalLayout(name, version);
    }

    private void doInstallPackage(Collection<org.openthinclient.pkgmgr.db.Package> otcPackages) {
      final PackageManagerOperation op = packageManager.createOperation();
      otcPackages.forEach(op::install);
      op.resolve();
  
      // FIXME validate the state (Conflicts, missing packages, etc.)
      final InstallationPlanSummaryDialog summaryDialog = new InstallationPlanSummaryDialog(op, packageManager);
      summaryDialog.onInstallClicked(() -> execute(op, true));
      summaryDialog.open(true);
  
    }
  
    private void doUninstallPackage(Collection<org.openthinclient.pkgmgr.db.Package> otcPackages) {
      final PackageManagerOperation op = packageManager.createOperation();
      otcPackages.forEach(op::uninstall);
      op.resolve();
  
      final InstallationPlanSummaryDialog summaryDialog = new InstallationPlanSummaryDialog(op, packageManager);
      summaryDialog.onInstallClicked(() -> execute(op, false));
      summaryDialog.open(true);
    }
    
    private void execute(PackageManagerOperation op, boolean install) {
      final ProgressReceiverDialog dialog = new ProgressReceiverDialog(install ? "Installation..." : "Uninstallation...");
      final ListenableProgressFuture<PackageManagerOperationReport> future = packageManager.execute(op);
      dialog.watch(future);

      dialog.open(true);
    }    

    public interface View {

      void addPackageDetails(PackageDetailsView packageDetailsView);

      void hide();

      void show();

      void clearPackageList();

      ComponentContainer getActionBar();
    }
}