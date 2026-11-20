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
import java.util.concurrent.TimeUnit
import spock.lang.Specification
import spock.lang.Timeout
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

    @Unroll
    void "a change among lines that are alike touches every one of them: #what is a conflict"() {
        when:
        ThreeWayMerge merge = ThreeWayMerge.of(bytes(base), bytes(current), bytes(proposed))

        then:
        merge.conflicts == 1
        merge.merged == null

        where:
        what                                                 | base              | current              | proposed
        'a line among empty lines, and a line before them'   | 'x\n\n\n\n'       | 'x\n\nmine\n\n\n'   | 'x\ny\n\n\n'
        'an empty line removed, and a line added among them' | 'a\n\n\n\nb\n'    | 'a\n\n\nb\n'        | 'a\n\nnew\n\n\nb\n'
        'a line doubled, and a line added after its like'    | 'a\nb\nb\nc\n'    | 'a\nb\nb\nb\nc\n'   | 'a\nb\nnew\nb\nc\n'
    }

    @Unroll
    void "a line that is like others keeps two changes apart when it can only be lined up one way: #what"() {
        when:
        ThreeWayMerge merge = ThreeWayMerge.of(bytes(base), bytes(current), bytes(proposed))

        then:
        merge.conflicts == 0
        new String(merge.merged, StandardCharsets.UTF_8) == merged

        where:
        what                                        | base                  | current                     | proposed                   || merged
        'lines on the two sides of an empty line'   | '{\na\n\nb\n\nc\n}\n' | '{\na\n\nb\nmine\n\nc\n}\n' | '{\na\n\nb\n\nnew\nc\n}\n' || '{\na\n\nb\nmine\n\nnew\nc\n}\n'
        'lines on the two sides of a closing brace' | 'a\n}\n}\n'           | 'a\n}\nmine\n}\n'          | 'a\nnew\n}\n}\n'          || 'a\nnew\n}\nmine\n}\n'
        'lines between and after two empty lines'   | 'a\n\n\nb\n'          | 'a\n\nmine\n\nb\n'         | 'a\n\n\nnew\nb\n'         || 'a\n\nmine\n\nnew\nb\n'
    }

    void "the same change made by both in one place is taken, also beside other changes, and is what both have"() {
        when: 'both added the same line, and each changed another place'
        ThreeWayMerge merge = ThreeWayMerge.of(bytes('first\n\n\nmiddle\nlast\n'), bytes('mine\nfirst\n\nsame\n\nmiddle\nlast\n'), bytes('first\n\nsame\n\nmiddle\nlast\nnew\n'))

        then:
        merge.conflicts == 0
        new String(merge.merged, StandardCharsets.UTF_8) == 'mine\nfirst\n\nsame\n\nmiddle\nlast\nnew\n'

        when: 'the project has a line of the generator already, as when it was added by hand, and its lines end otherwise'
        merge = ThreeWayMerge.of(bytes('a\nb\nc\nd\n'), bytes('a\r\nnew\r\nb\r\nc\r\nd\r\n'), bytes('a\nnew\nb\nc\nmore\nd\n'))

        then:
        merge.conflicts == 0
        new String(merge.merged, StandardCharsets.UTF_8) == 'a\r\nnew\r\nb\r\nc\r\nmore\r\nd\r\n'

        when: 'nearly the same change is none'
        merge = ThreeWayMerge.of(bytes('a\nb\nc\nd\n'), bytes('a\nnew\nmine\nb\nc\nd\n'), bytes('a\nnew\nb\nc\nmore\nd\n'))

        then:
        merge.conflicts == 1
        merge.conflictingChanges[0].line == 2
        merge.conflictingChanges[0].base == []
        merge.conflictingChanges[0].current == ['new', 'mine']
        merge.conflictingChanges[0].proposed == ['new']
        !merge.conflictingChanges[0].elsewhere
    }

    @Unroll
    void "#what is a conflict for that line, and not a file with the line #wrongly"() {
        when:
        ThreeWayMerge merge = ThreeWayMerge.of(bytes(base), bytes(current), bytes(proposed))

        then:
        merge.merged == null
        merge.conflictingChanges*.elsewhere == [true]
        merge.conflictingChanges[0].current == [line]
        merge.conflictingChanges[0].line == at

        where:
        what                                                    | wrongly      | base                    | current                   | proposed                  || line  | at
        'a line both moved, each to another place'              | 'twice'      | 'a\nb\nx\nc\nd\n'       | 'x\na\nb\nc\nd\n'         | 'a\nb\nc\nd\nx\n'         || 'x'   | 1
        'a line both added, each in another place'              | 'twice'      | 'a\nb\nc\n'             | 'new\na\nb\nc\n'           | 'a\nb\nc\nnew\n'           || 'new' | 1
    }

    void "a line that is there twice, of which each side moved one to the same place, is a conflict and not a file with the line once"() {
        expect:
        ThreeWayMerge.of(bytes('x\na\nb\nc\nx\nd\n'), bytes('a\nb\nx\nc\nx\nd\n'), bytes('x\na\nb\nx\nc\nd\n')).merged == null
    }

    void "a line like many that both added, each in another place, is no conflict: it is there as often as the two account for"() {
        when: 'a closing brace and an empty line more on each side'
        ThreeWayMerge merge = ThreeWayMerge.of(bytes('a {\n}\n\nb\nc\nd {\n}\n'), bytes('mine {\n}\n\na {\n}\n\nb\nc\nd {\n}\n'), bytes('a {\n}\n\nb\nc\nd {\n}\n\nnew {\n}\n'))

        then:
        merge.conflicts == 0
        new String(merge.merged, StandardCharsets.UTF_8) == 'mine {\n}\n\na {\n}\n\nb\nc\nd {\n}\n\nnew {\n}\n'
    }

    @Unroll
    void "of a side that changed #changed lines in one stretch it is #tooLarge that it is not merged"() {
        given:
        List<String> generated = (0..<3000).collect { "line ${it}\n".toString() }
        List<String> mine = (0..<replaced).collect { "mine ${it}\n".toString() } + generated.drop(replaced)
        String withFeature = generated.take(2999).join('') + 'feature\n'

        expect:
        ThreeWayMerge.of(bytes(generated.join('')), bytes(mine.join('')), bytes(withFeature)).tooLarge == tooLarge

        where:
        replaced | changed || tooLarge
        1000     | 2000    || false
        1001     | 2002    || true
    }

    @Timeout(value = 600, unit = TimeUnit.SECONDS)
    void "a file of 200,000 lines is merged, and one of a line more is not"() {
        given:
        String generated = (0..<200).collect { "line ${it}\n" }.join('')

        expect:
        ThreeWayMerge.of(bytes(generated), bytes('mine\n' * (count - 200) + generated), bytes(generated + 'feature\n')).tooLarge == tooLarge

        where:
        count   || tooLarge
        200000  || false
        200001  || true
    }

    void "the lines of a conflict are those of the two sides, without what they have alike, and the line of the file is said"() {
        when:
        ThreeWayMerge merge = ThreeWayMerge.of(bytes('a\nb\nc\nd\ne\nf\n'), bytes('a\nb\nc\nmine\nd\ne\nF\n'), bytes('a\nb\nc\nnew\nother\nd\ne\nf\nlast\n'))

        then:
        merge.conflicts == 2
        merge.conflictingChanges*.line == [4, 7]
        merge.conflictingChanges*.current == [['mine'], ['F']]
        merge.conflictingChanges*.proposed == [['new', 'other'], ['f', 'last']]
        !merge.tooLarge
    }

    @Unroll
    @Timeout(value = 600, unit = TimeUnit.SECONDS)
    void "a file of the project of #lines other lines is not merged, and that is found at once"() {
        given:
        String generated = (0..<200).collect { "line ${it}\n" }.join('')
        String withFeature = generated.replace('line 7\n', 'feature\n')

        when:
        ThreeWayMerge merge = ThreeWayMerge.of(bytes(generated), bytes((0..<lines).collect { "other ${it}\n" }.join('')), bytes(withFeature))

        then:
        merge.tooLarge
        merge.conflicts == 1
        merge.merged == null
        merge.conflictingChanges.isEmpty()

        where:
        lines << [3000, 20000, 100000, 300000, 1000000]
    }

    @Timeout(value = 600, unit = TimeUnit.SECONDS)
    void "a long file with few changes is merged, and so is a file that got many lines in one place"() {
        given:
        List<String> generated = (0..<50000).collect { "line ${it}\n".toString() }
        List<String> mine = new ArrayList<>(generated)
        mine.set(100, 'mine\n')
        List<String> withFeature = new ArrayList<>(generated)
        withFeature.set(40000, 'feature\n')

        when:
        ThreeWayMerge merge = ThreeWayMerge.of(bytes(generated.join('')), bytes(mine.join('')), bytes(withFeature.join('')))

        then:
        merge.conflicts == 0
        new String(merge.merged, StandardCharsets.UTF_8).readLines()[100] == 'mine'
        new String(merge.merged, StandardCharsets.UTF_8).readLines()[40000] == 'feature'

        when: 'a hundred thousand lines of the project before the generated ones'
        String many = (0..<100000).collect { "mine ${it}\n" }.join('')
        merge = ThreeWayMerge.of(bytes(generated.take(200).join('')), bytes(many + generated.take(200).join('')), bytes(generated.take(199).join('') + 'feature\n'))

        then:
        merge.conflicts == 0
        new String(merge.merged, StandardCharsets.UTF_8) == many + generated.take(199).join('') + 'feature\n'

        and:
        ThreeWayMerge.isComparable(bytes(generated.join('')), bytes(mine.join('')))
        !ThreeWayMerge.isComparable(bytes(generated.join('')), bytes((0..<5000).collect { "other ${it}\n" }.join('')))
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8)
    }
}
