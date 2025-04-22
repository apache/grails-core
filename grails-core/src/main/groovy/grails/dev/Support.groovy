/*
 * Copyright 2014 the original author or authors.
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
package grails.dev

import grails.util.BuildSettings
import grails.util.Environment
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Commons
import org.springframework.util.ClassUtils

import java.lang.management.ManagementFactory

/**
 * Methods to support the development environment
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@Commons
class Support {


    public static final String PROPERTY_RELOAD_AGENT_PATH = "reload.agent.path"
    public static final String ENV_RELOAD_AGENT_PATH = "RELOAD_AGENT_PATH"

    /**
     * Enables the reloading agent at runtime if it isn't present
     */
    static void enableAgentIfNotPresent(Class mainClass = null) {
        if(mainClass) {
            System.setProperty(BuildSettings.MAIN_CLASS_NAME, mainClass.getName())
        }

        def environment = Environment.current
        if(environment.isReloadEnabled() &&
                (!ClassUtils.isPresent("org.springsource.loaded.SpringLoaded", System.classLoader) ||
                        !ClassUtils.isPresent("org.springsource.loaded.TypeRegistry", System.classLoader))) {
            def grailsHome = System.getenv(Environment.ENV_GRAILS_HOME)

            if(grailsHome) {
                def agentPath = System.getProperty(PROPERTY_RELOAD_AGENT_PATH)
                if(!agentPath) {
                    agentPath = System.getenv(ENV_RELOAD_AGENT_PATH)
                }
                def file = findAgentJar(agentPath, grailsHome)
                if(file?.exists()) {
                    def runtimeMxBean = ManagementFactory.runtimeMXBean
                    def arguments = runtimeMxBean.inputArguments
                    if(!arguments.contains('-Xverify:none') && !arguments.contains('-noverify')) {
                        log.warn("Reloading is disabled. Development time reloading requires disabling the Java verifier. Please pass the argument '-Xverify:none' to the JVM")
                    }
                    else {
                        def vmName = runtimeMxBean.name
                        int i = vmName.indexOf('@')
                        String pid = vmName.subSequence(0, i)
                        if(ClassUtils.isPresent('com.sun.tools.attach.VirtualMachine', System.classLoader)) {
                            def vmClass = Support.classLoader.loadClass('com.sun.tools.attach.VirtualMachine')
                            attachAgentClassToProcess(vmClass, pid, file)
                        }

                    }
                }
            }
        }
    }

    protected static File findAgentJar(String agentPath, String grailsHome) {
        if(agentPath) {
            return new File(agentPath)
        }
        else if(grailsHome) {
            def parentDir = new File(grailsHome, "lib/org.springframework/springloaded/jars")
            if(parentDir.exists()) {
                return parentDir.listFiles()?.find() { File f -> f.name.endsWith('.RELEASE.jar')}
            }
        }
    }

    @CompileDynamic
    private static void attachAgentClassToProcess(Class<?> vmClass, String pid, File file) {
        try {
            def vm = vmClass.attach(pid)
            vm.loadAgent(file.absolutePath, "")
            vm.detach()
        } catch (e) {
            System.err.println("WARNING: Could not attach reloading agent. Reloading disabled. Message: $e.message")
        }
    }
}
