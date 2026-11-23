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

import java.lang.reflect.Method;
import java.net.Authenticator;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

import grails.proxy.SystemPropertiesAuthenticator;

/**
 * The purpose of this class is to download the expanded Grails wrapper jars into GRAILS_WRAPPER_HOME (defaults to `.grails` in the user home folder)
 * This class is not meant to be distributed as part of SDKMAN since we'll distribute the expanded jars with it.
 * After downloading the jars, it will delegate to the downloaded grails-cli project.
 * <p>
 * There are 3 ways this class can be used:
 * 1. in testing a grails release (run from a non-project directory) - requires GRAILS_REPO_URL set to `~/.m2/repository`
 * 2. running from a non-project directory (end user usage)
 * 3. running from inside a grails project
 */
public class GrailsWrapper {

    private static final int FIRST_MAJOR_WITH_UPDATE_WRAPPER = 9;

    // the version a wrapper says it is of when it runs from its classes and not from its jar, which only its development does
    private static final String UNKNOWN_VERSION = "unknown";

    public GrailsWrapper() {
        Authenticator.setDefault(new SystemPropertiesAuthenticator());
    }

    public void execute(String[] args) throws Exception {
        GrailsVersion preferredGrailsVersion = GrailsVersion.getPreferredGrailsVersion();
        LinkedHashSet<GrailsReleaseType> allowedTypes = GrailsVersion.getAllowedReleaseTypes(preferredGrailsVersion, this);

        GrailsUpdater updater = new GrailsUpdater(allowedTypes, preferredGrailsVersion);
        boolean forceUpdate = (args.length > 0 && args[0].trim().equals("update-wrapper"));

        boolean updated = false;
        String[] adjustedArgs = args;
        // A file that is installed and is no jar that can be started is what an interrupted download left with a
        // wrapper that did not look into what it downloaded. It is downloaded again, once.
        String installedDefect = updater.getInstalledDefect();
        if (installedDefect != null) {
            System.out.println("The installed Grails command line at [" + updater.getExecutedJarFile().getAbsolutePath() + "] cannot be started (" + installedDefect + "). Downloading it again.");
        }
        if (installedDefect != null || forceUpdate || updater.needsUpdating()) {
            String allowTypesString = allowedTypes.stream().map(GrailsReleaseType::name).collect(Collectors.joining(","));
            System.out.printf("Updating Grails wrapper, allowed versions to update to are [%s]...%n", allowTypesString);

            updated = installedDefect != null ? updater.repair() : updater.update();

            // The command line of Grails 9 and later has the command update-wrapper, which replaces the wrapper
            // that is committed in the project by the one of its version: it is given the argument. An earlier
            // command line has no such command, and for it the argument only asked for this download.
            if (forceUpdate && !hasUpdateWrapperCommand(updater.getSelectedVersion())) {
                adjustedArgs = Arrays.copyOfRange(args, 1, args.length);
            }
        }

        if (updated) {
            System.out.println("Updated wrapper to version: " + updater.getSelectedVersion().toString());
        }

        // every download is verified before it is installed, so this is a file no download of this run replaced
        String defect = updater.getInstalledDefect();
        if (defect != null) {
            throw new GrailsWrapperException("The Grails command line at [" + updater.getExecutedJarFile().getAbsolutePath() + "] cannot be started (" + defect + ")" +
                    (installedDefect != null ? ", and downloading it again did not replace it" : "") + ". Delete the file and run the command again.");
        }

        // every grails-cli jar has the start class at one name, and the class starts the command line of its jar,
        // so a wrapper committed in an application starts a later version of Grails wherever that has its command line
        Class<?> classToLoad = StartClass.load(updater.getExecutedJarFile());
        Method main = classToLoad.getMethod("main", String[].class);
        // what the command line finds through the context class loader (services, the configuration of
        // its logging, the generator it starts) is in the downloaded jar and not on the class path of the wrapper
        Thread.currentThread().setContextClassLoader(classToLoad.getClassLoader());
        // the start class of Grails 9 and later replaces the wrapper of a project when the wrapper does not say it
        // is no wrapper of Grails 7 or 8; that of Grails 7 and 8 reads nothing
        String version = getVersion();
        System.setProperty(StartClass.WRAPPER_VERSION_PROPERTY, version != null ? version : UNKNOWN_VERSION);
        main.invoke(null, (Object) adjustedArgs);
    }

    /**
     * @param version the version of the command line that is started
     * @return whether that command line has the command {@code update-wrapper}, as every one since Grails 9 has
     */
    static boolean hasUpdateWrapperCommand(GrailsVersion version) {
        return version != null && version.major >= FIRST_MAJOR_WITH_UPDATE_WRAPPER;
    }

    /**
     * @return the version of the packaged wrapper, if not packaged null
     */
    public String getVersion() {
        return Start.class.getPackage().getImplementationVersion();
    }
}
