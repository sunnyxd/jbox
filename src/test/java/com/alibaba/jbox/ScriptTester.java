package com.alibaba.jbox;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.alibaba.jbox.domain.Address;
import com.alibaba.jbox.domain.People;
import com.alibaba.jbox.script.ScriptType;

import com.taobao.hsf.app.api.util.HSFApiConsumerBean;
import com.taobao.hsf.model.metadata.MethodSpecial;
import com.taobao.hsf.remoting.service.GenericService;
import com.taobao.rdb.factory.RClientFactory;
import com.taobao.rdb.smart.RClientManager;
import org.junit.Test;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/11 18:01:00.
 */
public class ScriptTester {

    private static final Logger logger = LoggerFactory.getLogger(ScriptTester.class);


    @Test
    public void testMDC(){


    }

    @Test
    public void testCaller() {
        People people = new People("ss", new Address());
        System.out.println(people.getSource());
    }

    @Test
    public void init() {
        RClientManager rclientManager = RClientFactory.getClientManager();
        String init = rclientManager.init("8ac10d18-dc80-4452-abc5-b72cbb3cc86b",
            "8ac10d18-dc80-4452-abc5-b72cbb3cc86b");
        System.out.println(init);
    }

    @Test
    public void testScala() throws ScriptException {
        ScriptEngine scala = new ScriptEngineManager().getEngineByName("scala");
        scala.put("n", 10);
        scala.eval("1 to n.asInstanceOf[Int] foreach println");
    }

    @Test
    public void testPython() {
        PythonInterpreter interpreter = new PythonInterpreter();
        interpreter.exec("days=('mod','Tue','Wed','Thu','Fri','Sat','Sun');");
        PyObject pyObject = interpreter.get("days");
        //logger.info("{}", "nihao");
        //System.out.println(MessageFormatter.format("{}", pyObject));
        System.out.println(org.slf4j.helpers.Util.getCallingClass());
    }

    @Test
    public void testCenterInvoke() throws Exception {
        HSFApiConsumerBean consumerBean = createConsumerBean("ScriptExecutor[businessinventorysolution]",
            "1.0.0.daily.inner");
        GenericService genericService = (GenericService)consumerBean.getObject();

        String[] parameterTypes = new String[] {String.class.getName(), ScriptType.class.getName(),
            String.class.getName()};
        Object[] args = new Object[] {"int a = 1; return a*11;", ScriptType.Groovy, "@$^_^$@"};
        Object result = genericService.$invoke("execute", parameterTypes, args);
        System.out.println(result);
    }

    public static HSFApiConsumerBean createConsumerBean(String serviceName, String serviceVersion) throws Exception {
        HSFApiConsumerBean consumerBean = new HSFApiConsumerBean();
        consumerBean.setInterfaceName(serviceName);
        consumerBean.setTarget("11.160.116.126");
        consumerBean.setGeneric("true");
        consumerBean.setVersion(serviceVersion);
        MethodSpecial methodSpecial = new MethodSpecial();
        methodSpecial.setClientTimeout(3000);
        methodSpecial.setMethodName("execute");
        consumerBean.setMethodSpecials(new MethodSpecial[] {methodSpecial});
        consumerBean.init(true);
        return consumerBean;
    }
}
