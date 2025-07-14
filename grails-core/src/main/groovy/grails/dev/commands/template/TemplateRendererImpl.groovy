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

package grails.dev.commands.template

import grails.codegen.model.Model
import grails.dev.commands.io.FileSystemInteraction
import grails.dev.commands.io.FileSystemInteractionImpl
import groovy.text.GStringTemplateEngine
import groovy.text.Template
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.io.support.DefaultResourceLoader
import org.grails.io.support.Resource
import org.grails.io.support.ResourceLoader

/**
 * API for locating and rendering templates in the code generation layer
 *
 * @author Graeme Rocher
 * @since 3.2
 */
@CompileStatic
class TemplateRendererImpl implements TemplateRenderer {

    @Delegate
    FileSystemInteraction fileSystemInteraction
    protected Map<String, Template> templateCache = [:]

    TemplateRendererImpl(File baseDir, ResourceLoader resourceLoader = new DefaultResourceLoader()) {
        this.fileSystemInteraction = new FileSystemInteractionImpl(baseDir, resourceLoader)
    }

    /**
     * Render with the given named arguments
     *
     * @param namedArguments The named arguments are 'template', 'destination' and 'model'
     */
    @Override
    @CompileDynamic
    void render(Map<String, Object> namedArguments) {
        if (namedArguments?.template && namedArguments?.destination) {
            def templateArg = namedArguments.template
            Resource template = templateArg instanceof Resource ? templateArg : template(templateArg)
            boolean overwrite = namedArguments.overwrite as Boolean ?: false
            render template, file(namedArguments.destination), namedArguments.model ?: [:], overwrite
        }
    }

    /**
     * Render the given template to the give destination for the given model
     *
     * @param template The contents template
     * @param destination The destination
     * @param model The model
     */
    @Override
    void render(CharSequence template, File destination, Model model) {
        render(template, destination, model.asMap())
    }

    /**
     * Render the given template to the given destination
     *
     * @param template The contents of the template
     * @param destination The destination
     * @param model The model
     */
    void render(CharSequence template, File destination, Map model = Collections.emptyMap(), boolean overwrite = false) {
        if (template && destination) {
            if (destination.exists() && !overwrite) {
                println("Warning | Destination file ${projectPath(destination)} already exists, skipping...")
            } else {
                def templateEngine = new GStringTemplateEngine()
                try {
                    def t = templateEngine.createTemplate(template.toString())
                    writeTemplateToDestination(t, model, destination)
                } catch (e) {
                    destination.delete()
                    throw new TemplateException("Error rendering template to destination ${projectPath(destination)}: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Render the given template to the give destination for the given model
     *
     * @param template The template
     * @param destination The destination
     * @param model The model
     */
    void render(File template, File destination, Model model) {
        render(template, destination, model.asMap())
    }
    /**
     * Render the given template to the given destination
     *
     * @param template The template
     * @param destination The destination
     * @param model The model
     */
    void render(File template, File destination, Map model = Collections.emptyMap(), boolean overwrite = false) {
        if (template && destination) {
            if (destination.exists() && !overwrite) {
                println("Warning | Destination file ${projectPath(destination)} already exists, skipping...")
            } else {
                Template t = templateCache[template.absolutePath]
                if (t == null) {
                    try {
                        def templateEngine = new GStringTemplateEngine()
                        t = templateEngine.createTemplate(template)
                    } catch (e) {
                        throw new TemplateException("Error rendering template [$template] to destination ${projectPath(destination)}: ${e.message}", e)
                    }
                }
                try {
                    writeTemplateToDestination(t, model, destination)
                    println("Rendered template ${template.name} to destination ${projectPath(destination)}")
                } catch (Throwable e) {
                    destination.delete()
                    throw new TemplateException("Error rendering template [$template] to destination ${projectPath(destination)}: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Render the given template to the give destination for the given model
     *
     * @param template The contents template
     * @param destination The destination
     * @param model The model
     */
    void render(Resource template, File destination, Model model, boolean overwrite = false) {
        render(template, destination, model.asMap(), overwrite)
    }
    /**
     * Render the given template to the given destination
     *
     * @param template The template
     * @param destination The destination
     * @param model The model
     */
    void render(Resource template, File destination, Map model = Collections.emptyMap(), boolean overwrite = false) {
        if (template && destination) {
            if (destination.exists() && !overwrite) {
                println("Warning | Destination file ${projectPath(destination)} already exists, skipping...")
            } else if (!template?.exists()) {
                throw new TemplateException("Template [$template.filename] not found.")
            } else {
                Template t = templateCache[template.filename]
                if (t == null) {

                    try {
                        def templateEngine = new GStringTemplateEngine()
                        def reader = new InputStreamReader(template.inputStream, "UTF-8")
                        try {
                            t = templateEngine.createTemplate(reader)
                        } finally {
                            try {
                                reader.close()
                            } catch (e) {
                                // ignore
                            }
                        }
                    } catch (e) {
                        throw new TemplateException("Error rendering template [$template.filename] to destination ${projectPath(destination)}: ${e.message}", e)
                    }
                }
                if (t != null) {
                    try {
                        writeTemplateToDestination(t, model, destination)
                        println("Rendered template ${template.filename} to destination ${projectPath(destination)}")
                    } catch (Throwable e) {
                        destination.delete()
                        throw new TemplateException("Error rendering template [$template.filename] to destination ${projectPath(destination)}: ${e.message}", e)
                    }
                }
            }
        }
    }

    /**
     * Find templates matching the given pattern
     *
     * @param pattern The pattern
     * @return The available templates
     */
    Iterable<Resource> templates(String pattern) {
        Collection<Resource> resList = []
        resList.addAll(resources(pattern))
        resList.addAll(resources("classpath*:META-INF/templates/$pattern"))
        return resList.unique()
    }

    /**
     * Find a template at the given location
     *
     * @param location The location
     * @return The resource or null if it doesn't exist
     */
    Resource template(Object location) {
        Resource f = resource(file("src/main/templates/$location"))
        if (!f?.exists()) {
            return resource("classpath*:META-INF/templates/" + location)
        }
        return resource(f)
    }

    protected static void writeTemplateToDestination(Template template, Map model, File destination) {
        destination.parentFile.mkdirs()
        destination.withWriter { BufferedWriter w ->
            template.make(model).writeTo(w)
            w.flush()
        }
    }
}