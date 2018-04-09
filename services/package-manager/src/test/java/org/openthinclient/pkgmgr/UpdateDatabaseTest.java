package org.openthinclient.pkgmgr;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openthinclient.manager.util.http.DownloadManager;
import org.openthinclient.pkgmgr.db.Package;
import org.openthinclient.pkgmgr.db.PackageManagerDatabase;
import org.openthinclient.pkgmgr.db.PackageRepository;
import org.openthinclient.pkgmgr.db.Source;
import org.openthinclient.pkgmgr.db.SourceRepository;
import org.openthinclient.pkgmgr.db.Version;
import org.openthinclient.progress.NoopProgressReceiver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.openthinclient.pkgmgr.PackagesUtil.PACKAGES_SIZE;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = UpdateDatabaseTest.Config.class)
public class UpdateDatabaseTest {
   
  @Rule
  public final DebianTestRepositoryServer testRepositoryServer = new DebianTestRepositoryServer();

  @Autowired
  PackageManagerDatabase db;
  @Autowired
  PackageRepository packageRepository;
  @Autowired
  SourceRepository sourceRepository;
  @Autowired
  PackageManagerConfiguration configuration;
  @Autowired
  DownloadManager downloadManager;

  @Autowired
  PackageManagerFactory packageManagerFactory;

  @Before
  public void configureSource() {
    final Source source = new Source();
    source.setUrl(testRepositoryServer.getServerUrl());
    source.setEnabled(true);
    sourceRepository.saveAndFlush(source);
  }

  @After
  public void cleanup() {
    // delete all test-added sources, but keep initial source (with id=1)
    sourceRepository.delete(sourceRepository.findAll().stream().filter(s -> s.getId() > 1).collect(Collectors.toList()));
    sourceRepository.flush();
  }

  @Test
  public void testUpdatePackages() {

      UpdateDatabase updater = new UpdateDatabase(configuration, getSourcesList(), db, downloadManager);

      updater.execute(new NoopProgressReceiver());

      // we're expecting amount of PACKAGES_SIZE packages to exist at this point in time
      assertEquals(PACKAGES_SIZE, packageRepository.count());

      // Simple changelog test
      // check if changelog has been added zonk-dev.changelog is currently the only one
      Optional<Package> zonkDev = packageRepository.findAll().stream().filter(p -> p.getName().equals("zonk-dev")).findFirst();
      if (zonkDev.isPresent()) {
         String changeLog = zonkDev.get().getChangeLog();
         assertTrue(StringUtils.isNotBlank(changeLog));
         // check if correct version has been applied
         assertTrue(changeLog.contains("zonk-dev (2.0-1)"));
         assertTrue(changeLog.contains("Fixed something"));
         assertTrue(changeLog.contains("Mon, 28 Nov 2016 12:12:30 +0100"));
         // wrong version entries
         assertFalse(changeLog.contains("zonk-dev (2.0-7)"));
         assertFalse(changeLog.contains("copy requested profile from skel"));
         assertFalse(changeLog.contains("Wed, 18 Jan 2017 02:02:30 +0100"));

        // Test license information
        String license = zonkDev.get().getLicense();
        assertNotNull("Expected a license value.", license);
        assertTrue(license.contains("Grundfunktionen und zahlreiche Anwendungen"));

      } else {
         fail("Expected package 'zonk-dev' not found, cannot test changelog entries.");
      }

      // running another update should not add new packages
      updater = new UpdateDatabase(configuration, getSourcesList(), db, downloadManager);

      // TODO test: changelog update for a package

      updater.execute(new NoopProgressReceiver());
      assertEquals(PACKAGES_SIZE, packageRepository.count());

  }

  @Test
  public void testUpdateWithRepositoryContainingAdditionalVersion() {

    testRepositoryServer.setRepository("test-repository_versioning/repo01");

    UpdateDatabase updater = new UpdateDatabase(configuration, getSourcesList(), db, downloadManager);
    updater.execute(new NoopProgressReceiver());

    assertEquals(1, packageRepository.count());
    final Package pkgBeforeUpdate = packageRepository.findAll().get(0);
    assertEquals("foo", pkgBeforeUpdate.getName());
    assertEquals(Version.parse("2.0-1"), pkgBeforeUpdate.getVersion());

    // updating with a repository that contains a additional version
    // after the update, there must be two different versions of the same package
    testRepositoryServer.setRepository("test-repository_versioning/repo02");
    updater.execute(new NoopProgressReceiver());

    assertEquals(2, packageRepository.count());

  }

  @Test
  public void testUpdateWithVersionChanges() {

    testRepositoryServer.setRepository("test-repository_versioning/repo01");

    UpdateDatabase updater = new UpdateDatabase(configuration, getSourcesList(), db, downloadManager);
    updater.execute(new NoopProgressReceiver());

    assertEquals(1, packageRepository.count());
    final Package pkgBeforeUpdate = packageRepository.findAll().get(0);
    assertEquals("foo", pkgBeforeUpdate.getName());
    assertEquals(Version.parse("2.0-1"), pkgBeforeUpdate.getVersion());

    // updating with a repository that contains a additional version
    // after the update, there must be two different versions of the same package
    testRepositoryServer.setRepository("test-repository_versioning/repo03");
    updater.execute(new NoopProgressReceiver());

    assertEquals(1, packageRepository.count());

    final Package pkgAfterUpdate = packageRepository.findAll().get(0);

    assertEquals("foo", pkgAfterUpdate.getName());
    assertEquals(Version.parse("2.1-1"), pkgAfterUpdate.getVersion());

  }

  public SourcesList getSourcesList() {

      final SourcesList sourcesList = new SourcesList();
      sourcesList.getSources().addAll(sourceRepository.findAll());
      return sourcesList;

  }

  @Configuration()
  @Import({SimpleTargetDirectoryPackageManagerConfiguration.class, PackageManagerInMemoryDatabaseConfiguration.class})
  public static class Config {

  }
}