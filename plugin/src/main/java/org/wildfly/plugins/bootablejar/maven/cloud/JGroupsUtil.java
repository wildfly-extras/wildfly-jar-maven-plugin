/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugins.bootablejar.maven.cloud;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Utility for jgroups.
 *
 * @author jdenise
 */
public class JGroupsUtil {

    private static final String JGROUPS_CLUSTER_PASSWORD = "${env.JGROUPS_CLUSTER_PASSWORD}";

    static List<String> getAuthProtocolCommands(Path configFile) throws Exception {
        List<String> ret = new ArrayList<>();
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        NodeList lst = getSubSystems(configFile);
        for (int i = 0; i < lst.getLength(); i++) {
            Element subsystem = (Element) lst.item(i);
            String xmlns = subsystem.getAttribute("xmlns");
            if (xmlns.startsWith("urn:jboss:domain:jgroups:")) {
                NodeList stacks = (NodeList) xpath.evaluate("stacks/stack", subsystem, XPathConstants.NODESET);
                for (int j = 0; j < stacks.getLength(); j++) {
                    Element stack = (Element) stacks.item(j);
                    int currentIndex = 0;
                    String currentStack = stack.getAttribute("name");
                    NodeList protocols = (NodeList) xpath.evaluate("*[local-name()='protocol' or contains(local-name(), '-protocol')]", stack, XPathConstants.NODESET);
                    for (int k = 0; k < protocols.getLength(); k++) {
                        Element protocol = (Element) protocols.item(k);
                        String type = protocol.getAttribute("type");
                        if ("pbcast.GMS".equals(type)) {
                            ret.add("batch");
                            ret.add("/subsystem=jgroups/stack=" + currentStack + "/protocol=AUTH:add(add-index=" + currentIndex + ")");
                            ret.add("/subsystem=jgroups/stack=" + currentStack + "/protocol=AUTH/token=digest:add(algorithm=SHA-512, shared-secret-reference={clear-text=" + JGROUPS_CLUSTER_PASSWORD + "})");
                            ret.add("run-batch");
                            break;
                        }
                        currentIndex += 1;
                    }
                }
            }
        }
        return ret;
    }

    private static NodeList getSubSystems(Path configFile) throws Exception {
        try (FileInputStream fileInputStream = new FileInputStream(configFile.toFile())) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

            Document document = documentBuilder.parse(fileInputStream);
            Element root = document.getDocumentElement();
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();
            return (NodeList) xpath.evaluate("//subsystem", root, XPathConstants.NODESET);
        }
    }
}
