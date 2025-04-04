package org.grails.datastore.rx.proxy;

import org.grails.datastore.mapping.proxy.EntityProxyMethodHandler;
import org.grails.datastore.rx.RxDatastoreClient;
import org.grails.datastore.rx.query.QueryState;
import org.springframework.util.ReflectionUtils;
import rx.Observable;
import rx.Subscriber;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Abstract proxy generator for ObservableProxy instances
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public abstract class AbstractObservableProxyMethodHandler extends EntityProxyMethodHandler {
    protected final Class type;
    protected final RxDatastoreClient client;
    protected final QueryState queryState;
    protected Object target;

    public AbstractObservableProxyMethodHandler(Class<?> proxyClass, Class type, QueryState queryState, RxDatastoreClient client) {
        super(proxyClass);
        this.type = type;
        this.queryState = queryState;
        this.client = client;
    }

    @Override
    protected Object isProxyInitiated(Object self) {
        return target != null;
    }

    protected abstract Observable resolveObservable();

    @Override
    protected Object invokeEntityProxyMethods(Object self, String methodName, Object[] args) {
        if(methodName.equals("subscribe")) {
            Observable observable = resolveObservable();
            return observable.subscribe((Subscriber) args[0]);
        }
        else if(methodName.equals("toObservable")) {
            return resolveObservable();
        }
        else {
            return super.invokeEntityProxyMethods(self, methodName, args);
        }
    }

    protected Object handleInvocationFallback(Object self, Method thisMethod, Object[] args) {
        Object actualTarget = getProxyTarget(self);
        if(!thisMethod.getDeclaringClass().isInstance(actualTarget)) {
            if(Modifier.isPublic(thisMethod.getModifiers())) {
                final Method method = ReflectionUtils.findMethod(actualTarget.getClass(), thisMethod.getName(), thisMethod.getParameterTypes());
                if(method != null) {
                    ReflectionUtils.makeAccessible(method);
                    thisMethod = method;
                }
            } else {
                final Method method = ReflectionUtils.findMethod(actualTarget.getClass(), thisMethod.getName(), thisMethod.getParameterTypes());
                if(method != null) {
                    thisMethod = method;
                }
            }
        }
        return ReflectionUtils.invokeMethod(thisMethod, actualTarget, args);
    }
}
