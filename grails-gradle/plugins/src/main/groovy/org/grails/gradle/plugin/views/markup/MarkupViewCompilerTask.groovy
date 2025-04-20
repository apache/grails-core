package org.grails.gradle.plugin.views.markup

import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.process.ExecOperations
import org.grails.gradle.plugin.views.AbstractGroovyTemplateCompileTask

import javax.inject.Inject

/**
 * MarkupView compiler task for Gradle
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@CacheableTask
class MarkupViewCompilerTask extends AbstractGroovyTemplateCompileTask {

    @Input
    final Property<String> fileExtension

    @Input
    final Property<String> scriptBaseName

    @Input
    final Property<String> compilerName

    @Inject
    MarkupViewCompilerTask(ExecOperations execOperations, ObjectFactory objectFactory) {
        super(execOperations, objectFactory)
        fileExtension.convention('gml')
        scriptBaseName.convention('grails.plugin.markup.view.MarkupViewTemplate')
        compilerName.convention('grails.plugin.markup.view.MarkupViewCompiler')
    }
}
