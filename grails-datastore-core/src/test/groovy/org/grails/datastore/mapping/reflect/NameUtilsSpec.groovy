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
package org.grails.datastore.mapping.reflect

import spock.lang.Specification
import spock.lang.Unroll

class NameUtilsSpec extends Specification {

    @Unroll
    void "decapitalizeFirstChar for #name should be #expected"(String name, String expected) {
        expect:
        NameUtils.decapitalizeFirstChar(name) == expected

        where:
        name    | expected
        'Name'  | 'name'
        'name'  | 'name'
        'IName' | 'iName'
    }

    @Unroll
    void "isValidPropertyPath accepts identifier-shaped path #path"() {
        expect:
        NameUtils.isValidPropertyPath(path)

        where:
        path << [
                'name',
                'a',
                '_name',
                '$name',
                'name$2',
                'name2',
                'author.name',
                'author.address.city',
                'c1.name',
                'naïve',
                '名前',
                'name.名前',
        ]
    }

    @Unroll
    void "isValidPropertyPath rejects malformed path #description"() {
        expect:
        !NameUtils.isValidPropertyPath(path)

        where:
        path                | description
        null                | 'null'
        ''                  | 'empty'
        ' '                 | 'blank'
        'name '             | 'trailing whitespace'
        ' name'             | 'leading whitespace'
        'name desc'         | 'embedded whitespace'
        'name, e.id'        | 'comma with a second expression'
        'name,id'           | 'comma'
        'name;'             | 'semicolon'
        "name'"             | 'quote'
        'upper(name)'       | 'function call'
        'name-x'            | 'hyphen'
        'name/*'            | 'comment opener'
        '1name'             | 'leading digit'
        '.name'             | 'leading dot'
        'name.'             | 'trailing dot'
        'a..b'              | 'empty segment'
        'a.1b'              | 'segment starting with digit'
        'name\u0000'        | 'NUL control character'
        'na\u200Bme'        | 'zero-width space'
        'name\n'            | 'newline'
    }
}
