package org.cuner.groovy.loader.v2.trigger;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by houan on 18/6/13.
 */
public class GroovyScriptsModifiedTrigger implements GroovyRefreshTrigger {
    public boolean isTriggered(Map<String, Long> lastScriptsModified, String baseDir) {
        if (StringUtils.isBlank(baseDir)) {
            baseDir = this.getClass().getClassLoader().getResource("").getPath() + "/groovy";
        }

        List<File> fileList = getFileList(new File(baseDir));
        for (File file : fileList) {
            if (lastScriptsModified.get(file.getPath()) == null) {
                return true;
            }
            if (file.lastModified() != lastScriptsModified.get(file.getPath())) {
                return true;
            }
        }

        if (fileList.size() != lastScriptsModified.size()) {
            return true;
        }

        return false;
    }

    private List<File> getFileList(File base) {
        List<File> fileList = new ArrayList<File>();
        if (base.isDirectory()) {
            for (File file : base.listFiles()) {
                fileList.addAll(getFileList(file));
            }
        } else {
            if (base.getName().endsWith(".groovy")) {
                fileList.add(base);
            }
        }
        return fileList;
    }
}
