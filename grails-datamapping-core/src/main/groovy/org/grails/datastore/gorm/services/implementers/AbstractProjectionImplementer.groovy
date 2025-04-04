package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.grails.datastore.mapping.reflect.AstUtils

import java.beans.Introspector

import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS

/**
 * Abstract implementation for projections
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractProjectionImplementer extends AbstractDetachedCriteriaServiceImplementor {

    protected static final String RESOLVED_PROPERTY_NAME = "${FindOnePropertyProjectionImplementer.name}.resolved.property"
    // before find by implementer
    @Override
    int getOrder() {
        FindAllByImplementer.POSITION - 100
    }

    protected String establishPropertyName(MethodNode methodNode, String prefix, ClassNode domainClass) {
        String methodName = methodNode.name
        String stem = "${prefix}${domainClass.nameWithoutPackage}"
        String propertyName = null
        if (methodName.startsWith(stem)) {
            propertyName = Introspector.decapitalize(methodName.substring(stem.length()))
            methodNode.putNodeMetaData(RESOLVED_PROPERTY_NAME, propertyName)
        }
        return propertyName
    }

    protected boolean isValidPropertyType(ClassNode returnType, ClassNode propertyType) {
        if(propertyType == null) return false
        else {
            returnType == propertyType || AstUtils.isSubclassOfOrImplementsInterface(returnType, propertyType)
        }
    }


    @Override
    protected boolean lookupById() {
        return false
    }

    @Override
    void implementById(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode, BlockStatement body, Expression byIdLookup) {
        // no-op
    }


    @Override
    void implementWithQuery(ClassNode domainClassNode, MethodNode abstractMethodNode, MethodNode newMethodNode, ClassNode targetClassNode, BlockStatement body, VariableExpression detachedCriteriaVar, Expression queryArgs) {
        String propertyName = (String)abstractMethodNode.getNodeMetaData(RESOLVED_PROPERTY_NAME)
        assert propertyName != null : "Bug in ${getClass().name} transform logic. Method implement should never be called before doesImplement(..) check"

        body.addStatements([
                assignS(detachedCriteriaVar, callX(detachedCriteriaVar, getProjectionName(), constX(propertyName))),
                returnS(callX(detachedCriteriaVar, getQueryMethodToInvoke(domainClassNode, newMethodNode), queryArgs != null ? queryArgs : AstUtils.ZERO_ARGUMENTS))
        ])
    }

    protected String getProjectionName() {
        "property"
    }

    protected String getQueryMethodToInvoke(ClassNode domainClassNode, MethodNode newMethodNode) {
        "find"
    }
}
