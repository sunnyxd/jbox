# jbox 业务工具集合(类似Guava)
## 功能模块
### executor- 定制线程池
- `ExecutorsManager`
- `ExecutorsMonitor`
详见: `com.alibaba.jbox.executor.ExecutorManager`

### flood- ab test框架
详见: `com.alibaba.jbox.flood.AbFloodExperiment`

### scheduler- 单机任务调度框架
详见: `com.alibaba.jbox.scheduler.TaskScheduler`


### script

### spring
- Spring容器启动耗时监控: `com.alibaba.jbox.spring.BeanInstantiationMonitor`
- Spring `@Value` 注解适配Diamond配置, 支持动态修改属性配置: `com.alibaba.jbox.spring.DiamondPropertySourcesPlaceholder`
- 非Spring托管Bean `@Resource`、`@Autowired`、`@Value`生效适配器: `com.alibaba.jbox.spring.SpringAutowiredAdaptor`


### stream
Stream多级利用: `com.alibaba.jbox.stream.StreamForker`

### trace
- TraceAspect: 基于Aop性能监控工具
    1. 在方法执行前将`traceId`导入`MDC`;
    2. 方法入参`javax.validation`校验;
    3. `Sentinel`限流监控;
    4. 方法**入参**、**返回值**、**执行耗时**监控.
- TLogManager: log统一接入大盘, 目前支持SpEL(json、xml)配置日志占位符

| 默认占位打印 | desc |
| :------: | :-------- |
| `invoke thread` | 方法执行线程 |
| `rt`            | 方法执行耗时 |
| `class name`    | 方法所属类名 |
| `method name`   | 方法名 |
| `args`          | 入参 |
| `result`        | 返回值 |
| `exception`     | 方法执行抛出异常 |
| `server ip`     | 所属机器IP |
| `trace id`      | EagleEye TraceId | 
| `client name`   | 调用方name |
| `client ip`     | 调用方IP |

### utils- 通用工具

| Utils | desc |
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