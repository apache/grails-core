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

package grails.views

import groovy.transform.CompileStatic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import grails.views.api.GrailsView

/**
 * A script that is writable
 *
 * @author Graeme Rocher
 */
@CompileStatic
abstract class AbstractWritableScript extends Script implements WritableScript, GrailsView {

    /**
     * A logger that can be used within views
     */
    protected static final Logger log = LoggerFactory.getLogger(AbstractWritableScript)

    Writer out
    /**
     * The source file
     */
    File sourceFile

    private Map<String, Class> modelTypes

    /**
     * @return The current writer
     */
    Writer getOut() {
        return out
    }

    @Override
    final Writer writeTo(Writer out) throws IOException {
        setOut(out)
        try {
            return doWrite(out)
        } catch (Throwable e) {
            if(ViewsEnvironment.isDevelopmentMode() && sourceFile != null) {
                throw new ViewRenderException("Error rendering view: ${e.message}", e, this)
            }
            else {
                throw new ViewException("Error rendering view: ${e.message}", e)
            }
        }
    }

    /**
     * Subclasses should implement to perform the write
     * @param writer The writer
     * @return The original writer or a wrapped version
     */
    abstract Writer doWrite(Writer writer)


    void setModelTypes(Map<String, Class> modelTypes) {
        this.modelTypes = modelTypes
    }

    Map<String, Class> getModelTypes() {
        return modelTypes
    }
}
