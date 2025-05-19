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
package org.grails.spring.context

import org.grails.spring.context.support.ReloadableResourceBundleMessageSource
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import spock.lang.Specification

class ResourceBundleMessageSourceSpec extends Specification {
    Resource messages
    Resource other 
    void setup(){
        messages = new TestResource('messages.properties','''\
            foo=bar
            shared.message=Messages Message
        '''.stripIndent().getBytes('UTF-8'))
         
        other = new TestResource('other.properties','''\
            bar=foo
            shared.message=Other Message
        '''.stripIndent().getBytes('UTF-8'))
    }
    
    void 'Check method to retrieve bundle codes per messagebundle'(){
        given:
            def messageSource = new ReloadableResourceBundleMessageSource(
                resourceLoader: new DefaultResourceLoader(){
                    Resource getResourceByPath(String path){
                        path.startsWith('messages') ? messages:other
                    }
                }
            )
            messageSource.setBasenames('messages','other')
            def locale = Locale.default
        expect:
            messageSource.getBundleCodes(locale,'messages') == (['foo', 'shared.message'] as Set)
            messageSource.getBundleCodes(locale,'other') == (['bar', 'shared.message'] as Set)
            messageSource.getBundleCodes(locale,'messages','other') == (['foo','bar', 'shared.message'] as Set)
    }
    
    void 'Check method to verify ResourceBundle ordering prioritizes application over plugin messages'(){
        given:
        def messageSource = new ReloadableResourceBundleMessageSource(
          resourceLoader: new DefaultResourceLoader(){
              Resource getResourceByPath(String path){
                  path.startsWith('messages') ? messages:other
              }
          }
        )
        messageSource.setBasenames('other', 'messages')
        def locale = Locale.default
        expect: "other messages override plugin messages"
        messageSource.getMessage('shared.message', null, locale) == 'Other Message'
        messageSource.getMessage('foo', null, locale) == 'bar'
    }

    class TestResource extends ByteArrayResource{
        String filename

        TestResource(String filename, byte[] byteArray) {
            super(byteArray)
            this.filename=filename
        }
        
    }
    
}
