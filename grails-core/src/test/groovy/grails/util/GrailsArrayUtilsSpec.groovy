/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.util

import grails.util.GrailsArrayUtils
import spock.lang.Specification

/**
 * Created by graemerocher on 31/01/14.
 */
class GrailsArrayUtilsSpec extends Specification {

    void 'Test contains(Object[], Object) method'() {
        expect:
            GrailsArrayUtils.contains(['one', 'two'] as String[], "one")
            GrailsArrayUtils.contains(['one', 'two'] as String[], "two")
            !GrailsArrayUtils.contains(['one', 'two'] as String[], "three")
            !GrailsArrayUtils.contains(null, "one")
            !GrailsArrayUtils.contains([] as String[], "one")
    }
    
    void "Test toString() method"() {
        expect:
            GrailsArrayUtils.toString([1,2,3] as int[]) == '{1, 2, 3}'
    }

    void "Test add*() methods"() {
        given:
            int[] a = [1,2,3]
        expect:
            GrailsArrayUtils.add(a, 1, 4) == [1,4,2,3] as int[]
            GrailsArrayUtils.add(a, 0, 4) == [4,1,2,3] as int[]
            GrailsArrayUtils.add(a, 3, 4) == [1,2,3,4] as int[]
            GrailsArrayUtils.addToEnd(a, 4) == [1,2,3,4] as int[]
            GrailsArrayUtils.addToStart(a, 4) == [4,1,2,3] as int[]
            GrailsArrayUtils.addAll(a, [4,5] as int[]) == [1,2,3,4,5] as int[]
    }

    void "Test subarray method"() {
        given:
            int[] a = [1,2,3,4,5,6]
        expect:
            GrailsArrayUtils.subarray(a, -1,4) == [1,2,3,4] as int[]
            GrailsArrayUtils.subarray(a, 2,4) == [3,4] as int[]
            GrailsArrayUtils.subarray(a, 0,3) == [1,2,3] as int[]
            GrailsArrayUtils.subarray(a, 3,5) == [4,5] as int[]
            GrailsArrayUtils.subarray(a, 3,10) == [4,5,6] as int[]
    }
}

