package org.grails.compiler.scaffolding

import grails.compiler.ast.AstTransformer
import grails.compiler.ast.GrailsArtefactClassInjector
import grails.plugin.scaffolding.GormService
import grails.plugin.scaffolding.annotation.Scaffold
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit
import org.grails.compiler.injection.GrailsASTUtils
import org.grails.core.artefact.ServiceArtefactHandler
import org.grails.io.support.GrailsResourceUtils
import org.grails.plugins.web.rest.transform.ResourceTransform

import java.util.regex.Pattern

/**
 * Transformation that turns a service into a scaffolding service at compile time if '@ScaffoldService'
 * is specified
 *
 * @author Scott Murphy Heiberg
 * @since 5.1
 */
@AstTransformer
@CompileStatic
class ScaffoldingServiceInjector implements GrailsArtefactClassInjector {

    final String[] artefactTypes = [ServiceArtefactHandler.TYPE] as String[]
    public static Pattern SERVICE_PATTERN = Pattern.compile(".+/" +
        GrailsResourceUtils.GRAILS_APP_DIR + "/services/(.+)Service\\.groovy");

    @Override
    void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode)
    }

    @Override
    void performInjection(SourceUnit source, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode)
    }

    @Override
    void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        def annotationNode = classNode.getAnnotations(ClassHelper.make(Scaffold)).find()
        if (annotationNode) {
            ClassNode serviceClassNode = annotationNode?.getMember("value")?.type
            ClassNode superClassNode = ClassHelper.make(serviceClassNode?.getTypeClass()?:GormService).getPlainNodeReference()
            ClassNode currentSuperClass = classNode.getSuperClass()
            if (currentSuperClass.equals(GrailsASTUtils.OBJECT_CLASS_NODE)) {
                def domainClass = annotationNode.getMember("domain")?.type
                if (!domainClass) {
                    domainClass = ScaffoldingControllerInjector.extractGenericDomainClass(serviceClassNode)
                }
                if (!domainClass) {
                    GrailsASTUtils.error(source, classNode, "Scaffolded service (${classNode.name}) with @Scaffold does not have domain class set.", true)
                }
                classNode.setSuperClass(GrailsASTUtils.nonGeneric(superClassNode, domainClass))
                def readOnlyExpression = (ConstantExpression) annotationNode.getMember("readOnly")
                new ResourceTransform().addConstructor(classNode, domainClass, readOnlyExpression?.getValue()?.asBoolean()?:false)
            } else if (!currentSuperClass.isDerivedFrom(superClassNode)) {
               GrailsASTUtils.error(source, classNode, "Scaffolded services (${classNode.name}) cannot extend other classes: ${currentSuperClass.getName()}", true)
            }
        }
    }

    @Override
    boolean shouldInject(URL url) {
        return url != null && SERVICE_PATTERN.matcher(url.getFile()).find()
    }
}
