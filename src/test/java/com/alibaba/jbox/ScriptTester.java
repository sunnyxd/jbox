package com.alibaba.jbox;

import com.taobao.hsf.app.api.util.HSFApiConsumerBean;
import com.taobao.hsf.model.metadata.MethodSpecial;
import com.taobao.hsf.remoting.service.GenericService;
import org.junit.Test;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/11 18:01:00.
 */
public class ScriptTester {

    @Test
    public void testPython() {
        PythonInterpreter interpreter = new PythonInterpreter();
        interpreter.exec("days=('mod','Tue','Wed','Thu','Fri','Sat','Sun');");
        PyObject pyObject = interpreter.get("days");
        System.out.println(pyObject);
    }

    @Test
    public void testCenterInvoke() throws Exception {
        HSFApiConsumerBean consumerBean = createConsumerBean("ScriptExecutor[businessinventorysolution]", "1.0.0.daily.inner");
        GenericService genericService = (GenericService) consumerBean.getObject();
        Object result = genericService.$invoke("context", null, null);
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
        methodSpecial.setMethodName("context");
        consumerBean.setMethodSpecials(new MethodSpecial[]{methodSpecial});
        consumerBean.init(true);
        return consumerBean;
    }
}
