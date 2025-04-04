package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS

/**
 * Implementation for counting
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class CountImplementer extends AbstractDetachedCriteriaServiceImplementor implements SingleResultServiceImplementer<Number> {
    static final List<String> HANDLED_PREFIXES = ['count']

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        return AstUtils.isDomainClass(domainClass) && super.doesImplement(domainClass, methodNode)
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return AstUtils.isNumberType(returnType)
    }

    @Override
    Iterable<String> getHandledPrefixes() {
        return HANDLED_PREFIXES
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
        Expression callCount = callX(detachedCriteriaVar, "count", queryArgs)
        body.addStatement(
            returnS( castX(newMethodNode.returnType, callCount) )
        )
    }
}
