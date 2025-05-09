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

import grails.proxy.SystemPropertiesAuthenticator;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Start {

    private static final String PROJECT_NAME = "grails-wrapper-impl";
    private static final String WRAPPER_PATH = "/org/apache/grails/" + PROJECT_NAME;
    private static final String GRAILS_RELEASE_MAVEN_REPO_BASE_URL = "https://repository.apache.org/content/groups/public";
    private static final File WRAPPER_DIR = new File(System.getProperty("user.home") + "/.grails/wrapper");
    private static final File NO_VERSION_JAR = new File(WRAPPER_DIR, PROJECT_NAME + ".jar");

    private static String getMavenBaseUrl() {
        String baseUrl = System.getProperty("grails.maven.repo.baseUrl");
        if (baseUrl != null) {
            return baseUrl;
        }
        baseUrl = System.getenv("GRAILS_RELEASE_MAVEN_REPO_BASE_URL");
        if (baseUrl != null) {
            return baseUrl;
        }
        return GRAILS_RELEASE_MAVEN_REPO_BASE_URL;
    }

    private static String getSnapshotVersion(String baseVersion) {
        System.out.println("A Grails snapshot version has been detected.  Downloading latest snapshot.");
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            FindSnapshotHandler findVersionHandler = new FindSnapshotHandler();
            final String mavenMetadataFileUrl = getMavenBaseUrl() + WRAPPER_PATH + "/" + baseVersion + "/maven-metadata.xml";
            HttpURLConnection conn = createHttpURLConnection(mavenMetadataFileUrl);
            saxParser.parse(conn.getInputStream(), findVersionHandler);
            return findVersionHandler.getVersion();
        } catch (Exception e) {
            if (!NO_VERSION_JAR.exists()) {
                System.out.println("You must be connected to the internet the first time you use the Grails wrapper");
                e.printStackTrace();
                System.exit(1);
            }
            return null;
        }
    }

    private static String getVersion() {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            FindReleaseHandler findReleaseHandler = new FindReleaseHandler();
            final String mavenMetadataFileUrl = getMavenBaseUrl() + WRAPPER_PATH + "/maven-metadata.xml";
            HttpURLConnection conn = createHttpURLConnection(mavenMetadataFileUrl);
            saxParser.parse(conn.getInputStream(), findReleaseHandler);
            return findReleaseHandler.getVersion();
        } catch (Exception e) {
            if (!NO_VERSION_JAR.exists()) {
                System.out.println("You must be connected to the internet the first time you use the Grails wrapper");
                e.printStackTrace();
                System.exit(1);
            }
            return null;
        }
    }

    private static HttpURLConnection createHttpURLConnection(String mavenMetadataFileUrl) throws IOException {
        final URL url = new URL(mavenMetadataFileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Apache-Maven/3.9.6");
        conn.setInstanceFollowRedirects(true);
        return conn;
    }


    private static boolean updateJar(String baseVersion, String detailedVersion) {
        boolean success = false;

        final String localJarFilename = PROJECT_NAME + "-" + baseVersion;
        final String remoteJarFilename = detailedVersion != null ? PROJECT_NAME + "-" + detailedVersion : PROJECT_NAME + "-" + baseVersion;
        final String jarFileExtension = ".jar";

        if (WRAPPER_DIR.exists() || WRAPPER_DIR.mkdirs()) {
            try {
                File downloadedJar = File.createTempFile(localJarFilename, jarFileExtension);
                String wrapperUrl = getMavenBaseUrl() + WRAPPER_PATH + "/" + baseVersion + "/" + remoteJarFilename + jarFileExtension;
                HttpURLConnection conn = createHttpURLConnection(wrapperUrl);
                success = downloadWrapperJar(downloadedJar, conn.getInputStream());
            } catch (Exception e) {
                System.out.println("There was an error downloading the wrapper jar");
                e.printStackTrace();
            }
        }


        return success;
    }

    private static boolean downloadWrapperJar(File downloadedJar, InputStream inputStream) throws IOException {
        ReadableByteChannel rbc = Channels.newChannel(inputStream);
        try (FileOutputStream fos = new FileOutputStream(downloadedJar)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
        Files.move(downloadedJar.getAbsoluteFile().toPath(), NO_VERSION_JAR.getAbsoluteFile().toPath(), REPLACE_EXISTING);
        return true;
    }

    public static void main(String[] args) {
        Authenticator.setDefault(new SystemPropertiesAuthenticator());

        try {
            if (!NO_VERSION_JAR.exists() || (args.length > 0 && args[0].trim().equals("update-wrapper"))) {
                String baseVersion = getVersion();
                String detailedVersion = null;
                if (baseVersion != null && baseVersion.endsWith("SNAPSHOT")) {
                    detailedVersion = getSnapshotVersion(baseVersion);
                }
                updateJar(baseVersion, detailedVersion);
                if (args.length > 0 && args[0].trim().equals("update-wrapper")) {
                    args[0] = null;
                }
            }

            URLClassLoader child = new URLClassLoader(new URL[]{NO_VERSION_JAR.toURI().toURL()});
            Class<?> classToLoad = Class.forName("grails.init.RunCommand", true, child);
            Method main = classToLoad.getMethod("main", String[].class);
            main.invoke(null, (Object) args);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
