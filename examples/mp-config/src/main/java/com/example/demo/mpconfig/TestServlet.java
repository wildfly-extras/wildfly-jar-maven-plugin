package com.example.demo.mpconfig;

import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@WebServlet(urlPatterns = "/test-config")
public class TestServlet extends HttpServlet {

    @Inject
    @ConfigProperty(name = "a", defaultValue = "Default value for a")
    String a;

    @Inject
    @ConfigProperty(name = "b", defaultValue = "Default value for b")
    String b;

    @Inject
    @ConfigProperty(name = "c", defaultValue = "Default value for c")
    String c;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PrintWriter writer = resp.getWriter();
        writer.println("a = " + a);
        writer.println("b = " + b);
        writer.println("c = " + c);
        writer.close();
    }

}
