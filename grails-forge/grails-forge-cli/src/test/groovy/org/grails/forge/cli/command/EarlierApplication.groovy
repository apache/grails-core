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
package org.grails.forge.cli.command

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Stream
import org.grails.forge.util.VersionInfo

/**
 * Applications as released versions of Grails created them, kept under {@code earlier-applications}
 * in the test resources: the files a feature can change and those {@code project migrate} reads, as
 * the command line of that version wrote them, with {@code .gitignore} kept as {@code gitignore}.
 * <ul>
 * <li>{@code grails-8.0.0-shell-web}: {@code grails-shell-cli create-app com.example.legacy} of Grails 8.0.0, the web profile</li>
 * <li>{@code grails-8.0.0-forge-web}: {@code grails-forge-cli create-app com.example.legacy} of Grails 8.0.0</li>
 * <li>{@code grails-7.0.9-forge-web}: {@code grails-forge-cli create-app com.example.legacy} of Grails 7.0.9</li>
 * </ul>
 */
final class EarlierApplication {

    static final String GRAILS_8_SHELL = 'grails-8.0.0-shell-web'
    static final String GRAILS_8_FORGE = 'grails-8.0.0-forge-web'
    static final String GRAILS_7_FORGE = 'grails-7.0.9-forge-web'

    private EarlierApplication() {
    }

    /**
     * Copies an application into a directory, as it was created: with LF at the end of every line, as
     * the released versions wrote it on every system. The {@code .gitattributes} of the repository keeps
     * the files so on a checkout for Windows; a file with CR LF is refused, since the specs change and
     * compare its lines as the version wrote them.
     */
    static void copy(String name, File directory) {
        Path source = Path.of(EarlierApplication.getResource("/earlier-applications/${name}/build.gradle").toURI()).getParent()
        Stream<Path> files = Files.walk(source)
        try {
            files.filter { Path each -> Files.isRegularFile(each) }.forEach { Path each ->
                String path = source.relativize(each).join('/')
                if (Files.readAllBytes(each).contains((byte) 13)) {
                    throw new IllegalStateException("${name}/${path} has CR LF, which the version that created the application did not write: " +
                            'the .gitattributes of the repository keeps the files of earlier-applications with LF, also on a checkout for Windows')
                }
                Path target = directory.toPath().resolve(path == 'gitignore' ? '.gitignore' : path)
                Files.createDirectories(target.getParent())
                Files.copy(each, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            files.close()
        }
    }

    /**
     * Writes a plugin whose build does not say whether it has controllers: {@code project migrate}
     * assumes its type, and so writes its record without a baseline, the one record no feature is
     * added to.
     */
    static void pluginOfAssumedType(File directory) {
        new File(directory, 'grails-app/init/org/acme').mkdirs()
        new File(directory, 'settings.gradle').text = "rootProject.name = 'audit'\n"
        new File(directory, 'grails-app/init/org/acme/Application.groovy').text = 'package org.acme\n\nclass Application {\n}\n'
        new File(directory, 'build.gradle').text = 'plugins {\n    id "org.apache.grails.gradle.grails-plugin"\n}\n'
    }

    /**
     * What the upgrade notes ask of whoever moves the application to this version of Grails, as far
     * as the files that are kept show it: the version of Grails in {@code gradle.properties}, and the
     * dependency on the profile out of the build.
     */
    static void upgrade(File directory) {
        File properties = new File(directory, 'gradle.properties')
        properties.text = properties.text.replaceFirst(/(?m)^grailsVersion=.*$/, "grailsVersion=${VersionInfo.getGrailsVersion()}")
        File build = new File(directory, 'build.gradle')
        build.text = build.readLines().findAll { String line -> !(line =~ /^\s*profile\s/) }.join('\n') + '\n'
    }
}
