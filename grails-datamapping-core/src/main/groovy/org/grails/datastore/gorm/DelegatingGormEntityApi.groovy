package org.grails.datastore.gorm

import groovy.transform.CompileStatic

/**
 * A delegating implementation of {@link GormEntityApi}
 *
 * @author Graeme Rocher
 * @since 5.0.5
 */
@CompileStatic
class DelegatingGormEntityApi<D> implements GormEntityApi<D> {

    final GormInstanceApi<D> instanceApi
    final D target

    DelegatingGormEntityApi(GormInstanceApi<D> instanceApi, D target) {
        this.instanceApi = instanceApi
        this.target = target
    }

    @Override
    boolean instanceOf(Class cls) {
        return instanceApi.instanceOf(target, cls)
    }

    @Override
    D lock() {
        return instanceApi.lock(target)
    }

    @Override
    def mutex(Closure callable) {
        return instanceApi.mutex(target, callable)
    }

    @Override
    D refresh() {
        return instanceApi.refresh(target)
    }

    @Override
    D save() {
        return instanceApi.save(target)
    }

    @Override
    D insert() {
        return instanceApi.insert(target)
    }

    @Override
    D insert(Map params) {
        return instanceApi.insert(target, params)
    }

    @Override
    D merge() {
        return instanceApi.merge(target)
    }

    @Override
    D merge(Map params) {
        return instanceApi.merge(target, params)
    }

    @Override
    D save(boolean validate) {
        return instanceApi.save(target, validate)
    }

    @Override
    D save(Map params) {
        return instanceApi.save(target, params)
    }

    @Override
    Serializable ident() {
        return instanceApi.ident(target)
    }

    @Override
    D attach() {
        return instanceApi.attach(target)
    }

    @Override
    boolean isAttached() {
        return instanceApi.isAttached(target)
    }

    @Override
    void discard() {
        instanceApi.discard(target)
    }

    @Override
    void delete() {
        instanceApi.delete(target)
    }

    @Override
    void delete(Map params) {
        instanceApi.delete(target, params)
    }

    @Override
    boolean isDirty(String fieldName) {
        return instanceApi.isDirty(target, fieldName)
    }

    @Override
    boolean isDirty() {
        return instanceApi.isDirty(target)
    }
}
