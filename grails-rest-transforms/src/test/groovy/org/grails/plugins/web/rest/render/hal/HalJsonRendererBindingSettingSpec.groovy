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
package org.grails.plugins.web.rest.render.hal

import java.util.function.Supplier

import tools.jackson.core.json.JsonReadFeature
import tools.jackson.databind.json.JsonMapper

import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

import grails.databinding.CollectionDataBindingSource
import grails.databinding.DataBindingSource
import grails.databinding.SimpleMapDataBindingSource
import grails.rest.render.RendererRegistry
import grails.rest.render.hal.HalJsonRenderer
import grails.web.mapping.LinkGenerator
import grails.web.mime.MimeType
import org.grails.databinding.bindingsource.DataBindingSourceCreator
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.databinding.bindingsource.DataBindingSourceRegistry
import org.grails.web.databinding.bindingsource.DefaultDataBindingSourceRegistry
import org.grails.web.databinding.bindingsource.HalJsonDataBindingSourceCreator

class HalJsonRendererBindingSettingSpec extends Specification {

    void 'a HAL renderer bean reads grails.databinding.json.jackson, which is #jackson, to parse the bodies of its type'() {
        given: 'a HAL renderer bean, and an application HAL creator'
        def context = new GenericApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', ['grails.databinding.json.jackson': jackson]))
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.registerBean(DataBindingSourceRegistry, { new DefaultDataBindingSourceRegistry() })
        def linkGenerator = Stub(LinkGenerator)
        context.registerBean(LinkGenerator, { linkGenerator })
        context.registerBean('grailsDomainClassMappingContext', MappingContext, { new KeyValueMappingContext('hal') })
        def rendererRegistry = Stub(RendererRegistry)
        context.registerBean(RendererRegistry, { rendererRegistry })
        context.registerBean('halJsonDataBindingSourceCreator', DataBindingSourceCreator, { new ApplicationHalCreator() })
        context.registerBean('productRenderer', HalJsonRenderer, Product)
        context.refresh()
        def request = new MockHttpServletRequest()
        request.contentType = HalJsonRenderer.MIME_TYPE.name
        request.content = '{"name": "MacBook"}'.bytes

        when:
        def source = context.getBean(DataBindingSourceRegistry).createDataBindingSource(HalJsonRenderer.MIME_TYPE, Product, request)

        then:
        source.getPropertyValue('name') == name

        cleanup:
        context.close()

        where:
        jackson || name
        'true'  || 'from the application creator'
        'false' || 'MacBook'
    }
}

class HalJsonRendererOneClassCreatorSpec extends Specification {

    void "with Jackson parsing, a HAL body of the renderer's type is parsed with the application's mapper, where the application's HAL creator is for another class"() {
        given: "a HAL renderer bean, an application HAL creator for one other class, and the application's mapper"
        def context = new GenericApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', ['grails.databinding.json.jackson': 'true']))
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.registerBean(DataBindingSourceRegistry, { new DefaultDataBindingSourceRegistry() })
        def linkGenerator = Stub(LinkGenerator)
        context.registerBean(LinkGenerator, { linkGenerator })
        context.registerBean('grailsDomainClassMappingContext', MappingContext, { new KeyValueMappingContext('hal') })
        def rendererRegistry = Stub(RendererRegistry)
        context.registerBean(RendererRegistry, { rendererRegistry })
        context.registerBean(JsonMapper, { JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build() })
        context.registerBean('halJsonDataBindingSourceCreator', DataBindingSourceCreator, { new OneClassHalCreator() })
        context.registerBean('productRenderer', HalJsonRenderer, Product)
        context.refresh()
        def request = new MockHttpServletRequest()
        request.contentType = HalJsonRenderer.MIME_TYPE.name
        request.content = '/* a comment */ {"name": "MacBook"}'.bytes

        when:
        def source = context.getBean(DataBindingSourceRegistry).createDataBindingSource(HalJsonRenderer.MIME_TYPE, Product, request)

        then: 'not by the creator for the other class'
        source.getPropertyValue('name') == 'MacBook'

        cleanup:
        context.close()
    }
}

class HalJsonRendererOwnSettingSpec extends Specification {

    void "a HAL renderer set to parse with Jackson parses the bodies of its type as #description says"() {
        given: 'the setting off, a renderer bean set to parse with Jackson, and the HAL creator bean'
        def context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.registerBean(DataBindingSourceRegistry, { new DefaultDataBindingSourceRegistry() })
        def linkGenerator = Stub(LinkGenerator)
        context.registerBean(LinkGenerator, { linkGenerator })
        context.registerBean('grailsDomainClassMappingContext', MappingContext, { new KeyValueMappingContext('hal') })
        def rendererRegistry = Stub(RendererRegistry)
        context.registerBean(RendererRegistry, { rendererRegistry })
        context.registerBean(JsonMapper, { JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build() })
        context.registerBean('halJsonDataBindingSourceCreator', DataBindingSourceCreator, creator as Supplier<DataBindingSourceCreator>)
        def renderer = new RootBeanDefinition(HalJsonRenderer)
        renderer.constructorArgumentValues.addGenericArgumentValue(Product)
        renderer.propertyValues.add('useJackson', true)
        context.registerBeanDefinition('productRenderer', renderer)
        context.refresh()
        def request = new MockHttpServletRequest()
        request.contentType = HalJsonRenderer.MIME_TYPE.name
        request.content = '/* a comment */ {"name": "MacBook"}'.bytes

        when:
        def source = context.getBean(DataBindingSourceRegistry).createDataBindingSource(HalJsonRenderer.MIME_TYPE, Product, request)

        then:
        source.getPropertyValue('name') == name

        cleanup:
        context.close()

        where:
        description                                                      | creator                                     || name
        "an application creator for another class, the application's mapper" | { new OneClassHalCreator() }                || 'MacBook'
        "Grails' HAL creator, which the setting leaves on JsonSlurper, the application's mapper" | { new HalJsonDataBindingSourceCreator() } || 'MacBook'
        'a creator the application implements itself'                    | { new ApplicationHalCreator() }             || 'from the application creator'
    }
}

class OneClassHalCreator extends ApplicationHalCreator {

    @Override
    Class getTargetType() {
        OtherHalType
    }
}

class OtherHalType {
    String name
}

class ApplicationHalCreator implements DataBindingSourceCreator {

    @Override
    Class getTargetType() {
        Object
    }

    @Override
    MimeType[] getMimeTypes() {
        [HalJsonRenderer.MIME_TYPE] as MimeType[]
    }

    @Override
    DataBindingSource createDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource) {
        new SimpleMapDataBindingSource([name: 'from the application creator'])
    }

    @Override
    CollectionDataBindingSource createCollectionDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource) {
        throw new UnsupportedOperationException()
    }
}
