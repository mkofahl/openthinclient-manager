package org.openthinclient;

import org.openthinclient.common.VersionUtil;
import org.openthinclient.manager.util.http.DownloadManager;
import org.openthinclient.manager.util.http.config.NetworkConfiguration;
import org.openthinclient.manager.util.http.impl.HttpClientDownloadManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DownloadManagerFactory
 */
public class DownloadManagerFactory {

    private static final Logger logger = LoggerFactory.getLogger(DownloadManagerFactory.class);

    public static DownloadManager create(String serverID, NetworkConfiguration.ProxyConfiguration proxyConfiguration) {

        String version   = VersionUtil.readApplicationVersion();
        String userAgent = version == null ? serverID : serverID + "-" + version;

        return new HttpClientDownloadManager(proxyConfiguration, userAgent);
    }


}
