package org.cuner.groovy.loader.v2;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
import org.apache.commons.lang3.StringUtils;
import org.cuner.groovy.loader.v2.listener.GroovyRefreshEvent;
import org.cuner.groovy.loader.v2.listener.GroovyRefreshListener;
import org.cuner.groovy.loader.v2.trigger.GroovyRefreshTrigger;
import org.cuner.groovy.loader.v2.trigger.GroovyScriptsModifiedTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.scripting.groovy.GroovyScriptFactory;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.util.ReflectionUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by houan on 18/6/13.
 */
public class NamespacedGroovyLoader implements ApplicationListener<ContextRefreshedEvent> {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private String baseDir;

    private ApplicationContext parentContext;

    private Map<String, ApplicationContext> namespacedContext;

    private List<ApplicationContext> toBeDestoryedContext;

    private GroovyRefreshListener listener;

    private GroovyRefreshTrigger trigger;

    private AtomicBoolean loaded = new AtomicBoolean(false);

    private GroovyClassLoader groovyClassLoader;

    private URLClassLoader urlClassLoader;

    private Map<String, Long> scriptLastModifiedMap;

    private static Field injectionMetadataCacheField =
            ReflectionUtils.findField(AutowiredAnnotationBeanPostProcessor.class, "injectionMetadataCache");

    private static String[] xmls = {"classpath:subContext.xml"};

    static {
        injectionMetadataCacheField.setAccessible(true);
    }

    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (loaded.compareAndSet(false, true)) {
            if (trigger == null) {
                trigger = new GroovyScriptsModifiedTrigger();
            }
            this.parentContext = event.getApplicationContext();
            scanAndLoadGroovyBean();
            int checkInterval = 5000;
            startScriptReloadStrategy(trigger, checkInterval, scriptLastModifiedMap, baseDir);
        }
    }

    private void scanAndLoadGroovyBean() {
        if (namespacedContext != null) {
            this.toBeDestoryedContext = new ArrayList<ApplicationContext>(namespacedContext.values());
        }
        this.namespacedContext = new HashMap<String, ApplicationContext>();
        this.scriptLastModifiedMap = new HashMap<String, Long>();

        if (StringUtils.isBlank(this.baseDir)) {
            this.baseDir = this.getClass().getClassLoader().getResource("").getPath() + "/groovy";
        }

        File base = new File(this.baseDir);
        if (!base.exists()) {
            return;
        }

        // get class loader
        URL url;
        try {
            url = base.toURI().toURL();
        } catch (MalformedURLException e) {
            url = null;
        }
        this.urlClassLoader = new URLClassLoader(new URL[]{url}, NamespacedGroovyLoader.class.getClassLoader());
        this.groovyClassLoader = new GroovyClassLoader(this.urlClassLoader);

        //扫描命名空间
        scanNamespaces(base, base.getName());

        listener.refresh(new GroovyRefreshEvent(parentContext, namespacedContext));

    }

    private void scanNamespaces(File file, String namespacePrefix) {
        if (file == null || !file.exists()) {
            return;
        }

        //groovy文件夹下第一级文件夹名称拼接namespacePrefix作为namespace
        if (file.isDirectory()) {
            // 先初始化common
            for (File subDir : file.listFiles()) {
                if (subDir.exists()) {
                    try {
                        scanGroovyFiles(subDir, namespacePrefix + "/" + subDir.getName());
                    } catch (Exception e) {
                        logger.error("error:", e);
                    }
                }
            }
        }
    }

    /**
     * 递归查找并加载namespace下的所有groovy文件
     * @param file
     * @param namespace
     */
    private void scanGroovyFiles(File file, String namespace) throws Exception {
        if (!file.exists()) {
            return;
        }

        if (file.isFile() && file.getName().endsWith(".groovy")) {
            ApplicationContext context = getOrCreateContext(namespace);
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getAutowireCapableBeanFactory();

            String scriptLocation = file.toURI().toString();
            if (scriptNotExists(context, scriptLocation)) {
                throw new IllegalArgumentException("script not exists : " + scriptLocation);
            }
            scriptLastModifiedMap.put(file.getPath(), file.lastModified());

            String className = StringUtils.removeEnd(scriptLocation.substring(scriptLocation.indexOf(baseDir) + baseDir.length() + 1).replace("/", "."), ".groovy");
            // 只有GroovyBean声明的类才实例化
            DSLScriptSource scriptSource = new DSLScriptSource(new ResourceScriptSource(context.getResource(scriptLocation)), className);

            Class scriptClass = groovyClassLoader.parseClass(scriptSource.getScriptAsString(), scriptSource.suggestedClassName());

            // Tell Groovy we don't need any meta
            // information about these classes
            GroovySystem.getMetaClassRegistry().removeMetaClass(scriptClass);
            groovyClassLoader.clearCache();

            // Create script factory bean definition.
            GroovyScriptFactory groovyScriptFactory = new GroovyScriptFactory(scriptLocation);
            groovyScriptFactory.setBeanFactory(beanFactory);
            groovyScriptFactory.setBeanClassLoader(urlClassLoader);
            Object bean =
                    groovyScriptFactory.getScriptedObject(scriptSource);
            if (bean == null) {
                //只有静态方法的groovy脚本(没有类声明)
                return;
            }

            // Tell Groovy we don't need any meta
            // information about these classes
            GroovySystem.getMetaClassRegistry().removeMetaClass(bean.getClass());
            groovyScriptFactory.getGroovyClassLoader().clearCache();

            String beanName = file.getName();
            if (beanFactory.containsBean(beanName)) {
                beanFactory.destroySingleton(beanName); //移除单例bean
                removeInjectCache(context, bean); //移除注入缓存 否则Caused by: java.lang.IllegalArgumentException: object is not an instance of declaring class
            }
            beanFactory.registerSingleton(beanName, bean); //注册单例bean
            beanFactory.autowireBean(bean); //自动注入

        } else if (file.isDirectory()) {
            File[] subFiles = file.listFiles();
            for (File subFile : subFiles) {
                scanGroovyFiles(subFile, namespace);
            }
        }
    }

    private ApplicationContext getOrCreateContext(String namespace) {
        // get sub context
        ApplicationContext context = namespacedContext.get(namespace);
        if (context != null) {
            return context;
        }
        // create sub context
        context = new ClassPathXmlApplicationContext(xmls, parentContext);
        namespacedContext.put(namespace, context);
        return context;
    }

    private void removeInjectCache(ApplicationContext context, Object controller) {

        AutowiredAnnotationBeanPostProcessor autowiredAnnotationBeanPostProcessor =
                context.getBean(AutowiredAnnotationBeanPostProcessor.class);

        Map<String, InjectionMetadata> injectionMetadataMap =
                (Map<String, InjectionMetadata>) ReflectionUtils.getField(injectionMetadataCacheField, autowiredAnnotationBeanPostProcessor);

        injectionMetadataMap.remove(controller.getClass().getName());
    }

    private boolean scriptNotExists(ApplicationContext context, String scriptLocation) {
        return !context.getResource(scriptLocation).exists();
    }

    private void startScriptReloadStrategy(final GroovyRefreshTrigger trigger, final long scriptCheckInterval, final Map<String, Long> scriptLastModifiedMap, final String baseDir) {
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(scriptCheckInterval);
                        if (trigger.isTriggered(scriptLastModifiedMap, baseDir)) {
                            // reload
                            Thread.sleep(3000);
                            scanAndLoadGroovyBean();
                        }
                    } catch (Throwable e) {
                        logger.error("groovy refresh/check error!", e);
                    } finally {
                    }
                }
            }
        }.start();
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public GroovyRefreshListener getListener() {
        return listener;
    }

    public void setListener(GroovyRefreshListener listener) {
        this.listener = listener;
    }

    public GroovyRefreshTrigger getTrigger() {
        return trigger;
    }

    public void setTrigger(GroovyRefreshTrigger trigger) {
        this.trigger = trigger;
    }
}
