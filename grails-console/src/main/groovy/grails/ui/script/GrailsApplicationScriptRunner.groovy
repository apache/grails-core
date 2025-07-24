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
package grails.ui.script

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import org.springframework.context.ConfigurableApplicationContext

import grails.config.Config
import grails.core.GrailsApplication
import grails.persistence.support.PersistenceContextInterceptor
import grails.ui.support.DevelopmentGrailsApplication

/**
 * Used to run Grails scripts within the context of a Grails application
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsApplicationScriptRunner extends DevelopmentGrailsApplication {
    List<File> scripts

    private GrailsApplicationScriptRunner(List<File> scripts, Class<?>... sources) {
        super(sources)
        this.scripts = scripts
    }

    @Override
    ConfigurableApplicationContext run(String...args) {
        ConfigurableApplicationContext ctx
        try {
            ctx = super.run(args)
        } catch (Throwable e) {
            System.err.println("Context failed to load: $e.message")
            System.exit(1)
        }

        def binding = new Binding()
        binding.setVariable("ctx", ctx)

        Config config = ctx.getBean('grailsApplication', GrailsApplication).config
        String defaultPackageKey = 'grails.codegen.defaultPackage'
        GroovyShell sh
        CompilerConfiguration configuration = CompilerConfiguration.DEFAULT
        if (config.containsProperty(defaultPackageKey)) {
            ImportCustomizer importCustomizer = new ImportCustomizer()
            importCustomizer.addStarImports config.getProperty(defaultPackageKey, String)
            configuration.addCompilationCustomizers(importCustomizer)
        }
        sh = new GroovyShell(binding, configuration)

        Collection<PersistenceContextInterceptor> interceptors = ctx.getBeansOfType(PersistenceContextInterceptor).values()

        try {
            scripts.each {
                try {
                    for(i in interceptors) {
                        i.init()
                    }
                    sh.evaluate(it)
                    for(i in interceptors) {
                        i.destroy()
                    }
                } catch (Throwable e) {
                    System.err.println("Script execution error: $e.message")
                    System.exit(1)
                }
            }
        } finally {
            try {
                for(i in interceptors) {
                    i.destroy()
                }
                ctx?.close()
            } catch (Throwable e) {
                // ignore
            }
        }


        return ctx
    }
    /**
     * Main method to run an existing Application class
     *
     * @param args The last argument is the Application class name. All other args are script names
     */
    public static void main(String[] args) {
        if(args.size() > 1) {
            Class applicationClass = null
            String className = args.last()
            try {
                applicationClass = Thread.currentThread().contextClassLoader.loadClass(className)
            } catch (Throwable e) {
                System.err.println("runScript: Application class ${className} not found")
                System.exit(1)
            }
            String[] scriptNames = args.init() as String[]
            List<File> scripts = []
            scriptNames.each { String scriptName ->
                File script = new File(scriptName)
                if (script.exists()) {
                    scripts.add(script)
                } else {
                    System.err.println("Specified script [${scriptName}] not found")
                    System.exit(1)
                }
            }

            new GrailsApplicationScriptRunner(scripts, applicationClass).run(args)
        } else {
            System.err.println("Missing application class name and script name arguments")
            System.exit(1)
        }
    }
}
