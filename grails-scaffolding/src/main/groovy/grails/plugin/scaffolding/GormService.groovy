package grails.plugin.scaffolding

import grails.artefact.Artefact
import grails.gorm.api.GormAllOperations
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormEntityApi

@Artefact("Service")
@ReadOnly
@CompileStatic
class GormService<T extends GormEntity<T>> {

    GormAllOperations<T> resource
    String resourceName
    String resourceClassName
    boolean readOnly

    GormService(Class<T> resource, boolean readOnly) {
        this.resource = resource.getDeclaredConstructor().newInstance() as GormAllOperations<T>
        this.readOnly = readOnly
        resourceClassName = resource.simpleName
        resourceName = GrailsNameUtils.getPropertyName(resource)
    }

    protected T queryForResource(Serializable id) {
        resource.get(id)
    }

    T get(Serializable id) {
        queryForResource(id)
    }

    List<T> list(Map args) {
        resource.list(args)
    }

    Long count() {
        resource.count()
    }

    @Transactional
    void delete(Serializable id) {
        if (readOnly) {
            return
        }
        ((GormEntityApi) queryForResource(id)).delete flush: true
    }

    @Transactional
    T save(T instance) {
        if (readOnly) {
            return instance
        }
        (T) ((GormEntityApi) instance).save(flush: true)
    }
}