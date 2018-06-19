package org.cuner.groovy.loader.v2.test;

import org.cuner.groovy.loader.v2.listener.GroovyRefreshEvent;
import org.cuner.groovy.loader.v2.listener.GroovyRefreshListener;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;

/**
 * Created by houan on 18/6/5.
 */
public class TestListener implements GroovyRefreshListener {

    public void refresh(GroovyRefreshEvent event) {
        for (String namespace : event.getNamespacedApplicationContext().keySet()) {
            System.out.println("nameSpace:" + namespace);
            ApplicationContext context = event.getNamespacedApplicationContext().get(namespace);
            Object groovyObject = context.getBean("test");
            for (Method method : groovyObject.getClass().getMethods()) {
                try {
                    if (method.getName().contains("test")) {
                        method.invoke(groovyObject);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
