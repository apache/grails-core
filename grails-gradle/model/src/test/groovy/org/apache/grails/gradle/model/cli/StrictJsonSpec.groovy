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
package org.apache.grails.gradle.model.cli

import spock.lang.Specification
import spock.lang.Unroll

/**
 * The reader of JSON somebody else wrote takes what the grammar has and refuses everything else,
 * and nothing it is given makes it throw anything but its refusal.
 */
class StrictJsonSpec extends Specification {

    def "the reader agrees with another reader of JSON on every text of a corpus, for both shapes it is used for"() {
        given: 'texts made by changing JSON at random, each with what Python\'s json makes of it (strict-json-corpus.py)'
        List<String> lines = getClass().getResourceAsStream('/strict-json-corpus.tsv').readLines('US-ASCII')

        when:
        List<String> disagreements = []
        int accepted = 0
        lines.each { String line ->
            List<String> parts = line.split('\t', 3) as List<String>
            String verdict = verdict(parts[0].decodeHex(), parts[1])
            if (verdict.startsWith('OK')) {
                accepted++
            }
            if (verdict != parts[2]) {
                disagreements << "${parts[1]} ${parts[0]}: ${verdict} and not ${parts[2]}".toString()
            }
        }

        then:
        lines.size() > 2000
        accepted > 100
        disagreements == []
    }

    def "texts changed at random are read or refused and never anything else, and what is read is written and read as the same"() {
        given: 'the same texts every time'
        Random random = new Random(16348)
        List<String> seeds = ['["a","b c",""]', '{"format":1,"class":"a.B","name":"b","description":"x \\u00e9 \\ud83d\\ude00"}',
                              '{"a":[1,-2.5e3,true,false,null,{"b":"c"}]}', '["\\"\\\\\\/\\b\\f\\n\\r\\t"]', '[]', '{}', '"é😀"']
        List<String> pieces = '[]{}",:\\ \n\tabu0123456789dDfnrte-+.E/\'é😀'.toList() + ['\\u', '\\ud83d', '\\ude00', 'true', 'null', '"a"', '\u0000', '﻿']

        when:
        int read = 0
        int refused = 0
        20000.times {
            StringBuilder text = new StringBuilder(seeds[random.nextInt(seeds.size())])
            (1 + random.nextInt(4)).times {
                int at = random.nextInt(text.length() + 1)
                switch (random.nextInt(3)) {
                    case 0: text.insert(at, pieces[random.nextInt(pieces.size())]); break
                    case 1: if (at < text.length()) { text.deleteCharAt(at) }; break
                    default: text.setLength(at)
                }
            }
            byte[] bytes = text.toString().getBytes('UTF-8')
            if (random.nextInt(20) == 0 && bytes.length) {
                bytes[random.nextInt(bytes.length)] = (byte) random.nextInt(256)
            }
            try {
                Object value = StrictJson.parse(bytes, 4)
                String written = StrictJson.write(value)
                assert written.bytes.every { byte b -> b >= 0x20 && b <= 0x7e }
                assert StrictJson.write(StrictJson.parse(written, 4)) == written
                read++
            } catch (StrictJson.Refused ignored) {
                refused++
            }
        }

        then: 'no other exception ended the loop, and both things happened often'
        read > 500
        refused > 5000
    }

    @Unroll
    def "an array of strings is read: #text"() {
        expect:
        StrictJson.stringArray(text.getBytes('UTF-8')) == expected

        where:
        text                                     | expected
        '[]'                                     | []
        ' [ "a" ,\n\t"b c", "" ]\r\n'            | ['a', 'b c', '']
        '["\\"\\\\\\/\\b\\f\\n\\r\\t"]'          | ['"\\/\b\f\n\r\t']
        '["\\u00e9\\u00E9é"]'                    | ['ééé']
        '["\\ud83d\\ude00😀"]'                   | ['😀😀']
        '["\\u0000"]'                            | ['\u0000']
        '["\u007f"]'                             | ['\u007f']
    }

    @Unroll
    def "what is not exactly an array of strings is refused: #text"() {
        when:
        StrictJson.stringArray(text.getBytes('UTF-8'))

        then:
        thrown(StrictJson.Refused)

        where:
        text << [
                '', '   ', '"a"', '{"a":"b"}', '[1]', '[null]', '[true]', '[["a"]]', '[{}]',
                // what a lenient reader takes for ["a"]
                '["a"]["b"]', '["a"]x', '["a"],', '["a"]]', '["a"] // c', '["a",]', '[,"a"]', '["a",,"b"]', '["a" "b"]',
                "['a']", '[a]', '["a";"b"]', '["a":"b"]',
                // escapes
                '["\\u12"]', '["\\u12', '["\\', '["a\\', '["\\x"]', '["\\u00zz"]', '["\\u+123"]', '["\\u１234"]', '["\\U0041"]',
                // half a surrogate pair, however it is spelled
                '["\\ud83d"]', '["\\ude00"]', '["\\ude00\\ud83d"]', '["\\ud83dx"]', '["\\ud83d\\n"]', '["\\ud83d\\u0041"]', '["\\ud83d", "\\ude00"]',
                // control characters, and what is not JSON's white space
                '["a\nb"]', '["a\tb"]', '["\u0001"]', '["\u0000"]', '﻿["a"]', ' ["a"]', '["a"]\u000b',
                '[', '["a"', '["a', '[NaN]', '[tru', '[-]',
        ]
    }

    def "bytes that are not UTF-8 are refused"() {
        when:
        StrictJson.stringArray(bytes as byte[])

        then:
        thrown(StrictJson.Refused)

        where:
        bytes << [
                [0x5b, 0x22, 0xff, 0x22, 0x5d],
                [0x5b, 0x22, 0xc0, 0xaf, 0x22, 0x5d],
                [0x5b, 0x22, 0xed, 0xa0, 0x80, 0x22, 0x5d],
                [0x5b, 0x22, 0xe2, 0x82, 0x22, 0x5d],
        ]
    }

    def "arrays and objects are read to the depth that is given and no deeper, however deep the text goes"() {
        expect:
        StrictJson.parse('[[[[1]]]]', 4) == [[[[new StrictJson.Numeral('1')]]]]
        StrictJson.parse('{"a":{"b":[{"c":null}]}}', 4) == [a: [b: [[c: null]]]]

        when:
        StrictJson.parse(text, 4)

        then:
        thrown(StrictJson.Refused)

        where:
        text << ['[[[[[1]]]]]', '{"a":{"b":{"c":{"d":{}}}}}', '[' * 1000000, '{"a":' * 200000, '[' * 5000 + ']' * 5000]
    }

    def "an object keeps the order of its members, and a name it has twice is refused"() {
        expect:
        (StrictJson.parse('{"z":1,"a":"x","m":[true,false]}', 2) as Map).keySet() as List == ['z', 'a', 'm']

        when:
        StrictJson.parse('{"name":"good","name":"evil"}', 2)

        then:
        thrown(StrictJson.Refused)
    }

    @Unroll
    def "a number is read as it is written, and #text is not one"() {
        expect:
        ['0', '-0', '10', '-1.5', '1e5', '1E-5', '0.0e+0', '123456789012345678901234567890'].every { String number ->
            StrictJson.parse(number, 0) == new StrictJson.Numeral(number)
        }
        (StrictJson.parse('1', 0) as StrictJson.Numeral).isInteger(1)
        !(StrictJson.parse('1.0', 0) as StrictJson.Numeral).isInteger(1)
        !(StrictJson.parse('1e0', 0) as StrictJson.Numeral).isInteger(1)

        when:
        StrictJson.parse(text, 0)

        then:
        thrown(StrictJson.Refused)

        where:
        text << ['01', '1.', '.5', '1e', '-', '+1', '0x10', 'Infinity', '-Infinity', 'NaN', '1 2', '１']
    }

    private static String verdict(byte[] bytes, String shape) {
        try {
            if (shape == 'array') {
                List<String> strings = StrictJson.stringArray(bytes)
                return strings.size() > 4096 || strings.any { String string -> string.indexOf(0) >= 0 } ? 'REFUSED' : 'OK ' + StrictJson.write(strings)
            }
            return 'OK ' + StrictJson.write(StrictJson.parse(bytes, 4))
        } catch (StrictJson.Refused ignored) {
            return 'REFUSED'
        }
    }
}
