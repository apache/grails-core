package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.transform.DelegateASTTransformation
import org.grails.datastore.gorm.transform.AstPropertyResolveUtils
import org.grails.datastore.mapping.reflect.AstUtils
import org.grails.datastore.mapping.reflect.NameUtils

import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS
import static org.codehaus.groovy.ast.tools.GeneralUtils.block
import static org.codehaus.groovy.ast.tools.GeneralUtils.param
import static org.codehaus.groovy.ast.tools.GeneralUtils.params
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX

/**
 * Base trait for building interface projections
 *
 * @author Graeme Rocher
 * @since 6.1.1
 */
@CompileStatic
trait InterfaceProjectionBuilder {

    boolean isInterfaceProjection(ClassNode domainClass, MethodNode methodNode, ClassNode returnType) {
        if(returnType.isInterface() && !returnType.packageName?.startsWith("java.")) {
            List<String> interfacePropertyNames = AstPropertyResolveUtils.getPropertyNames(returnType)

            for(prop in interfacePropertyNames) {
                ClassNode existingType = AstPropertyResolveUtils.getPropertyType(domainClass, prop)
                ClassNode propertyType = AstPropertyResolveUtils.getPropertyType(returnType, prop)
                if(existingType == null) {
                    return false
                }
                else if(!AstUtils.isSubclassOfOrImplementsInterface(existingType, propertyType)) {
                    return false
                }
            }
            return true
        }
        return false
    }

    MethodNode buildInterfaceImpl(ClassNode interfaceNode, ClassNode declaringClass, ClassNode targetDomainClass, MethodNode abstractMethodNode) {
        List<Expression> getterNames = (List<Expression>) AstPropertyResolveUtils.getPropertyNames(interfaceNode)
                .collect() {
            new ConstantExpression(NameUtils.getGetterName(it))
        }
        String innerClassName = "${declaringClass.name}\$${interfaceNode.nameWithoutPackage}"
        InnerClassNode innerClassNode = (InnerClassNode) declaringClass.innerClasses.find { InnerClassNode inner -> inner.name == innerClassName }

        MethodNode methodTarget
        Parameter domainClassParam = param(targetDomainClass.plainNodeReference, "target")
        Parameter[] params = params(domainClassParam)
        if (innerClassNode == null) {
            innerClassNode = new InnerClassNode(declaringClass, innerClassName, Modifier.STATIC | Modifier.PRIVATE, ClassHelper.OBJECT_TYPE, [interfaceNode.plainNodeReference] as ClassNode[], null)
            FieldNode field = innerClassNode.addField(
                    '$target', Modifier.PUBLIC, targetDomainClass.plainNodeReference, null
            )
            methodTarget = innerClassNode.addMethod('$setTarget', Modifier.PUBLIC, ClassHelper.VOID_TYPE, params, null, block(
                    assignS(varX(field), varX(domainClassParam))
            ))
            AnnotationNode delegateAnn = new AnnotationNode(new ClassNode(Delegate))
            delegateAnn.setMember("includes", new ListExpression(getterNames))
            delegateAnn.setMember("interfaces", new ConstantExpression(false))
            ModuleNode module = abstractMethodNode.declaringClass.module
            new DelegateASTTransformation().visit(
                    [delegateAnn, field] as ASTNode[],
                    module.context
            )
            module.addClass(innerClassNode)
        } else {
            methodTarget = innerClassNode.getMethod('$setTarget', params)
        }
        return methodTarget
    }
}