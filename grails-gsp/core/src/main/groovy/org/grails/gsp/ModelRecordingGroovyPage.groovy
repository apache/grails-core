/*
 * Copyright 2016-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.gsp

import groovy.transform.CompileStatic
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.gsp.compiler.GroovyPageParser
import org.grails.gsp.jsp.JspTagLib
import org.grails.taglib.encoder.OutputContext

import java.util.concurrent.ConcurrentHashMap


/**
 * Development time helper class to add model definitions to existing GSP pages
 *
 * This adds a feature for migrating existing GSPs to type checked and staticly compiled GSPs.
 * The model types are recorded during use and the model definition is written directly to the
 * original GSP files at shutdown.
 *
 * Activate with '-Dgrails.views.gsp.modelrecording=true' system property
 *
 * @author Lari Hotari
 * @since 3.3
 */
@CompileStatic
abstract class ModelRecordingGroovyPage extends GroovyPage {
    public static final String CONFIG_SYSTEM_PROPERTY_NAME
    public static final boolean ENABLED
    static {
        CONFIG_SYSTEM_PROPERTY_NAME = "grails.views.gsp.modelrecording"
        ENABLED = Boolean.getBoolean(CONFIG_SYSTEM_PROPERTY_NAME)
    }
    private static final ModelRecordingCache modelRecordingCache = new ModelRecordingCache()
    private ModelEntry modelEntry

    @Override
    void initRun(Writer target, OutputContext outputContext, GroovyPageMetaInfo metaInfo) {
        super.initRun(target, outputContext, metaInfo)
        def key = getGroovyPageFileName()
        modelEntry = modelRecordingCache.models.get(key)
        if (modelEntry == null) {
            modelEntry = new ModelEntry()
            modelRecordingCache.models.put(key, modelEntry)
        }
    }

    @Override
    protected Object lookupTagDispatcher(String namespace) {
        Object value = super.lookupTagDispatcher(namespace)
        if (value != null) {
            modelEntry.taglibs.add(namespace)
        }
        return value
    }

    @Override
    protected JspTagLib lookupJspTagLib(String jspTagLibName) {
        Object value = super.lookupJspTagLib(jspTagLibName)
        if (value != null) {
            modelEntry.taglibs.add(jspTagLibName)
        }
        return value
    }

    @Override
    protected Object resolveProperty(String property) {
        Object value = super.resolveProperty(property)
        if (value != null) {
            if (!modelEntry.model.containsKey(property) && !modelEntry.taglibs.contains(property)) {
                Class valueClass = value.getClass()
                if (valueClass.name.contains('$') || valueClass.isSynthetic()) {
                    valueClass = valueClass.superclass
                }
                if (value instanceof CharSequence) {
                    valueClass = CharSequence
                }
                modelEntry.model.put(property, valueClass.getName())
            }
        }
        return value
    }
}

@CompileStatic
class ModelRecordingCache {
    private Map<String, ModelEntry> models = new ConcurrentHashMap<>()
    private boolean initialized

    synchronized Map<String, ModelEntry> getModels() {
        if (!initialized) {
            initialize()
            initialized = true
        }
        this.@models
    }

    private void initialize() {
        System.err.println("Initialized model recording.")
        ShutdownOperations.addOperation {
            System.err.println("Writing model recordings to disk...")
            try {
                close()
            } catch (e) {
                e.printStackTrace(System.err)
            } finally {
                System.err.println("Done.")
            }
        }
    }

    void close() {
        this.@models.each { String fileName, ModelEntry modelEntry ->
            def gspDeclaration = modelEntry.gspDeclaration
            if (gspDeclaration) {
                File file = new File(fileName)
                if (file.exists()) {
                    System.err.println("Writing model recordings to ${file.name}...")
                    file.text = gspDeclaration + file.text
                } else {
                    System.err.println("GSP file '${fileName}' not found. Declaration: ${gspDeclaration}")
                }
            }
        }
    }
}

@CompileStatic
class ModelEntry {
    // defaults are defined by org.grails.web.taglib.WebRequestTemplateVariableBinding
    static Map<String, String> DEFAULT_TYPES = [webRequest        : 'org.grails.web.servlet.mvc.GrailsWebRequest',
                                                request           : 'jakarta.servlet.http.HttpServletRequest',
                                                response          : 'jakarta.servlet.http.HttpServletResponse',
                                                flash             : 'grails.web.mvc.FlashScope',
                                                application       : 'jakarta.servlet.ServletContext',
                                                applicationContext: 'org.springframework.context.ApplicationContext',
                                                grailsApplication : 'grails.core.GrailsApplication',
                                                session           : 'grails.web.servlet.mvc.GrailsHttpSession',
                                                params            : 'grails.web.servlet.mvc.GrailsParameterMap',
                                                actionName        : 'CharSequence',
                                                controllerName    : 'CharSequence']

    Map<String, String> model = Collections.synchronizedMap([:])
    Set<String> taglibs = Collections.synchronizedSet([] as Set<String>)
    Set<String> defaultTagLibs = new HashSet(GroovyPageParser.DEFAULT_TAGLIB_NAMESPACES)
    int initialSize

    ModelEntry() {
        taglibs.addAll(defaultTagLibs)
        initialSize = taglibs.size()
    }

    boolean hasTagLibs() {
        taglibs.size() > initialSize
    }

    Iterable<String> getCustomTagLibs() {
        taglibs.findAll { !defaultTagLibs.contains(it) }
    }

    String getGspDeclaration() {
        if (model || hasTagLibs()) {
            def gspDeclaration = new StringBuilder()
            gspDeclaration << "@{"
            if (model) {
                gspDeclaration << " model='''\n"
                model.each { String fieldName, String fieldType ->
                    String cleanedFieldType = fieldType - ~/^java\.(util|lang)\./
                    String defaultType = DEFAULT_TYPES.get(fieldName)
                    if(defaultType) {
                        try {
                            // use default field type for if field type is instance of the class
                            // for example instance of 'org.apache.catalina.core.ApplicationHttpRequest', use 'jakarta.servlet.http.HttpServletRequest'
                            Class<?> fieldTypeClass = Class.forName(fieldType)
                            Class<?> defaultTypeClass = Class.forName(defaultType)
                            if (defaultTypeClass.isAssignableFrom(fieldTypeClass)) {
                                cleanedFieldType = defaultType
                            }
                        } catch (e) {
                            // ignore
                        }
                    }
                    gspDeclaration << "${cleanedFieldType} ${fieldName}\n"
                }
                gspDeclaration << "''' "
            }
            if (hasTagLibs()) {
                gspDeclaration << " taglibs='${customTagLibs.join(', ')}' "
            }
            gspDeclaration << "}\n"
            return gspDeclaration.toString()
        }
        return null
    }
}
