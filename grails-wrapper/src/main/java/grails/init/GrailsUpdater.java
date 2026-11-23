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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

/**
 * Handles updating the grails-cli shadowJar under `~/.grails/wrapper`
 * <p>
 * A download is written beside the place of the jar and is moved there, in one step, only after it was found to
 * be whole: it matches the strongest checksum file the repository has beside the jar, or, when the repository
 * has none, every entry of it was read and matches its CRC; and in both cases it opens as a jar that has its
 * start class. The checksum is from the repository the jar is from, so it tells a download that was cut off or
 * damaged, and does not tell a repository that serves another jar.
 */
public class GrailsUpdater {

    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
    // of one read: a download that delivers nothing for this long has stalled
    private static final int READ_TIMEOUT_MILLIS = 60_000;

    private static final String PART_SUFFIX = ".part";
    private static final long STALE_PART_MILLIS = 24L * 60 * 60 * 1000;

    // the checksum files of a Maven repository, the strongest first
    private static final String[][] CHECKSUMS = {{".sha512", "SHA-512"}, {".sha256", "SHA-256"}, {".sha1", "SHA-1"}, {".md5", "MD5"}};
    private static final int MAX_CHECKSUM_FILE_LENGTH = 4096;
    private static final int MAX_MESSAGE_LENGTH = 300;

    private static final Pattern SNAPSHOT_VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]*");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

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

            // a directory of the version without its jar is what an update that failed can leave
            if (!getExecutedJarFile().isFile()) {
                return true;
            }

            // Force snapshots to update always
            return preferredVersion.releaseType.isSnapshot();
        }

        return !jarFile.isFile();
    }

    /**
     * Says what is wrong with the jar that is installed for the selected version, when a file is there and it
     * cannot be started: an earlier wrapper installed what it downloaded without looking into it, so a download
     * that was interrupted left such a file.
     *
     * @return what is wrong with the installed jar, or null when it can be started or none is installed
     */
    public String getInstalledDefect() {
        File jarFile = getExecutedJarFile();
        return jarFile == null || !jarFile.exists() ? null : StartClass.defect(jarFile);
    }

    /**
     * Fetches the selectedVersion and if it already exists, replaces the jar file.
     *
     * @return true if an update was performed; false if the download was whole and the jar that is installed,
     * which is whole too, could not be replaced by it
     * @throws GrailsWrapperException if no repository had the command line, or the home cannot be written to
     */
    public boolean update() {
        return update(preferredVersion);
    }

    /**
     * Downloads the selected version again, over a jar that is installed and cannot be started.
     *
     * @return true if the jar was replaced
     * @throws GrailsWrapperException if no repository had the command line, or the home cannot be written to
     */
    public boolean repair() {
        return update(getSelectedVersion());
    }

    private boolean update(GrailsVersion wantedVersion) {
        requireWritableHome();

        List<GrailsWrapperRepo> repos = GrailsWrapperRepo.getSelectedRepos();
        List<String> misses = new ArrayList<>();
        for (GrailsWrapperRepo repo : repos) {
            try {
                GrailsVersion selectedVersion = wantedVersion != null ? wantedVersion : getRootVersion(repo);
                String detailedVersion = selectedVersion.releaseType.isSnapshot() ? fetchSnapshotForVersion(repo, selectedVersion) : null;
                boolean installed = updateJar(repo, selectedVersion, detailedVersion);
                updatedVersion = selectedVersion;
                return installed;
            } catch (GrailsReleaseNotFoundException e) {
                // the repository does not have it: ordinary for all but a repository the user named
                misses.add("[" + repo.getDisplayUrl() + "]: " + said(e));
                reportSkippedOverride(repo, e);
            } catch (GrailsDownloadException e) {
                misses.add("[" + repo.getDisplayUrl() + "]: " + said(e));
                System.err.println("The Grails CLI could not be taken from the repository [" + repo.getDisplayUrl() + "]: " + said(e));
            }
        }

        throw new GrailsWrapperException("Could not find the Grails CLI in any repository:" +
                misses.stream().map(miss -> System.lineSeparator() + "  " + miss).collect(Collectors.joining()));
    }

    /**
     * Says that a repository the user named does not have the command line, before the next repository is tried.
     * Without it a command line from Maven Central or the ASF repository looks like the one of the named repository.
     */
    private static void reportSkippedOverride(GrailsWrapperRepo repo, GrailsReleaseNotFoundException reason) {
        if (repo.overridden) {
            System.out.println("...The Grails CLI was not found in the overridden repository [" + repo.getDisplayUrl() + "]: " + said(reason));
            System.out.println("...Trying the next repository.");
        }
    }

    /**
     * What a repository that was passed over said, as a line names it. The message can have the address of the
     * repository in it, or a path made from it where the repository is a directory, so it is held to what an
     * address is held to wherever it was made.
     */
    private static String said(Exception reason) {
        return GrailsWrapperRepo.textWithoutCredentials(reason.getMessage());
    }

    /**
     * Nothing can be installed in a home that cannot be written to, whatever repository is asked.
     */
    private void requireWritableHome() {
        File directory = grailsWrapperHome.wrapperDirectory;
        try {
            // createDirectories and not mkdirs: of two wrappers that start at once, mkdirs fails in the one
            // that finds the directory made by the other between its two steps
            Files.createDirectories(directory.toPath());
        } catch (IOException e) {
            throw unwritableHome(directory, null);
        }
        if (!Files.isWritable(directory.toPath())) {
            throw unwritableHome(directory, null);
        }
    }

    private static GrailsWrapperException unwritableHome(File directory, IOException reason) {
        return new GrailsWrapperException("The Grails wrapper cannot write to [" + directory.getAbsolutePath() + "], where it keeps the command line it downloads" +
                (reason == null ? "" : " (" + describe(reason) + ")") +
                ". Make the directory writable, or name another home for the wrapper with the environment variable GRAILS_WRAPPER_HOME.");
    }

    private static String jarName(GrailsVersion version) {
        return GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME + "-" + version.version + "-all.jar";
    }

    private boolean updateJar(GrailsWrapperRepo repo, GrailsVersion version, String snapshotVersion) throws GrailsReleaseNotFoundException, GrailsDownloadException {
        // shadowjars will always have the 'all' classifier
        String remoteJarFilename = GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME + "-" + (snapshotVersion != null ? snapshotVersion : version.version) + "-all.jar";
        String wrapperUrl = repo.getFileUrl(version, remoteJarFilename);
        if (snapshotVersion != null) {
            System.out.println("... Using Snapshot URL: " + GrailsWrapperRepo.withoutCredentials(wrapperUrl));
        }

        File directory = grailsWrapperHome.getVersionDirectory(version);
        Path part = null;
        try {
            long[] contentLength = {-1};
            try (InputStream inputStream = open(repo, wrapperUrl, contentLength)) {
                // only now: a repository that does not have the jar leaves nothing in the home
                part = createPart(directory, version);
                transfer(part, inputStream, contentLength[0]);
            } catch (IOException e) {
                throw new GrailsDownloadException("the download failed: " + describe(e));
            }
            verify(repo, wrapperUrl, part);
            return install(version, part, repo.isFile);
        } finally {
            if (part != null) {
                deleteQuietly(part);
            }
            // only a directory that is empty is removed: an installed jar, or the download of another wrapper, keeps it
            if (!Files.isSymbolicLink(directory.toPath())) {
                directory.delete();
            }
        }
    }

    /**
     * The file a download is written to: beside the place of the jar, so that it can be moved there in one step,
     * and with a name of its own, so that two wrappers that download the same version do not write to one file.
     */
    private static Path createPart(File directory, GrailsVersion version) {
        IOException failure = null;
        // twice: another wrapper whose download failed may remove the directory, while it is empty, between the two steps
        for (int attempt = 0; attempt < 2; attempt++) {
            directory.mkdirs();
            try {
                return Files.createTempFile(directory.toPath(), jarName(version) + ".", PART_SUFFIX);
            } catch (IOException e) {
                failure = e;
            }
        }
        throw unwritableHome(directory, failure);
    }

    /**
     * Opens a file of a repository.
     *
     * @param contentLength receives the length of the file, or -1 when the repository does not say
     * @throws GrailsReleaseNotFoundException if the repository does not have the file
     */
    private static InputStream open(GrailsWrapperRepo repo, String location, long[] contentLength) throws IOException, GrailsReleaseNotFoundException {
        if (repo.isFile) {
            File file = new File(location);
            if (!file.isFile()) {
                throw new GrailsReleaseNotFoundException(file.getAbsolutePath() + " does not exist");
            }
            contentLength[0] = file.length();
            return Files.newInputStream(file.toPath());
        }

        HttpURLConnection conn = createHttpURLConnection(location);
        try {
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND || responseCode == HttpURLConnection.HTTP_GONE) {
                throw new GrailsReleaseNotFoundException("HTTP " + responseCode + " for " + GrailsWrapperRepo.withoutCredentials(location));
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + " for " + location);
            }
            contentLength[0] = conn.getContentLengthLong();
            return conn.getInputStream();
        } catch (IOException | RuntimeException | GrailsReleaseNotFoundException e) {
            // createHttpURLConnection transfers ownership; do not leak the
            // connection when the response turns out to be an error (e.g. 404)
            closeQuietly(conn);
            throw e;
        }
    }

    private void transfer(Path downloadJarLocation, InputStream inputStream, long expectedSize) throws IOException {
        try (OutputStream fos = Files.newOutputStream(downloadJarLocation)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            int lastProgressPercent = 0;
            long lastMillis = System.currentTimeMillis();
            long lastBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                try {
                    fos.write(buffer, 0, bytesRead);
                } catch (IOException e) {
                    // a full disk is not a fault of the repository, and the next one would fill it again
                    throw unwritableHome(downloadJarLocation.getParent().toFile(), e);
                }
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
                } else {
                    // cannot determine the size
                    System.out.printf("\r... %s", readableSize(totalBytesRead, false));
                }
            }
            System.out.print("\n");

            if (expectedSize >= 0 && totalBytesRead != expectedSize) {
                throw new IOException("it was cut off after " + totalBytesRead + " of " + expectedSize + " bytes");
            }
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

    /**
     * A download is installed only when it is whole. With a checksum file of the repository, that is what the
     * checksum says. Without one, every entry of the jar is read and compared with the CRC the jar records for
     * it. Either way the file opens as a jar, which one that was cut off does not, and has its start class.
     */
    private static void verify(GrailsWrapperRepo repo, String location, Path downloaded) throws GrailsDownloadException {
        String[] checksum = findChecksum(repo, location);
        String defect = StartClass.defect(downloaded.toFile());
        try {
            if (checksum != null) {
                if (!checksum[1].equalsIgnoreCase(digest(downloaded, checksum[0]))) {
                    throw new GrailsDownloadException("the download does not match the " + checksum[0] + " checksum of the repository: it was cut off or is corrupt");
                }
            } else if (defect == null) {
                readEveryEntry(downloaded);
            }
        } catch (IOException e) {
            throw new GrailsDownloadException("the download is corrupt: " + describe(e));
        }
        if (defect != null) {
            throw new GrailsDownloadException("the download is not a Grails command line that can be started: " + defect);
        }
    }

    /**
     * @return the algorithm and the checksum of the strongest checksum file the repository has beside a file, or null when it has none
     */
    private static String[] findChecksum(GrailsWrapperRepo repo, String location) throws GrailsDownloadException {
        for (String[] kind : CHECKSUMS) {
            int length;
            try {
                length = MessageDigest.getInstance(kind[1]).getDigestLength() * 2;
            } catch (NoSuchAlgorithmException e) {
                continue;
            }

            byte[] content;
            try (InputStream stream = open(repo, location + kind[0], new long[1])) {
                content = stream.readNBytes(MAX_CHECKSUM_FILE_LENGTH);
            } catch (GrailsReleaseNotFoundException e) {
                continue;
            } catch (IOException e) {
                throw new GrailsDownloadException("its " + kind[0] + " checksum file could not be read: " + describe(e));
            }

            // the checksum, and after it the name of the file with some tools
            String checksum = new String(content, StandardCharsets.ISO_8859_1).trim().split("\\s+")[0];
            if (checksum.length() != length || !checksum.matches("[0-9A-Fa-f]+")) {
                throw new GrailsDownloadException("its " + kind[0] + " checksum file does not hold a checksum");
            }
            return new String[]{kind[1], checksum};
        }
        return null;
    }

    private static String digest(Path file, String algorithm) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static void readEveryEntry(Path file) throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            byte[] buffer = new byte[8192];
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                CRC32 crc = new CRC32();
                try (InputStream stream = jar.getInputStream(entry)) {
                    int bytesRead;
                    while ((bytesRead = stream.read(buffer)) != -1) {
                        crc.update(buffer, 0, bytesRead);
                    }
                }
                if (entry.getCrc() != -1 && entry.getCrc() != crc.getValue()) {
                    throw new IOException("an entry of the jar does not match its CRC");
                }
            }
        } catch (RuntimeException e) {
            // the zip classes of the JDK say some damage this way
            throw new IOException(e.getClass().getSimpleName());
        }
    }

    /**
     * Puts a download that was verified in the place of the jar, in one step: another wrapper that starts the
     * jar at this moment finds the one that was there or the new one, and never part of one.
     *
     * @return true if the download is installed; false if it could not replace a jar that is installed and whole
     */
    private boolean install(GrailsVersion version, Path part, boolean isLocal) {
        Path jarFile = part.resolveSibling(jarName(version));
        try {
            Files.move(part, jarFile, ATOMIC_MOVE);
        } catch (IOException e) {
            // On Windows a jar that a running command line has open cannot be replaced. The jar is the one it
            // was: when that one is whole it is used, and what was downloaded is dropped.
            if (StartClass.defect(jarFile.toFile()) == null) {
                System.out.println("...The Grails command line at [" + jarFile.toAbsolutePath() + "] could not be replaced by the download (" + describe(e) + ") and is used as it is: it may be in use by a command that is running.");
                return false;
            }
            throw unwritableHome(jarFile.getParent().toFile(), e);
        }
        System.out.println("...Moved " + (isLocal ? "local" : "remotely") + " downloaded jar to: " + jarFile.toAbsolutePath());
        removeStaleParts(jarFile.getParent().toFile());
        return true;
    }

    /**
     * A wrapper that was killed while it downloaded leaves its file. One that is a day old is no download in
     * progress.
     */
    private static void removeStaleParts(File directory) {
        File[] parts = directory.listFiles((dir, name) -> name.endsWith(PART_SUFFIX));
        if (parts == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (File stale : parts) {
            long modified = stale.lastModified();
            if (modified > 0 && now - modified > STALE_PART_MILLIS) {
                stale.delete();
            }
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // nothing more can be done about a file that cannot be removed
        }
    }

    /**
     * An exception as a message names it: its kind and what it says, without the credentials of a URL and
     * without control characters, since part of it can be what a server answered.
     */
    private static String describe(Throwable e) {
        String message = e.getMessage();
        String text = e.getClass().getSimpleName() + (message == null ? "" : ": " + GrailsWrapperRepo.textWithoutCredentials(message));
        text = CONTROL_CHARACTERS.matcher(text).replaceAll("?");
        return text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) + "..." : text;
    }

    private static InputStream retrieveMavenMetadata(GrailsWrapperRepo repo, String metadataUrl) throws IOException, GrailsReleaseNotFoundException {
        if (repo.isFile && !new File(metadataUrl).exists()) {
            throw new GrailsReleaseNotFoundException("Could not determine local metadata file from local maven repository: neither " + new File(metadataUrl).getAbsolutePath() + " nor a maven-metadata.xml beside it exists");
        }
        return open(repo, metadataUrl, new long[1]);
    }

    /**
     * Reads a metadata file of a repository. One that cannot be read or parsed is a miss of that repository; what
     * is said of it has nothing of the content of the file.
     */
    private static void parseMetadata(GrailsWrapperRepo repo, String metadataUrl, DefaultHandler handler) throws GrailsReleaseNotFoundException, GrailsDownloadException {
        String name = metadataUrl.substring(Math.max(metadataUrl.lastIndexOf('/'), metadataUrl.lastIndexOf(File.separatorChar)) + 1);
        try (InputStream stream = retrieveMavenMetadata(repo, metadataUrl)) {
            createSAXParser().parse(stream, handler);
        } catch (SAXParseException e) {
            throw new GrailsDownloadException("its " + name + " is not well-formed XML (line " + e.getLineNumber() + ")");
        } catch (SAXException | ParserConfigurationException e) {
            throw new GrailsDownloadException("its " + name + " could not be parsed");
        } catch (IllegalStateException e) {
            // of RootMetadataHandler, which repeats of a version it cannot read only the characters a version has
            throw new GrailsDownloadException("its " + name + " could not be read: " + e.getMessage());
        } catch (IOException e) {
            throw new GrailsDownloadException("its " + name + " could not be read: " + describe(e));
        }
    }

    private GrailsVersion getRootVersion(GrailsWrapperRepo repo) throws GrailsReleaseNotFoundException, GrailsDownloadException {
        RootMetadataHandler findLastReleaseHandler = new RootMetadataHandler(grailsWrapperHome.allowedReleaseTypes);
        parseMetadata(repo, repo.getRootMetadataUrl(), findLastReleaseHandler);

        List<GrailsVersion> foundVersions = findLastReleaseHandler.getVersions();
        if (foundVersions.isEmpty()) {
            throw new GrailsReleaseNotFoundException("No Grails Releases were found for the allowed types: " + grailsWrapperHome.allowedReleaseTypes.stream().map(Enum::name).collect(Collectors.joining(", ")));
        }

        Collections.sort(foundVersions);

        return foundVersions.get(0);
    }

    private String fetchSnapshotForVersion(GrailsWrapperRepo repo, GrailsVersion baseVersion) throws GrailsReleaseNotFoundException, GrailsDownloadException {
        System.out.println("...A Grails snapshot version has been detected. Downloading latest snapshot.");

        FindLastSnapshotHandler findVersionHandler = new FindLastSnapshotHandler();
        parseMetadata(repo, repo.getMetadataUrl(baseVersion), findVersionHandler);

        String snapshotVersion = findVersionHandler.getVersion();
        if (snapshotVersion == null) {
            throw new GrailsDownloadException("its metadata of the snapshot names no jar");
        }
        // it becomes part of the path of the jar
        if (!SNAPSHOT_VALUE.matcher(snapshotVersion).matches() || snapshotVersion.contains("..")) {
            throw new GrailsDownloadException("its metadata of the snapshot names what is no version");
        }
        return snapshotVersion;
    }

    static HttpURLConnection createHttpURLConnection(String mavenMetadataFileUrl) throws IOException {
        return createHttpURLConnection(mavenMetadataFileUrl, url -> (HttpURLConnection) url.openConnection());
    }

    @FunctionalInterface
    interface HttpURLConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    static HttpURLConnection createHttpURLConnection(String mavenMetadataFileUrl, HttpURLConnectionFactory openConnection) throws IOException {
        URI uri = createSecureRemoteUri(mavenMetadataFileUrl);
        for (int redirectCount = 0; ; redirectCount++) {
            URL url = uri.toURL();
            HttpURLConnection conn = openConnection.open(url);
            String location;
            try {
                conn.setRequestProperty("User-Agent", "Apache-Maven/3.9.6");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                conn.setReadTimeout(READ_TIMEOUT_MILLIS);
                conn.setInstanceFollowRedirects(false);

                int responseCode = conn.getResponseCode();
                if (!isRedirect(responseCode)) {
                    // Ownership of the open connection transfers to the caller.
                    return conn;
                }

                if (redirectCount >= MAX_REDIRECTS) {
                    throw new IOException("Too many redirects while downloading Grails wrapper remote resource: " + GrailsWrapperRepo.withoutCredentials(mavenMetadataFileUrl));
                }

                location = conn.getHeaderField("Location");
            }
            catch (IOException | RuntimeException e) {
                // Never leak a connection when request setup, the response code, or the
                // redirect cap fails partway through an iteration.
                closeQuietly(conn);
                throw e;
            }
            closeQuietly(conn);
            uri = resolveSecureRedirectUrl(uri, location);
        }
    }

    private static void closeQuietly(HttpURLConnection conn) {
        try {
            InputStream errorStream = conn.getErrorStream();
            if (errorStream != null) {
                errorStream.close();
            }
        } catch (IOException ignored) {
            // ignore cleanup failure
        } finally {
            conn.disconnect();
        }
    }

    static URL createSecureRemoteUrl(String mavenMetadataFileUrl) throws IOException {
        return createSecureRemoteUri(mavenMetadataFileUrl).toURL();
    }

    private static URI createSecureRemoteUri(String mavenMetadataFileUrl) throws IOException {
        try {
            URI uri = new URI(mavenMetadataFileUrl);
            validateHttpsUri(uri);
            return uri;
        } catch (URISyntaxException | IllegalArgumentException e) {
            // without the cause: its message is the URL as it was given
            throw new IOException("Invalid Maven metadata URL: " + GrailsWrapperRepo.withoutCredentials(mavenMetadataFileUrl));
        }
    }

    static URI resolveSecureRedirectUrl(URI currentUri, String location) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IOException("Redirect response is missing a Location header for Grails wrapper remote resource: " + GrailsWrapperRepo.withoutCredentials(currentUri.toString()));
        }
        try {
            URI redirectUri = currentUri.resolve(location);
            validateHttpsUri(redirectUri);
            return redirectUri;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid redirect Location header for Grails wrapper remote resource: " + GrailsWrapperRepo.withoutCredentials(location));
        }
    }

    private static boolean isRedirect(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
            responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
            responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
            responseCode == 307 ||
            responseCode == 308;
    }

    private static void validateHttpsUri(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Grails wrapper remote repository URLs must use HTTPS: " + GrailsWrapperRepo.withoutCredentials(uri.toString()));
        }
    }

    private static SAXParser createSAXParser() throws ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setNamespaceAware(true);
        return factory.newSAXParser();
    }
}
