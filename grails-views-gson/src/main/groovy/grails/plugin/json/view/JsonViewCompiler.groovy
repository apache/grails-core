package grails.plugin.json.view

import grails.plugin.json.view.internal.JsonViewsTransform
import grails.views.AbstractGroovyTemplateCompiler
import grails.views.compiler.ViewsTransform
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer

/**
 * A compiler for JSON views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@InheritConstructors
class JsonViewCompiler extends AbstractGroovyTemplateCompiler {

    @Override
    protected CompilerConfiguration configureCompiler(CompilerConfiguration configuration) {
        CompilerConfiguration compiler = super.configureCompiler(configuration)
        if(viewConfiguration.compileStatic) {
            configuration.addCompilationCustomizers(
                    new ASTTransformationCustomizer(
                            Collections.singletonMap(
                                    'extensions',
                                    'grails.plugin.json.view.internal.JsonTemplateTypeCheckingExtension'
                            ),
                            CompileStatic
                    )
            )
        }
        configuration.setScriptBaseClass(
                viewConfiguration.baseTemplateClass.name
        )
        return compiler
    }

    @Override
    protected ViewsTransform newViewsTransform() {
        return new JsonViewsTransform(viewConfiguration.extension)
    }

    static void main(String[] args) {
        run(args, JsonViewConfiguration, JsonViewCompiler)
    }
}
