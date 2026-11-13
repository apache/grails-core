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
package org.grails.io.support

import java.util.regex.PatternSyntaxException

import spock.lang.Specification
import spock.lang.Unroll

class AntPathMatcherSpec extends Specification {

    private final AntPathMatcher matcher = new AntPathMatcher()

    @Unroll
    void 'match #pattern against #path returns #expected'() {
        expect:
        matcher.match(pattern, path) == expected

        where:
        pattern                    | path                          | expected
        '/docs/file.txt'           | '/docs/file.txt'              | true
        '/docs/file.txt'           | '/docs/other.txt'             | false
        '/docs/file.txt'           | '/docs/file.txt/more'         | false
        ''                         | ''                            | true
        ''                         | '/'                           | false
        '/'                        | '/'                           | true
        '/'                        | ''                            | false
        '/docs'                    | 'docs'                        | false
        'docs'                     | '/docs'                       | false
        '/docs/'                   | '/docs'                       | false
        '/docs'                    | '/docs/'                      | false
        '/docs/'                   | '/docs/'                      | true
        '/docs//file.txt'          | '/docs/file.txt'              | true
        '/docs/file.txt'           | '/docs//file.txt'             | true
        'docs/file.txt'            | 'docs/file.txt'               | true
        '/docs/t?st.txt'           | '/docs/test.txt'              | true
        '/docs/t?st.txt'           | '/docs/tst.txt'               | false
        '/docs/t?st.txt'           | '/docs/teest.txt'             | false
        '/docs/?.txt'              | '/docs/a.txt'                 | true
        '/docs/?.txt'              | '/docs/a/b.txt'               | false
        '/docs/*.txt'              | '/docs/file.txt'              | true
        '/docs/*.txt'              | '/docs/.txt'                  | true
        '/docs/*.txt'              | '/docs/file.csv'              | false
        '/docs/*.txt'              | '/docs/sub/file.txt'          | false
        '/docs/*'                  | '/docs/'                      | true
        '/docs/*'                  | '/docs'                       | false
        '/docs/*/'                 | '/docs/file'                  | false
        '/docs/*/'                 | '/docs/file/'                 | true
        '/docs/**'                 | '/docs'                       | true
        '/docs/**'                 | '/docs/'                      | true
        '/docs/**'                 | '/docs/a/b/c.txt'             | true
        '/docs/**/file.txt'        | '/docs/file.txt'              | true
        '/docs/**/file.txt'        | '/docs/a/b/file.txt'          | true
        '/docs/**/file.txt'        | '/docs/a/b/other.txt'         | false
        '/docs/**/a/file.txt'      | '/docs/file.txt'              | false
        '/docs/**/a/**/file.txt'   | '/docs/x/a/y/file.txt'        | true
        '/docs/**/a/b/**/file.txt' | '/docs/x/a/no/a/b/y/file.txt' | true
        '/docs/**/a/b/**/file.txt' | '/docs/x/a/no/b/y/file.txt'   | false
        '/docs/**/a/b/**/file.txt' | '/docs/x/file.txt'            | false
        '/docs/**/**/file.txt'     | '/docs/a/b/file.txt'          | true
        '/docs/**/**'              | '/docs'                       | true
        '/docs/**/a/*'             | '/docs/a'                     | false
        '**'                       | ''                            | true
        '*'                        | ''                            | false
        '/**'                      | '/'                           | true
        '/?'                       | '/'                           | false
        '/docs/a.b+(c)[d]$'        | '/docs/a.b+(c)[d]$'           | true
        '/docs/a.b'                | '/docs/axb'                   | false
        '/Docs/*.TXT'              | '/docs/file.txt'              | false
        '/docs/ file '             | '/docs/file'                  | false
        '/docs/ file '             | '/docs/ file '                | true
        '/docs/{name}'             | '/docs/file'                  | true
        '/docs/{id:[0-9]{2}}'      | '/docs/42'                    | true
        '/docs/{id:[0-9]{2}}'      | '/docs/4'                     | false
        '/docs/{id:[0-9]+}'        | '/docs/name'                  | false
        '/docs/{unclosed'          | '/docs/{unclosed'             | true
    }

    @Unroll
    void 'matchStart #pattern against #path differs from full matching when appropriate'() {
        expect:
        matcher.matchStart(pattern, path) == startMatch
        matcher.match(pattern, path) == fullMatch

        where:
        pattern                | path                   | startMatch | fullMatch
        '/docs/file.txt'        | '/docs'                | true       | false
        '/docs/*/file.txt'      | '/docs/a'              | true       | false
        '/docs/**/file.txt'     | '/docs/a/other.txt'    | true       | false
        '/docs/**/file.txt'     | '/other/a/file.txt'   | false      | false
        '/docs/file.txt'        | '/docs/other.txt'      | false      | false
        '/docs'                | '/docs/a'              | false      | false
        '/docs'                | '/docs/'               | false      | false
        '/docs/file.txt'        | '/docs/file.txt'       | true       | true
        'docs/file.txt'         | '/docs'                | false      | false
        '**/file.txt'           | ''                     | true       | false
        ''                     | ''                     | true       | true
    }

    @Unroll
    void 'extract wildcard path from #pattern and #path'() {
        expect:
        matcher.extractPathWithinPattern(pattern, path) == expected

        where:
        pattern                    | path                       | expected
        '/docs/cvs/commit.html'     | '/docs/cvs/commit.html'    | ''
        '/docs/*'                  | '/docs/cvs/commit'         | 'cvs/commit'
        '/docs/cvs/*.html'         | '/docs/cvs/commit.html'    | 'commit.html'
        '/docs/**'                 | '/docs/cvs/commit'         | 'cvs/commit'
        '/docs/**/*.html'          | '/docs/cvs/commit.html'    | 'cvs/commit.html'
        '/*.html'                  | '/docs/cvs/commit.html'    | 'docs/cvs/commit.html'
        '*.html'                   | '/docs/cvs/commit.html'    | '/docs/cvs/commit.html'
        '*'                        | '/docs/cvs/commit.html'    | '/docs/cvs/commit.html'
        '/docs/t?st/*'             | '/docs/test/file'          | 'test/file'
        '/docs/{name}'             | '/docs/file'               | ''
        '/docs/*/fixed/*'          | '/docs/a/fixed/b'          | 'a/b'
        '/docs/*'                  | '/docs'                    | ''
        '/docs'                    | '/docs/a/b'                | '/a/b'
        ''                         | 'a/b'                      | 'a/b'
        ''                         | ''                         | ''
    }

    @Unroll
    void 'extract URI variables from #pattern and #path'() {
        expect:
        matcher.extractUriTemplateVariables(pattern, path) == expected

        where:
        pattern                            | path                    | expected
        '/users/{user}'                    | '/users/alice'          | [user: 'alice']
        '/{first}/{second}'                | '/one/two'              | [first: 'one', second: 'two']
        '/files/{name}.{extension}'        | '/files/report.pdf'     | [name: 'report', extension: 'pdf']
        '/files/{name}.txt'                | '/files/.txt'           | [name: '']
        '/users/{id:[0-9]{2}}'             | '/users/42'             | [id: '42']
        '/users/{id:(?:ab|cd)}'            | '/users/ab'             | [id: 'ab']
        '/users/{id:[0-9]+}'               | '/users/alice'          | [:]
        '/{name}/**/{file}.txt'            | '/docs/a/b/readme.txt' | [name: 'docs', file: 'readme']
        '/{name}/{name}'                   | '/first/last'           | [name: 'last']
        '/docs/file.txt'                   | '/docs/file.txt'        | [:]
        '/users/{user}'                    | '/other/alice'          | [:]
        '/{user}/required'                 | '/alice/other'          | [user: 'alice']
        ''                                 | ''                      | [:]
    }

    void 'URI variables preserve insertion order and extraction results are independent'() {
        when:
        Map<String, String> first = matcher.extractUriTemplateVariables('/{one}/{two}', '/a/b')
        Map<String, String> second = matcher.extractUriTemplateVariables('/{one}/{two}', '/c/d')

        then:
        first.keySet().toList() == ['one', 'two']
        first == [one: 'a', two: 'b']
        second == [one: 'c', two: 'd']
        !first.is(second)
    }

    @Unroll
    void 'combine #first and #second using legacy pattern rules'() {
        expect:
        matcher.combine(first, second) == expected

        where:
        first          | second            | expected
        null           | null              | ''
        ''             | ''                | ''
        null           | '/hotels'         | '/hotels'
        '/hotels'      | null              | '/hotels'
        ''             | '/hotels'         | '/hotels'
        '/hotels'      | ''                | '/hotels'
        '/hotels'      | '/hotels'         | '/hotels'
        '/hotels/*'    | '/hotels/booking' | '/hotels/booking'
        '/hotels'      | '/booking'        | '/hotels/booking'
        '/hotels'      | 'booking'         | '/hotels/booking'
        '/hotels/'     | 'booking'         | '/hotels/booking'
        '/hotels/'     | '/booking'        | '/hotels//booking'
        '/hotels/*'    | '/booking'        | '/hotels/booking'
        '/hotels/*'    | 'booking'         | '/hotels/booking'
        '/hotels/**'   | '/booking'        | '/hotels/**/booking'
        '/hotels/**'   | 'booking'         | '/hotels/**/booking'
        '/hotels/*'    | '{hotel}'         | '/hotels/{hotel}'
        '/hotels/**'   | '{hotel}'         | '/hotels/**/{hotel}'
        '/{hotel}'     | '/booking'        | '/{hotel}/booking'
        '/*.html'      | '/hotels.html'    | '/hotels.html'
        '/*.html'      | '/hotels'         | '/hotels.html'
        '/*.html'      | '/hotels.*'       | '/hotels.html'
        '/*.*'         | '/*.html'         | '/*.html'
        '/report.html' | '/other.txt'      | '/report.html/other.txt'
        '/users/{id:[}' | '/users/42'       | '/users/{id:[}/users/42'
        ' '            | 'booking'         | ' /booking'
    }

    @Unroll
    void 'combine rejects #first and #second because both name a wildcard extension'() {
        when:
        matcher.combine(first, second)

        then:
        def error = thrown(IllegalArgumentException)
        error.message == "Cannot combine patterns: $first vs $second"

        where:
        first     | second
        '/*.html' | '/*.txt'
        '/*.html' | '/hotels.txt'
    }

    @Unroll
    void 'recognize wildcard patterns without treating URI templates as wildcards'() {
        expect:
        matcher.isPattern(path) == expected

        where:
        path             | expected
        ''               | false
        '/docs/file.txt' | false
        '/docs/{name}'   | false
        '/docs/*'        | true
        '/docs/**'       | true
        '/docs/?.txt'    | true
    }

    void 'custom path separator applies to matching and both extraction methods and null resets it'() {
        given:
        matcher.setPathSeparator('.')

        expect:
        matcher.match('org.**.File', 'org.grails.io.File')
        !matcher.match('org.*.File', 'org.grails.io.File')
        !matcher.match('.org.*', 'org.grails')
        matcher.matchStart('org.grails.File', 'org.grails')
        matcher.extractPathWithinPattern('org.*', 'org.grails.io') == 'grails.io'
        matcher.extractUriTemplateVariables('org.{name}.File', 'org.grails.File') == [name: 'grails']

        when:
        matcher.setPathSeparator(null)

        then:
        matcher.match('/org/**/File', '/org/grails/io/File')
        !matcher.match('org.grails.File', 'org/grails/File')
    }

    @Unroll
    void 'null input to #operation throws NullPointerException'() {
        when:
        matcher."$operation"(*arguments)

        then:
        thrown(NullPointerException)

        where:
        operation                     | arguments
        'match'                       | [null, '/docs']
        'match'                       | ['/docs', null]
        'matchStart'                  | [null, '/docs']
        'matchStart'                  | ['/docs', null]
        'isPattern'                   | [null]
        'extractPathWithinPattern'    | [null, '/docs']
        'extractPathWithinPattern'    | ['/docs', null]
        'extractUriTemplateVariables' | [null, '/docs']
        'extractUriTemplateVariables' | ['/docs', null]
    }

    @Unroll
    void 'malformed template regex is rejected by #operation'() {
        when:
        matcher."$operation"('/users/{id:[}', '/users/42')

        then:
        thrown(PatternSyntaxException)

        where:
        operation << ['match', 'matchStart', 'extractUriTemplateVariables']
    }

    void 'capturing groups in a template regex match but cannot be extracted as variables'() {
        expect:
        matcher.match('/users/{id:(ab|cd)}', '/users/ab')
        !matcher.match('/users/{id:(ab|cd)}', '/users/ef')
        matcher.extractUriTemplateVariables('/users/{id:(?:ab|cd)}', '/users/ab') == [id: 'ab']

        when:
        matcher.extractUriTemplateVariables('/users/{id:(ab|cd)}', '/users/ab')

        then:
        def error = thrown(IllegalArgumentException)
        error.message.contains('does not match the number of URI template variables')
        error.message.endsWith('Use non-capturing groups instead.')
    }

    @Unroll
    void 'pattern comparator orders #first relative to #second by specificity'() {
        given:
        Comparator<String> comparator = matcher.getPatternComparator('/hotels/2')

        expect:
        Integer.signum(comparator.compare(first, second)) == expected
        Integer.signum(comparator.compare(second, first)) == -expected

        where:
        first               | second              | expected
        null                | null                | 0
        null                | '/hotels/*'         | 1
        '/hotels/2'         | '/hotels/2'         | 0
        '/hotels/2'         | '/hotels/1'         | -1
        '/hotels/new'       | '/hotels/*'         | -1
        '/hotels/**'        | '/hotels/*'         | 1
        '/hotels/long'      | '/hotels/new'       | -1
        '/hotels/{hotel}'   | '/hotels/*'         | -1
        '/a/{one}'          | '/a/{two}'          | 0
        '/a/{id}/literal'   | '/a/literal/*'      | -1
        '/file.*'           | '/file'             | -1
    }

    @Unroll
    void 'count nonoverlapping occurrences of #substring in #text'() {
        expect:
        AntPathMatcher.countOccurrencesOf(text, substring) == expected

        where:
        text       | substring | expected
        null       | 'a'       | 0
        'a'        | null      | 0
        ''         | 'a'       | 0
        'a'        | ''        | 0
        'abc'      | 'z'       | 0
        'aaaa'     | 'aa'      | 2
        'aaa'      | 'aa'      | 1
        '*a**b*'   | '*'       | 4
    }
}
