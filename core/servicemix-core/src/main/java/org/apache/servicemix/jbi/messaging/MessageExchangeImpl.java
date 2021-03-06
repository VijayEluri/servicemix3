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
package org.apache.servicemix.jbi.messaging;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URI;
import java.util.Comparator;
import java.util.Set;

import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.Fault;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.transaction.Transaction;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Node;

import org.apache.commons.lang.ObjectUtils;

import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.jbi.container.ActivationSpec;
import org.apache.servicemix.jbi.framework.ComponentContextImpl;
import org.apache.servicemix.jbi.framework.ComponentNameSpace;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple message exchange declaration. This is partial, just giving us enough
 * ME function for the doodle. This doesn't add anything new to the current
 * MessageExchange definition.
 * 
 * @version $Revision$
 */
public abstract class MessageExchangeImpl implements MessageExchange, Externalizable {

    public static final String IN = "in";

    public static final String OUT = "out";

    public static final String FAULT = "fault";

    public static final int MAX_MSG_DISPLAY_SIZE = Integer.MAX_VALUE;

    public static final boolean PRESERVE_CONTENT = Boolean.getBoolean("org.apache.servicemix.preserveContent");

    public static final int SYNC_STATE_ASYNC = 0;

    public static final int SYNC_STATE_SYNC_SENT = 1;

    public static final int SYNC_STATE_SYNC_RECEIVED = 2;

    /**
     * Exchange is not transactional
     */
    public static final int TX_STATE_NONE = 0;

    /**
     * Exchange has been enlisted in the current transaction. This means that
     * the transaction must be commited for the exchange to be delivered.
     */
    public static final int TX_STATE_ENLISTED = 1;

    /**
     * Transaction is being conveyed by the exchange. The transaction context
     * will be given to the target component.
     */
    public static final int TX_STATE_CONVEYED = 2;

    protected static final int CAN_SET_IN_MSG = 0x00000001;

    protected static final int CAN_SET_OUT_MSG = 0x00000002;

    protected static final int CAN_SET_FAULT_MSG = 0x00000004;

    protected static final int CAN_PROVIDER = 0x00000008;

    protected static final int CAN_CONSUMER = 0x00000000;

    protected static final int CAN_SEND = 0x00000010;

    protected static final int CAN_STATUS_ACTIVE = 0x00000040;

    protected static final int CAN_STATUS_DONE = 0x00000080;

    protected static final int CAN_STATUS_ERROR = 0x00000100;

    protected static final int CAN_OWNER = 0x00000200;

    protected static final int STATES_CANS = 0;

    protected static final int STATES_NEXT_OUT = 1;

    protected static final int STATES_NEXT_FAULT = 2;

    protected static final int STATES_NEXT_ERROR = 3;

    protected static final int STATES_NEXT_DONE = 4;

    private static final long serialVersionUID = -3639175136897005605L;

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageExchangeImpl.class);

    protected ComponentContextImpl sourceContext;

    protected ExchangePacket packet;

    protected PojoMarshaler marshaler;

    protected int state;

    protected int syncState = SYNC_STATE_ASYNC;

    protected int txState = TX_STATE_NONE;

    protected int[][] states;

    protected MessageExchangeImpl mirror;

    protected transient boolean pushDeliver;

    protected transient Object txLock;

    protected transient String key;

    /**
     * Constructor
     * 
     * @param exchangeId
     * @param pattern
     */
    public MessageExchangeImpl(String exchangeId, URI pattern, int[][] states) {
        this.states = states;
        this.packet = new ExchangePacket();
        this.packet.setExchangeId(exchangeId);
        this.packet.setPattern(pattern);
    }

    protected MessageExchangeImpl(ExchangePacket packet, int[][] states) {
        this.states = states;
        this.packet = packet;
    }

    protected MessageExchangeImpl() {
    }

    protected void copyFrom(MessageExchangeImpl me) {
        if (this != me) {
            this.packet = me.packet;
            this.state = me.state;
            this.mirror.packet = me.packet;
            this.mirror.state = me.mirror.state;
        }
    }

    protected boolean can(int c) {
        return (this.states[state][STATES_CANS] & c) == c;
    }

    /**
     * Returns the activation spec that was provided when the component was
     * registered
     * 
     * @return the spec
     */
    public ActivationSpec getActivationSpec() {
        if (sourceContext != null) {
            return sourceContext.getActivationSpec();
        }
        return null;
    }

    /**
     * Returns the context which created the message exchange which can then be
     * used for routing
     * 
     * @return the context
     */
    public ComponentContextImpl getSourceContext() {
        return sourceContext;
    }

    /**
     * Set the context
     * 
     * @param sourceContext
     */
    public void setSourceContext(ComponentContextImpl sourceContext) {
        this.sourceContext = sourceContext;
        this.mirror.sourceContext = sourceContext;
    }

    /**
     * @return the packet
     */
    public ExchangePacket getPacket() {
        return packet;
    }

    /**
     * @return URI of pattern exchange
     */
    public URI getPattern() {
        return packet.getPattern();
    }

    /**
     * @return the exchange Id
     */
    public String getExchangeId() {
        return packet.getExchangeId();
    }

    /**
     * @return the processing status of the exchange
     */
    public ExchangeStatus getStatus() {
        if (this.packet.isAborted()) {
            return ExchangeStatus.ERROR;
        }
        return this.packet.getStatus();
    }

    /**
     * set the processing status
     * 
     * @param exchangeStatus
     * @throws MessagingException
     */
    public void setStatus(ExchangeStatus exchangeStatus) throws MessagingException {
        if (!can(CAN_OWNER)) {
            throw new IllegalStateException("component is not owner");
        }
        this.packet.setStatus(exchangeStatus);

    }

    /**
     * set the source of a failure
     * 
     * @param exception
     */
    public void setError(Exception exception) {
        if (!can(CAN_OWNER)) {
            throw new IllegalStateException("component is not owner when trying to set error: " + exception, exception);
        }
        this.packet.setError(exception);
    }

    /**
     * @return the exception describing a processing error
     */
    public Exception getError() {
        return packet.getError();
    }

    /**
     * @return the fault message for an exchange
     */
    public Fault getFault() {
        return packet.getFault();
    }

    /**
     * set the fault message for the exchange
     * 
     * @param fault
     * @throws MessagingException
     */
    public void setFault(Fault fault) throws MessagingException {
        setMessage(fault, FAULT);
    }

    /**
     * @return a new message
     * @throws MessagingException
     */
    public NormalizedMessage createMessage() throws MessagingException {
        return new NormalizedMessageImpl(this);
    }

    /**
     * factory method for fault objects
     * 
     * @return a new fault
     * @throws MessagingException
     */
    public Fault createFault() throws MessagingException {
        return new FaultImpl();
    }

    /**
     * get a NormalizedMessage based on the message reference
     * 
     * @param name
     * @return a NormalizedMessage
     */
    public NormalizedMessage getMessage(String name) {
        if (IN.equals(name)) {
            return packet.getIn();
        } else if (OUT.equals(name)) {
            return packet.getOut();
        } else if (FAULT.equals(name)) {
            return packet.getFault();
        } else {
            return null;
        }
    }

    /**
     * set a NormalizedMessage with a named reference
     * 
     * @param message
     * @param name
     * @throws MessagingException
     */
    public void setMessage(NormalizedMessage message, String name) throws MessagingException {
        if (!can(CAN_OWNER)) {
            throw new IllegalStateException("component is not owner");
        }
        if (message == null) {
            throw new IllegalArgumentException("message should not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name should not be null");
        }
        if (IN.equalsIgnoreCase(name)) {
            if (!can(CAN_SET_IN_MSG)) {
                throw new MessagingException("In not supported");
            }
            if (packet.getIn() != null) {
                throw new MessagingException("In message is already set");
            }
            ((NormalizedMessageImpl) message).exchange = this;
            packet.setIn((NormalizedMessageImpl) message);
        } else if (OUT.equalsIgnoreCase(name)) {
            if (!can(CAN_SET_OUT_MSG)) {
                throw new MessagingException("Out not supported");
            }
            if (packet.getOut() != null) {
                throw new MessagingException("Out message is already set");
            }
            ((NormalizedMessageImpl) message).exchange = this;
            packet.setOut((NormalizedMessageImpl) message);
        } else if (FAULT.equalsIgnoreCase(name)) {
            if (!can(CAN_SET_FAULT_MSG)) {
                throw new MessagingException("Fault not supported");
            }
            if (!(message instanceof Fault)) {
                throw new MessagingException("Setting fault, but message is not a fault");
            }
            if (packet.getFault() != null) {
                throw new MessagingException("Fault message is already set");
            }
            ((NormalizedMessageImpl) message).exchange = this;
            packet.setFault((FaultImpl) message);
        } else {
            throw new MessagingException("Message name must be in, out or fault");
        }
    }

    /**
     * @param name
     * @return the property from the exchange
     */
    public Object getProperty(String name) {
        if (JTA_TRANSACTION_PROPERTY_NAME.equals(name)) {
            return packet.getTransactionContext();
        } else if (JbiConstants.PERSISTENT_PROPERTY_NAME.equals(name)) {
            return packet.getPersistent();
        } else {
            return packet.getProperty(name);
        }
    }

    /**
     * set a named property on the exchange
     * 
     * @param name
     * @param value
     */
    public void setProperty(String name, Object value) {
        if (!can(CAN_OWNER)) {
            throw new IllegalStateException("component is not owner");
        }
        if (name == null) {
            throw new IllegalArgumentException("name should not be null");
        }
        if (JTA_TRANSACTION_PROPERTY_NAME.equals(name)) {
            packet.setTransactionContext((Transaction) value);
        } else if (JbiConstants.PERSISTENT_PROPERTY_NAME.equals(name)) {
            packet.setPersistent((Boolean) value);
        } else {
            packet.setProperty(name, value);
        }
    }

    /**
     * @return property names
     */
    public Set getPropertyNames() {
        return packet.getPropertyNames();
    }

    /**
     * Set an endpoint
     * 
     * @param endpoint
     */
    public void setEndpoint(ServiceEndpoint endpoint) {
        packet.setEndpoint(endpoint);
    }

    /**
     * set a service
     * 
     * @param name
     */
    public void setService(QName name) {
        packet.setServiceName(name);
    }

    /**
     * set an operation
     * 
     * @param name
     */
    public void setOperation(QName name) {
        packet.setOperationName(name);
    }

    /**
     * set an interface
     * 
     * @param name
     */
    public void setInterfaceName(QName name) {
        packet.setInterfaceName(name);
    }

    /**
     * @return the endpoint
     */
    public ServiceEndpoint getEndpoint() {
        return packet.getEndpoint();
    }

    /**
     * @return the service
     */
    public QName getService() {
        return packet.getServiceName();
    }

    /**
     * @return the interface name
     */
    public QName getInterfaceName() {
        return packet.getInterfaceName();
    }

    /**
     * @return the operation
     */
    public QName getOperation() {
        return packet.getOperationName();
    }

    /**
     * @return the transaction context
     */
    public Transaction getTransactionContext() {
        return packet.getTransactionContext();
    }

    /**
     * set the transaction
     * 
     * @param transaction
     * @throws MessagingException
     */
    public void setTransactionContext(Transaction transaction) throws MessagingException {
        packet.setTransactionContext(transaction);
    }

    /**
     * @return true if transacted
     */
    public boolean isTransacted() {
        return this.packet.getTransactionContext() != null;
    }

    /**
     * @return the Role of this exchange
     */
    public Role getRole() {
        return can(CAN_PROVIDER) ? Role.PROVIDER : Role.CONSUMER;
    }

    /**
     * @return the in message
     */
    public NormalizedMessage getInMessage() {
        return this.packet.getIn();
    }

    /**
     * set the in message
     * 
     * @param message
     * @throws MessagingException
     */
    public void setInMessage(NormalizedMessage message) throws MessagingException {
        setMessage(message, IN);
    }

    /**
     * @return the out message
     */
    public NormalizedMessage getOutMessage() {
        return getMessage(OUT);
    }

    /**
     * set the out message
     * 
     * @param message
     * @throws MessagingException
     */
    public void setOutMessage(NormalizedMessage message) throws MessagingException {
        setMessage(message, OUT);
    }

    /**
     * @return Returns the sourceId.
     */
    public ComponentNameSpace getSourceId() {
        return packet.getSourceId();
    }

    /**
     * @param sourceId
     *            The sourceId to set.
     */
    public void setSourceId(ComponentNameSpace sourceId) {
        packet.setSourceId(sourceId);
    }

    /**
     * @return Returns the destinationId.
     */
    public ComponentNameSpace getDestinationId() {
        return packet.getDestinationId();
    }

    /**
     * @param destinationId
     *            The destinationId to set.
     */
    public void setDestinationId(ComponentNameSpace destinationId) {
        packet.setDestinationId(destinationId);
    }

    public Boolean getPersistent() {
        return packet.getPersistent();
    }

    public void setPersistent(Boolean persistent) {
        packet.setPersistent(persistent);
    }

    public PojoMarshaler getMarshaler() {
        if (marshaler == null) {
            marshaler = new DefaultMarshaler();
        }
        return marshaler;
    }

    public void setMarshaler(PojoMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public abstract void readExternal(ObjectInput in) throws IOException, ClassNotFoundException;

    public void writeExternal(ObjectOutput out) throws IOException {
        packet.writeExternal(out);
        out.write(state);
        out.write(mirror.state);
        out.writeBoolean(can(CAN_PROVIDER));
    }

    public void handleSend(boolean sync) throws MessagingException {
        // Check if send / sendSync is legal
        if (!can(CAN_SEND)) {
            throw new MessagingException("illegal call to send / sendSync");
        }
        if (sync && getStatus() != ExchangeStatus.ACTIVE) {
            throw new MessagingException("illegal call to sendSync");
        }
        this.syncState = sync ? SYNC_STATE_SYNC_SENT : SYNC_STATE_ASYNC;
        // Check status
        ExchangeStatus status = getStatus();
        if (status == ExchangeStatus.ACTIVE && !can(CAN_STATUS_ACTIVE)) {
            throw new MessagingException("illegal exchange status: active");
        }
        if (status == ExchangeStatus.DONE && !can(CAN_STATUS_DONE)) {
            throw new MessagingException("illegal exchange status: done");
        }
        if (status == ExchangeStatus.ERROR && !can(CAN_STATUS_ERROR)) {
            throw new MessagingException("illegal exchange status: error");
        }
        // Check message
        // Change state
        if (status == ExchangeStatus.ACTIVE && packet.getFault() == null) {
            this.state = this.states[this.state][STATES_NEXT_OUT];
        } else if (status == ExchangeStatus.ACTIVE && packet.getFault() != null) {
            this.state = this.states[this.state][STATES_NEXT_FAULT];
        } else if (status == ExchangeStatus.ERROR) {
            this.state = this.states[this.state][STATES_NEXT_ERROR];
        } else if (status == ExchangeStatus.DONE) {
            this.state = this.states[this.state][STATES_NEXT_DONE];
        } else {
            throw new IllegalStateException("unknown status");
        }
        if (this.state < 0 || this.state >= this.states.length) {
            throw new IllegalStateException("next state is illegal");
        }
    }

    public void handleAccept() throws MessagingException {
        // Change state
        ExchangeStatus status = getStatus();
        int nextState;
        if (status == ExchangeStatus.ACTIVE && packet.getFault() == null) {
            nextState = this.states[this.state][STATES_NEXT_OUT];
        } else if (status == ExchangeStatus.ACTIVE && packet.getFault() != null) {
            nextState = this.states[this.state][STATES_NEXT_FAULT];
        } else if (status == ExchangeStatus.ERROR) {
            nextState = this.states[this.state][STATES_NEXT_ERROR];
        } else if (status == ExchangeStatus.DONE) {
            nextState = this.states[this.state][STATES_NEXT_DONE];
        } else {
            throw new IllegalStateException("unknown status");
        }
        if (nextState < 0 || nextState >= this.states.length) {
            throw new IllegalStateException("next state is illegal");
        }
        this.state = nextState;
    }

    public MessageExchangeImpl getMirror() {
        return mirror;
    }

    public int getSyncState() {
        return syncState;
    }

    public void setSyncState(int syncState) {
        this.syncState = syncState;
    }

    /**
     * @return the txState
     */
    public int getTxState() {
        return txState;
    }

    /**
     * @param txState
     *            the txState to set
     */
    public void setTxState(int txState) {
        this.txState = txState;
    }

    public boolean isPushDelivery() {
        return this.pushDeliver;
    }

    public void setPushDeliver(boolean b) {
        this.pushDeliver = true;
    }

    /**
     * @return the txLock
     */
    public Object getTxLock() {
        return txLock;
    }

    /**
     * @param txLock
     *            the txLock to set
     */
    public void setTxLock(Object txLock) {
        this.txLock = txLock;
    }

    @Override
    public String toString() {
        try {
            StringBuffer sb = new StringBuffer();
            String name = getClass().getName();
            name = name.substring(name.lastIndexOf('.') + 1, name.length() - 4);
            sb.append(name);
            sb.append("[\n");
            sb.append("  id: ").append(getExchangeId()).append('\n');
            sb.append("  status: ").append(getStatus()).append('\n');
            sb.append("  role: ").append(getRole() == Role.CONSUMER ? "consumer" : "provider").append('\n');
            sb.append("  mep: ").append(getPattern()).append('\n');
            if (getInterfaceName() != null) {
                sb.append("  interface: ").append(getInterfaceName()).append('\n');
            }
            if (getService() != null) {
                sb.append("  service: ").append(getService()).append('\n');
            }
            if (getEndpoint() != null) {
                sb.append("  endpoint: ").append(getEndpoint().getEndpointName()).append('\n');
            }
            if (getOperation() != null) {
                sb.append("  operation: ").append(getOperation()).append('\n');
            }
            if (getPropertyNames().size() > 0) {
                sb.append("  properties: [").append('\n');
                for (Object propName : getPropertyNames()) {
                    sb.append("    ").append(propName).append(" = ");
                    sb.append(getProperty((String)propName));
                    sb.append('\n');
                }
                sb.append("  ]").append('\n');
            }
            SourceTransformer st = new SourceTransformer();
            display("in", sb, st);
            display("out", sb, st);
            display("fault", sb, st);
            if (getError() != null) {
                sb.append("  error: ");
                sb.append(getError());
                sb.append('\n');
            }
            sb.append("]");
            return sb.toString();
        } catch (Exception e) {
            LOGGER.trace("Error caught in toString", e);
            return super.toString();
        }
    }

    private void display(String msg, StringBuffer sb, SourceTransformer st) {
        NormalizedMessage message = getMessage(msg);
        if (message != null) {
            sb.append("  ").append(msg).append(": ");
            try {
                Set<?> propertyNames = message.getPropertyNames();
                if (propertyNames.size() > 0) {
                    sb.append("\n  ").append(msg).append(" properties: [").append('\n');
                    for (Object propertyName : propertyNames) {
                        sb.append("    ").append(propertyName).append(" = ");
                        Object propertyValue = message.getProperty((String) propertyName);
                        if (propertyValue != null && propertyValue.getClass().getName().startsWith("java.lang")) {
                            sb.append(propertyValue);
                        } else {
                            sb.append(ObjectUtils.identityToString(propertyValue));
                        }
                        sb.append('\n');
                    }
                    sb.append("  ]").append('\n');
                }
                Source content = message.getContent();
                if (content != null) {
                    if (PRESERVE_CONTENT) {
                        sb.append(content.getClass());
                    } else {
                        Node node = st.toDOMNode(content);
                        message.setContent(new DOMSource(node));
                        String str = st.toString(node);
                        if (str.length() > MAX_MSG_DISPLAY_SIZE) {
                            sb.append(str.substring(0, MAX_MSG_DISPLAY_SIZE)).append("...");
                        } else {
                            sb.append(str);
                        }
                    }
                } else {
                    sb.append("null");
                }
            } catch (Exception e) {
                sb.append("Unable to display: ").append(e);
            }
            sb.append('\n');
        }
    }

    /**
     * Compute a unique key for this exchange proxy. It has to be different for
     * the two sides of the exchange, so we include the role + the exchange id.
     * Obviously, it works, because the role never change for a given proxy.
     * 
     * @return
     */
    public String getKey() {
        if (key == null) {
            key = (getRole() == Role.CONSUMER ? "consumer:" : "provider:") + getExchangeId();
        }
        return key;
    }

    /**
     * Comparator that can be used to sort exchanges according to their "age" in
     * their processing: i.e.: a newly created exchange will be more than a DONE
     * exchange ... If the arguments are not instances of MessageExchangeImpl,
     * returns 0.
     */
    public static class AgeComparator implements Comparator<MessageExchangeImpl> {
        public int compare(MessageExchangeImpl m0, MessageExchangeImpl m1) {
            int i0 = (m0.state * 4) / (m0.states.length - 1);
            int i1 = (m1.state * 4) / (m1.states.length - 1);
            if (i0 < i1) {
                return +1;
            } else if (i0 == i1) {
                return 0;
            } else {
                return -1;
            }
        }
    }

}
