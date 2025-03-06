package grails.views.gradle.util

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

/**
 * Copied from grails-gradle-plugin to remove the dependency on that plugin.
 * This is a possible candidate for a future "grails-common" module.
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class SourceSets {

    /**
     * Finds the main SourceSet for the project
     * @param project The project
     * @return The main source set or null if it can't be found
     */
    static SourceSet findMainSourceSet(Project project) {
        return findSourceSet(project, SourceSet.MAIN_SOURCE_SET_NAME)
    }

    /**
     * Finds the main SourceSet for the project
     * @param project The project
     * @return The main source set or null if it can't be found
     */
    static SourceSet findSourceSet(Project project, String name) {
        SourceSetContainer sourceSets = findSourceSets(project)
        return sourceSets?.find { SourceSet sourceSet ->
            sourceSet.name == name
        } as SourceSet
    }

    static SourceSetContainer findSourceSets(Project project) {
        JavaPluginExtension plugin = project.extensions.getByType(JavaPluginExtension)
        SourceSetContainer sourceSets = plugin?.sourceSets
        return sourceSets
    }
}

