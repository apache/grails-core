/*
 * Copyright 2011 SpringSource.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class CollectionUtilsTests {

    @Test
    void testNewMapEvenArgs() {
        def map = CollectionUtils.newMap("foo", 1, "bar", 2, "baz", 42)
        assertNotNull map
        assertEquals 3, map.size()
        assertEquals 1, map.foo
        assertEquals 2, map.bar
        assertEquals 42, map.baz
    }

    @Test
    void testNewMapOddArgs() {
        assertThrows(IllegalArgumentException) {
            CollectionUtils.newMap "foo", 1, "bar"
        }
    }

    @Test
    void testNewMapNull() {
        def map = CollectionUtils.newMap(null)
        assertTrue map instanceof Map
        assertEquals 0, map.size()
    }

    @Test
    void testNewSetNull() {
        def set = CollectionUtils.newSet(null)
        assertTrue set instanceof Set
        assertEquals 0, set.size()
    }

    @Test
    void testNewSet() {
        def set = CollectionUtils.newSet(1, 2, 42)
        assertTrue set instanceof Set
        assertEquals 3, set.size()
        assertTrue set.contains(1)
        assertTrue set.contains(2)
        assertTrue set.contains(42)
    }

    @Test
    void testNewListNull() {
        def list = CollectionUtils.newList(null)
        assertTrue list instanceof List
        assertEquals 0, list.size()
    }

    @Test
    void testNewList() {
        def list = CollectionUtils.newList(1, 2, 42)
        assertTrue list instanceof List
        assertEquals 3, list.size()
        assertEquals([1, 2, 42], list)
    }
}
