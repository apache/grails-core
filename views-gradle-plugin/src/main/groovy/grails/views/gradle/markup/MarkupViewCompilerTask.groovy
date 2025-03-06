package grails.views.gradle.markup

import grails.views.gradle.AbstractGroovyTemplateCompileTask
import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * MarkupView compiler task for Gradle
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class MarkupViewCompilerTask extends AbstractGroovyTemplateCompileTask {

    @Input
    final Property<String> fileExtension

    @Input
    final Property<String> scriptBaseName

    @Input
    final Property<String> compilerName

    MarkupViewCompilerTask(ObjectFactory objectFactory) {
        super(objectFactory)
        fileExtension.convention('gml')
        scriptBaseName.convention('grails.plugin.markup.view.MarkupViewTemplate')
        compilerName.convention('grails.plugin.markup.view.MarkupViewCompiler')
    }
}
