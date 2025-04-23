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

import grails.databinding.CollectionDataBindingSource
import grails.databinding.DataBindingSource
import groovy.transform.CompileStatic
import org.springframework.http.HttpMethod

import jakarta.servlet.http.HttpServletRequest

import org.grails.databinding.bindingsource.DataBindingSourceCreationException;

import grails.web.mime.MimeType
import grails.web.servlet.mvc.GrailsParameterMap

@CompileStatic
abstract class AbstractRequestBodyDataBindingSourceCreator extends DefaultDataBindingSourceCreator {

    List<HttpMethod> ignoredRequestBodyMethods = [HttpMethod.GET, HttpMethod.DELETE]

    @Override
    Class getTargetType() {
        Object
    }

    @Override
    DataBindingSource createDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource) throws DataBindingSourceCreationException {
        try {
            if(bindingSource instanceof HttpServletRequest) {
                def req = (HttpServletRequest)bindingSource
                HttpMethod method = HttpMethod.valueOf(req.method)
                if (req.contentLength != 0 && !ignoredRequestBodyMethods.contains(method)) {
                    def is = req.getInputStream()
                    return createBindingSource(is, req.getCharacterEncoding())
                }
            }
            if(bindingSource instanceof InputStream) {
                def is = (InputStream)bindingSource
                return createBindingSource(is, "UTF-8")
            }
            if(bindingSource instanceof Reader) {
                def is = (Reader)bindingSource
                return createBindingSource(is)
            }

            return super.createDataBindingSource(mimeType, bindingTargetType, bindingSource)
        } catch (Exception e) {
            throw createBindingSourceCreationException(e)
        }
    }

    protected DataBindingSourceCreationException createBindingSourceCreationException(Exception e) {
        return new DataBindingSourceCreationException(e)
    }

    @Override
    CollectionDataBindingSource createCollectionDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource) throws DataBindingSourceCreationException {
        try {
            if(bindingSource instanceof GrailsParameterMap) {
                def req = bindingSource.getRequest()
                def is = req.getInputStream()
                return createCollectionBindingSource(is, req.getCharacterEncoding())
            }
            if(bindingSource instanceof HttpServletRequest) {
                def req = (HttpServletRequest)bindingSource
                def is = req.getInputStream()
                return createCollectionBindingSource(is, req.getCharacterEncoding())
            }
            if(bindingSource instanceof InputStream) {
                def is = (InputStream)bindingSource
                return createCollectionBindingSource(is, "UTF-8")
            }
            if(bindingSource instanceof Reader) {
                def is = (Reader)bindingSource
                return createCollectionBindingSource(is)
            }

            return super.createCollectionDataBindingSource(mimeType, bindingTargetType, bindingSource)
        } catch (Exception e) {
            throw new DataBindingSourceCreationException(e)
        }
    }

    protected DataBindingSource createBindingSource(InputStream inputStream, String charsetName){
        return createBindingSource(new InputStreamReader(inputStream, charsetName?:'UTF-8'))
    }

    protected abstract DataBindingSource createBindingSource(Reader reader)

    protected CollectionDataBindingSource createCollectionBindingSource(InputStream inputStream, String charsetName){
        return createCollectionBindingSource(new InputStreamReader(inputStream, charsetName?:'UTF-8'))
    }

    protected abstract CollectionDataBindingSource createCollectionBindingSource(Reader reader)
}
