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
package org.grails.web.databinding.bindingsource

import grails.databinding.SimpleMapDataBindingSource
import grails.web.mime.MimeType
import org.grails.databinding.bindingsource.DataBindingSourceCreator
import spock.lang.Specification

class DefaultDataBindingSourceRegistrySpec extends Specification {

    void 'a creator registered for a media type is found for #requested, whatever case it is in'() {
        given: 'a creator an application registers directly, for a type no configuration names'
        def registry = new DefaultDataBindingSourceRegistry()
        registry.initialize()
        registry.addDataBindingSourceCreator(Stub(DataBindingSourceCreator) {
            getTargetType() >> Object
            getMimeTypes() >> ([new MimeType('application/vnd.Acme+json')] as MimeType[])
            createDataBindingSource(_, _, _) >> new SimpleMapDataBindingSource([name: 'acme'])
        })

        expect:
        registry.createDataBindingSource(new MimeType(requested), Object, [:]).getPropertyValue('name') == 'acme'

        where:
        requested << ['application/vnd.Acme+json', 'application/vnd.acme+json']
    }

    void 'a creator registered for an interface handles the bodies bound to a class that implements it'() {
        given: 'a creator for a type no creator for Object handles'
        def shapes = new MimeType('application/vnd.shape+json', 'shape')
        def registry = new DefaultDataBindingSourceRegistry()
        registry.initialize()
        registry.addDataBindingSourceCreator(Stub(DataBindingSourceCreator) {
            getTargetType() >> Shape
            getMimeTypes() >> ([shapes] as MimeType[])
            createDataBindingSource(_, _, _) >> new SimpleMapDataBindingSource([name: 'shape'])
        })

        expect:
        registry.createDataBindingSource(shapes, Circle, [:]).getPropertyValue('name') == 'shape'
    }

    void 'a creator registered for a version of a media type is found for #requested, by its name in any case'() {
        given: 'a creator for one version of a type'
        def registry = new DefaultDataBindingSourceRegistry()
        registry.initialize()
        registry.addDataBindingSourceCreator(Stub(DataBindingSourceCreator) {
            getTargetType() >> Object
            getMimeTypes() >> ([new MimeType('application/vnd.Acme+json;v=1')] as MimeType[])
            createDataBindingSource(_, _, _) >> new SimpleMapDataBindingSource([name: 'acme'])
        })

        expect: 'a request for another version finds it by name, as no creator is registered for that version'
        registry.createDataBindingSource(new MimeType(requested), Object, [:]).getPropertyValue('name') == 'acme'

        where:
        requested << ['application/vnd.Acme+json;v=2', 'application/vnd.acme+json;v=2']
    }
}

interface Shape {
}

class Circle implements Shape {
}
