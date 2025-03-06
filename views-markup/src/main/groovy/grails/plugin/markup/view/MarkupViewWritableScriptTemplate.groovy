package grails.plugin.markup.view

import grails.plugin.markup.view.api.MarkupView
import grails.views.GrailsViewTemplate
import grails.views.api.GrailsView
import groovy.text.markup.MarkupTemplateEngine
import groovy.transform.CompileStatic
/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class MarkupViewWritableScriptTemplate extends GrailsViewTemplate {

    MarkupTemplateEngine templateEngine
    MarkupViewConfiguration configuration

    MarkupViewWritableScriptTemplate(Class<? extends GrailsView> templateClass, File sourceFile, MarkupTemplateEngine templateEngine, MarkupViewConfiguration configuration) {
        super(templateClass, sourceFile)
        this.templateEngine = templateEngine
        this.configuration = configuration
    }

    @Override
    Writable make(Map binding) {
        MarkupView writableTemplate = (MarkupView)templateClass
                .newInstance(templateEngine, binding, Collections.emptyMap(), configuration)
        writableTemplate.viewTemplate = (GrailsViewTemplate)this
        writableTemplate.prettyPrint = prettyPrint

        writableTemplate.setSourceFile(sourceFile)

        return writableTemplate
    }
}
