package org.openthinclient.pkgmgr.op;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openthinclient.manager.util.http.DownloadManager;
import org.openthinclient.pkgmgr.PackageManagerConfiguration;
import org.openthinclient.pkgmgr.db.Installation;
import org.openthinclient.pkgmgr.db.PackageManagerDatabase;
import org.openthinclient.pkgmgr.exception.PackageManagerDownloadException;
import org.openthinclient.pkgmgr.op.PackageManagerOperationReport.PackageReport;
import org.openthinclient.pkgmgr.op.PackageManagerOperationReport.PackageReportType;
import org.openthinclient.pkgmgr.progress.ProgressReceiver;
import org.openthinclient.pkgmgr.progress.ProgressTask;
import org.openthinclient.util.dpkg.LocalPackageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PackageManagerOperationTask implements ProgressTask<PackageManagerOperationReport> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PackageManagerOperationTask.class);

    private final PackageManagerConfiguration configuration;
    private final InstallPlan installPlan;
    private final PackageManagerDatabase packageManagerDatabase;
    private final LocalPackageRepository localPackageRepository;
    private final DownloadManager downloadManager;

    public PackageManagerOperationTask(final PackageManagerConfiguration configuration, InstallPlan installPlan, PackageManagerDatabase packageManagerDatabase, LocalPackageRepository localPackageRepository, DownloadManager downloadManager) {
        this.configuration = configuration;
        this.installPlan = installPlan;
        this.packageManagerDatabase = packageManagerDatabase;
        this.localPackageRepository = localPackageRepository;
        this.downloadManager = downloadManager;
    }

    @Override
    public PackageManagerOperationReport execute(ProgressReceiver progressReceiver) throws PackageManagerDownloadException {
        LOGGER.info("Package installation/uninstallatio started.");

        Installation installation = new Installation();
        installation.setStart(LocalDateTime.now());


        // persist the installation first to allow on the go persistence of the installationlogentry entities
        installation = packageManagerDatabase.getInstallationRepository().save(installation);

        LOGGER.info("Determining packages to be downloaded");

//        // FIXME we should verify that the test install directory is actually empty at the moment.
//        final Path testInstallDir = configuration.getTestinstallDir().toPath();
        final Path installDir = configuration.getInstallDir().toPath();
        LOGGER.info("Operation destination directory: {}", installDir);

        try {
          downloadPackages(installation, installDir);
        } catch (IOException e) {
          throw new PackageManagerDownloadException(e);
        }

        PackageManagerOperationReport report = executeSteps(installation, installDir, installPlan.getSteps());

        installation.setEnd(LocalDateTime.now());

        packageManagerDatabase.getInstallationRepository().save(installation);

        LOGGER.info("Package installation/uninstallation completed.");

        return report;
    }

    private PackageManagerOperationReport executeSteps(Installation installation, Path installDir, List<InstallPlanStep> steps) {

        final List<PackageOperation> operations = new ArrayList<>(steps.size());
        
        for (InstallPlanStep step : steps) {
            if (step instanceof InstallPlanStep.PackageInstallStep) {
                operations.add(new PackageOperationInstall(((InstallPlanStep.PackageInstallStep) step).getPackage()));
            } else if (step instanceof InstallPlanStep.PackageUninstallStep) {
                operations.add(new PackageOperationUninstall(((InstallPlanStep.PackageUninstallStep) step).getInstalledPackage()));
            } else if (step instanceof InstallPlanStep.PackageVersionChangeStep) {
                operations.add(new PackageOperationUninstall(((InstallPlanStep.PackageVersionChangeStep) step).getInstalledPackage()));
                operations.add(new PackageOperationInstall(((InstallPlanStep.PackageVersionChangeStep) step).getTargetPackage()));
            } else {
               
                throw new IllegalArgumentException("Unsupported type of install plan step " + step);
            }
        }

        return execute(installation, installDir, operations);
    }

    /**
     * Download all packages that are not available in the {@link #localPackageRepository local
     * package repository}
     */
    private void downloadPackages(Installation installation, Path targetDirectory) throws IOException {

        List<PackageOperationDownload> operations = Stream.concat( //
                installPlan.getPackageInstallSteps()
                        .map(InstallPlanStep.PackageInstallStep::getPackage), //
                installPlan.getPackageVersionChangeSteps()
                        .map(InstallPlanStep.PackageVersionChangeStep::getTargetPackage) //
        )
                // filtering out all packages that are already locally available
                .filter(pkg -> !localPackageRepository.isAvailable(pkg))
                .map(pkg -> new PackageOperationDownload(pkg, downloadManager)) //
                .collect(Collectors.toList());

        execute(installation, targetDirectory, operations);
    }

    private PackageManagerOperationReport execute(Installation installation, Path targetDirectory, List<? extends PackageOperation> operations) {
        final PackageManagerOperationReport report = new PackageManagerOperationReport();
        for (PackageOperation operation : operations) {
          try {
            report.addPackageReport(execute(installation, targetDirectory, operation));
          } catch (IOException exception) {
            LOGGER.error("Failed to execute PackageOperation: " + operation, exception);
            // add FAIL-report entry
            report.addPackageReport(new PackageReport(operation.getPackage(), PackageReportType.FAIL));
          }
        }
        return report;
    }

    private PackageReport execute(Installation installation, Path targetDirectory, PackageOperation operation) throws IOException {
        final DefaultPackageOperationContext context = new DefaultPackageOperationContext(localPackageRepository, packageManagerDatabase, 
                                                                                          installation, targetDirectory,
                                                                                          operation.getPackage());
        operation.execute(context);

        // save the generated log entries
        packageManagerDatabase.getInstallationLogEntryRepository().save(context.getLog());
        
        PackageReportType reportType = null;
        if (operation instanceof PackageOperationInstall) {
          reportType = PackageReportType.INSTALL;
        } else if (operation instanceof PackageOperationUninstall) {
          reportType = PackageReportType.UNINSTALL;
        } else if (operation instanceof PackageOperationDownload) {
          reportType = PackageReportType.DOWNLOAD;
        }
        
        return new PackageReport(operation.getPackage(), reportType);
    }

    @Override
    public ProgressTaskDescription getDescription(Locale locale) {
        // FIXME
        return null;
    }

}
