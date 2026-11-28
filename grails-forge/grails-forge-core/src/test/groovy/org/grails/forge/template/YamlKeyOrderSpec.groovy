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
package org.grails.forge.template

import spock.lang.Specification
import spock.lang.Unroll

class YamlKeyOrderSpec extends Specification {

    void "the sections an application always has come first, in their order, and every other key follows alphabetically"() {
        given:
        Map<String, Object> config = [zeta: 1, grails: [views: 1, codegen: 2, cache: 3], Spring: 1, hibernate: [:], 'quartz.autoStartup': true, environments: [:],
                                      dataSource: [username: 'sa', driverClassName: 'x', password: ''], micronaut: 1, info: [app: [version: 1, name: 2, grailsVersion: 3]], alpha: 1]

        when:
        Map<String, Object> ordered = YamlKeyOrder.canonical(config)

        then:
        ordered.keySet().toList() == ['info', 'dataSource', 'environments', 'hibernate', 'grails', 'alpha', 'micronaut', 'quartz.autoStartup', 'Spring', 'zeta']
        (ordered.grails as Map).keySet().toList() == ['cache', 'codegen', 'views']
        (ordered.dataSource as Map).keySet().toList() == ['driverClassName', 'password', 'username']
        ((ordered.info as Map).app as Map).keySet().toList() == ['grailsVersion', 'name', 'version']

        and: 'nothing but the order is another, and the map that was given is as it was'
        ordered == config
        config.keySet().first() == 'zeta'
    }

    void "the environments are in the order development, test, production, then the others, and each has its keys as the top of the file has them"() {
        given:
        Map<String, Object> config = [environments: [staging: [a: 1], production: [grails: [b: 1, a: 2], dataSource: [url: 'p']], test: [dataSource: [url: 't', dbCreate: 'update']],
                                                     custom: [:], development: [zeta: 1, hibernate: [:], dataSource: [url: 'd']]]]

        when:
        Map<String, Object> environments = YamlKeyOrder.canonical(config).environments as Map<String, Object>

        then:
        environments.keySet().toList() == ['development', 'test', 'production', 'custom', 'staging']
        (environments.development as Map).keySet().toList() == ['dataSource', 'hibernate', 'zeta']
        (environments.production as Map).keySet().toList() == ['dataSource', 'grails']
        ((environments.production as Map).grails as Map).keySet().toList() == ['a', 'b']
        ((environments.test as Map).dataSource as Map).keySet().toList() == ['dbCreate', 'url']
    }

    void "a list keeps the order of its elements, and a map in it has its keys in alphabetical order"() {
        expect:
        YamlKeyOrder.canonical([grails: [list: ['b', 'a', [y: 1, x: 2]]]]).grails.list.toString() == '[b, a, [x:2, y:1]]'
    }

    void "a key that is written with dots is where its first step would be"() {
        expect:
        YamlKeyOrder.canonical(['spring.main.banner-mode': 'off', 'grails.profile': 'web', beta: 1, 'info.app.name': 'x']).keySet().toList() ==
                ['info.app.name', 'grails.profile', 'beta', 'spring.main.banner-mode']
    }

    @Unroll
    void "the entries of #setting keep the order they were declared in, at the top of a file and in an environment, and what is around and below them is alphabetical"() {
        given:
        Map<String, Object> entries = [json: [zeta: 1, alpha: 2], html: ['text/html', 'application/xhtml+xml'], all: '*/*']
        Map<String, Object> nested = entries
        for (String step : setting.split(/\./).reverse()) {
            nested = [zz: 1, (step): nested, aa: 2] as Map<String, Object>
        }

        when:
        Map<String, Object> ordered = YamlKeyOrder.canonical(nested + [environments: [production: nested]])

        then:
        at(ordered, setting).keySet().toList() == ['json', 'html', 'all']
        at(ordered.environments.production as Map, setting).keySet().toList() == ['json', 'html', 'all']
        (at(ordered, setting).json as Map).keySet().toList() == ['alpha', 'zeta']
        ordered.keySet().toList() == ['environments', 'grails', 'aa', 'zz']
        (ordered.grails as Map).keySet().toList() == ['aa', setting.split(/\./)[1], 'zz']

        and: 'written with dots in a key, the setting is the same one'
        (YamlKeyOrder.canonical([(setting): entries]).get(setting) as Map).keySet().toList() == ['json', 'html', 'all']
        ((YamlKeyOrder.canonical([(setting.substring(0, setting.lastIndexOf('.'))): [(setting.substring(setting.lastIndexOf('.') + 1)): entries, a: 1]])
                .values().first() as Map).values().last() as Map).keySet().toList() == ['json', 'html', 'all']

        where:
        setting << YamlKeyOrder.DECLARED.toList().sort()
    }

    void "a map of the name of such a setting that is elsewhere, as in a list or below another key, is in alphabetical order"() {
        expect:
        YamlKeyOrder.canonical([mine: [grails: [mime: [types: [json: 1, html: 2]]]]]).mine.grails.mime.types.keySet().toList() == ['html', 'json']
        YamlKeyOrder.canonical([grails: [[mime: [types: [json: 1, html: 2]]]]]).grails[0].mime.types.keySet().toList() == ['html', 'json']
        YamlKeyOrder.canonical([grails: [mime: [typesOfMine: [json: 1, html: 2]]]]).grails.mime.typesOfMine.keySet().toList() == ['html', 'json']
    }

    void "an entry that is added to such a map leaves the others in their order"() {
        given:
        Map<String, Object> config = [grails: [mime: [types: [json: ['application/json'], html: ['text/html']]]]]
        List<String> before = YamlTemplate.dump(YamlKeyOrder.canonical(config)).readLines()

        when:
        Map<String, Object> with = [grails: [mime: [types: [json: ['application/json'], html: ['text/html'], csv: 'text/csv']]]]
        List<String> after = YamlTemplate.dump(YamlKeyOrder.canonical(with)).readLines()

        then:
        after - ['      csv: text/csv'] == before
        after.last() == '      csv: text/csv'
    }

    @Unroll
    void "the order does not depend on the order the keys were put in (#seed)"() {
        given:
        Map<String, Object> config = [info: [app: [name: 1, version: 2]], dataSource: [a: 1, b: 2, c: 3], environments: [development: [x: 1, y: 2], test: [x: 1], production: [x: 1]],
                                      hibernate: [cache: [queries: false, use_query_cache: false]], grails: [codegen: 1, views: [gsp: [encoding: 'UTF-8', codecs: [scriptlet: 'html']]]],
                                      quartz: 1, spring: [a: 1, b: 2]]

        expect:
        YamlTemplate.dump(YamlKeyOrder.canonical(shuffled(config, new Random(seed)))) == YamlTemplate.dump(YamlKeyOrder.canonical(config))

        where:
        seed << (1..10)
    }

    void "a configuration that gets a key has that key and every other line where it was"() {
        given:
        Map<String, Object> config = [info: [app: [name: 1]], dataSource: [url: 'x'], grails: [codegen: [defaultPackage: 'a'], views: [gsp: [encoding: 'UTF-8']]]]
        List<String> before = YamlTemplate.dump(YamlKeyOrder.canonical(config)).readLines()

        when: 'another feature puts its keys there first, as a feature that is applied earlier does'
        Map<String, Object> with = [grails: [cache: [enabled: true], codegen: [defaultPackage: 'a'], views: [gsp: [encoding: 'UTF-8']]], info: [app: [name: 1]], dataSource: [url: 'x'],
                                    quartz: [autoStartup: true]]
        List<String> after = YamlTemplate.dump(YamlKeyOrder.canonical(with)).readLines()

        then:
        after - ['  cache:', '    enabled: true', 'quartz:', '  autoStartup: true'] == before
        after.indexOf('  cache:') == after.indexOf('grails:') + 1
        after.takeRight(2) == ['quartz:', '  autoStartup: true']
    }

    void "a configuration is put into the order another has its keys in, and its own keys are put among them where they were"() {
        given: 'the order of the generator'
        Map<String, Object> generated = [info: [name: 1, version: 2], dataSource: [a: 1, b: 2], grails: [cache: 1, codegen: 2, views: 3], quartz: 1]

        and: 'a file that has the keys in another order, without some of them and with one of its own'
        Map<String, Object> other = [mine: 1, grails: [views: 3, codegen: 2], info: [version: 2, name: 1], dataSource: [b: 2, a: 1]]

        when:
        Map<String, Object> following = YamlKeyOrder.following(generated, other)

        then: 'the keys both have are where the other has them; quartz follows grails as it did, and cache comes before codegen as it did'
        following.keySet().toList() == ['grails', 'quartz', 'info', 'dataSource']
        (following.grails as Map).keySet().toList() == ['views', 'cache', 'codegen']
        (following.info as Map).keySet().toList() == ['version', 'name']
        (following.dataSource as Map).keySet().toList() == ['b', 'a']
        following == generated
    }

    void "a configuration that has its keys in the order of the other already comes back in its own order"() {
        given:
        Map<String, Object> generated = [info: [name: 1, version: 2], added: [x: 1], dataSource: [a: 1, new: 3, b: 2], grails: 1]
        Map<String, Object> other = [mine: 1, info: [name: 1, version: 2], dataSource: [a: 1, b: 2], more: 2, grails: 1]

        expect:
        YamlTemplate.dump(YamlKeyOrder.following(generated, other)) == YamlTemplate.dump(generated)
    }

    void "a key that was first, and that the other does not have, comes before the key it came before"() {
        expect:
        YamlKeyOrder.following([first: 1, second: 2, a: 1, b: 2], [b: 2, a: 1]).keySet().toList() == ['b', 'first', 'second', 'a']
        YamlKeyOrder.following([only: 1], [b: 2]).keySet().toList() == ['only']
    }

    private static Map<String, Object> at(Map<String, Object> config, String setting) {
        Object value = config
        for (String step : setting.split(/\./)) {
            value = (value as Map).get(step)
        }
        return value as Map<String, Object>
    }

    private static Map<String, Object> shuffled(Map<String, Object> map, Random random) {
        List<String> keys = new ArrayList<String>(map.keySet())
        Collections.shuffle(keys, random)
        Map<String, Object> result = new LinkedHashMap<>()
        for (String key : keys) {
            Object value = map.get(key)
            result.put(key, value instanceof Map ? shuffled((Map<String, Object>) value, random) : value)
        }
        return result
    }
}
