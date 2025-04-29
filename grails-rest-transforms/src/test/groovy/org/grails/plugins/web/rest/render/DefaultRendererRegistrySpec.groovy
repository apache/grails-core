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
package org.grails.plugins.web.rest.render

import grails.rest.render.AbstractRenderer
import grails.rest.render.RenderContext
import grails.rest.render.hal.HalJsonCollectionRenderer
import grails.web.mime.MimeType
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import spock.lang.Specification

class DefaultRendererRegistrySpec extends Specification {

    void "Test that registering a HAL collection renderer works"() {
        given:"A registry with a specific renderer"
            def registry = new DefaultRendererRegistry()

        when:"A HAL collection renderer is specified"
            registry.addRenderer(new HalJsonCollectionRenderer(URL))
            def list = new LinkedList()
            list << new URL("https://grails.org")
        then:"The renderer is available"
            registry.findContainerRenderer(MimeType.HAL_JSON, LinkedList, list) != null

    }

    void "Test that the registry returns an appropriate render for a container type"() {
        when:"A registry with a specific renderer"
            def registry = new DefaultRendererRegistry()
            registry.initialize()


        then:"An errors renderer can be found"
            registry.findContainerRenderer(MimeType.XML, Errors, new BeanPropertyBindingResult("foo", "bar"))
            !registry.findContainerRenderer(MimeType.XML, List, new URL("https://grails.org"))

        when:"A collection renderer is specified"
            registry.addContainerRenderer(URL, new AbstractRenderer(List, MimeType.XML) {
                @Override
                void render(Object object, RenderContext context) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            })
            List<URL> list =  [new URL("https://grails.org")]

        then:"A renderer is found"
            registry.findContainerRenderer(MimeType.XML, List, new URL("https://grails.org"))
            registry.findContainerRenderer(MimeType.XML, List, list)
    }

    void "Test that registry returns appropriate renderer for type"() {
        given:"A registry with a specific renderer"
            def registry = new DefaultRendererRegistry()
            registry.initialize()
            def mimeType = new MimeType("text/xml", 'xml')
            registry.addRenderer(new AbstractRenderer(URL,mimeType) {
                @Override
                void render(Object object, RenderContext context) {

                }
            })

        expect:"A renderer is found"
            registry.findRenderer(mimeType, URL)
            registry.findRenderer(mimeType, URL).mimeTypes.contains mimeType
            registry.findRenderer(mimeType, new URL("https://grails.org"))
            registry.findRenderer(mimeType, new URL("https://grails.org")).mimeTypes.contains mimeType
    }

    void "Test that registry returns appropriate renderer for subclass"() {
        given:"A registry with a specific renderer"
            def registry = new DefaultRendererRegistry()
            registry.initialize()
            def mimeType = new MimeType("text/xml", 'xml')
            registry.addRenderer(new AbstractRenderer(CharSequence,mimeType) {
                @Override
                void  render(Object object, RenderContext context) {

                }
            })

        expect:"A renderer is found"
            registry.findRenderer(mimeType, "foo")
            registry.findRenderer(mimeType, "foo").mimeTypes.contains mimeType

            registry.findRenderer(mimeType, String)
            registry.findRenderer(mimeType, String).mimeTypes.contains mimeType
    }

    void "Test that registry fallbacks to a default renderer if none found"() {
        given:"A registry with a specific renderer"
            def registry = new DefaultRendererRegistry()
            registry.initialize()
            def mimeType = new MimeType("text/xml", 'xml')
            registry.addDefaultRenderer(new AbstractRenderer(Object,mimeType) {
                @Override
                void  render(Object object, RenderContext context) {

                }
            })

        expect:"A renderer is found"
            registry.findRenderer(mimeType, String)
            registry.findRenderer(mimeType, String).mimeTypes.contains mimeType
            registry.findRenderer(mimeType, "foo")
            registry.findRenderer(mimeType, "foo").mimeTypes.contains mimeType
    }
}

