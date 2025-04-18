package org.grails.datastore.gorm.neo4j.proxy;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyObject;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.proxy.EntityProxy;
import org.grails.datastore.mapping.proxy.GroovyObjectMethodHandler;
import org.grails.datastore.mapping.proxy.JavassistProxyFactory;
import org.grails.datastore.mapping.proxy.SessionEntityProxyMethodHandler;
import org.springframework.beans.BeanUtils;
import org.springframework.util.ReflectionUtils;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * extends {@link org.grails.datastore.mapping.proxy.JavassistProxyFactory} to capture method calls `hashCode` and `equals`
 * without expanding the proxy
 */
public class HashcodeEqualsAwareProxyFactory extends JavassistProxyFactory {

    @Override

    protected Object createProxiedInstance(final Session session, final Class cls, Class proxyClass, final Serializable id) {
            MethodHandler mi = new GroovyObjectMethodHandler(proxyClass) {
                private Object target;

                @Override
                protected Object resolveDelegate(Object self) {
                    return target;
                }

                public Object invoke(Object proxy, Method method, Method proceed, Object[] args) throws Throwable {
                    final String methodName = method.getName();
                    if (args.length == 0) {
                        if (methodName.equals("getId") || methodName.equals("getProxyKey")) {
                            return id;
                        }
                        if (methodName.equals("initialize")) {
                            initialize();
                            return null;
                        }
                        if (methodName.equals("isInitialized")) {
                            return target != null;
                        }
                        if (methodName.equals("getTarget")) {
                            initialize();
                            return target;
                        }
                        if (methodName.equals("hashCode")) {
                            return ((Long)id).intValue();
                        }
                    }
                    else if(args.length == 1) {
                        if (methodName.equals("equals")) {
                            final Method getIdMethod = ReflectionUtils.findMethod(cls, "getId");
                            Object other = args[0];
                            if(other == null) {
                                return false;
                            }
                            if(other == proxy) {
                                return true;
                            }
                            if(other.getClass() == proxyClass && other instanceof EntityProxy && id.equals(((EntityProxy)other).getProxyKey())) {
                                return true;
                            }
                            if(other.getClass() == cls && id.equals(ReflectionUtils.invokeMethod(getIdMethod, other))) {
                                return true;
                            }
                            return false;
                        }
                    }
                    if (target == null) {
                        initialize();

                        // This tends to happen during unit testing if the proxy class is not properly mocked
                        // and therefore can't be found in the session.
                        if( target == null ) {
                            throw new IllegalStateException("Proxy for ["+cls.getName()+":"+id+"] could not be initialized");
                        }
                    }

                    Object result = handleInvocation(target, method, args);
                    if(!wasHandled(result)) {
                        return org.springframework.util.ReflectionUtils.invokeMethod(method, target, args);
                    } else {
                        return result;
                    }
                }

                public void initialize() {
                    target = session.retrieve(cls, id);
                }
            };
            Object proxy = BeanUtils.instantiateClass(proxyClass);
            ((ProxyObject)proxy).setHandler(mi);
            return proxy;
        }

}
