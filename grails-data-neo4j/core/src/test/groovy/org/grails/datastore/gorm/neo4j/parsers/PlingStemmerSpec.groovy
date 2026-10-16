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
package org.grails.datastore.gorm.neo4j.parsers

import spock.lang.Specification
import spock.lang.Unroll

class PlingStemmerSpec extends Specification {

    @Unroll
    void "stem(#word) == #singular"() {
        expect:
        PlingStemmer.stem(word) == singular

        where:
        word          | singular
        'boy'         | 'boy'
        'boys'        | 'boy'
        'biophysics'  | 'biophysics'
        'automata'    | 'automaton'
        'genus'       | 'genus'
        'emus'        | 'emu'
        'children'    | 'child'
        'firemen'     | 'fireman'
        'mice'        | 'mouse'
        'appendices'  | 'appendix'
        'analyses'    | 'analysis'
        'buses'       | 'bus'
        'boxes'       | 'box'
        'cities'      | 'city'
        'wolves'      | 'wolf'
        'churches'    | 'church'
        'heroes'      | 'hero'
        'radii'       | 'radius'
        'data'        | 'datum'
        'series'      | 'series'
        'physics'     | 'physic'
        'books'       | 'book'
        'glass'       | 'glass'
    }

    void "plural and singular forms are classified"() {
        expect:
        PlingStemmer.isPlural('boys')
        !PlingStemmer.isPlural('boy')
        PlingStemmer.isSingular('boy')
        !PlingStemmer.isSingular('boys')
        PlingStemmer.isSingularAndPlural('physics')
        !PlingStemmer.isSingularAndPlural('boys')
        PlingStemmer.isPlural('children')
        PlingStemmer.isSingular('child')
    }

}
