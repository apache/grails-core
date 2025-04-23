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
package org.grails.plugins;

import groovy.xml.slurpersupport.GPathResult;
import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.io.support.SpringIOUtils;
import org.springframework.core.io.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Holds a reference to the parsed grails-plugin.xml descriptor and the
 * resource used to parse the descriptor
 *
 */
public class BinaryGrailsPluginDescriptor {

    private final Resource resource;
    private final List<String> providedlassNames;
    private GPathResult parsedXml;

    public BinaryGrailsPluginDescriptor(Resource resource, List<String> providedlassNames) {
        this.resource = resource;
        this.providedlassNames = providedlassNames;
    }

    /**
     * The resource the descriptor was parsed from
     *
     * @return The resource instance
     */
    public Resource getResource() {
        return resource;
    }

    /**
     * @return The class names provided by the plugin
     */
    public List<String> getProvidedlassNames() {
        return providedlassNames;
    }

    /**
     * @return The parsed descriptor
     */
    public GPathResult getParsedXml() {
        if(parsedXml == null) {
            InputStream inputStream;
            try {
                inputStream = resource.getInputStream();
            } catch (IOException e) {
                throw new GrailsConfigurationException("Error parsing plugin descript: " + resource.getFilename(), e);
            }
            try {
                parsedXml = SpringIOUtils.createXmlSlurper().parse(inputStream);
            } catch (Throwable e) {
                throw new GrailsConfigurationException("Error parsing plugin descript: " + resource.getFilename(), e);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return parsedXml;
    }
}
