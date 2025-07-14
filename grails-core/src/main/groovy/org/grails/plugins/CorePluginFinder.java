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

import grails.core.GrailsApplication;
import grails.core.support.ParentApplicationContextAware;
import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.io.support.SpringIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads core plugin classes. Contains functionality moved in from <code>DefaultGrailsPluginManager</code>.
 *
 * @author Graeme Rocher
 * @author Phil Zoio
 */
public class CorePluginFinder implements ParentApplicationContextAware {

    public static final String CORE_PLUGIN_PATTERN = "META-INF/grails-plugin.xml";

    private static final Logger LOG = LoggerFactory.getLogger(CorePluginFinder.class);

    private final Set<Class<?>> foundPluginClasses = new HashSet<Class<?>>();
    @SuppressWarnings("unused")
    private final GrailsApplication application;
    @SuppressWarnings("rawtypes")
    private final Map<Class, BinaryGrailsPluginDescriptor> binaryDescriptors = new HashMap<Class, BinaryGrailsPluginDescriptor>();

    public CorePluginFinder(GrailsApplication application) {
        this.application = application;
    }

    public Class<?>[] getPluginClasses() {

        // just in case we try to use this twice
        foundPluginClasses.clear();

        try {
            Resource[] resources = resolvePluginResources();
            if (resources.length > 0) {
                loadCorePluginsFromResources(resources);
            } else {
                throw new IllegalStateException("Grails was unable to load plugins dynamically. This is normally a problem with the container class loader configuration, see troubleshooting and FAQ for more info. ");
            }
        } catch (IOException e) {
            throw new IllegalStateException("WARNING: I/O exception loading core plugin dynamically, attempting static load. This is usually due to deployment onto containers with unusual classloading setups. Message: " + e.getMessage());
        }
        return foundPluginClasses.toArray(new Class[foundPluginClasses.size()]);
    }

    public BinaryGrailsPluginDescriptor getBinaryDescriptor(Class<?> pluginClass) {
        return binaryDescriptors.get(pluginClass);
    }

    private Resource[] resolvePluginResources() throws IOException {
        Enumeration<URL> resources = application.getClassLoader().getResources(CORE_PLUGIN_PATTERN);
        List<Resource> resourceList = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            resourceList.add(new UrlResource(url));
        }
        return resourceList.toArray(new Resource[resourceList.size()]);
    }

    @SuppressWarnings("rawtypes")
    private void loadCorePluginsFromResources(Resource[] resources) throws IOException {

        LOG.debug("Attempting to load [" + resources.length + "] core plugins");
        try {
            SAXParser saxParser = SpringIOUtils.newSAXParser();
            for (Resource resource : resources) {
                InputStream input = null;

                try {
                    input = resource.getInputStream();
                    PluginHandler ph = new PluginHandler();
                    saxParser.parse(input, ph);

                    for (String pluginType : ph.pluginTypes) {
                        Class<?> pluginClass = attemptCorePluginClassLoad(pluginType);
                        if (pluginClass != null) {
                            addPlugin(pluginClass);
                            binaryDescriptors.put(pluginClass, new BinaryGrailsPluginDescriptor(resource, ph.pluginClasses));
                        }
                    }

                } finally {
                    if (input != null) {
                        input.close();
                    }
                }
            }
        } catch (ParserConfigurationException e) {
            throw new GrailsConfigurationException("XML parsing error loading core plugins: " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new GrailsConfigurationException("XML parsing error loading core plugins: " + e.getMessage(), e);
        }
    }

    private Class<?> attemptCorePluginClassLoad(String pluginClassName) {
        try {
            final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            return classLoader.loadClass(pluginClassName);
        } catch (ClassNotFoundException e) {
            LOG.warn("[GrailsPluginManager] Core plugin [" + pluginClassName +
                    "] not found, resuming load without..");
            if (LOG.isDebugEnabled()) {
                LOG.debug(e.getMessage(), e);
            }
        }
        return null;
    }

    private void addPlugin(Class<?> plugin) {
        foundPluginClasses.add(plugin);
    }

    public void setParentApplicationContext(ApplicationContext parent) {
    }

    private enum PluginParseState {
        PARSING, TYPE, RESOURCE
    }

    class PluginHandler extends DefaultHandler {

        PluginParseState state = PluginParseState.PARSING;

        List<String> pluginTypes = new ArrayList<>();
        List<String> pluginClasses = new ArrayList<>();
        private StringBuilder buff = new StringBuilder();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (localName.equals("type")) {
                state = PluginParseState.TYPE;
                buff = new StringBuilder();
            } else if (localName.equals("resource")) {
                state = PluginParseState.RESOURCE;
                buff = new StringBuilder();
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            switch (state) {
                case TYPE:
                    buff.append(String.valueOf(ch, start, length));
                    break;
                case RESOURCE:
                    buff.append(String.valueOf(ch, start, length));
                    break;
                default:
                    // do nothing
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            switch (state) {
                case TYPE:
                    pluginTypes.add(buff.toString());
                    break;
                case RESOURCE:
                    pluginClasses.add(buff.toString());
                    break;
                default:
                    // do nothing
                    break;
            }
            state = PluginParseState.PARSING;
        }
    }
}
