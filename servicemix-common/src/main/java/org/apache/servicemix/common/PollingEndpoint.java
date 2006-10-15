/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.common;

import java.util.Date;

import javax.jbi.JBIException;
import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.MessagingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.components.varscheduler.ScheduleIterator;
import org.apache.servicemix.components.varscheduler.Scheduler;
import org.apache.servicemix.components.varscheduler.SchedulerTask;
import org.apache.servicemix.executors.Executor;

/**
 * An implementation inheritence class for an endpoint which polls some resource at periodic intervals to decide if
 * there is an event to process.
 *
 * @version $Revision$
 */
public abstract class PollingEndpoint extends ProviderEndpoint {
    private static final Log log = LogFactory.getLog(PollingEndpoint.class);
    private Executor executor;
    private Scheduler scheduler;
    private Date firstTime;
    private long period = 5000;
    private long delay;
    private SchedulerTask schedulerTask;
    private ScheduleIterator scheduleIterator;
    private boolean started;
    private boolean scheduleExecutedFlag;

    /**
     * Polls the underlying resource to see if some event is required
     *
     * @throws JBIException
     */
    public abstract void poll() throws Exception;


    // Properties
    // -------------------------------------------------------------------------
    public Executor getExecutor() {
        return executor;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public Date getFirstTime() {
        return firstTime;
    }

    public void setFirstTime(Date firstTime) {
        this.firstTime = firstTime;
    }

    public long getPeriod() {
        return period;
    }

    public void setPeriod(long period) {
        this.period = period;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public synchronized void start() throws Exception {
        if (!started) {
            started = true;

            if (scheduler == null) {
                scheduler = new Scheduler(true);
            }
            if (scheduleIterator == null) {
                scheduleIterator = new PollingEndpoint.PollScheduleIterator();
            }

            if (executor == null) {
                executor = getServiceUnit().getComponent().getExecutor();
            }
            if (schedulerTask != null) {
                schedulerTask.cancel();
            }
            schedulerTask = new PollingEndpoint.PollSchedulerTask();
            this.scheduler.schedule(schedulerTask, scheduleIterator);
        }
        super.start();
    }

    public synchronized void stop() throws Exception {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
        scheduleExecutedFlag = false;
        started = false;
        super.stop();
    }

    public void deactivate() throws Exception {
        scheduler.cancel();
        executor.shutdown();
        scheduler = null;
        scheduleIterator = null;
        executor = null;
        super.deactivate();
    }


    public DeliveryChannel getDeliveryChannel() throws MessagingException {
        return getServiceUnit().getComponent().getComponentContext().getDeliveryChannel();
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    private class PollSchedulerTask extends SchedulerTask {
        public void run() {
            try {
                // lets run the work inside the JCA worker pools to ensure
                // the threads are setup correctly when we actually do stuff
                getExecutor().execute(new Runnable() {
                    public void run() {
                        try {
                            poll();
                        }
                        catch (Exception e) {
                            handlePollException(e);
                        }
                    }
                });
            }
            catch (Throwable e) {
                log.error("Failed to schedule work: " + e, e);
            }
        }
    }

    protected void handlePollException(Exception e) {
        log.error("Caught exception while polling: " + e, e);
    }


    private class PollScheduleIterator implements ScheduleIterator {
        public Date nextExecution() {
            long nextTime = System.currentTimeMillis();
            if (scheduleExecutedFlag) {
                nextTime += period;
            }
            else {
                if (firstTime != null) {
                    nextTime = firstTime.getTime();
                }
                nextTime += delay;
                scheduleExecutedFlag = true;
            }
            return (started) ? new Date(nextTime) : null;
        }
    }
}
