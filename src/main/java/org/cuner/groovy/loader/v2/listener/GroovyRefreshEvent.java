package org.cuner.groovy.loader.v2.listener;

import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * Created by houan on 18/6/13.
 */
public class GroovyRefreshEvent {

    private ApplicationContext parentContext;

    private Map<String, ApplicationContext> namespacedApplicationContext;

    public GroovyRefreshEvent(ApplicationContext parentContext, Map<String, ApplicationContext> namespacedApplicationContext) {
        this.parentContext = parentContext;
        this.namespacedApplicationContext = namespacedApplicationContext;
    }

    public ApplicationContext getParentContext() {
        return parentContext;
    }

    public void setParentContext(ApplicationContext parentContext) {
        this.parentContext = parentContext;
    }

    public Map<String, ApplicationContext> getNamespacedApplicationContext() {
        return namespacedApplicationContext;
    }

    public void setNamespacedApplicationContext(Map<String, ApplicationContext> namespacedApplicationContext) {
        this.namespacedApplicationContext = namespacedApplicationContext;
    }
}
