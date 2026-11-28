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
import spock.lang.Unroll

import grails.plugins.exceptions.PluginException

class GrailsPluginSorterSpec extends Specification {

    void 'no plugins sort to no plugins'() {
        expect:
        sort([]).isEmpty()
    }

    void 'plugins the declarations leave unordered come in the order of their names'() {
        expect:
        sort([plugin('gamma'), plugin('alpha'), plugin('beta')])*.name == ['alpha', 'beta', 'gamma']
    }

    void 'a plugin comes after the plugins its after names'() {
        expect:
        sort([plugin('alpha', after: ['gamma']), plugin('beta'), plugin('gamma', after: ['beta'])])*.name ==
                ['beta', 'gamma', 'alpha']
    }

    void 'a plugin comes after the plugins it depends on, as if after named them'() {
        expect:
        sort([plugin('alpha', dependsOn: ['beta']), plugin('beta')])*.name == ['beta', 'alpha']
    }

    void 'a plugin comes before the plugins its before names'() {
        expect:
        sort([plugin('alpha'), plugin('beta'), plugin('zeta', before: ['alpha'])])*.name == ['beta', 'zeta', 'alpha']
    }

    void 'after, before and dependsOn combine into one order'() {
        expect:
        sort([plugin('a', after: ['d']), plugin('b', before: ['d']), plugin('c', dependsOn: ['b']), plugin('d')])*.name ==
                ['b', 'c', 'd', 'a']
    }

    void 'a plugin named in after or before that is absent is ignored'() {
        expect:
        sort([plugin('beta', after: ['missing']), plugin('alpha', before: ['alsoMissing'])])*.name == ['alpha', 'beta']
    }

    void 'the same declaration made by both plugins orders them once'() {
        expect:
        sort([plugin('beta', after: ['alpha']), plugin('alpha', before: ['beta'])])*.name == ['alpha', 'beta']
    }

    void 'a diamond comes out with every plugin after the plugins it comes after'() {
        expect:
        sort([plugin('d', after: ['b', 'c']), plugin('c', after: ['a']), plugin('b', after: ['a']), plugin('a')])*.name ==
                ['a', 'b', 'c', 'd']
    }

    @Unroll
    void 'the order does not depend on the order the plugins are found in (#permutation)'() {
        expect:
        sort(permutation)*.name == ['core', 'i18n', 'controllers', 'urlMappings', 'aaa', 'zzz']

        where:
        permutation << [plugin('core'), plugin('i18n', dependsOn: ['core']), plugin('controllers', dependsOn: ['core', 'i18n']),
                        plugin('urlMappings', dependsOn: ['core', 'controllers']), plugin('zzz', after: ['aaa']),
                        plugin('aaa', after: ['urlMappings'])].permutations().toList().subList(0, 24)
    }

    void 'a circular order fails, naming the plugins of the circle and why each comes after the other'() {
        when:
        sort([plugin('alpha', after: ['beta'], before: ['gamma']), plugin('beta', dependsOn: ['gamma']), plugin('gamma'),
              plugin('unrelated', after: ['alpha'])])

        then:
        PluginException e = thrown()
        e.message.contains('circular')
        e.message.contains('alpha')
        e.message.contains('beta')
        e.message.contains('gamma')
        !e.message.contains('unrelated')
        e.message.contains('alpha comes after beta')
        e.message.contains('beta comes after gamma')
        e.message.contains('alpha comes before gamma')
    }

    void 'a plugin ordered against itself fails'() {
        when:
        sort([plugin('alpha', after: ['alpha'])])

        then:
        PluginException e = thrown()
        e.message.contains('[alpha]')
    }

    void 'two plugins with the same name fail'() {
        when:
        sort([plugin('alpha'), plugin('alpha')])

        then:
        PluginException e = thrown()
        e.message.contains('[alpha]')
    }

    private static List<TestPlugin> sort(List<TestPlugin> plugins) {
        GrailsPluginSorter.sort(plugins, { TestPlugin p -> p.name }, { TestPlugin p -> p.dependsOn + p.after },
                { TestPlugin p -> p.before })
    }

    private static TestPlugin plugin(Map<String, List<String>> declarations = [:], String name) {
        new TestPlugin(name, declarations.dependsOn ?: [], declarations.after ?: [], declarations.before ?: [])
    }

    static class TestPlugin {
        final String name
        final List<String> dependsOn
        final List<String> after
        final List<String> before

        TestPlugin(String name, List<String> dependsOn, List<String> after, List<String> before) {
            this.name = name
            this.dependsOn = dependsOn
            this.after = after
            this.before = before
        }

        @Override
        String toString() {
            name
        }
    }
}
