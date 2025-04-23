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
package org.grails.commons;

import grails.core.ArtefactHandlerAdapter;
import org.grails.core.artefact.DomainClassArtefactHandler;

/**
 * @author Marc Palmer (marc@anyware.co.uk)
 */
public class CodecArtefactHandler extends ArtefactHandlerAdapter {

    public static final String TYPE = "Codec";

    public CodecArtefactHandler() {
        super(TYPE, GrailsCodecClass.class, DefaultGrailsCodecClass.class, TYPE);
    }

    @Override
    public boolean isArtefactClass(@SuppressWarnings("rawtypes") Class clazz) {
        if (clazz == null) return false;

        return clazz.getName().endsWith(DefaultGrailsCodecClass.CODEC) && !DomainClassArtefactHandler.isDomainClass(clazz);
    }
}
