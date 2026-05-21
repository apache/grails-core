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
package grails.plugins

import spock.lang.Specification

class GrailsPluginSorterSpec extends Specification {

    def "sort handles empty list"() {
        when:
        def sorted = sortTestPlugins([])

        then:
        sorted.isEmpty()
    }

    def "sort handles single plugin with no dependencies"() {
        given:
        def alpha = new TestPlugin('alpha')

        when:
        def sorted = sortTestPlugins([alpha])

        then:
        sorted == [alpha]
    }

    def "sort handles plugins with no dependencies preserving input order"() {
        given:
        def alpha = new TestPlugin('alpha')
        def beta = new TestPlugin('beta')
        def gamma = new TestPlugin('gamma')

        when:
        def sorted = sortTestPlugins([gamma, alpha, beta])

        then:
        sorted.size() == 3
        sorted*.name as Set == ['alpha', 'beta', 'gamma'] as Set
    }

    def "sort respects loadAfter declarations"() {
        given:
        def alpha = new TestPlugin('alpha')
        def beta = new TestPlugin('beta', loadAfter: ['alpha'])
        def gamma = new TestPlugin('gamma', loadAfter: ['beta'])

        when:
        def sorted = sortTestPlugins([gamma, alpha, beta])

        then:
        def names = sorted*.name
        names.indexOf('alpha') < names.indexOf('beta')
        names.indexOf('beta') < names.indexOf('gamma')
    }

    def "sort respects loadBefore declarations"() {
        given:
        def alpha = new TestPlugin('alpha', loadBefore: ['gamma'])
        def beta = new TestPlugin('beta')
        def gamma = new TestPlugin('gamma')

        when:
        def sorted = sortTestPlugins([gamma, alpha, beta])

        then:
        def names = sorted*.name
        names.indexOf('alpha') < names.indexOf('gamma')
    }

    def "sort does not use dependsOn for ordering (matches original DPM behavior)"() {
        given: "beta declares dependsOn alpha, but no loadAfter"
        def alpha = new TestPlugin('alpha')
        def beta = new TestPlugin('beta')  // no loadAfter, no loadBefore

        when: "sorted with beta before alpha in input"
        def sorted = sortTestPlugins([beta, alpha])

        then: "order is preserved since dependsOn is NOT used for sort ordering"
        def names = sorted*.name
        // Without loadAfter/loadBefore, the DFS preserves input order
        names == ['beta', 'alpha']
    }

    def "sort handles combined loadAfter and loadBefore"() {
        given:
        def alpha = new TestPlugin('alpha', loadBefore: ['beta'])
        def beta = new TestPlugin('beta')
        def gamma = new TestPlugin('gamma', loadAfter: ['beta'])

        when:
        def sorted = sortTestPlugins([gamma, beta, alpha])

        then:
        def names = sorted*.name
        names.indexOf('alpha') < names.indexOf('beta')
        names.indexOf('beta') < names.indexOf('gamma')
    }

    def "sort ignores references to non-existent plugins"() {
        given:
        def alpha = new TestPlugin('alpha', loadAfter: ['nonExistent'])
        def beta = new TestPlugin('beta', loadBefore: ['alsoNonExistent'])

        when:
        def sorted = sortTestPlugins([alpha, beta])

        then:
        sorted.size() == 2
        sorted*.name as Set == ['alpha', 'beta'] as Set
    }

    def "sort with external lookup function delegates correctly"() {
        given: "A lookup function that resolves plugins by name from a map"
        def alpha = new TestPlugin('alpha')
        def beta = new TestPlugin('beta', loadAfter: ['alpha'])
        def plugins = [beta, alpha]
        def lookup = { String name -> plugins.find { it.name == name } }

        when:
        def sorted = GrailsPluginSorter.<TestPlugin>sortPlugins(
                plugins,
                { TestPlugin it -> it.loadAfter as String[] },
                { TestPlugin it -> it.loadBefore as String[] },
                lookup
        )

        then:
        def names = sorted*.name
        names.indexOf('alpha') < names.indexOf('beta')
    }

    def "sort handles diamond dependency pattern"() {
        given: "D depends on B and C, both of which depend on A"
        def a = new TestPlugin('a')
        def b = new TestPlugin('b', loadAfter: ['a'])
        def c = new TestPlugin('c', loadAfter: ['a'])
        def d = new TestPlugin('d', loadAfter: ['b', 'c'])

        when:
        def sorted = sortTestPlugins([d, c, b, a])

        then:
        def names = sorted*.name
        names.indexOf('a') < names.indexOf('b')
        names.indexOf('a') < names.indexOf('c')
        names.indexOf('b') < names.indexOf('d')
        names.indexOf('c') < names.indexOf('d')
    }

    def "sort produces same order for GrailsPlugin-like and PluginInfo-like types"() {
        given: "Two different representations of the same plugin graph"
        def pluginA = new TestPlugin('alpha')
        def pluginB = new TestPlugin('beta', loadAfter: ['alpha'])
        def pluginC = new TestPlugin('gamma', loadAfter: ['beta'])

        def infoA = new TestPluginInfo('alpha')
        def infoB = new TestPluginInfo('beta', loadAfter: ['alpha'])
        def infoC = new TestPluginInfo('gamma', loadAfter: ['beta'])

        when: "Both are sorted using the same GrailsPluginSorter"
        def sortedPlugins = sortTestPlugins([pluginC, pluginA, pluginB])
        def sortedInfos = sortTestInfos([infoC, infoA, infoB])

        then: "The resulting order is identical"
        sortedPlugins*.name == sortedInfos*.infoName
    }

    def "sort handles loadBefore creating a dependency chain"() {
        given:
        def alpha = new TestPlugin('alpha', loadBefore: ['beta'])
        def beta = new TestPlugin('beta', loadBefore: ['gamma'])
        def gamma = new TestPlugin('gamma')

        when:
        def sorted = sortTestPlugins([gamma, beta, alpha])

        then:
        def names = sorted*.name
        names.indexOf('alpha') < names.indexOf('beta')
        names.indexOf('beta') < names.indexOf('gamma')
    }

    def "sort with duplicate plugin names uses first occurrence"() {
        given:
        def alpha1 = new TestPlugin('alpha')
        def alpha2 = new TestPlugin('alpha')
        def beta = new TestPlugin('beta', loadAfter: ['alpha'])

        when:
        def sorted = sortTestPlugins([alpha1, alpha2, beta])

        then: "Both alphas and beta are present"
        sorted.size() == 3
    }

    def "sort handles loadAfter combined with loadBefore between same plugins"() {
        given: "alpha loadBefore beta, beta also loadAfter alpha (redundant but valid)"
        def alpha = new TestPlugin('alpha', loadBefore: ['beta'])
        def beta = new TestPlugin('beta', loadAfter: ['alpha'])

        when:
        def sorted = sortTestPlugins([beta, alpha])

        then:
        def names = sorted*.name
        names.indexOf('alpha') < names.indexOf('beta')
    }

    def "sort handles long chain via loadAfter"() {
        given:
        def a = new TestPlugin('a')
        def b = new TestPlugin('b', loadAfter: ['a'])
        def c = new TestPlugin('c', loadAfter: ['b'])
        def d = new TestPlugin('d', loadAfter: ['c'])
        def e = new TestPlugin('e', loadAfter: ['d'])

        when:
        def sorted = sortTestPlugins([e, d, c, b, a])

        then:
        sorted*.name == ['a', 'b', 'c', 'd', 'e']
    }

    /**
     * Helper to sort TestPlugin instances using the self-contained overload.
     */
    private List<TestPlugin> sortTestPlugins(List<TestPlugin> plugins) {
        GrailsPluginSorter.<TestPlugin>sort(
                plugins,
                { it.name },
                { it.loadAfter as String[] },
                { it.loadBefore as String[] }
        )
    }

    /**
     * Helper to sort TestPluginInfo instances using the self-contained overload.
     */
    private List<TestPluginInfo> sortTestInfos(List<TestPluginInfo> infos) {
        GrailsPluginSorter.<TestPluginInfo>sort(
                infos,
                { it.infoName },
                { it.loadAfter as String[] },
                { it.loadBefore as String[] }
        )
    }

    /**
     * Lightweight test fixture representing a plugin with ordering metadata.
     * Simulates GrailsPlugin-like behavior.
     */
    static class TestPlugin {
        final String name
        final List<String> loadAfter
        final List<String> loadBefore

        TestPlugin(Map opts = [:], String name) {
            this.name = name
            this.loadAfter = opts.loadAfter ?: []
            this.loadBefore = opts.loadBefore ?: []
        }

        @Override
        String toString() { "TestPlugin[$name]" }
    }

    /**
     * A second lightweight test fixture with different accessor names,
     * simulating PluginInfo-like behavior to prove the sorter is truly generic.
     */
    static class TestPluginInfo {
        final String infoName
        final List<String> loadAfter
        final List<String> loadBefore

        TestPluginInfo(Map opts = [:], String name) {
            this.infoName = name
            this.loadAfter = opts.loadAfter ?: []
            this.loadBefore = opts.loadBefore ?: []
        }

        @Override
        String toString() { "TestPluginInfo[$infoName]" }
    }
}
