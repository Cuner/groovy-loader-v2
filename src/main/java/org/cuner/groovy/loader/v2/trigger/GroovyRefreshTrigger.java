package org.cuner.groovy.loader.v2.trigger;

import java.util.Map;

/**
 * Created by houan on 18/6/13.
 */
public interface GroovyRefreshTrigger {

    public boolean isTriggered(Map<String, Long> lastScriptsModified, String baseDir);
}
