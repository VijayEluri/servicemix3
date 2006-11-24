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
package org.apache.servicemix.bean;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.jbi.component.ComponentContext;
import javax.jbi.messaging.DeliveryChannel;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOut;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.messaging.MessageExchange.Role;
import javax.jbi.servicedesc.ServiceEndpoint;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.jexl.Expression;
import org.apache.commons.jexl.ExpressionFactory;
import org.apache.commons.jexl.JexlContext;
import org.apache.commons.jexl.JexlHelper;
import org.apache.servicemix.MessageExchangeListener;
import org.apache.servicemix.bean.support.BeanInfo;
import org.apache.servicemix.bean.support.DefaultMethodInvocationStrategy;
import org.apache.servicemix.bean.support.DestinationImpl;
import org.apache.servicemix.bean.support.Holder;
import org.apache.servicemix.bean.support.MethodInvocationStrategy;
import org.apache.servicemix.bean.support.ReflectionUtils;
import org.apache.servicemix.bean.support.Request;
import org.apache.servicemix.common.EndpointComponentContext;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.expression.JAXPStringXPathExpression;
import org.apache.servicemix.expression.PropertyExpression;
import org.apache.servicemix.jbi.resolver.URIResolver;
import org.apache.servicemix.jbi.util.MessageUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContext;

/**
 * Represents a bean endpoint which consists of a together with a {@link MethodInvocationStrategy}
 * so that JBI message exchanges can be invoked on the bean.
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="endpoint"
 */
public class BeanEndpoint extends ProviderEndpoint implements ApplicationContextAware {

    private ApplicationContext applicationContext;
    private String beanName;
    private Object bean;
    private BeanInfo beanInfo;
    private Class<?> beanType;
    private String beanClassName;
    private MethodInvocationStrategy methodInvocationStrategy;
    private org.apache.servicemix.expression.Expression correlationExpression;
    
    private Map<String, Holder> exchanges = new ConcurrentHashMap<String, Holder>();
    private Map<Object, Request> requests = new ConcurrentHashMap<Object, Request>();
    private ThreadLocal<Request> currentRequest = new ThreadLocal<Request>();
    private ComponentContext context;
    private DeliveryChannel channel;

    public BeanEndpoint() {
    }

    public BeanEndpoint(BeanComponent component, ServiceEndpoint serviceEndpoint) {
        super(component, serviceEndpoint);
        setApplicationContext(component.getApplicationContext());
    }

    public void start() throws Exception {
        super.start();
        context = new EndpointComponentContext(this);
        channel = context.getDeliveryChannel();
        Object pojo = getBean();
        if (pojo != null) {
            injectBean(pojo);
            ReflectionUtils.callLifecycleMethod(pojo, PostConstruct.class);
        }
        beanType = pojo != null ? pojo.getClass() : createBean().getClass();
        if (getMethodInvocationStrategy() == null) {
            throw new IllegalArgumentException("No 'methodInvocationStrategy' property set");
        }
    }


    public void stop() throws Exception {
        super.stop();
        Object pojo = getBean();
        if (pojo != null) {
            ReflectionUtils.callLifecycleMethod(pojo, PreDestroy.class);
        }
    }


    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public Object getBean() {
        return bean;
    }

    public void setBean(Object bean) {
        this.bean = bean;
    }

    /**
     * @return the beanType
     */
    public Class<?> getBeanType() {
        return beanType;
    }

    /**
     * @param beanType the beanType to set
     */
    public void setBeanType(Class<?> beanType) {
        this.beanType = beanType;
    }

    /**
     * @return the beanClassName
     */
    public String getBeanClassName() {
        return beanClassName;
    }

    /**
     * @param beanClassName the beanClassName to set
     */
    public void setBeanClassName(String beanClassName) {
        this.beanClassName = beanClassName;
    }

    public BeanInfo getBeanInfo() {
        if (beanInfo == null) {
            beanInfo = new BeanInfo(beanType, getMethodInvocationStrategy());
        }
        return beanInfo;
    }

    public void setBeanInfo(BeanInfo beanInfo) {
        this.beanInfo = beanInfo;
    }

    public MethodInvocationStrategy getMethodInvocationStrategy() {
        if (methodInvocationStrategy == null) {
            methodInvocationStrategy = createMethodInvocationStrategy();
        }
        return methodInvocationStrategy;
    }

    public void setMethodInvocationStrategy(MethodInvocationStrategy methodInvocationStrategy) {
        this.methodInvocationStrategy = methodInvocationStrategy;
    }


    @Override
    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getRole() == Role.CONSUMER) {
            onConsumerExchange(exchange);
        // Find or create the request for this provider exchange
        } else if (exchange.getRole() == MessageExchange.Role.PROVIDER) {
            onProviderExchange(exchange);
        } else {
            throw new IllegalStateException("Unknown role: " + exchange.getRole());
        }
    }
    
    protected void onProviderExchange(MessageExchange exchange) throws Exception {
        Object corId = getCorrelation(exchange);
        Request req = requests.get(corId);
        if (req == null) {
            Object pojo = getBean();
            if (pojo == null) {
                pojo = createBean();
                injectBean(pojo);
                ReflectionUtils.callLifecycleMethod(pojo, PostConstruct.class);
            }
            req = new Request(pojo, exchange);
            requests.put(corId, req);
        }
        currentRequest.set(req);
        // If the bean implements MessageExchangeListener,
        // just call the method
        if (req.getBean() instanceof MessageExchangeListener) {
            ((MessageExchangeListener) req.getBean()).onMessageExchange(exchange);
        } else {
            // Exchange is finished
            if (exchange.getStatus() == ExchangeStatus.DONE) {
                return;
            }
            // Exchange has been aborted with an exception
            else if (exchange.getStatus() == ExchangeStatus.ERROR) {
                return;
                // Fault message
            } else if (exchange.getFault() != null) {
                // TODO: find a way to send it back to the bean before setting the DONE status
                done(exchange);
            } else {
                MethodInvocation invocation = getMethodInvocationStrategy().createInvocation(req.getBean(), getBeanInfo(), exchange, this);
                if (invocation == null) {
                    throw new UnknownMessageExchangeTypeException(exchange, this);
                }
                try {
                    invocation.proceed();
                } catch (Exception e) {
                    throw e;
                } catch (Throwable throwable) {
                    throw new MethodInvocationFailedException(req.getBean(), invocation, exchange, this, throwable);
                }
                if (exchange.getStatus() == ExchangeStatus.ERROR) {
                    send(exchange);
                }
                if (exchange.getFault() == null && exchange.getMessage("out") == null)  {
                    // TODO: handle MEP correctly (DONE should only be sent for InOnly)
                    done(exchange);
                }
            }
        }
        checkEndOfRequest(req);
        currentRequest.set(null);
    }
    
    protected void onConsumerExchange(MessageExchange exchange) throws Exception {
        Object corId = exchange.getExchangeId();
        Request req = requests.remove(corId);
        if (req == null) {
            throw new IllegalStateException("Receiving unknown consumer exchange: " + exchange);
        }
        currentRequest.set(req);
        // If the bean implements MessageExchangeListener,
        // just call the method
        if (req.getBean() instanceof MessageExchangeListener) {
            ((MessageExchangeListener) req.getBean()).onMessageExchange(exchange);
        } else {
            Holder me = exchanges.get(exchange.getExchangeId());
            if (me == null) {
                throw new IllegalStateException("Consumer exchange not found");
            }
            me.set(exchange);
            evaluateCallbacks(req);
        }
        checkEndOfRequest(req);
        currentRequest.set(null);
    }
    
    protected Object getCorrelation(MessageExchange exchange) throws MessagingException {
        return getCorrelationExpression().evaluate(exchange, exchange.getMessage("in"));
    }

    protected Object createBean() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (beanName == null && beanType == null) {
            throw new IllegalArgumentException("Property 'beanName' has not been set!");
        }
        if (beanType == null && beanClassName != null) {
            beanType = Class.forName(beanClassName, true, getServiceUnit().getConfigurationClassLoader());
        }
        if (beanType != null) {
            return beanType.newInstance();
        } else if (beanName == null) {
            throw new IllegalArgumentException("Property 'beanName', 'beanType' or 'beanClassName' must be set!");
        } else if (applicationContext == null) {
            throw new IllegalArgumentException("Property 'beanName' specified, but no BeanFactory set!");
        } else {
            Object answer = applicationContext.getBean(beanName);
            if (answer == null) {
                throw new NoSuchBeanException(beanName, this);
            }
            return answer;
        }
    }

    protected MethodInvocationStrategy createMethodInvocationStrategy() {
        return new DefaultMethodInvocationStrategy();
    }

    /**
     * A strategy method to allow implementations to perform some custom JBI based injection of the POJO
     *
     * @param bean the bean to be injected
     */
    protected void injectBean(final Object bean) {
        // Inject fields
        ReflectionUtils.doWithFields(bean.getClass(), new ReflectionUtils.FieldCallback() {
            public void doWith(Field f) throws IllegalArgumentException, IllegalAccessException {
                ExchangeTarget et = f.getAnnotation(ExchangeTarget.class);
                if (et != null) {
                    ReflectionUtils.setField(f, bean, new DestinationImpl(et.uri(), BeanEndpoint.this));
                }
                if (f.getAnnotation(Resource.class) != null) {
                    if (ComponentContext.class.isAssignableFrom(f.getType())) {
                        ReflectionUtils.setField(f, bean, context);
                    } else if (DeliveryChannel.class.isAssignableFrom(f.getType())) {
                        ReflectionUtils.setField(f, bean, channel);
                    }
                }
            }
        });
    }
    
    protected void evaluateCallbacks(final Request req) {
        final Object bean = req.getBean();
        ReflectionUtils.doWithMethods(bean.getClass(), new ReflectionUtils.MethodCallback() {
            @SuppressWarnings("unchecked")
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                if (method.getAnnotation(Callback.class) != null) {
                    try {
                        Expression e = ExpressionFactory.createExpression(method.getAnnotation(Callback.class).condition());
                        JexlContext jc = JexlHelper.createContext();
                        jc.getVars().put("this", bean);
                        Object r = e.evaluate(jc);
                        if (r instanceof Boolean == false) {
                            throw new RuntimeException("Expression did not returned a boolean value but: " + r);
                        }
                        Boolean oldVal = req.getCallbacks().get(method);
                        Boolean newVal = (Boolean) r;
                        if ((oldVal == null || oldVal == false) && newVal == true) {
                            req.getCallbacks().put(method, newVal);
                            Object o = method.invoke(bean, new Object[0]);
                            // TODO: handle return value and sent it as the answer
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Unable to invoke callback", e);
                    }
                }
            }
        });
    }
    
    /**
     * Used by POJOs acting as a consumer
     * @param uri
     * @param message
     * @return
     */
    public Future<NormalizedMessage> send(String uri, NormalizedMessage message) {
        try {
            InOut me = getExchangeFactory().createInOutExchange();
            URIResolver.configureExchange(me, getServiceUnit().getComponent().getComponentContext(), uri);
            MessageUtil.transferTo(message, me, "in");
            final Holder h = new Holder(); 
            requests.put(me.getExchangeId(), currentRequest.get());
            exchanges.put(me.getExchangeId(), h);
            BeanEndpoint.this.send(me);
            return h;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    protected void checkEndOfRequest(Request request) {
        if (request.getExchange().getStatus() != ExchangeStatus.ACTIVE) {
            ReflectionUtils.callLifecycleMethod(request.getBean(), PreDestroy.class);
            request.setBean(null);
            request.setExchange(null);
            requests.remove(request);
        }
    }

    /**
     * @return the correlationExpression
     */
    public org.apache.servicemix.expression.Expression getCorrelationExpression() {
        if (correlationExpression == null) {
            // Find correlation expression
            Correlation cor = beanType.getAnnotation(Correlation.class);
            if (cor != null) {
                if (cor.property() != null) {
                    correlationExpression = new PropertyExpression(cor.property());
                } else if (cor.xpath() != null) {
                    correlationExpression = new JAXPStringXPathExpression(cor.xpath());
                }
            }
            if (correlationExpression == null) {
                correlationExpression = new org.apache.servicemix.expression.Expression() {
                    public Object evaluate(MessageExchange exchange, NormalizedMessage message) throws MessagingException {
                        return exchange.getExchangeId();
                    }
                };
            }
        }
        return correlationExpression;
    }

    /**
     * @param correlationExpression the correlationExpression to set
     */
    public void setCorrelationExpression(org.apache.servicemix.expression.Expression correlationExpression) {
        this.correlationExpression = correlationExpression;
    }
}