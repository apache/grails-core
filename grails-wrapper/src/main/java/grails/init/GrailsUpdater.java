/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.init;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Handles updating the grails-cli shadowJar under `~/.grails/wrapper`
 */
public class GrailsUpdater {

    private final GrailsWrapperHome grailsWrapperHome;
    private final GrailsVersion preferredVersion;
    private GrailsVersion updatedVersion;

    /**
     * @param allowedTypes     the release types that are allowed to be updated to
     * @param preferredVersion the preferred version to update to
     * @throws IOException if canonicalizing the grails home fails
     */
    public GrailsUpdater(LinkedHashSet<GrailsReleaseType> allowedTypes, GrailsVersion preferredVersion) throws IOException {
        this(allowedTypes, preferredVersion, null);
    }

    /**
     * @param allowedTypes       the release types that are allowed to be updated to
     * @param preferredVersion   the preferred version to update to
     * @param possibleGrailsHome a possible directory for the grails home
     * @throws IOException if canonicalizing the grails home fails
     */
    public GrailsUpdater(LinkedHashSet<GrailsReleaseType> allowedTypes, GrailsVersion preferredVersion, String possibleGrailsHome) throws IOException {
        grailsWrapperHome = new GrailsWrapperHome(allowedTypes, possibleGrailsHome);
        this.preferredVersion = preferredVersion;
    }

    /**
     * @return the `grails-cli` version that was selected by this updater
     */
    public GrailsVersion getSelectedVersion() {
        if (preferredVersion != null) {
            return preferredVersion;
        }

        if (updatedVersion != null) {
            return updatedVersion;
        }

        return grailsWrapperHome.latestVersion;
    }

    /**
     * @return the jar file for the `grails-cli` verison that was selected by this updater`
     */
    public File getExecutedJarFile() {
        GrailsVersion selectedVersion = getSelectedVersion();
        return grailsWrapperHome.getWrapperImplementation(selectedVersion, grailsWrapperHome.getVersionDirectory(selectedVersion));
    }

    /**
     * @return true if the updater should update the `grails-cli` shadowJar
     */
    public boolean needsUpdating() {
        File jarFile = grailsWrapperHome.getLatestWrapperImplementation();
        if (jarFile == null) {
            return true;
        }

        if (preferredVersion != null) {
            if (!grailsWrapperHome.versions.contains(preferredVersion)) {
                return true;
            }

            // Force snapshots to update always
            return preferredVersion.releaseType.isSnapshot();
        }

        return false;
    }

    /**
     * Fetches the selectedVersion and if it already exists, replaces the jar file.
     *
     * @return true if an update was performed, false otherwise
     */
    public boolean update() {
        GrailsWrapperRepo repo = GrailsWrapperRepo.getSelectedRepo();

        GrailsVersion selectedVersion = null;
        if (preferredVersion != null) {
            selectedVersion = preferredVersion;
        } else {
            try {
                selectedVersion = getRootVersion(repo);
            } catch (Exception e) {
                System.err.println("Unable to fetch latest Grails CLI.");
                e.printStackTrace();
                System.exit(1);
            }
        }

        String detailedVersion = null;
        if (selectedVersion.releaseType.isSnapshot()) {
            try {
                detailedVersion = fetchSnapshotForVersion(repo, selectedVersion);
            } catch (Exception e) {
                System.err.println("Could not parse snapshot version from maven metadata.");
                e.printStackTrace();
                System.exit(1);
            }
        }

        boolean theResult = updateJar(repo, selectedVersion, detailedVersion);
        if (theResult) {
            updatedVersion = selectedVersion;
        }

        return theResult;
    }

    private boolean updateJar(GrailsWrapperRepo repo, GrailsVersion version, String snapshotVersion) {
        boolean success = false;

        final String localJarFilename = GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME + "-" + version.version + "-all";
        // shadowjars will always have the 'all' classifier
        final String remoteJarFilename = snapshotVersion != null ? GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME + "-" + snapshotVersion + "-all" : GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME + "-" + version.version + "-all";
        final String jarFileExtension = ".jar";

        try {
            File downloadedJar = File.createTempFile(localJarFilename, jarFileExtension);
            String wrapperUrl = repo.getFileUrl(version, remoteJarFilename + jarFileExtension);
            if(snapshotVersion != null) {
                System.out.println("... Using Snapshot URL: " + wrapperUrl);
            }

            long contentLength = -1;
            InputStream inputStream;
            if (repo.isFile) {
                File jarFile = new File(wrapperUrl);
                if (!jarFile.exists()) {
                    throw new IllegalStateException("Could not determine local metadata file from local maven repository: " + jarFile.getAbsolutePath() + " does not exist");
                }
                inputStream = Files.newInputStream(jarFile.toPath());
                contentLength = jarFile.length();
            } else {
                HttpURLConnection conn = createHttpURLConnection(wrapperUrl);
                contentLength = conn.getContentLengthLong();
                inputStream = conn.getInputStream();
            }

            success = downloadWrapperJar(version, downloadedJar, inputStream, contentLength, repo.isFile);
        } catch (Exception e) {
            System.err.println("There was an error downloading the wrapper jar");
            e.printStackTrace();
        }
        return success;
    }

    private void transfer(File downloadJarLocation, InputStream inputStream, long expectedSize) throws IOException {
        try (inputStream; FileOutputStream fos = new FileOutputStream(downloadJarLocation)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            int lastProgressPercent = 0;
            long lastMillis = System.currentTimeMillis();
            long lastBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                if (expectedSize > 0) {
                    // can determine the max size, so show a progress bar with % and speed
                    long currentMillis = System.currentTimeMillis();
                    if (currentMillis - lastMillis >= 500) {
                        long elapsedMillis = currentMillis - lastMillis;
                        long bytesThisRound = totalBytesRead - lastBytes;
                        double bytesPerSecond = bytesThisRound / (elapsedMillis / 1000.0);

                        int progressPercent = (int) (totalBytesRead * 100 / expectedSize);
                        if (progressPercent != lastProgressPercent) {
                            String transferSpeed = readableSize((long) bytesPerSecond, true);
                            System.out.printf("\r... %3d%% (%s)", progressPercent, transferSpeed);
                            lastProgressPercent = progressPercent;
                        }

                        lastMillis = currentMillis;
                        lastBytes = totalBytesRead;
                    }
                }
                else {
                    // cannot determine the size
                    System.out.printf("\r... %s", readableSize(totalBytesRead, false));
                }
            }
            System.out.print("\n");
        }
    }

    public static String readableSize(long bytes, boolean addSeconds) {
        List<String> units = List.of("KiB", "MiB", "GiB", "TiB");
        List<Long> thresholds = List.of(1024L, 1_048_576L, 1_073_741_824L, 1_099_511_627_776L);

        if (bytes > 1_099_511_627_776L) {
            return "---";
        }

        for (int i = thresholds.size() - 1; i >= 0; i--) {
            if (bytes >= thresholds.get(i)) {
                double thresholdValue = (double) bytes / thresholds.get(i);
                return String.format(addSeconds ? "%.2f %s/s" : "%.2f %s", thresholdValue, units.get(i));
            }
        }

        return addSeconds ? "B/s" : "B";
    }

    private boolean downloadWrapperJar(GrailsVersion version, File downloadJarLocation, InputStream inputStream, long expectedSize, boolean isLocal) throws IOException {
        transfer(downloadJarLocation, inputStream, expectedSize);

        try {
            grailsWrapperHome.cleanupOtherVersions(version);
        } catch (Exception e) {
            System.err.println("Unable to cleanup old versions of the wrapper");
            e.printStackTrace();
        }

        File directory = grailsWrapperHome.getVersionDirectory(version);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        Path jarFile = new File(directory, GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME + "-" + version.version + "-all.jar").toPath();
        System.out.println("...Moving " + (isLocal ? "local" : "remotely") + " downloaded jar to: " + jarFile.toAbsolutePath());
        Files.move(downloadJarLocation.getAbsoluteFile().toPath(), jarFile, REPLACE_EXISTING);

        return true;
    }

    private static InputStream retrieveMavenMetadata(GrailsWrapperRepo repo, String metadataUrl) throws IOException {
        if (repo.isFile) {
            File metadataFile = new File(metadataUrl);
            if (!metadataFile.exists()) {
                throw new IllegalStateException("Could not determine local metadata file from local maven repository: " + metadataFile.getAbsolutePath() + " does not exist");
            }
            return Files.newInputStream(metadataFile.toPath());
        } else {
            HttpURLConnection connection = createHttpURLConnection(metadataUrl);
            try {
                return connection.getInputStream();
            } catch (Exception e) {
                throw new RuntimeException("There was an error downloading the metadata file", e);
            }
        }
    }

    private GrailsVersion getRootVersion(GrailsWrapperRepo repo) throws IOException, SAXException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        RootMetadataHandler findLastReleaseHandler = new RootMetadataHandler(grailsWrapperHome.allowedReleaseTypes);

        try (InputStream stream = retrieveMavenMetadata(repo, repo.getRootMetadataUrl())) {
            saxParser.parse(stream, findLastReleaseHandler);
            List<GrailsVersion> foundVersions = findLastReleaseHandler.getVersions();
            if (foundVersions.isEmpty()) {
                throw new IllegalStateException("No Grails Releases were found for the allowed types: " + grailsWrapperHome.allowedReleaseTypes.stream().map(Enum::name).collect(Collectors.joining(", ")));
            }

            Collections.sort(foundVersions);

            return foundVersions.get(0);

        }
    }

    private String fetchSnapshotForVersion(GrailsWrapperRepo repo, GrailsVersion baseVersion) throws IOException, SAXException, ParserConfigurationException {
        System.out.println("...A Grails snapshot version has been detected. Downloading latest snapshot.");

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        FindLastSnapshotHandler findVersionHandler = new FindLastSnapshotHandler();

        try (InputStream stream = retrieveMavenMetadata(repo, repo.getMetadataUrl(baseVersion))) {
            saxParser.parse(stream, findVersionHandler);
            return findVersionHandler.getVersion();
        }
    }

    private static HttpURLConnection createHttpURLConnection(String mavenMetadataFileUrl) throws IOException {
        final URL url = new URL(mavenMetadataFileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Apache-Maven/3.9.6");
        conn.setInstanceFollowRedirects(true);
        return conn;
    }
}
