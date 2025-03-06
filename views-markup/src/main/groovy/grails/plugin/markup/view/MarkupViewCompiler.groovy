package grails.plugin.markup.view

import grails.plugin.markup.view.internal.MarkupViewsTransform
import grails.views.AbstractGroovyTemplateCompiler
import grails.views.ViewConfiguration
import grails.views.compiler.ViewsTransform
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.CompilationCustomizer

/**
 * A compiler for markup templates
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@InheritConstructors
@CompileStatic
class MarkupViewCompiler extends AbstractGroovyTemplateCompiler {

    @Override
    protected CompilerConfiguration configureCompiler(CompilerConfiguration configuration) {
        super.configureCompiler(configuration)
        ViewConfiguration viewConfiguration = (ViewConfiguration) this.viewConfiguration
        def templateCustomizer = (CompilationCustomizer) getClass().classLoader.loadClass("groovy.text.markup.TemplateASTTransformer")
                .newInstance(viewConfiguration)
        configuration.addCompilationCustomizers(templateCustomizer)
        if(viewConfiguration.isCompileStatic()) {
            configuration.addCompilationCustomizers(
                    new ASTTransformationCustomizer(Collections.singletonMap("extensions", "groovy.text.markup.MarkupTemplateTypeCheckingExtension"), CompileStatic.class));
        }
        return configuration
    }

    @Override
    protected ViewsTransform newViewsTransform() {
        return new MarkupViewsTransform(viewConfiguration.extension)
    }

    static void main(String[] args) {
        run(args, MarkupViewConfiguration, MarkupViewCompiler)
    }
}

