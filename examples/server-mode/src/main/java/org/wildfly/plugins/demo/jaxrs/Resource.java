/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wildfly.plugins.demo.jaxrs;

import javax.annotation.PostConstruct;

/**
 *
 * @author jdenise
 */
    public  class Resource {

        // with @PostConstruct you will make sure
        // your bean is going to be properly configure on its creation...
        @PostConstruct
        public void init() {
            System.out.println("Post construct");
        }
    }
