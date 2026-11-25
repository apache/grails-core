/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.forge.cli

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.artefact.FeatureArtefact
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.core.state.EarlierRecord
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.springframework.context.ApplicationContext
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.DefaultFeature
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Language
import org.grails.forge.options.Options
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.util.NameUtils
import java.util.stream.Collectors

@CompileStatic
class CodeGenConfig {

    private ApplicationType applicationType
    private String defaultPackage
    private DevelopmentReloading reloading
    private Language sourceLanguage
    private List<String> features
    private List<String> excludedFeatures = Collections.emptyList()

    private List<FeatureArtefact> artefacts = Collections.emptyList()

    private boolean legacy
    private File projectDirectory

    private static final int MAX_EARLIER_RECORD = 256 * 1024
    private static final int MAX_VALUE = 255
    private static final int MAX_FEATURES = 1000

    ApplicationType getApplicationType() {
        return applicationType
    }

    void setApplicationType(ApplicationType applicationType) {
        this.applicationType = applicationType
    }

    String getDefaultPackage() {
        return defaultPackage
    }

    void setDefaultPackage(String defaultPackage) {
        this.defaultPackage = defaultPackage
    }

    DevelopmentReloading getReloading() {
        return reloading
    }

    void setReloading(DevelopmentReloading reloading) {
        this.reloading = reloading
    }

    Language getSourceLanguage() {
        return sourceLanguage
    }

    void setSourceLanguage(Language sourceLanguage) {
        this.sourceLanguage = sourceLanguage
    }

    List<String> getFeatures() {
        return features
    }

    void setFeatures(List<String> features) {
        this.features = features
    }

    /**
     * @return the names of the features that were excluded when the application was created
     */
    List<String> getExcludedFeatures() {
        return excludedFeatures
    }

    void setExcludedFeatures(List<String> excludedFeatures) {
        this.excludedFeatures = excludedFeatures
    }

    /**
     * @return the commands the features of the application give the command line, as the record of
     * the project has them, in the order of the features; none for a record that is not the one Grails
     * writes when it generates an application
     */
    List<FeatureArtefact> getArtefacts() {
        return artefacts
    }

    void setArtefacts(List<FeatureArtefact> artefacts) {
        this.artefacts = artefacts
    }

    boolean isLegacy() {
        return legacy
    }

    /**
     * @return the directory of the project the record was read in, which is where the commands
     * write; {@code null} for a configuration that was not read from a directory
     */
    File getProjectDirectory() {
        return projectDirectory
    }

    /**
     * Reads the record of the project the current directory is in: of the current directory, or of
     * the nearest directory above it that is a project.
     */
    static CodeGenConfig load(ApplicationContext beanContext, ConsoleOutput consoleOutput) {
        return load(ForgeContext.of(beanContext), consoleOutput)
    }

    /**
     * Reads the record of the project the current directory is in, as {@link #load(ApplicationContext, ConsoleOutput)}
     * does. The Forge application is asked for only by a record of the oldest kind, which names a
     * profile and not the features of the application; every other record is read without it.
     */
    static CodeGenConfig load(ForgeContext beanContext, ConsoleOutput consoleOutput) {
        try {
            File current = FileSystemOutputHandler.getDefaultBaseDirectory()
            return load(beanContext, ConsoleProject.directoryFor(current.toPath()).toFile(), consoleOutput)
        } catch (IOException e) {
            return null
        }
    }

    static CodeGenConfig load(ApplicationContext beanContext, File directory, ConsoleOutput consoleOutput) {
        return load(ForgeContext.of(beanContext), directory, consoleOutput)
    }

    static CodeGenConfig load(ForgeContext beanContext, File directory, ConsoleOutput consoleOutput) {
        CodeGenConfig codeGenConfig = read(beanContext, directory, consoleOutput)
        if (codeGenConfig != null) {
            codeGenConfig.projectDirectory = directory
        }
        return codeGenConfig
    }

    private static CodeGenConfig read(ForgeContext beanContext, File directory, ConsoleOutput consoleOutput) {

        // A project generated by Grails 9 has its state under .grails, which is what the commands go
        // by; grails-forge-cli.yml is read for a project that has none, and for one whose state cannot
        // be used. Only the manifest and the lock are read: the commands need nothing of the baseline
        if (ProjectState.exists(directory.toPath())) {
            try {
                ProjectState state = ProjectState.readSelection(directory.toPath())
                CodeGenConfig codeGenConfig = new CodeGenConfig()
                codeGenConfig.setApplicationType(state.manifest.applicationType)
                codeGenConfig.setDefaultPackage(state.manifest.defaultPackage)
                codeGenConfig.setReloading(state.manifest.options.reloading)
                codeGenConfig.setSourceLanguage(Language.DEFAULT_OPTION)
                codeGenConfig.setFeatures(new ArrayList<String>(state.lock.featureNames))
                codeGenConfig.setExcludedFeatures(new ArrayList<String>(state.manifest.excludedFeatures*.name))
                codeGenConfig.setArtefacts(FeatureArtefact.of(state.lock))
                warnOfAnotherRecord(beanContext, directory, codeGenConfig, consoleOutput)
                return codeGenConfig
            } catch (ProjectStateException e) {
                notUsed(consoleOutput)
                consoleOutput.warning(e.getMessage())
            }
        }
        return loadEarlierRecord(beanContext, directory, consoleOutput)
    }

    private static void notUsed(ConsoleOutput consoleOutput) {
        if (consoleOutput instanceof RecordListener) {
            ((RecordListener) consoleOutput).recordNotUsed()
        }
    }

    /**
     * What a record of a project is read for can be this as well as where the reading says what it
     * has to say: it is then told that a record is there and cannot be used, which is more than a
     * warning about a record that is used.
     */
    interface RecordListener {

        void recordNotUsed()
    }

    // A project has grails-forge-cli.yml beside its state when a version that wrote both generated it,
    // or when the file was put there. When it does not say what the state says, as after a change by
    // hand, which changed what the commands did before there was a state, it is not followed, and is
    // not passed over in silence either
    private static void warnOfAnotherRecord(ForgeContext beanContext, File directory, CodeGenConfig config, ConsoleOutput consoleOutput) {
        File record = new File(directory, ProjectState.EARLIER_RECORD)
        if (!record.isFile() || record.length() > MAX_EARLIER_RECORD) {
            return
        }
        // an earlier record that cannot be used is not compared, and nothing is said of it: the state is what counts
        CodeGenConfig earlier = loadEarlierRecord(beanContext, directory, ConsoleOutput.NOOP)
        if (earlier == null || earlier.legacy) {
            return
        }
        List<String> different = []
        if (earlier.defaultPackage != null && earlier.defaultPackage != config.defaultPackage) {
            different << 'the default package'
        }
        if (earlier.features != null && new HashSet<String>(earlier.features) != new HashSet<String>(config.features)) {
            different << 'the features'
        }
        if (different) {
            consoleOutput.warning("${ProjectState.EARLIER_RECORD} and ${ProjectState.DIRECTORY} do not say the same about ${different.join(' and ')} of the project; " +
                    "the commands go by ${ProjectState.DIRECTORY}, the record of how the project was generated")
        }
    }

    /**
     * Reads {@code grails-forge-cli.yml}, or {@code grails-cli.yml} before it. The file is whatever the
     * directory holds, of a project that was cloned as well: it is read within bounds and without
     * anything of YAML the file has no use for, and every value of it is held to what it has to be.
     * A file that cannot be used is said to be, in one line, and the directory is then one without a
     * project for the commands.
     */
    private static CodeGenConfig loadEarlierRecord(ForgeContext beanContext, File directory, ConsoleOutput consoleOutput) {

        File grailsCli = new File(directory, 'grails-forge-cli.yml')

        if (!grailsCli.exists()) {
            grailsCli = new File(directory, 'grails-cli.yml')
        }
        if (!grailsCli.exists()) {
            return null
        }
        try {
            return earlierRecord(beanContext, EarlierRecord.read(grailsCli.toPath()), grailsCli.getName(), consoleOutput)
        } catch (ProjectStateException e) {
            notUsed(consoleOutput)
            consoleOutput.warning("${e.getMessage()}; the commands that create the classes of an application are not there without it")
        } catch (ForgeContext.NotStartedException e) {
            // a record that names a profile says which features the application has only through the generator
            notUsed(consoleOutput)
            consoleOutput.warning("${grailsCli.getName()} cannot be used: ${e.getMessage()}")
        } catch (RuntimeException e) {
            notUsed(consoleOutput)
            // nothing of the file is repeated: what failed was not foreseen, and neither is what it would say
            consoleOutput.warning("${grailsCli.getName()} cannot be used (${e.getClass().getSimpleName()}); " +
                    'the commands that create the classes of an application are not there without it')
        }
        return null
    }

    private static CodeGenConfig earlierRecord(ForgeContext beanContext, Map<String, Object> map, String file, ConsoleOutput consoleOutput) {
        CodeGenConfig codeGenConfig = new CodeGenConfig()
        String type = text(map, 'applicationType', file)
        if (type != null) {
            codeGenConfig.setApplicationType(ApplicationType.values().find { ApplicationType each -> each.name().equalsIgnoreCase(type.replace('-', '_')) })
            if (codeGenConfig.getApplicationType() == null) {
                throw new ProjectStateException("${file}: applicationType is not a type of application, but '${EarlierRecord.shown(type)}'")
            }
        }
        String defaultPackage = text(map, 'defaultPackage', file)
        if (defaultPackage == null || !NameUtils.isValidJavaPackage(defaultPackage)) {
            throw new ProjectStateException("${file}: defaultPackage ${defaultPackage == null ? 'is missing' : 'is not the name of a package'}")
        }
        codeGenConfig.setDefaultPackage(defaultPackage)
        String reloading = text(map, 'reloading', file)
        if (reloading != null) {
            codeGenConfig.setReloading(DevelopmentReloading.values().find { DevelopmentReloading each -> each.getName() == reloading })
            if (codeGenConfig.getReloading() == null) {
                throw new ProjectStateException("${file}: reloading is not a way an application reloads, but '${EarlierRecord.shown(reloading)}'")
            }
        }
        String language = text(map, 'sourceLanguage', file)
        if (language != null) {
            codeGenConfig.setSourceLanguage(Language.values().find { Language each -> each.name().equalsIgnoreCase(language) })
            if (codeGenConfig.getSourceLanguage() == null) {
                throw new ProjectStateException("${file}: sourceLanguage is not a language of an application, but '${EarlierRecord.shown(language)}'")
            }
        }
        codeGenConfig.setFeatures(names(map, 'features', file))
        List<String> excluded = names(map, 'excludedFeatures', file)
        if (excluded != null) {
            codeGenConfig.setExcludedFeatures(excluded)
        }

        if (map.containsKey('testFramework') && !map.containsKey('reloading')) {
            codeGenConfig.setReloading(DevelopmentReloading.NONE)
        }

        if (map.containsKey('profile')) {
            codeGenConfig.legacy = true
            String profile = text(map, 'profile', file)
            if ('web' == profile) {
                codeGenConfig.setApplicationType(ApplicationType.WEB)
            } else if ('rest-api' == profile) {
                codeGenConfig.setApplicationType(ApplicationType.REST_API)
            } else if ('plugin' == profile) {
                codeGenConfig.setApplicationType(ApplicationType.PLUGIN)
            } else if ('web-plugin' == profile) {
                codeGenConfig.setApplicationType(ApplicationType.WEB_PLUGIN)
            } else if ('rest-api-plugin' == profile) {
                codeGenConfig.setApplicationType(ApplicationType.REST_API_PLUGIN)
            } else {
                // said, as every record that cannot be used is: the directory is not one without a record
                throw new ProjectStateException("${file}: ${profile == null ? 'profile has no value' : "the profile ${EarlierRecord.shown(profile)} has no type of application in this version of Grails"}")
            }
            // such a record names no way of reloading, and the defaults of an application are asked with one
            if (codeGenConfig.getReloading() == null) {
                codeGenConfig.setReloading(DevelopmentReloading.NONE)
            }

            AvailableFeatures availableFeatures = beanContext.get().getBean(FeatureRegistry)
                    .availableFeatures(codeGenConfig.getApplicationType())

            codeGenConfig.setFeatures(availableFeatures.getAllFeatures()
                    .filter(f -> f instanceof DefaultFeature)
                    .map { (DefaultFeature) it }
                    .filter(f -> f.shouldApply(
                            codeGenConfig.getApplicationType(),
                            new Options(codeGenConfig.getReloading(), JdkVersion.DEFAULT_OPTION),
                            new HashSet<>()))
                    .map(Feature::getName)
                    .collect(Collectors.toList()))

            consoleOutput.warning("${file} names a profile, as the record of an earlier command line did, and not the features of the application: the commands take " +
                    "the application to have the features an application of the type ${codeGenConfig.getApplicationType().name().toLowerCase(Locale.ROOT).replace('_', '-')} " +
                    'has by default. project migrate writes the record of this version, from what the project has')
        }
        // the commands ask what kind of application it is and which features it has
        if (codeGenConfig.getApplicationType() == null || codeGenConfig.getFeatures() == null) {
            throw new ProjectStateException("${file}: ${codeGenConfig.getApplicationType() == null ? 'applicationType' : 'features'} is missing")
        }
        return codeGenConfig
    }

    // a value that is one line of text, or nothing
    private static String text(Map<String, Object> map, String key, String file) {
        Object value = map.get(key)
        if (value == null || value == '') {
            return null
        }
        if (!(value instanceof String) || !EarlierRecord.isLine((String) value, MAX_VALUE)) {
            throw new ProjectStateException("${file}: ${key} must be one line of at most ${MAX_VALUE} characters, none of them a control character or one that is not shown")
        }
        return (String) value
    }

    // a list of the names of features, or nothing
    private static List<String> names(Map<String, Object> map, String key, String file) {
        Object value = map.get(key)
        if (value == null || value == '') {
            return null
        }
        if (!(value instanceof List) || ((List) value).size() > MAX_FEATURES) {
            throw new ProjectStateException("${file}: ${key} must be a list of at most ${MAX_FEATURES} names of features")
        }
        List<String> names = new ArrayList<>()
        for (Object each : (List) value) {
            if (!(each instanceof String) || !EarlierRecord.FEATURE_NAME.matcher((String) each).matches()) {
                throw new ProjectStateException("${file}: ${key} has an entry that is not the name of a feature")
            }
            names.add((String) each)
        }
        return names
    }
}
