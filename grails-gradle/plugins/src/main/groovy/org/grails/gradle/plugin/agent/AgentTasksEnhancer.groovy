/*
 * Copyright 2015-2024 original authors
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
package org.grails.gradle.plugin.agent

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.tasks.JavaExec
import org.grails.gradle.plugin.core.GrailsExtension

/**
 * Sets up the reloading agent based on the agent configuration after the project has been configured
 *
 * @author Graeme Rocher
 */
@CompileStatic
class AgentTasksEnhancer implements Action<Project> {
    @Override
    void execute(Project project) {
        try {
            Set<ResolvedArtifact> agentJars = project.getConfigurations().getByName("agent").resolvedConfiguration.resolvedArtifacts

            if(agentJars) {
                File agentJar = agentJars.iterator().next().file
                for (Task task : project.getTasks()) {
                    if (task instanceof JavaExec) {
                        addAgent(project, (JavaExec) task, agentJar)
                    }
                }
            }
        } catch (Throwable e) {
            project.logger.warn("Cannot resolve reloading agent JAR: ${e.message}")
        }
    }

    private void addAgent(Project project, JavaExec exec, File agent) {

        GrailsExtension.Agent agentConfig = project.extensions.findByType(GrailsExtension)?.agent ?: new GrailsExtension.Agent()
        if(agentConfig.enabled) {
            exec.jvmArgs "-javaagent:${agentConfig.path?.absolutePath ?: agent.absolutePath}"

            for (String arg in agentConfig.jvmArgs) {
                exec.jvmArgs arg
            }
            for(Map.Entry<String, String> entry in agentConfig.systemProperties) {
                exec.systemProperty(entry.key, entry.value)
            }

            Map<String, String> agentArgs= [
                    inclusions: agentConfig.inclusions,
                    synchronize: String.valueOf( agentConfig.synchronize ),
                    allowSplitPackages: String.valueOf( agentConfig.allowSplitPackages ),
                    cacheDir: agentConfig.cacheDir ? project.mkdir(agentConfig.cacheDir) : project.mkdir("build/springloaded")
            ] as Map<String, String>
            if(agentConfig.logging != null) {
                agentArgs.put("logging", String.valueOf(agentConfig.logging))
            }
            if(agentConfig.exclusions) {
                agentArgs.put('exclusions', agentConfig.exclusions)
            }
            agentArgs.put("profile","grails")
            agentArgs.put("caching","true")
            exec.systemProperty('springloaded', agentArgs.collect { entry -> "$entry.key=$entry.value"}.join(';'))
        }

    }
}
