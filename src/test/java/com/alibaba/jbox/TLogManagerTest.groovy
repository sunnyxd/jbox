package com.alibaba.jbox

import com.alibaba.jbox.trace.TLogManager
import org.springframework.core.io.FileSystemResource

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/10/20 15:50:00.
 */
class TLogManagerTest extends GroovyTestCase {

    void testParseFromXml() throws IOException {
        TLogManager tLogManager = new TLogManager()
        tLogManager.setELResource(new FileSystemResource("/Users/jifang.zjf/IdeaProjects/jbox/src/test/java/resources/tlog-config.xml"))
        System.out.println(tLogManager.getMethodELMap())
        assertTrue tLogManager.methodELMap.size() == 7
        def userWrapperSize = tLogManager.methodELMap.find { key, value ->
            key.contains("userWrapper")
        }.value.size()

        assertTrue userWrapperSize == 3
    }
}
