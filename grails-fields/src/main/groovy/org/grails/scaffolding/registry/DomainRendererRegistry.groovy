package org.grails.scaffolding.registry

import org.grails.scaffolding.model.property.DomainProperty
import groovy.transform.CompileStatic

import java.util.concurrent.atomic.AtomicInteger

/**
 * A registry of domain property renderers sorted by priority and order of addition
 *
 * @author James Kleeh
 */
@CompileStatic
abstract class DomainRendererRegistry<T extends DomainRenderer> {

    protected SortedSet<Entry> domainRegistryEntries = new TreeSet<Entry>();

    protected final AtomicInteger RENDERER_SEQUENCE = new AtomicInteger(0);

    void registerDomainRenderer(T domainRenderer, Integer priority) {
        domainRegistryEntries.add(new Entry(domainRenderer, priority))
    }

    public SortedSet<Entry> getDomainRegistryEntries() {
        this.domainRegistryEntries
    }

    T get(DomainProperty domainProperty) {
        for (Entry entry : domainRegistryEntries) {
            if (entry.renderer.supports(domainProperty)) {
                return entry.renderer
            }
        }
        null
    }

    private class Entry implements Comparable<Entry> {
        protected final T renderer
        private final int priority;
        private final int seq;

        Entry(T renderer, int priority) {
            this.renderer = renderer
            this.priority = priority
            seq = RENDERER_SEQUENCE.incrementAndGet()
        }

        public int compareTo(Entry entry) {
            return priority == entry.priority ? entry.seq - seq : entry.priority - priority;
        }
    }
}
