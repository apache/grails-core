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
package org.grails.gradle.plugin.views.gsp

import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Specification
import spock.lang.TempDir

/**
 * The on-disk format is owned by {@code org.grails.taglib.index.TagLibraryIndex}, which this plugin
 * cannot reference: the generator is forked against the project's compile classpath precisely because
 * the framework is not on the plugin's own.
 *
 * <p>So the format is restated here, and pinned here. The framework side pins the same strings in
 * {@code TagLibraryIndexSpec}, so renaming either without the other fails a test rather than quietly
 * writing an index that nothing reads.
 */
class TagLibraryIndexFilesSpec extends Specification {

    @TempDir
    Path tempDir

    void 'the descriptor directory is the one the framework reads'() {
        expect: 'TagLibraryIndex.INDEX_LOCATION, without the trailing separator it uses for resources'
        TagLibraryIndexFiles.INDEX_LOCATION == 'META-INF/grails/taglibs'
    }

    void 'the settings file is the one the framework reads'() {
        expect: 'the file part of TagLibraryIndex.SETTINGS_LOCATION'
        TagLibraryIndexFiles.SETTINGS_FILE == 'compile-settings.properties'
    }

    void 'the settings keys are the ones the framework reads'() {
        expect:
        TagLibraryIndexFiles.STRICT_KEY == 'strictTags'
        TagLibraryIndexFiles.DYNAMIC_NAMESPACES_KEY == 'dynamicTagNamespaces'
        TagLibraryIndexFiles.UNQUALIFIED_KEY == 'unqualifiedTagCalls'
        TagLibraryIndexFiles.LOCAL_NAMESPACES_KEY == 'localNamespaces'
    }

    void 'unqualified tag calls default to off when the build says nothing'() {
        given:
        File destination = Files.createDirectory(tempDir.resolve('default')).toFile()

        when:
        TagLibraryIndexFiles.writeSettings(destination, false, [] as Set)

        then: 'a bare name is left to dispatch as it always did unless a build opts in'
        new File(destination, 'META-INF/grails/taglibs/compile-settings.properties').text
                .contains('unqualifiedTagCalls=false')
    }

    void 'settings are written under those keys, sorted, without a timestamp'() {
        given:
        File destination = Files.createDirectory(tempDir.resolve('out')).toFile()

        when:
        TagLibraryIndexFiles.writeSettings(destination, true, ['zeta', 'alpha'] as Set, true,
                ['mine', 'also'] as Set)

        then: 'sorted so that two otherwise identical builds produce identical output'
        new File(destination, 'META-INF/grails/taglibs/compile-settings.properties').text ==
                'dynamicTagNamespaces=alpha,zeta\nstrictTags=true\nunqualifiedTagCalls=true\n' +
                'localNamespaces=also,mine\n'
    }

    void 'the namespaces read back are the ones the descriptors declare'() {
        given:
        File destination = Files.createDirectory(tempDir.resolve('ns')).toFile()
        File indexDir = new File(destination, 'META-INF/grails/taglibs')
        indexDir.mkdirs()
        new File(indexDir, 'demo.OneTagLib.properties').text = 'class=demo.OneTagLib\nnamespace=mine\n'
        new File(indexDir, 'demo.TwoTagLib.properties').text = 'class=demo.TwoTagLib\nnamespace=other\n'
        TagLibraryIndexFiles.writeSettings(destination, false, [] as Set)

        expect: 'taken from what was generated, so a tag library that could not be read is not counted'
        TagLibraryIndexFiles.readNamespaces(destination) == ['mine', 'other'] as Set
    }

    void 'clearing removes descriptors but keeps the settings beside them'() {
        given:
        File destination = Files.createDirectory(tempDir.resolve('clear')).toFile()
        File indexDir = new File(destination, 'META-INF/grails/taglibs')
        indexDir.mkdirs()
        new File(indexDir, 'demo.OldTagLib.properties').text = 'class=demo.OldTagLib\n'
        TagLibraryIndexFiles.writeSettings(destination, false, [] as Set)

        when: 'a project that no longer declares the tag library is rebuilt'
        TagLibraryIndexFiles.clearIndex(destination)

        then: 'the stale descriptor is gone and the settings survive'
        !new File(indexDir, 'demo.OldTagLib.properties').exists()
        new File(indexDir, 'compile-settings.properties').exists()
    }
}
