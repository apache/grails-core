package grails.views.gradle.json

import grails.views.gradle.AbstractGroovyTemplateCompileTask
import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

import javax.inject.Inject

/**
 * Concrete implementation that compiles JSON templates
 *
 * @author Graeme Rocher
 */
@CompileStatic
class JsonViewCompilerTask extends AbstractGroovyTemplateCompileTask {

    @Inject
    JsonViewCompilerTask(ObjectFactory objectFactory) {
        super(objectFactory)
        fileExtension.convention('gson')
        scriptBaseName.convention('grails.plugin.json.view.JsonViewTemplate')
        compilerName.convention('grails.plugin.json.view.JsonViewCompiler')
    }
}
