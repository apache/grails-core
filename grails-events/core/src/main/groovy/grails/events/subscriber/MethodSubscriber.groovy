package grails.events.subscriber

import grails.events.Event
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.util.ReflectionUtils

import java.lang.reflect.Method

/**
 * Invokes a method to trigger an event
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@Slf4j
@AutoFinal
@CompileStatic
@EqualsAndHashCode(includes = ['target', 'method'])
@ToString(includes = ['method'])
class MethodSubscriber<T, R> implements Subscriber<T, R> {

    final Object target
    final Method method
    final Class[] parameterTypes
    final int parameterLength

    ConversionService conversionService = new DefaultConversionService()

    MethodSubscriber(Object target, Method method) {

        this.target = target
        this.method = method
        this.parameterTypes = method.parameterTypes
        this.parameterLength = parameterTypes.length

        if (target.getClass() != method.getDeclaringClass()) {
            throw new IllegalArgumentException("The target must be an instance of the declaring class for method $method")
        }
    }

    @Override
    R call(T arg) {
        switch (parameterLength) {
            case 0:
                return ReflectionUtils.invokeMethod(method, target) as R
            case 1:
                Class parameterType = parameterTypes[0]
                if(parameterType.isInstance(arg)) {
                    return ReflectionUtils.invokeMethod(method, target, arg) as R
                }
                else {
                    def converted = conversionService.canConvert(arg.getClass(), parameterType) ? conversionService.convert(arg, parameterType) : null
                    if(converted != null) {
                        return ReflectionUtils.invokeMethod(method, target, converted) as R
                    }
                    else {
                        log.debug('Could not convert Event argument [{}] to required type to invoke listener [{}]. Ignoring.', arg, method)
                        break
                    }
                }
            default:
                if(arg != null && arg.getClass().isArray()) {
                    Object[] array = (Object[]) arg

                    if(array.length == parameterLength) {
                        Object[] converted = new Object[array.length]
                        int i = 0
                        for(o in array) {
                            Class parameterType = parameterTypes[i]
                            if(parameterType.isInstance(o)) {
                                converted[i] = array[i]
                            }
                            else {
                                converted[i] = conversionService.convert(o, parameterType)
                            }
                            i++
                        }
                        return ReflectionUtils.invokeMethod(method, target, converted) as R
                    }
                    else {
                        log.debug("Could not convert Event argument [{}] to required type to invoke listener [{}]. Ignoring.", arg, method)
                        break
                    }
                }
                else {
                    log.debug("Could not convert Event argument [{}] to required type to invoke listener [{}]. Ignoring.", arg, method)
                    break
                }
        }
        return null
    }
}
