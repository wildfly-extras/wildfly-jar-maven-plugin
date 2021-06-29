package org.wildfly.plugins.demo.jaxrs;

import java.net.URL;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 */
@RunWith(Arquillian.class)
@RunAsClient
public class JaxrsITCase {


    @Test
    public void testURL() throws Exception {
        String result = TestUtil.performCall(new URL("http://127.0.0.1:8080/hello"));
        System.out.println("RESULT " + result);
        assertEquals(result, "Hello from WildFly!", result);
    }

}
