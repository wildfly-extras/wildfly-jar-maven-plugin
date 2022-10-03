package org.wildfly.plugins.demo.ejb.timers.clustered;

import java.util.logging.Logger;

import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;

@Singleton
@Startup
public class AutomaticRecurringTimer {

    private static final Logger logger = Logger.getLogger(AutomaticRecurringTimer.class.getName());

    @Schedule(hour = "*", minute = "*", second = "*/10", info = "Every 10 secs timer", persistent = true)
    public void printDate() {
        logger.info("This is the actual timer execution, it is " + new java.util.Date().toString());
    }
}
