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

import java.nio.charset.StandardCharsets
import spock.lang.Specification
import spock.lang.Unroll

class ThreeWayMergeSpec extends Specification {

    @Unroll
    void "#what is merged"() {
        when:
        ThreeWayMerge merge = ThreeWayMerge.of(bytes(base), bytes(current), bytes(proposed))

        then:
        merge.conflicts == 0
        new String(merge.merged, StandardCharsets.UTF_8) == merged

        where:
        what                                             | base                | current                  | proposed               || merged
        'a change of the generator alone'                | 'a\nb\nc\n'         | 'a\nb\nc\n'              | 'a\nB\nc\n'            || 'a\nB\nc\n'
        'a change of the project alone'                  | 'a\nb\nc\n'         | 'a\nb\nmine\nc\n'        | 'a\nb\nc\n'            || 'a\nb\nmine\nc\n'
        'changes in different places'                    | 'a\nb\nc\nd\ne\n'   | 'mine\na\nb\nc\nd\ne\n'  | 'a\nb\nc\nd\ne\nnew\n' || 'mine\na\nb\nc\nd\ne\nnew\n'
        'changes with a line between them'               | 'a\nb\nc\n'         | 'A\nb\nc\n'              | 'a\nb\nC\n'            || 'A\nb\nC\n'
        'the same change made by both'                   | 'a\nb\nc\n'         | 'a\nsame\nc\n'           | 'a\nsame\nc\n'         || 'a\nsame\nc\n'
        'the same line added by both'                    | 'a\nc\n'            | 'a\nsame\nc\n'           | 'a\nsame\nc\n'         || 'a\nsame\nc\n'
        'a line removed by the generator'                | 'a\nb\nc\nd\n'      | 'a\nb\nc\nd\nmine\n'     | 'b\nc\nd\n'            || 'b\nc\nd\nmine\n'
        'a line removed by the project'                  | 'a\nb\nc\nd\n'      | 'a\nb\nc\n'              | 'new\na\nb\nc\nd\n'    || 'new\na\nb\nc\n'
        'a file of the project without a last line feed' | 'a\nb\nc\n'         | 'a\nb\nc\nmine'          | 'new\na\nb\nc\n'       || 'new\na\nb\nc\nmine'
        'an empty file the generator fills'              | ''                  | ''                       | 'new\n'                || 'new\n'
        'a file the project has as it is generated now'  | 'a\n'               | 'a\nnew\n'               | 'a\nnew\n'             || 'a\nnew\n'
        'text that is not ASCII'                         | 'ä\nb\nc\n'         | 'ä\nb\nç\n'              | 'é\nb\nc\n'            || 'é\nb\nç\n'
    }

    @Unroll
    void "#what is a conflict, and there is no result"() {
        when:
        ThreeWayMerge merge = ThreeWayMerge.of(bytes(base), bytes(current), bytes(proposed))

        then:
        merge.conflicts == conflicts
        merge.merged == null

        where:
        what                                          | base              | current             | proposed            || conflicts
        'one line changed in two ways'                | 'a\nb\nc\n'       | 'a\nmine\nc\n'      | 'a\nnew\nc\n'       || 1
        'lines next to each other changed'            | 'a\nb\nc\n'       | 'a\nmine\nc\n'      | 'a\nb\nnew\n'       || 1
        'two lines added at one place'                | 'a\nc\n'          | 'a\nmine\nc\n'      | 'a\nnew\nc\n'       || 1
        'lines added at the end by both'              | 'a\n'             | 'a\nmine\n'         | 'a\nnew\n'          || 1
        'a line the generator changes removed'        | 'a\nb\nc\n'       | 'a\nc\n'            | 'a\nnew\nc\n'       || 1
        'a line the project changes removed'          | 'a\nb\nc\n'       | 'a\nmine\nc\n'      | 'a\nc\n'            || 1
        'a line added next to a line that is changed' | 'a\nb\nc\n'       | 'a\nb\nmine\nc\n'   | 'a\nnew\nc\n'       || 1
        'two places changed in two ways'              | 'a\nb\nc\nd\ne\n' | 'A\nb\nc\nd\nE\n'   | '1\nb\nc\nd\n5\n'   || 2
    }

    void "a file whose lines end with CR LF keeps them, and gets the lines of the generator with them"() {
        when:
        ThreeWayMerge merge = ThreeWayMerge.of(bytes('a\nb\nc\nd\n'), bytes('a\r\nb\r\nc\r\nmine\r\nd\r\n'), bytes('new\nA\nb\nc\nd\n'))

        then:
        merge.conflicts == 0
        new String(merge.merged, StandardCharsets.UTF_8) == 'new\r\nA\r\nb\r\nc\r\nmine\r\nd\r\n'
    }

    void "a file with both line endings keeps each of its lines as it is, and gets the lines of the generator as they were generated"() {
        when:
        ThreeWayMerge merge = ThreeWayMerge.of(bytes('a\nb\nc\nd\n'), bytes('a\r\nb\nc\r\nd\n'), bytes('a\nb\nc\nd\nnew\n'))

        then:
        merge.conflicts == 0
        new String(merge.merged, StandardCharsets.UTF_8) == 'a\r\nb\nc\r\nd\nnew\n'
    }

    void "files that differ in how their lines end only are the same text"() {
        expect:
        ThreeWayMerge.sameText(bytes('a\nb\n'), bytes('a\r\nb\r\n'))
        ThreeWayMerge.sameText(bytes('a\nb\n'), bytes('a\nb\n'))
        !ThreeWayMerge.sameText(bytes('a\nb\n'), bytes('a\nb'))
        !ThreeWayMerge.sameText(bytes('a\rb\n'), bytes('a\nb\n'))
    }

    void "bytes that are no text come out of a merge as they went in"() {
        given:
        byte[] current = [(byte) 0xff, (byte) 0xfe, (byte) 10, (byte) 98, (byte) 10, (byte) 99, (byte) 10] as byte[]

        when:
        ThreeWayMerge merge = ThreeWayMerge.of([(byte) 97, (byte) 10, (byte) 98, (byte) 10, (byte) 99, (byte) 10] as byte[], current, bytes('a\nb\nc\nnew\n'))

        then:
        merge.conflicts == 0
        merge.merged.toList() == current.toList() + bytes('new\n').toList()
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8)
    }
}
