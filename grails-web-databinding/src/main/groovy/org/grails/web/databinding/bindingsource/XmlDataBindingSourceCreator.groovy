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

import groovy.transform.CompileStatic
import groovy.xml.slurpersupport.GPathResult

import org.xml.sax.SAXParseException

import grails.databinding.CollectionDataBindingSource
import grails.databinding.DataBindingSource
import grails.databinding.SimpleMapDataBindingSource
import grails.web.mime.MimeType
import org.grails.databinding.bindingsource.DataBindingSourceCreationException
import org.grails.databinding.xml.GPathResultCollectionDataBindingSource
import org.grails.databinding.xml.GPathResultMap
import org.grails.io.support.SpringIOUtils

/**
 * Creates DataBindingSource objects from XML in the request body
 *
 * @since 2.3
 * @see DataBindingSource
 * @see DataBindingSourceCreator
 */
@CompileStatic
class XmlDataBindingSourceCreator extends AbstractRequestBodyDataBindingSourceCreator {

    @Override
    MimeType[] getMimeTypes() {
        [MimeType.XML, MimeType.TEXT_XML] as MimeType[]
    }

    @Override
    DataBindingSource createDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource) {
        if(bindingSource instanceof GPathResult) {
            def gpathMap = new GPathResultMap((GPathResult)bindingSource)
            return new SimpleMapDataBindingSource(gpathMap)
        }
        return super.createDataBindingSource(mimeType, bindingTargetType, bindingSource)
    }

    @Override
    protected DataBindingSource createBindingSource(Reader reader) {
        def gpath = SpringIOUtils.createXmlSlurper().parse(reader)
        def gpathMap = new GPathResultMap(gpath)
        return new SimpleMapDataBindingSource(gpathMap)
    }

    @Override
    CollectionDataBindingSource createCollectionDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource) {
        if(bindingSource instanceof GPathResult) {
            new GPathResultCollectionDataBindingSource(bindingSource)
        }
        else {
            return super.createCollectionDataBindingSource(mimeType, bindingTargetType, bindingSource)
        }
    }

    @Override
    protected CollectionDataBindingSource createCollectionBindingSource(Reader reader) {
        def gpath = SpringIOUtils.createXmlSlurper().parse(reader)
        return new GPathResultCollectionDataBindingSource(gpath)
    }

    @Override
    protected DataBindingSourceCreationException createBindingSourceCreationException(Exception e) {
        if(e instanceof SAXParseException) {
            return new InvalidRequestBodyException(e)
        }
        return super.createBindingSourceCreationException(e)
    }

}
