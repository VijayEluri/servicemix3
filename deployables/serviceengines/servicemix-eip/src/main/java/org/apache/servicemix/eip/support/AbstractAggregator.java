/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.eip.support;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.messaging.RobustInOnly;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.servicemix.eip.EIPEndpoint;
import org.apache.servicemix.jbi.util.MessageUtil;
import org.apache.servicemix.timers.Timer;
import org.apache.servicemix.timers.TimerListener;

/**
 * Aggregator can be used to wait and combine several messages.
 * This component implements the
 * <a href="http://www.enterpriseintegrationpatterns.com/Aggregator.html">Aggregator</a>
 * pattern.
 *
 * TODO: keep list of closed aggregations for a certain time
 * TODO: distributed lock manager
 * TODO: persistent / transactional timer
 *
 * @author gnodet
 * @version $Revision: 376451 $
 */
public abstract class AbstractAggregator extends EIPEndpoint {

    private static final Log LOG = LogFactory.getLog(AbstractAggregator.class);

    private ExchangeTarget target;

    private boolean rescheduleTimeouts;

    private boolean synchronous;

    private ConcurrentMap<String, Boolean> closedAggregates = new ConcurrentHashMap<String, Boolean>();

    private ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<String, Timer>();

    /**
     * @return the synchronous
     */
    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * @param synchronous the synchronous to set
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    /**
     * @return the rescheduleTimeouts
     */
    public boolean isRescheduleTimeouts() {
        return rescheduleTimeouts;
    }

    /**
     * @param rescheduleTimeouts the rescheduleTimeouts to set
     */
    public void setRescheduleTimeouts(boolean rescheduleTimeouts) {
        this.rescheduleTimeouts = rescheduleTimeouts;
    }

    /**
     * @return the target
     */
    public ExchangeTarget getTarget() {
        return target;
    }

    /**
     * @param target the target to set
     */
    public void setTarget(ExchangeTarget target) {
        this.target = target;
    }

    /* (non-Javadoc)
     * @see org.apache.servicemix.eip.EIPEndpoint#processSync(javax.jbi.messaging.MessageExchange)
     */
    protected void processSync(MessageExchange exchange) throws Exception {
        throw new IllegalStateException();
    }

    /* (non-Javadoc)
     * @see org.apache.servicemix.eip.EIPEndpoint#processAsync(javax.jbi.messaging.MessageExchange)
     */
    protected void processAsync(MessageExchange exchange) throws Exception {
        throw new IllegalStateException();
    }

    /* (non-Javadoc)
     * @see org.apache.servicemix.common.ExchangeProcessor#process(javax.jbi.messaging.MessageExchange)
     */
    public void process(MessageExchange exchange) throws Exception {
        // Skip DONE
        if (exchange.getStatus() == ExchangeStatus.DONE) {
            return;
            // Skip ERROR
        } else if (exchange.getStatus() == ExchangeStatus.ERROR) {
            return;
            // Handle an ACTIVE exchange as a PROVIDER
        } else if (exchange.getRole() == MessageExchange.Role.PROVIDER) {
            if (!(exchange instanceof InOnly)
                && !(exchange instanceof RobustInOnly)) {
                fail(exchange, new UnsupportedOperationException("Use an InOnly or RobustInOnly MEP"));
            } else {
                processProvider(exchange);
            }
            // Handle an ACTIVE exchange as a CONSUMER
        } else if (exchange.getStatus() == ExchangeStatus.ACTIVE) {
            done(exchange);
        }
    }

    private void processProvider(MessageExchange exchange) throws Exception {
        NormalizedMessage in = MessageUtil.copyIn(exchange);
        final String correlationId = getCorrelationID(exchange, in);
        if (correlationId == null || correlationId.length() == 0) {
            throw new IllegalArgumentException("Could not retrieve correlation id for incoming exchange");
        }
        // Load existing aggregation
        Lock lock = getLockManager().getLock(correlationId);
        lock.lock();
        try {
            Object aggregation = store.load(correlationId);
            Date timeout = null;
            // Create a new aggregate
            if (aggregation == null) {
                if (isAggregationClosed(correlationId)) {
                    // TODO: should we return an error here ?
                } else {
                    aggregation = createAggregation(correlationId);
                    timeout = getTimeout(aggregation);
                }
            } else if (isRescheduleTimeouts()) {
                Timer t = timers.remove(correlationId);
                if (t != null) {
                    t.cancel();
                }
                timeout = getTimeout(aggregation);
            }
            // If the aggregation is not closed
            if (aggregation != null) {
                if (addMessage(aggregation, in, exchange)) {
                    Timer t = timers.remove(correlationId);
                    if (t != null) {
                        t.cancel();
                    }
                    sendAggregate(correlationId, aggregation, false);
                } else {
                    store.store(correlationId, aggregation);
                    if (timeout != null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Scheduling timeout at " + timeout + " for aggregate " + correlationId);
                        }
                        Timer t = getTimerManager().schedule(new TimerListener() {
                            public void timerExpired(Timer timer) {
                                AbstractAggregator.this.onTimeout(correlationId, timer);
                            }
                        }, timeout);
                        timers.put(correlationId, t);
                    }
                }
            }
            done(exchange);
        } finally {
            lock.unlock();
        }
    }

    protected void sendAggregate(String correlationId,
                                 Object aggregation,
                                 boolean timeout) throws Exception {
        InOnly me = getExchangeFactory().createInOnlyExchange();
        target.configureTarget(me, getContext());
        NormalizedMessage nm = me.createMessage();
        me.setInMessage(nm);
        buildAggregate(aggregation, nm, me, timeout);
        closeAggregation(correlationId);
        if (isSynchronous()) {
            sendSync(me);
        } else {
            send(me);
        }
    }

    protected void onTimeout(String correlationId, Timer timer) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Timeout expired for aggregate " + correlationId);
        }
        Lock lock = getLockManager().getLock(correlationId);
        lock.lock();
        try {
            // the timeout event could have been fired before timer was canceled
            Timer t = getTimer(correlationId);
            if (t == null || !t.equals(timer)) {
                return;
            }
            timers.remove(correlationId);
            Object aggregation = store.load(correlationId);
            if (aggregation != null) {
                sendAggregate(correlationId, aggregation, true);
            } else if (!isAggregationClosed(correlationId)) {
                throw new IllegalStateException("Aggregation is not closed, but can not be retrieved from the store");
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Aggregate " + correlationId + " is closed");
                }
            }
        } catch (Exception e) {
            LOG.info("Caught exception while processing timeout aggregation", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if the aggregation with the given correlation id is closed or not.
     * Called when the aggregation has not been found in the store.
     *
     * @param correlationId
     * @return
     */
    protected boolean isAggregationClosed(String correlationId) {
        // TODO: implement this using a persistent / cached behavior
        return closedAggregates.containsKey(correlationId);
    }

    /**
     * Mark an aggregation as closed
     * @param correlationId
     */
    protected void closeAggregation(String correlationId) {
        // TODO: implement this using a persistent / cached behavior
        closedAggregates.put(correlationId, Boolean.TRUE);
    }
    
    /**
     * Get the time-out timer for an active aggregation
     * 
     * @param correlationId
     * @return 
     */
    protected Timer getTimer(String correlationId) {
        return timers.get(correlationId); 
    }

    /**
     * Retrieve the correlation ID of the given exchange
     * @param exchange
     * @param message
     * @return the correlationID
     * @throws Exception
     */
    protected abstract String getCorrelationID(MessageExchange exchange, NormalizedMessage message) throws Exception;

    /**
     * Creates a new empty aggregation.
     * @param correlationID
     * @return a newly created aggregation
     */
    protected abstract Object createAggregation(String correlationID) throws Exception;

    /**
     * Returns the date when the onTimeout method should be called if the aggregation is not completed yet,
     * or null if the aggregation has no timeout.
     *
     * @param aggregate
     * @return
     */
    protected abstract Date getTimeout(Object aggregate);

    /**
     * Add a newly received message to this aggregation
     *
     * @param aggregate
     * @param message
     * @param exchange
     * @return <code>true</code> if the aggregate id complete
     */
    protected abstract boolean addMessage(Object aggregate,
                                          NormalizedMessage message,
                                          MessageExchange exchange) throws Exception;

    /**
     * Fill the given JBI message with the aggregation result.
     *
     * @param aggregate
     * @param message
     * @param exchange
     * @param timeout <code>false</code> if the aggregation has completed or <code>true</code>
     *                  if this aggregation has timed out
     */
    protected abstract void buildAggregate(Object aggregate,
                                           NormalizedMessage message,
                                           MessageExchange exchange,
                                           boolean timeout) throws Exception;
}
