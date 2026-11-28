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
package org.apache.grails.forge.core.change

import spock.lang.Specification
import spock.lang.Unroll

/**
 * What a YAML configuration file has more than once, for the files a merge of lines can come to
 * that adding a feature to a generated application does not: see {@link FeatureAdditionYamlKeysSpec}
 * for those.
 */
class RepeatedSettingsSpec extends Specification {

    @Unroll
    void "#what: the top-level keys #keys and the settings #settings are there twice"() {
        when:
        RepeatedSettings repeated = RepeatedSettings.of(yaml.bytes)

        then:
        repeated.topLevelKeys == keys
        repeated.settings == settings

        where:
        what                                        | yaml                                                   || keys      | settings
        'nothing twice'                             | 'a: 1\nb:\n  c: 2\n  d: 3\n'                           || []        | []
        'a key twice, once in quotes'               | "a.b: 1\n'a.b': 2\n\"c\": 3\nc: 4\n"                   || ['a.b', 'c'] | []
        'a group twice under one key'               | 'a:\n  b: 1\na:\n  c: 2\n'                             || ['a']     | []
        'a key twice further down'                  | 'a:\n  b: 1\n  "b": 2\n'                               || []        | ['a.b']
        'with dots and nested'                      | 'a.b: 1\na:\n  b: 2\n'                                 || []        | ['a.b']
        'nested and with dots'                      | 'a:\n  b: 2\nx: 1\na.b: 1\n'                           || []        | ['a.b']
        'dots at two depths'                        | 'a:\n  b.c: 1\na.b:\n  c: 2\n'                         || []        | ['a.b.c']
        'a value and a group of the name'           | 'a.b: 1\na:\n  b:\n    c: 2\n'                         || []        | ['a.b']
        'a group and a value of the name'           | 'a:\n  b:\n    c: 2\na.b: 1\n'                         || []        | ['a.b']
        'a group written in two ways'               | 'a.b:\n  c: 1\na:\n  b:\n    d: 2\n'                   || []        | []
        'the same key in two documents'             | 'a: 1\n---\na: 2\n'                                    || []        | []
        'the same key in two environments'          | 'environments:\n  test:\n    a: 1\n  production:\n    a: 2\na: 3\n' || [] | []
        'a key of a map in a list'                  | 'a:\n  - b: 1\n  - b: 2\n'                             || []        | []
        'a mapping that is there by an alias twice' | 'base: &base\n  x: 1\none: *base\ntwo: *base\n'        || []        | []
        'the merge key twice'                       | 'p: &p {x: 1}\nq: &q {y: 2}\na:\n  <<: *p\n  <<: *q\n' || []        | []
        'a document that is a list'                 | '- a: 1\n- a: 2\n'                                     || []        | []
        'an empty file'                             | ''                                                     || []        | []
    }

    @Unroll
    void "a file that is not YAML that reads has nothing that is told of it: #what"() {
        expect:
        RepeatedSettings.of(yaml.bytes) == null

        where:
        what                           | yaml
        'a flow that is not closed'    | 'a: [1, 2\nb: 3\n'
        'a key below a scalar'         | 'a: 1\n  b: 2\n'
        'an alias of nothing'          | 'a: *nothing\n'
        'a mapping that holds itself'  | 'a: &a\n  b: *a\n'
        'a tab for an indentation'     | 'a:\n\tb: 1\n'
    }

    void "what the two sides of a merge have twice themselves is not what the merge made"() {
        given:
        RepeatedSettings merged = RepeatedSettings.of('a: 1\na: 2\nb: 1\nb: 2\nc:\n  d: 1\nc.d: 2\ne.f: 1\ne:\n  f: 2\n'.bytes)
        RepeatedSettings mine = RepeatedSettings.of('a: 1\na: 2\n'.bytes)
        RepeatedSettings theirs = RepeatedSettings.of('c:\n  d: 1\nc.d: 2\n'.bytes)

        expect:
        merged.topLevelKeysBeyond(mine, theirs) == ['b']
        merged.settingsBeyond(mine, theirs) == ['e.f']
    }
}
