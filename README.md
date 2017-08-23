# jbox 通用工具集合(类似Guava)
## 提供
- 定制的线程池: `ExecutorsManager`、`ExecutorsMonitor`, 详细说明请参考`com.alibaba.jbox.executor.ExecutorsManager`
- flood ab test框架: `com.alibaba.jbox.flood.AbFloodExperiment`
- Log配置动态修改Configurator, 支持Logback, Log4j: `LogbackConfigurator`、`Log4jConfigurator`
- 反射简化工具: `com.alibaba.jbox.reflections.Reflect`
- 通用单机调度框架: `com.alibaba.jbox.scheduler.TaskScheduler`
- Spring容器启动耗时监控: `com.alibaba.jbox.spring.BeanInstantiationLogger`
- Spring `@Value` 注解适配Diamond配置, 支持动态修改属性配置: `com.alibaba.jbox.spring.DiamondPropertySourcesPlaceholder`
- 非Spring托管Bean `@Resource`、`@Autowired`、`@Value`生效适配器: `com.alibaba.jbox.spring.SpringAutowiredAdaptor`
- Stream多级利用: `com.alibaba.jbox.stream.StreamForker`
- 基于注解的性能监控工具: `com.alibaba.jbox.trace.TraceAspect`支持:
    1. 在方法执行前将`traceId`塞入slf4j MDC;
    2. 方法入参`@NotNull`、`@NotEmpty` 校验;
    3. 方法执行耗时监控.
- 通用工具`com.alibaba.jbox.utils`:

| Util | desc |
| :------: | :-------- |
| `AopTargetUtils` | 获取Aop target |
| `BeanCopyUtil` | Bean属性对拷, 忽略属性类型 |
| `DateUtils` | 日期时间格式化 |
| `JboxUtils` | 其他工具集合, 如*获取本地IP*、*获取Thread调用栈*、*获取`JoinPoint`真是Method*、*获取Object属性值*
| `JsonAppender` | append json string |
| `Performer` | 性能测试工具, 监控如***RT***、***QPS*** |
| `ProxyUtil` | 获取Proxy target |
| `SizeOf` | 精确测量内存内Java对象大小(`-javaagent`) |
| `Converters` | 函数式批量DO转换工具 |