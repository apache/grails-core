package org.grails.gradle.plugin.views.json

import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.process.ExecOperations
import org.grails.gradle.plugin.views.AbstractGroovyTemplateCompileTask

import javax.inject.Inject

/**
 * Concrete implementation that compiles JSON templates
 *
 * @author Graeme Rocher
 */
@CompileStatic
@CacheableTask
class GsonViewCompilerTask extends AbstractGroovyTemplateCompileTask {

    @Inject
    GsonViewCompilerTask(ExecOperations execOperations, ObjectFactory objectFactory) {
        super(execOperations, objectFactory)
        fileExtension.convention('gson')
        scriptBaseName.convention('grails.plugin.json.view.JsonViewTemplate')
        compilerName.convention('grails.plugin.json.view.JsonViewCompiler')
    }
}
