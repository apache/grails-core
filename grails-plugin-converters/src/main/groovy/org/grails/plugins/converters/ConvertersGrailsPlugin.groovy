/*
 * Copyright 2004-2006 Graeme Rocher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.converters

import grails.converters.JSON
import grails.converters.XML
import grails.core.GrailsApplication
import grails.core.support.GrailsApplicationAware
import grails.plugins.Plugin
import grails.util.GrailsUtil
import org.grails.plugins.codecs.JSONCodec
import org.grails.plugins.codecs.XMLCodec
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.configuration.ObjectMarshallerRegisterer
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller as JsonErrorsMarshaller
import org.grails.web.converters.marshaller.xml.ValidationErrorsMarshaller as XmlErrorsMarshaller

/**
 * Allows the "obj as XML" and "obj as JSON" syntax.
 *
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 *
 * @since 0.6
 */
class ConvertersGrailsPlugin extends Plugin {

    def version = GrailsUtil.getGrailsVersion()
    def observe = ["controllers"]
    def dependsOn = [controllers: version, domainClass: version]
    def providedArtefacts = [
        JSONCodec,
        XMLCodec
    ]

    @Override
    Closure doWithSpring() {{->
        jsonErrorsMarshaller(JsonErrorsMarshaller)

        xmlErrorsMarshaller(XmlErrorsMarshaller)

        convertersConfigurationInitializer(ConvertersConfigurationInitializer)

        errorsXmlMarshallerRegisterer(ObjectMarshallerRegisterer) {
            marshaller = { XmlErrorsMarshaller om -> }
            converterClass = XML
        }

        errorsJsonMarshallerRegisterer(ObjectMarshallerRegisterer) {
            marshaller = { JsonErrorsMarshaller om -> }
            converterClass = JSON
        }
    }}
}
