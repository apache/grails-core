package grails.plugin.markup.view

import grails.plugin.markup.view.internal.MarkupViewsTransform
import grails.views.ResolvableGroovyTemplateEngine
import grails.views.ViewCompilationException
import grails.views.WritableScriptTemplate
import grails.views.api.GrailsView
import grails.views.compiler.ViewsTransform
import groovy.text.Template
import groovy.text.markup.MarkupTemplateEngine
import groovy.text.markup.TemplateConfiguration
import groovy.text.markup.TemplateResolver
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.CompilationCustomizer

/**
 * A {@link ResolvableGroovyTemplateEngine} that uses Groovy's {@link MarkupTemplateEngine} internally
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class MarkupViewTemplateEngine extends ResolvableGroovyTemplateEngine {

    public static final String VIEW_BASE_CLASS = 'grails.views.markup.baseClass'
    public static final String COMPILE_STATIC = 'grails.views.markup.compileStatic'


    MarkupTemplateEngine innerEngine

    private final boolean compileStatic

    MarkupViewTemplateEngine(MarkupViewConfiguration config = new MarkupViewConfiguration(), ClassLoader classLoader = Thread.currentThread().contextClassLoader) {
        super(config, classLoader)
        this.compileStatic = compileStatic
        innerEngine = new MarkupTemplateEngine(classLoader, config, new TemplateResolver() {
            @Override
            void configure(ClassLoader templateClassLoader, TemplateConfiguration configuration) {
            }

            @Override
            URL resolveTemplate(String templatePath) throws IOException {
                return templateResolver.resolveTemplate(templatePath)
            }
        })
        prepareCustomizers(this.compilerConfiguration)
    }





    @Override
    WritableScriptTemplate createTemplate(String path, URL url) throws CompilationFailedException, ClassNotFoundException, IOException {
        prepareCustomizers(innerEngine.compilerConfiguration)
        def file = new File(url.file)

        try {
            def template = innerEngine.createTemplate(url)
            return createMarkupViewTemplate(template)
        } catch (CompilationFailedException e) {
            throw new ViewCompilationException(e, file.canonicalPath)
        }

    }

    @Override
    WritableScriptTemplate createTemplate(File file) throws CompilationFailedException, ClassNotFoundException, IOException {
        prepareCustomizers(innerEngine.compilerConfiguration)
        try {
            def template = innerEngine.createTemplate(file.toURI().toURL())
            return createMarkupViewTemplate(template)
        } catch (CompilationFailedException e) {
            throw new ViewCompilationException(e, file.canonicalPath)
        }

    }

    @Override
    WritableScriptTemplate createTemplate(Reader reader) throws CompilationFailedException, ClassNotFoundException, IOException {
        prepareCustomizers(innerEngine.compilerConfiguration)
        try {

            def template = innerEngine.createTemplate(reader)
            return createMarkupViewTemplate(template)

        } catch (CompilationFailedException e) {
            throw new ViewCompilationException(e, "Generated")
        }

    }

    @CompileDynamic
    protected MarkupViewWritableScriptTemplate createMarkupViewTemplate(Template template) {
        def clazz = template.@templateClass

        def markupViewTemplate = new MarkupViewWritableScriptTemplate(clazz, (File) null, innerEngine, viewConfiguration)
        super.initializeTemplate(markupViewTemplate, null)
        return markupViewTemplate
    }

    @Override
    String getDynamicTemplatePrefix() {
        "GeneratedMarkupTemplate".intern()
    }

    @Override
    protected WritableScriptTemplate createTemplate(Class<? extends Template> cls, File sourceFile) {
        def template = new MarkupViewWritableScriptTemplate((Class<? extends GrailsView>) cls, sourceFile, innerEngine, (MarkupViewConfiguration) viewConfiguration)
        super.initializeTemplate(template, sourceFile)
    }

    @Override
    protected void prepareCustomizers(CompilerConfiguration cc) {
        if(innerEngine != null) {

            innerEngine.compilerConfiguration.compilationCustomizers.removeAll( this.compilerConfiguration.compilationCustomizers )
            CompilerConfiguration newConfig = new CompilerConfiguration(this.compilerConfiguration)
            super.prepareCustomizers(newConfig)

            if(compileStatic) {
                newConfig.addCompilationCustomizers(
                        new ASTTransformationCustomizer(Collections.singletonMap("extensions", "groovy.text.markup.MarkupTemplateTypeCheckingExtension"), CompileStatic.class));
            }

            innerEngine.compilerConfiguration.addCompilationCustomizers( newConfig.compilationCustomizers as CompilationCustomizer[])
        }

    }

    @Override
    protected ViewsTransform newViewsTransform() {
        return new MarkupViewsTransform(viewConfiguration.extension)
    }
}
