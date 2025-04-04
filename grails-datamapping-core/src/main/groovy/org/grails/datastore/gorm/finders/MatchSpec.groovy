package org.grails.datastore.gorm.finders

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.expr.MethodCallExpression

/**
 * A match spec details a matched finder
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class MatchSpec {
    /**
     * The full method name
     */
    final String methodName

    /**
     * The prefix (for example "findBy")
     */
    final String prefix
    /**
     * The query expression without the prefix i.e. methodName - prefix
     */
    final String queryExpression
    /**
     * The required arguments
     */
    final int requiredArguments
    /**
     * The method call expressions
     */
    final List<MethodExpression> methodCallExpressions

    MatchSpec(String methodName, String prefix, String queryExpression, int requiredArguments, List<MethodExpression> methodCallExpressions) {
        this.methodName = methodName
        this.prefix = prefix
        this.queryExpression = queryExpression
        this.requiredArguments = requiredArguments
        this.methodCallExpressions = methodCallExpressions
    }

    Collection<String> getPropertyNames() {
        methodCallExpressions.collect() { MethodExpression me ->
            me.propertyName
        }
    }
}
