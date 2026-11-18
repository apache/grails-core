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
package grails.util

import spock.lang.Specification
import spock.lang.Unroll

class GrailsNameUtilsSpec extends Specification {

    @Unroll
    void 'getSetterName(#property) is #expected'() {
        expect:
        GrailsNameUtils.getSetterName(property) == expected

        where:
        property | expected
        'name'   | 'setName'
        'aName'  | 'setaName'
        'x'      | 'setX'
        'URL'    | 'setURL'
    }

    @Unroll
    void 'getGetterName(#property) is #expected'() {
        expect:
        GrailsNameUtils.getGetterName(property) == expected

        where:
        property | expected
        'name'   | 'getName'
        'aName'  | 'getaName'
        'x'      | 'getX'
    }

    void 'getClassName combines a logical name and a trailing name'() {
        expect:
        GrailsNameUtils.getClassName('person', 'Controller') == 'PersonController'
        GrailsNameUtils.getClassName('person', null) == 'Person'
        GrailsNameUtils.getClassName('person') == 'Person'
    }

    @Unroll
    void 'getClassName rejects the blank logical name [#name]'() {
        when:
        GrailsNameUtils.getClassName(name, 'Controller')

        then:
        thrown(IllegalArgumentException)

        where:
        name << [null, '', '   ']
    }

    void 'getFullClassName strips proxy and closure suffixes'() {
        expect:
        GrailsNameUtils.getFullClassName('com.acme.Foo$_closure1') == 'com.acme.Foo'
        GrailsNameUtils.getFullClassName('com.acme.Foo') == 'com.acme.Foo'
        GrailsNameUtils.getFullClassName(Map.Entry) == 'java.util.Map'
    }

    @Unroll
    void 'getClassNameRepresentation(#name) is #expected'() {
        expect:
        GrailsNameUtils.getClassNameRepresentation(name) == expected

        where:
        name          | expected
        null          | ''
        ''            | ''
        'foo-bar baz' | 'FooBarBaz'
        'foo'         | 'Foo'
        'a'           | 'A'
        'foo__bar'    | 'Foo__bar'
    }

    void 'getLogicalName removes the trailing name from a class or class name'() {
        expect:
        GrailsNameUtils.getLogicalName('PersonController', 'Controller') == 'Person'
        GrailsNameUtils.getLogicalName('com.acme.PersonController', 'Controller') == 'Person'
        GrailsNameUtils.getLogicalName('Person', 'Controller') == 'Person'
        GrailsNameUtils.getLogicalName('PersonController', '') == 'PersonController'
        GrailsNameUtils.getLogicalName('PersonController', null) == 'PersonController'
        GrailsNameUtils.getLogicalName(ArrayList, 'List') == 'Array'
    }

    void 'getLogicalPropertyName lower-cases the logical name'() {
        expect:
        GrailsNameUtils.getLogicalPropertyName('PersonController', 'Controller') == 'person'
        GrailsNameUtils.getLogicalPropertyName('AController', 'Controller') == 'a'
        GrailsNameUtils.getLogicalPropertyName('Person', 'Controller') == 'person'
        GrailsNameUtils.getLogicalPropertyName('Person', null) == 'person'
    }

    @Unroll
    void 'getPropertyName(#name) is #expected'() {
        expect:
        GrailsNameUtils.getPropertyName(name) == expected
        GrailsNameUtils.getPropertyNameRepresentation(name) == expected

        where:
        name         | expected
        'MyClass'    | 'myClass'
        'URL'        | 'URL'
        'a.b.Foo'    | 'foo'
        'Foo Bar'    | 'fooBar'
        ''           | ''
        'X'          | 'x'
    }

    void 'getPropertyName of a class uses its short name'() {
        expect:
        GrailsNameUtils.getPropertyName(StringBuilder) == 'stringBuilder'
        GrailsNameUtils.getPropertyNameRepresentation(Map.Entry) == 'map$Entry'
    }

    @Unroll
    void 'getPropertyNameForLowerCaseHyphenSeparatedName(#name) is #expected'() {
        expect:
        GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(name) == expected

        where:
        name            | expected
        'foo-bar'       | 'fooBar'
        'foo'           | 'foo'
        'foo--bar-baz'  | 'fooBarBaz'
        ''              | ''
    }

    void 'getNameFromScript converts a hyphenated script name to a class name'() {
        expect:
        GrailsNameUtils.getNameFromScript('my-funk-grails-script') == 'MyFunkGrailsScript'
        GrailsNameUtils.getNameFromScript('script') == 'Script'
    }

    @Unroll
    void 'getShortName and getPackageName split #name'() {
        expect:
        GrailsNameUtils.getShortName(name) == shortName
        GrailsNameUtils.getPackageName(name) == packageName

        where:
        name        | shortName | packageName
        'a.b.C'     | 'C'       | 'a.b'
        'C'         | 'C'       | ''
        'a.C'       | 'C'       | 'a'
    }

    void 'getShortName of a class drops the package'() {
        expect:
        GrailsNameUtils.getShortName(String) == 'String'
    }

    @Unroll
    void 'getScriptName(#name) is #expected'() {
        expect:
        GrailsNameUtils.getScriptName(name) == expected

        where:
        name                     | expected
        'MyFunkyGrailsScript'    | 'my-funky-grails-script'
        'MyFunkyScript.groovy'   | 'my-funky-script'
        'script'                 | 'script'
    }

    void 'getScriptName tolerates null'() {
        expect:
        GrailsNameUtils.getScriptName((String) null) == null
        GrailsNameUtils.getScriptName((Class) null) == null
        GrailsNameUtils.getScriptName(StringBuilder) == 'string-builder'
    }

    @Unroll
    void 'getPluginName(#descriptor) is #expected'() {
        expect:
        GrailsNameUtils.getPluginName(descriptor) == expected

        where:
        descriptor                    | expected
        'DbUtilsGrailsPlugin.groovy'  | 'db-utils'
        'FooGrailsPlugin.groovy'      | 'foo'
        ''                            | ''
        null                          | null
    }

    void 'getPluginName rejects a name that is not a plugin descriptor'() {
        when:
        GrailsNameUtils.getPluginName('Foo.groovy')

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Plugin descriptor name is not valid: Foo.groovy'
    }

    @Unroll
    void 'getNaturalName(#name) is #expected'() {
        expect:
        GrailsNameUtils.getNaturalName(name) == expected

        where:
        name                  | expected
        'firstName'           | 'First Name'
        'MyFunkyGrailsScript' | 'My Funky Grails Script'
        'URL'                 | 'URL'
        'address1'            | 'Address1'
        'com.acme.lastName'   | 'Last Name'
        'a'                   | 'A'
    }

    @Unroll
    void 'isBlank([#value]) is #expected'() {
        expect:
        GrailsNameUtils.isBlank(value) == expected

        where:
        value  | expected
        null   | true
        ''     | true
        '  \t' | true
        'a'    | false
        ' a '  | false
    }

    @Unroll
    void 'isValidJavaPackage(#name) is #expected'() {
        expect:
        GrailsNameUtils.isValidJavaPackage(name) == expected

        where:
        name         | expected
        'a.b.c'      | true
        'abc'        | true
        'a..b'       | false
        '1a.b'       | false
        'a.b-c'      | false
        ''           | false
        null         | false
    }

    @Unroll
    void 'isValidJavaIdentifier(#name) is #expected'() {
        expect:
        GrailsNameUtils.isValidJavaIdentifier(name) == expected

        where:
        name     | expected
        'foo_1'  | true
        '$bar'   | true
        '1foo'   | false
        'a-b'    | false
        ' '      | false
        null     | false
    }

    void 'getPropertyNameConvention names scalars after their type'() {
        expect:
        GrailsNameUtils.getPropertyNameConvention(null) == null
        GrailsNameUtils.getPropertyNameConvention('text') == 'string'
        GrailsNameUtils.getPropertyNameConvention('text', 'X') == 'stringX'
        GrailsNameUtils.getPropertyNameConvention(new StringBuilder()) == 'stringBuilder'
    }

    void 'getPropertyNameConvention names collections after their element type'() {
        expect:
        GrailsNameUtils.getPropertyNameConvention([]) == 'emptyCollection'
        GrailsNameUtils.getPropertyNameConvention(['a']) == 'stringList'
        GrailsNameUtils.getPropertyNameConvention(['a'], 'X') == 'stringXList'
        GrailsNameUtils.getPropertyNameConvention(['a'] as Set) == 'stringSet'
        GrailsNameUtils.getPropertyNameConvention(new ArrayDeque(['a'])) == 'stringCollection'
    }

    void 'getPropertyNameConvention names maps after their value type'() {
        expect:
        GrailsNameUtils.getPropertyNameConvention([:]) == 'emptyMap'
        GrailsNameUtils.getPropertyNameConvention([k: 'v']) == 'stringMap'
        GrailsNameUtils.getPropertyNameConvention([k: null]) == null
    }

    void 'getPropertyNameConvention names arrays after their component type'() {
        expect:
        GrailsNameUtils.getPropertyNameConvention([1, 2] as int[]) == 'intArray'
        GrailsNameUtils.getPropertyNameConvention(['a'] as String[], 'X') == 'stringXArray'
    }

    @Unroll
    void 'getPropertyForGetter(#getter, #returnType) is #expected'() {
        expect:
        GrailsNameUtils.getPropertyForGetter(getter, returnType) == expected

        where:
        getter      | returnType | expected
        'getFoo'    | 'String'   | 'foo'
        'getURL'    | 'String'   | 'URL'
        'getA'      | 'String'   | 'a'
        'getaName'  | 'String'   | 'aName'
        'get_x'     | 'String'   | '_x'
        'get'       | 'String'   | null
        'getfoo'    | 'String'   | null
        'get1'      | 'String'   | null
        'getx'      | 'String'   | null
        'isActive'  | 'boolean'  | 'active'
        'isActive'  | 'String'   | null
        'foo'       | 'String'   | null
        ''          | 'String'   | null
        null        | 'String'   | null
    }

    void 'getPropertyForGetter defaults to a boolean return type'() {
        expect:
        GrailsNameUtils.getPropertyForGetter('isActive') == 'active'
        GrailsNameUtils.getPropertyForGetter('getName') == 'name'
        GrailsNameUtils.getPropertyForGetter('isActive', String) == null
        GrailsNameUtils.getPropertyForGetter('isActive', boolean) == 'active'
    }

    @Unroll
    void 'isGetter(#name, #returnType, #args) is #expected'() {
        expect:
        GrailsNameUtils.isGetter(name, returnType, args as Class[]) == expected

        where:
        name         | returnType | args       | expected
        'getFoo'     | String     | []         | true
        'getFoo'     | String     | [String]   | false
        'getX'       | String     | []         | true
        'getaProp'   | String     | []         | true
        'getx'       | String     | []         | false
        'get2other'  | String     | []         | false
        'get'        | String     | []         | false
        'isActive'   | boolean    | []         | true
        'isActive'   | String     | []         | false
        'isactive'   | boolean    | []         | false
        'foo'        | String     | []         | false
        ''           | String     | []         | false
        null         | String     | []         | false
        'getFoo'     | String     | null       | false
    }

    void 'the deprecated isGetter overload assumes a boolean return type'() {
        expect:
        GrailsNameUtils.isGetter('isActive', [] as Class[])
        GrailsNameUtils.isGetter('getActive', [] as Class[])
        !GrailsNameUtils.isGetter('getActive', [String] as Class[])
    }

    @Unroll
    void 'getPropertyForSetter(#setter) is #expected'() {
        expect:
        GrailsNameUtils.getPropertyForSetter(setter) == expected

        where:
        setter     | expected
        'setFoo'   | 'foo'
        'setURL'   | 'URL'
        'set'      | null
        'setfoo'   | null
        'getFoo'   | null
        ''         | null
        null       | null
    }

    @Unroll
    void 'convertValidPropertyMethodSuffix(#suffix) is #expected'() {
        expect:
        GrailsNameUtils.convertValidPropertyMethodSuffix(suffix) == expected

        where:
        suffix  | expected
        ''      | null
        '1a'    | null
        'A'     | 'a'
        'a'     | null
        'Name'  | 'name'
        'aName' | 'aName'
        '_x'    | '_x'
        'name'  | null
    }

}
