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
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class GrailsUpdater {

    private final GrailsWrapperHome grailsWrapperHome;
    private final GrailsVersion preferredVersion;
    private GrailsVersion updatedVersion;

    public GrailsUpdater(List<GrailsReleaseType> allowedTypes, GrailsVersion preferredVersion) throws IOException {
        this(allowedTypes, preferredVersion, null);
    }

    public GrailsUpdater(List<GrailsReleaseType> allowedTypes, GrailsVersion preferredVersion, String possibleGrailsHome) throws IOException {
        grailsWrapperHome = new GrailsWrapperHome(allowedTypes, possibleGrailsHome);
        this.preferredVersion = preferredVersion;
    }

    public GrailsVersion getSelectedVersion() {
        if(preferredVersion != null) {
            return preferredVersion;
        }

        if(updatedVersion != null) {
            return updatedVersion;
        }

        return grailsWrapperHome.latestVersion;
    }

    public File getExecutedJarFile() {
        GrailsVersion selectedVersion = getSelectedVersion();
        return grailsWrapperHome.getWrapperImplementation(selectedVersion, grailsWrapperHome.getVersionDirectory(selectedVersion));
    }

    /**
     * Reasons the grails wrapper may need updating:
     * 1. the expanded wrapper jar does not exist
     *
     */
    public boolean needsUpdating() {
        File jarFile = grailsWrapperHome.getLatestWrapperImplementation();
        if(jarFile == null) {
            return true;
        }

        if(preferredVersion != null) {
            if (!grailsWrapperHome.versions.contains(preferredVersion)) {
                return true;
            }

            // Force snapshots to update always
            return preferredVersion.releaseType.isSnapshot();
        }

        return false;
    }

    public boolean update() {
        GrailsWrapperRepo repo = GrailsWrapperRepo.getSelectedRepo();

        GrailsVersion latestVersion = null;
        if(preferredVersion != null) {
            latestVersion = preferredVersion;
        }
        else {
            try {
                latestVersion = getLastVersion(repo);
            }
            catch(Exception e) {
                System.err.println("Unable to fetch latest Grails CLI.");
                e.printStackTrace();
                System.exit(1);
            }
        }

        String detailedVersion = null;
        if (latestVersion.releaseType.isSnapshot()) {
            try {
                detailedVersion = fetchSnapshotForVersion(repo, latestVersion);
            }
            catch(Exception e) {
                System.err.println("Could not parse snapshot version from maven metadata.");
                e.printStackTrace();
                System.exit(1);
            }
        }

        boolean theResult = updateJar(repo, latestVersion, detailedVersion);
        if(theResult) {
            updatedVersion = latestVersion;
        }

        return theResult;
    }

    private boolean updateJar(GrailsWrapperRepo repo, GrailsVersion version, String snapshotVersion) {
        boolean success = false;

        final String localJarFilename = GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME + "-" + version.version;
        final String remoteJarFilename = snapshotVersion != null ? GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME + "-" + snapshotVersion : GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME + "-" + version.version;
        final String jarFileExtension = ".jar";

        try {
            File downloadedJar = File.createTempFile(localJarFilename, jarFileExtension);
            String wrapperUrl = repo.getFileUrl(version, remoteJarFilename + jarFileExtension);

            InputStream inputStream;
            if(repo.isFile) {
                File jarFile = new File(wrapperUrl);
                if(!jarFile.exists()) {
                    throw new IllegalStateException("Could not determine local metadata file from local maven repository: " + jarFile.getAbsolutePath() + " does not exist");
                }
                inputStream = Files.newInputStream(jarFile.toPath());
            }
            else {
                HttpURLConnection conn = createHttpURLConnection(wrapperUrl);
                inputStream = conn.getInputStream();
            }

            success = downloadWrapperJar(version, downloadedJar, inputStream);
        } catch (Exception e) {
            System.err.println("There was an error downloading the wrapper jar");
            e.printStackTrace();
        }
        return success;
    }

    private boolean downloadWrapperJar(GrailsVersion version, File downloadJarLocation, InputStream inputStream) throws IOException {
        ReadableByteChannel rbc = Channels.newChannel(inputStream);
        try (FileOutputStream fos = new FileOutputStream(downloadJarLocation)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }

        try {
            grailsWrapperHome.cleanupOtherVersions(version);
        }
        catch(Exception e) {
            System.err.println("Unable to cleanup old versions of the wrapper");
            e.printStackTrace();
        }

        File directory = grailsWrapperHome.getVersionDirectory(version);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        Path jarFile = new File(directory, GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME + "-" + version.version + ".jar").toPath();
        System.out.println("...Moving downloaded jar to: " + jarFile.toAbsolutePath());
        Files.move(downloadJarLocation.getAbsoluteFile().toPath(), jarFile, REPLACE_EXISTING);

        return true;
    }

    private static InputStream retrieveMavenMetadata(GrailsWrapperRepo repo, String metadataUrl) throws IOException {
        if(repo.isFile) {
            File metadataFile = new File(metadataUrl);
            if(!metadataFile.exists()) {
                throw new IllegalStateException("Could not determine local metadata file from local maven repository: " + metadataFile.getAbsolutePath() + " does not exist");
            }
            return Files.newInputStream(metadataFile.toPath());
        }
        else {
            HttpURLConnection connection = createHttpURLConnection(metadataUrl);
            try {
                return connection.getInputStream();
            }
            catch(Exception e) {
                throw new RuntimeException("There was an error downloading the metadata file", e);
            }
        }
    }

    private GrailsVersion getLastVersion(GrailsWrapperRepo repo) throws IOException, SAXException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        FindLastReleaseHandler findLastReleaseHandler = new FindLastReleaseHandler();

        try(InputStream stream = retrieveMavenMetadata(repo, repo.getRootMetadataUrl())) {
            saxParser.parse(stream, findLastReleaseHandler);
            String parsedVersion = findLastReleaseHandler.getVersion();
            try {
                return new GrailsVersion(parsedVersion);
            }
            catch(Exception e) {
                throw new IllegalStateException("Failed to parse version '" + parsedVersion + "' from maven repository.", e);
            }
        }
    }

    private String fetchSnapshotForVersion(GrailsWrapperRepo repo, GrailsVersion baseVersion) throws IOException, SAXException, ParserConfigurationException {
        System.out.println("...A Grails snapshot version has been detected. Downloading latest snapshot.");

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        FindLastSnapshotHandler findVersionHandler = new FindLastSnapshotHandler();

        try(InputStream stream = retrieveMavenMetadata(repo, repo.getMetadataUrl(baseVersion))) {
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
