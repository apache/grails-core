
/*
 * Copyright 2014 original authors
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

package org.grails.cli.gradle.commands

import grails.io.support.SystemOutErrCapturer
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.gradle.tooling.ProjectConnection
import org.grails.cli.gradle.FetchAllTaskSelectorsBuildAction
import org.grails.cli.gradle.cache.ListReadingCachedGradleOperation
import org.grails.cli.profile.ProjectContext

/**
 * @author Graeme Rocher
 */
@CompileStatic
class ReadGradleTasks extends ListReadingCachedGradleOperation<String> {

    private static final Closure<String> taskNameFormatter = { String projectPath, String taskName ->
        if(projectPath == ':') {
            ":$taskName".toString()
        } else {
            "$projectPath:$taskName".toString()
        }
    }

    ReadGradleTasks(ProjectContext projectContext) {
        super(projectContext, ".gradle-tasks")
    }

    @Override
    protected String createListEntry(String str) { str }

    @Override
    List<String> readFromGradle(ProjectConnection connection) {
        SystemOutErrCapturer.withNullOutput {
            FetchAllTaskSelectorsBuildAction.AllTasksModel allTasksModel = (FetchAllTaskSelectorsBuildAction.AllTasksModel)connection.action(new FetchAllTaskSelectorsBuildAction(projectContext.getBaseDir())).run()
            Collection<String> allTaskSelectors=[]

            if (allTasksModel.currentProject) {
                allTaskSelectors.addAll(allTasksModel.allTaskSelectors.get(allTasksModel.currentProject))
            }

            allTasksModel.projectPaths.each { String projectName, String projectPath ->
                allTasksModel.allTasks.get(projectName).each { String taskName ->
                    allTaskSelectors.add(taskNameFormatter(projectPath, taskName))
                }
            }

            allTaskSelectors.unique().toList()
        }
    }
}
