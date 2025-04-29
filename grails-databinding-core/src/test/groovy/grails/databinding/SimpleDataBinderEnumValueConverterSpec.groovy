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
package grails.databinding

import grails.databinding.SimpleDataBinder;
import grails.databinding.SimpleMapDataBindingSource;
import grails.databinding.converters.ValueConverter;
import spock.lang.Issue
import spock.lang.Specification

class SimpleDataBinderEnumValueConverterSpec extends Specification {

    @Issue('GRAILS-10837')
    void 'Test ValueConverter for enum'() {
        given:
        def binder = new SimpleDataBinder()
        binder.registerConverter(new ColorConverter())
        def hat = new Hat()
        
        when:
        binder.bind hat, [hatColor: '2', hatSize: 'LARGE'] as SimpleMapDataBindingSource
        
        then:
        hat.hatColor == Color.GREEN
        hat.hatSize == Size.LARGE
    }
}

class Hat {
    Color hatColor
    Size hatSize
}

enum Size {
    MEDIUM, LARGE
}

enum Color {
    RED('1'),
    GREEN('2'),
    BLUE('3')

    String id

    Color(String id) {
        this.id = id
    }

    static getById(String id) {
        Color.find{ it.id == id }
    }
}

class ColorConverter implements ValueConverter {
    @Override
    boolean canConvert(Object value) {
        value instanceof String
    }

    @Override
    Object convert(Object value) {
        Color.getById(value)
    }

    @Override
    Class<?> getTargetType() {
        Color
    }
}
