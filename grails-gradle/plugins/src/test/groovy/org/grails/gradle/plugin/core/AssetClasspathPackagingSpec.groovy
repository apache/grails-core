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
package org.grails.gradle.plugin.core

import org.gradle.testkit.runner.BuildResult

/**
 * Covers compiled assets being readable from an executable jar.
 *
 * <p>The asset pipeline packages them at the root of the archive, which is where a war serves its
 * web content from. An executable jar has no web content: assets are read off the classpath, and
 * its classpath is {@code BOOT-INF/classes}. The same bytes at the same place, in a jar rather than
 * a war, are packaged but unreachable -- the page renders and every asset on it is a 404.</p>
 */
class AssetClasspathPackagingSpec extends GradleSpecification {

    void 'compiled assets are packaged where an executable jar reads its classpath'() {
        given:
        setupTestResourceProject('asset-classpath-packaging')

        when:
        BuildResult result = executeTask('inspectAssetPackaging')

        then:
        result.output.contains('ON_CLASSPATH=true')
        result.output.contains('ON_CLASSPATH_SVG=true')

        and: 'every compiled asset, not a sample of them'
        result.output.contains('ASSET_COUNT=2')
    }
}
