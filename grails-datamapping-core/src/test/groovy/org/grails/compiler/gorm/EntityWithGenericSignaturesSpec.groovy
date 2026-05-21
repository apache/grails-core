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
package org.grails.compiler.gorm

import groovy.transform.Generated

import spock.lang.Specification

import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext

class EntityWithGenericSignaturesSpec extends Specification {

    void 'test compile entity with generic signatures'() {

        when: 'an entity with generic signatures is compiled'
        def cls = new GroovyClassLoader().parseClass('''
            package test
            
            import grails.gorm.annotation.Entity
            import grails.gorm.dirty.checking.DirtyCheck
            
            @Entity
            class Widget<SettingType extends WidgetSetting> {
                SettingType setting
            
            }
            
            @Entity
            abstract class WidgetSetting {
                 String name
            }
            
            @Entity
            class HotWidgetSetting extends WidgetSetting {
                Integer temperature
            }
        ''')

        then: 'the entity is compiled correctly'
        cls.name == 'test.Widget'
        cls.getMethod('getSetting').returnType.name == 'test.WidgetSetting'


        when: 'a mapping context is created'
        def mappingContext = new KeyValueMappingContext('test')
        def entity = mappingContext.addPersistentEntity(cls)

        then: 'the entity has a setting property'
        entity.getPropertyByName('setting')
        entity.getPropertyByName('setting').type.name == 'test.WidgetSetting'

        and: 'generated methods are marked as Generated'
        cls.getMethod('getSetting').isAnnotationPresent(Generated)
        cls.getMethod('getId').isAnnotationPresent(Generated)
        cls.getMethod('getVersion').isAnnotationPresent(Generated)
    }
}
