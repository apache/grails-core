package org.apache.grails.buildsrc

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.file.Directory

@CompileStatic
class GradleUtils {

    static Directory findRootGrailsCoreDir(Project project) {
        def rootLayout = project.rootProject.layout
        if (rootLayout.projectDirectory.dir('.github').asFile.exists()) {
            return rootLayout.projectDirectory
        }

        // we currently only nest 1 project level deep
        rootLayout.projectDirectory.dir('../')
    }

    static <T> T lookupProperty(Project project, String name, T defaultValue = null) {
        project.findProperty(name) as T ?: defaultValue
    }
}
