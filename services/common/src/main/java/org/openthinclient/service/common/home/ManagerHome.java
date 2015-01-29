package org.openthinclient.service.common.home;

/**
 * A model representation of the openthinclient manager home directory.
 */
public interface ManagerHome {

  public <T extends Configuration> T getConfiguration(Class<T> configurationClass);

  public void saveAll();

  public void save(Class<? extends Configuration> configurationClass);
}
