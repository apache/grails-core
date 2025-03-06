package org.grails.compiler.scaffolding

import grails.compiler.ast.AstTransformer
import grails.compiler.ast.GrailsArtefactClassInjector
import grails.plugin.scaffolding.annotation.Scaffold
import grails.rest.RestfulController
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit
import org.grails.compiler.injection.GrailsASTUtils
import org.grails.compiler.web.ControllerActionTransformer
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.plugins.web.rest.transform.ResourceTransform

/**
 * Transformation that turns a controller into a scaffolding controller at compile time if 'static scaffold = Foo'
 * is specified
 *
 * @author Graeme Rocher
 * @since 3.1
 */
@AstTransformer
@CompileStatic
class ScaffoldingControllerInjector implements GrailsArtefactClassInjector {

    public static final String PROPERTY_SCAFFOLD = "scaffold"

    final String[] artefactTypes = [ControllerArtefactHandler.TYPE] as String[]

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
        def propertyNode = classNode.getProperty(PROPERTY_SCAFFOLD)
        def annotationNode = classNode.getAnnotations(ClassHelper.make(Scaffold)).find()

        def expression = propertyNode?.getInitialExpression()
        if (expression instanceof ClassExpression || annotationNode) {
            ClassNode controllerClassNode = annotationNode?.getMember("value")?.type
            ClassNode superClassNode = ClassHelper.make(controllerClassNode?.getTypeClass()?:RestfulController).getPlainNodeReference()
            ClassNode currentSuperClass = classNode.getSuperClass()
            if (currentSuperClass.equals(GrailsASTUtils.OBJECT_CLASS_NODE)) {
                def domainClass = expression? ((ClassExpression) expression).getType() : null
                if (!domainClass) {
                    domainClass = annotationNode.getMember("domain")?.type
                    if (!domainClass) {
                        domainClass = extractGenericDomainClass(controllerClassNode)
                        if (domainClass) {
                            // set the domain value on the annotation so that ScaffoldingViewResolver can identify the domain object.
                            annotationNode.addMember("domain", new ClassExpression(domainClass))
                        }
                    }
                    if (!domainClass) {
                        GrailsASTUtils.error(source, classNode, "Scaffolded controller (${classNode.name}) with @Scaffold does not have domain class set.", true)
                    }
                }
                classNode.setSuperClass(GrailsASTUtils.nonGeneric(superClassNode, domainClass))
                def readOnlyExpression = (ConstantExpression) annotationNode?.getMember("readOnly")
                new ResourceTransform().addConstructor(classNode, domainClass, readOnlyExpression?.getValue()?.asBoolean()?:false)
            } else if (!currentSuperClass.isDerivedFrom(superClassNode)) {
               GrailsASTUtils.error(source, classNode, "Scaffolded controllers (${classNode.name}) cannot extend other classes: ${currentSuperClass.getName()}", true)
            }
        } else if (propertyNode != null) {
            GrailsASTUtils.error(source, propertyNode, "The 'scaffold' property must refer to a domain class.", true)
        }
    }

    protected static ClassNode extractGenericDomainClass(ClassNode controllerClassNode) {
        def genericsTypes = controllerClassNode?.genericsTypes
        if (genericsTypes && genericsTypes.length > 0) {
            return genericsTypes[0].type
        }
        return null
    }

    @Override
    boolean shouldInject(URL url) {
        return url != null && ControllerActionTransformer.CONTROLLER_PATTERN.matcher(url.getFile()).find()
    }
}
