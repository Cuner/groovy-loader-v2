package org.cuner.groovy.loader.v2;

import org.springframework.scripting.ScriptSource;
import org.springframework.scripting.support.ResourceScriptSource;

import java.io.IOException;


public class DSLScriptSource implements ScriptSource {

    private ResourceScriptSource resourceScriptSource;

    private String className;

    public DSLScriptSource(ResourceScriptSource resourceScriptSource, String className) {
        this.resourceScriptSource = resourceScriptSource;
        this.className = className;
    }

    public String getScriptAsString() throws IOException {
        return resourceScriptSource.getScriptAsString();
    }

    public boolean isModified() {
        return resourceScriptSource.isModified();
    }

    public String suggestedClassName() {
        return className;
    }
}
