# groovy-loader-v2
load groovy scripts in file directory dynamically

## 简介
动态加载指定目录下的groovy脚本，并将其注册为groovy bean，放置于ApplicationContext容器中，并使用命名空间进行分类区分(一个namespace对应于一个ApplicationContext)。同时能够动态感知到groovy脚本的新增、修改以及删除事件，并自动重新加载。

## 原理
- 使用文件目录管理groovy bean：指定文件夹下的每一个一级文件夹对应一个namespace，某个一级文件夹下的所有groovy被注册成bean后会分配到同一个ApplicationContext
- 通过GroovyScriptFactory一级类加载器来实例化groovy脚本
- 通过扫描监听指定路径下groovy文件的变更，来接受groovy脚本的新增、删除、更新事件

## 特性
- 能动态感知到groovy脚本的新增、删除、修改
- 针对加载得到的groovy bean，提供命名空间（根据namespace，放置到不同的ApplicationContext中）
- 用户可自定义listener，监听groovy脚本的变更
- 用户可自定义trigger，用于触发groovy bean的reload

## 使用
```
<bean id="listener" class="org.cuner.groovy.loader.v2.test.TestListener"/><!--需要实现org.cuner.groovy.loader.listener.GroovyRefreshedListener -->
<bean id="groovyLoader" class="org.cuner.groovy.loader.v2.NamespacedGroovyLoader">
    <property name="groovyResourcesDir" value=""/><!--指定spring groovy配置文件目录，若不设置或者为空则默认为classpath下groovy目录-->
    <property name="listener" ref="listener"/>
</bean>
```

## 优化
相比于groovy-loader来说，groovy-loader-v2解决了使用标签<lang:groovy>带来的定时强制reload的问题：仅当文件变更才回去reload，减少性能消耗