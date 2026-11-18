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
package org.grails.plugins.codecs

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import org.grails.encoder.CodecLookup
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Specification

class CodecsConfigurationSpec extends Specification {

    void 'registers codec artefacts during Spring context initialization'() {
        given:
            DefaultGrailsApplication grailsApplication = new DefaultGrailsApplication(HTMLCodec)
            grailsApplication.initialise()
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
            context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, grailsApplication)
            context.register(CodecsConfiguration)

        when:
            context.refresh()
            CodecLookup codecLookup = context.getBean(CodecLookup)

        then:
            codecLookup.lookupEncoder('HTML') != null
            codecLookup.lookupEncoder('HTML').encode('<tag>') == '&lt;tag&gt;'

        cleanup:
            context.close()
    }

    void 'the HTML codec uses the encoder selected by grails.views.gsp.htmlcodec #htmlCodecSetting'() {
        given:
            DefaultGrailsApplication grailsApplication = new DefaultGrailsApplication(HTMLCodec)
            if (htmlCodecSetting != null) {
                grailsApplication.config[HTMLCodec.CONFIG_PROPERTY_GSP_HTMLCODEC] = htmlCodecSetting
                grailsApplication.configChanged()
            }
            grailsApplication.initialise()
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()
            grailsApplication.mainContext = context
            context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, grailsApplication)
            context.register(CodecsConfiguration)

        when:
            context.refresh()
            CodecLookup codecLookup = context.getBean(CodecLookup)

        then:
            codecLookup.lookupEncoder('HTML').encode("<a href='x'>Vid\u00E9o @ ` \\ \u2028</a>") == expected

        cleanup:
            context?.close()

        where:
            htmlCodecSetting | expected
            null             | '&lt;a href=&#39;x&#39;&gt;Vid\u00E9o &#64; &#96; &#92; &#8232;&lt;/a&gt;'
            'xml'            | '&lt;a href=&#39;x&#39;&gt;Vid\u00E9o &#64; &#96; &#92; &#8232;&lt;/a&gt;'
            'xhtml'          | '&lt;a href=&#39;x&#39;&gt;Vid\u00E9o &#64; &#96; &#92; &#8232;&lt;/a&gt;'
            'html4'          | "&lt;a href=&#39;x&#39;&gt;Vid&eacute;o @ ` \\ \u2028&lt;/a&gt;"
    }
}
