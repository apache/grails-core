package org.grails.datastore.gorm.transform

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.tools.GeneralUtils

import static org.codehaus.groovy.ast.ClassHelper.make
import static org.grails.datastore.mapping.reflect.AstUtils.ZERO_ARGUMENTS

/**
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class AstMethodDispatchUtils extends GeneralUtils {

    /**
     * Create named arguments
     *
     * @param args The args
     * @return The MapExpression
     */
    static MapExpression namedArgs(Map<String, ? extends Expression> args) {
        def expression = new MapExpression()
        for(entry in args) {
            expression.addMapEntryExpression(
                new MapEntryExpression(
                    constX(entry.key),
                    entry.value
                )
            )
        }
        return expression
    }
    /**
     * Make a direct method call on this object for the given name and arguments
     *
     * @return The expression
     */
    static MethodCallExpression callD(Class targetType, String var, String methodName, Expression arguments = ZERO_ARGUMENTS) {
        ClassNode targetNode = make(targetType)
        callD( targetNode, var, methodName, arguments)
    }

    /**
     * Make a direct method call on this object for the given name and arguments
     *
     * @return The expression
     */
    static MethodCallExpression callD(ClassNode targetType, String var, String methodName, Expression arguments = ZERO_ARGUMENTS) {
        VariableExpression varExpression = varX(var, targetType)
        return callD(varExpression, methodName, arguments)
    }

    /**
     * Make a direct method call on this object for the given name and arguments
     *
     * @return The expression
     */
    static MethodCallExpression callD(Expression var, String methodName, Expression arguments = ZERO_ARGUMENTS) {
        MethodCallExpression methodCall = callX(var, methodName, arguments)
        Parameter[] params = paramsForArgs(arguments)
        MethodNode mn = var.getType()?.getDeclaredMethod(methodName, params)
        if (mn != null) {
            methodCall.setMethodTarget(mn)
        }
        return methodCall
    }
    /**
     * Make a direct method call on this object for the given name and arguments
     *
     * @return The expression
     */
    static MethodCallExpression callThisD(Class thisType, String methodName, Expression arguments = ZERO_ARGUMENTS) {
        ClassNode classNode = make(thisType)
        return callThisD(classNode, methodName, arguments)
    }

    /**
     * Make a direct method call on this object for the given name and arguments
     *
     * @return The expression
     */
    static MethodCallExpression callThisD(ClassNode thisType, String methodName, Expression arguments) {
        MethodCallExpression methodCall = callX(varX("this", thisType), methodName, arguments)
        Parameter[] params = paramsForArgs(arguments)
        MethodNode mn = thisType.getDeclaredMethod(methodName, params)
        if(mn != null) {
            methodCall.setMethodTarget(mn)
        }
        return methodCall
    }

    static Parameter[] paramsForArgs(Expression expression) {
        if(expression instanceof TupleExpression) {
            TupleExpression te = (TupleExpression)expression
            List<Parameter> params = []
            int i = 0
            for(exp in te.expressions) {
                def type = exp instanceof ClassExpression ? ClassHelper.CLASS_Type : exp.type
                params.add( param(type, "p${i++}"))
            }
            return params as Parameter[]
        }
        else {
            def type = expression instanceof ClassExpression ? ClassHelper.CLASS_Type : expression.type
            return params( param(type, "p"))
        }
    }
}
