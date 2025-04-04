package org.grails.datastore.gorm.services.implementers

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.grails.datastore.mapping.reflect.AstUtils

import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS
import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.namedArgs

/**
 * Abstract implementation of saving
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractSaveImplementer extends AbstractWriteOperationImplementer {
    protected Statement bindParametersAndSave(ClassNode domainClassNode, MethodNode abstractMethodNode, Parameter[] parameters, BlockStatement body, VariableExpression entityVar) {
        Expression argsExpression = null

        for (Parameter parameter in parameters) {
            String parameterName = parameter.name
            if (isValidParameter(domainClassNode, parameter, parameterName)) {
                body.addStatement(
                        assignS(propX(entityVar, parameterName), varX(parameter))
                )
            } else if (parameter.type == ClassHelper.MAP_TYPE && parameterName == 'args') {
                argsExpression = varX(parameter)
            } else {
                AstUtils.error(
                        abstractMethodNode.declaringClass.module.context,
                        abstractMethodNode,
                        "Cannot implement method for argument [${parameterName}]. No property exists on domain class [$domainClassNode.name]"
                )
            }
        }
        Expression saveArgs
        if (argsExpression != null) {
            saveArgs = varX('$args')
            body.addStatement(
                    declS(saveArgs, namedArgs(failOnError: ConstantExpression.TRUE))
            )
            body.addStatement(
                    stmt(callX(saveArgs, "putAll", argsExpression))
            )
        } else {
            saveArgs = namedArgs(failOnError: ConstantExpression.TRUE)
        }

        Expression connectionId = findConnectionId(abstractMethodNode)
        if(connectionId != null) {
            returnS(callX(buildInstanceApiLookup(domainClassNode, connectionId), "save", args(entityVar, saveArgs)))
        }
        else {
            return returnS(callX(entityVar, "save", saveArgs))
        }
    }

}
