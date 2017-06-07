# jbox 通用工具集合(类Guava)
## 提供
- 定制线程池: `ThreadPoolManager`
- flood AB测试框架: `com.alibaba.jbox.flood`
- 基于注解的性能监控工具: `com.alibaba.jbox.profiler`
- 简化反射工具: `com.alibaba.jbox.reflections`
- 通用调度框架: `com.alibaba.jbox.scheduler`
- 其他工具: `com.alibaba.jbox.utils`
    - `DataUtils`
    - `JsonAppender`
    - `Performer`: 性能测试工具, 监控如***RT***、***QPS***
    - `Collections3`: `Collection`相关功能: `Collection`属性抽取; `List`转`Map`, `List`转`MultiMap`
    - `SizeOf`: 精确测量Java对象大小(-javaagent)
    - `Converters`: 函数式批量DO转换工具