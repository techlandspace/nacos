/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.client.naming;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.FuzzyWatchEventWatcher;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.selector.NamingContext;
import com.alibaba.nacos.api.naming.selector.NamingResult;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.cache.NamingFuzzyWatchContext;
import com.alibaba.nacos.client.naming.cache.NamingFuzzyWatchServiceListHolder;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.core.Balancer;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.event.InstancesDiff;
import com.alibaba.nacos.client.naming.event.NamingFuzzyWatchNotifyEvent;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.client.naming.remote.NamingClientProxyDelegate;
import com.alibaba.nacos.client.naming.selector.NamingSelectorFactory;
import com.alibaba.nacos.client.naming.selector.NamingSelectorWrapper;
import com.alibaba.nacos.client.naming.selector.ServiceInfoContext;
import com.alibaba.nacos.client.naming.utils.InitUtils;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import com.alibaba.nacos.client.utils.ClientBasicParamUtil;
import com.alibaba.nacos.client.utils.PreInitUtils;
import com.alibaba.nacos.client.utils.ValidatorUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Future;

import static com.alibaba.nacos.api.common.Constants.ANY_PATTERN;
import static com.alibaba.nacos.client.naming.selector.NamingSelectorFactory.getUniqueClusterString;
import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Nacos Naming Service.
 *
 * @author nkorange
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosNamingService implements NamingService {
    
    private static final String DEFAULT_NAMING_LOG_FILE_PATH = "naming.log";
    
    private static final String UP = "UP";
    
    private static final String DOWN = "DOWN";
    
    /**
     * Each Naming service should have different namespace.
     */
    private String namespace;
    
    @Deprecated
    private String logName;
    
    private ServiceInfoHolder serviceInfoHolder;
    
    private NamingFuzzyWatchServiceListHolder namingFuzzyWatchServiceListHolder;
    
    private InstancesChangeNotifier changeNotifier;
    
    private NamingClientProxy clientProxy;
    
    private String notifierEventScope;
    
    public NacosNamingService(String serverList) throws NacosException {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverList);
        init(properties);
    }
    
    public NacosNamingService(Properties properties) throws NacosException {
        init(properties);
    }
    
    private void init(Properties properties) throws NacosException {
        PreInitUtils.asyncPreLoadCostComponent();
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        NAMING_LOGGER.info(ClientBasicParamUtil.getInputParameters(nacosClientProperties.asProperties()));
        ValidatorUtils.checkInitParam(nacosClientProperties);
        this.namespace = InitUtils.initNamespaceForNaming(nacosClientProperties);
        InitUtils.initSerialization();
        InitUtils.initWebRootContext(nacosClientProperties);
        initLogName(nacosClientProperties);
        
        this.notifierEventScope = UUID.randomUUID().toString();
        this.changeNotifier = new InstancesChangeNotifier(this.notifierEventScope);
        NotifyCenter.registerToPublisher(InstancesChangeEvent.class, 16384);
        NotifyCenter.registerSubscriber(changeNotifier);
        this.serviceInfoHolder = new ServiceInfoHolder(namespace, this.notifierEventScope, nacosClientProperties);
        
        NotifyCenter.registerToPublisher(NamingFuzzyWatchNotifyEvent.class, 16384);
        this.namingFuzzyWatchServiceListHolder = new NamingFuzzyWatchServiceListHolder(this.notifierEventScope);
        
        this.clientProxy = new NamingClientProxyDelegate(this.namespace, serviceInfoHolder, nacosClientProperties,
                changeNotifier, namingFuzzyWatchServiceListHolder);
    }
    
    @Deprecated
    private void initLogName(NacosClientProperties properties) {
        logName = properties.getProperty(UtilAndComs.NACOS_NAMING_LOG_NAME, DEFAULT_NAMING_LOG_FILE_PATH);
    }
    
    @Override
    public void registerInstance(String serviceName, String ip, int port) throws NacosException {
        registerInstance(serviceName, ip, port, Constants.DEFAULT_CLUSTER_NAME);
    }
    
    @Override
    public void registerInstance(String serviceName, String groupName, String ip, int port) throws NacosException {
        registerInstance(serviceName, groupName, ip, port, Constants.DEFAULT_CLUSTER_NAME);
    }
    
    @Override
    public void registerInstance(String serviceName, String ip, int port, String clusterName) throws NacosException {
        registerInstance(serviceName, Constants.DEFAULT_GROUP, ip, port, clusterName);
    }
    
    @Override
    public void registerInstance(String serviceName, String groupName, String ip, int port, String clusterName)
            throws NacosException {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setWeight(1.0);
        instance.setClusterName(clusterName);
        registerInstance(serviceName, groupName, instance);
    }
    
    @Override
    public void registerInstance(String serviceName, Instance instance) throws NacosException {
        registerInstance(serviceName, Constants.DEFAULT_GROUP, instance);
    }
    
    @Override
    public void registerInstance(String serviceName, String groupName, Instance instance) throws NacosException {
        NamingUtils.checkInstanceIsLegal(instance);
        checkAndStripGroupNamePrefix(instance, groupName);
        clientProxy.registerService(serviceName, groupName, instance);
    }
    
    @Override
    public void batchRegisterInstance(String serviceName, String groupName, List<Instance> instances)
            throws NacosException {
        NamingUtils.batchCheckInstanceIsLegal(instances);
        batchCheckAndStripGroupNamePrefix(instances, groupName);
        clientProxy.batchRegisterService(serviceName, groupName, instances);
    }
    
    @Override
    public void batchDeregisterInstance(String serviceName, String groupName, List<Instance> instances)
            throws NacosException {
        NamingUtils.batchCheckInstanceIsLegal(instances);
        batchCheckAndStripGroupNamePrefix(instances, groupName);
        clientProxy.batchDeregisterService(serviceName, groupName, instances);
    }
    
    @Override
    public void deregisterInstance(String serviceName, String ip, int port) throws NacosException {
        deregisterInstance(serviceName, ip, port, Constants.DEFAULT_CLUSTER_NAME);
    }
    
    @Override
    public void deregisterInstance(String serviceName, String groupName, String ip, int port) throws NacosException {
        deregisterInstance(serviceName, groupName, ip, port, Constants.DEFAULT_CLUSTER_NAME);
    }
    
    @Override
    public void deregisterInstance(String serviceName, String ip, int port, String clusterName) throws NacosException {
        deregisterInstance(serviceName, Constants.DEFAULT_GROUP, ip, port, clusterName);
    }
    
    @Override
    public void deregisterInstance(String serviceName, String groupName, String ip, int port, String clusterName)
            throws NacosException {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setClusterName(clusterName);
        deregisterInstance(serviceName, groupName, instance);
    }
    
    @Override
    public void deregisterInstance(String serviceName, Instance instance) throws NacosException {
        deregisterInstance(serviceName, Constants.DEFAULT_GROUP, instance);
    }
    
    @Override
    public void deregisterInstance(String serviceName, String groupName, Instance instance) throws NacosException {
        NamingUtils.checkInstanceIsLegal(instance);
        checkAndStripGroupNamePrefix(instance, groupName);
        clientProxy.deregisterService(serviceName, groupName, instance);
    }
    
    @Override
    public List<Instance> getAllInstances(String serviceName) throws NacosException {
        return getAllInstances(serviceName, new ArrayList<>());
    }
    
    @Override
    public List<Instance> getAllInstances(String serviceName, String groupName) throws NacosException {
        return getAllInstances(serviceName, groupName, new ArrayList<>());
    }
    
    @Override
    public List<Instance> getAllInstances(String serviceName, boolean subscribe) throws NacosException {
        return getAllInstances(serviceName, new ArrayList<>(), subscribe);
    }
    
    @Override
    public List<Instance> getAllInstances(String serviceName, String groupName, boolean subscribe)
            throws NacosException {
        return getAllInstances(serviceName, groupName, new ArrayList<>(), subscribe);
    }
    
    @Override
    public List<Instance> getAllInstances(String serviceName, List<String> clusters) throws NacosException {
        return getAllInstances(serviceName, clusters, true);
    }
    
    @Override
    public List<Instance> getAllInstances(String serviceName, String groupName, List<String> clusters)
            throws NacosException {
        return getAllInstances(serviceName, groupName, clusters, true);
    }
    
    @Override
    public List<Instance> getAllInstances(String serviceName, List<String> clusters, boolean subscribe)
            throws NacosException {
        return getAllInstances(serviceName, Constants.DEFAULT_GROUP, clusters, subscribe);
    }
    
    @Override
    public List<Instance> getAllInstances(String serviceName, String groupName, List<String> clusters,
            boolean subscribe) throws NacosException {
        List<Instance> list;
        ServiceInfo serviceInfo = getServiceInfo(serviceName, groupName, clusters, subscribe);
        if (serviceInfo == null || CollectionUtils.isEmpty(list = serviceInfo.getHosts())) {
            return new ArrayList<>();
        }
        return list;
    }
    
    @Override
    public List<Instance> selectInstances(String serviceName, boolean healthy) throws NacosException {
        return selectInstances(serviceName, new ArrayList<>(), healthy);
    }
    
    @Override
    public List<Instance> selectInstances(String serviceName, String groupName, boolean healthy) throws NacosException {
        return selectInstances(serviceName, groupName, healthy, true);
    }
    
    @Override
    public List<Instance> selectInstances(String serviceName, boolean healthy, boolean subscribe)
            throws NacosException {
        return selectInstances(serviceName, new ArrayList<>(), healthy, subscribe);
    }
    
    @Override
    public List<Instance> selectInstances(String serviceName, String groupName, boolean healthy, boolean subscribe)
            throws NacosException {
        return selectInstances(serviceName, groupName, new ArrayList<>(), healthy, subscribe);
    }
    
    @Override
    public List<Instance> selectInstances(String serviceName, List<String> clusters, boolean healthy)
            throws NacosException {
        return selectInstances(serviceName, clusters, healthy, true);
    }
    
    @Override
    public List<Instance> selectInstances(String serviceName, String groupName, List<String> clusters, boolean healthy)
            throws NacosException {
        return selectInstances(serviceName, groupName, clusters, healthy, true);
    }
    
    @Override
    public List<Instance> selectInstances(String serviceName, List<String> clusters, boolean healthy, boolean subscribe)
            throws NacosException {
        return selectInstances(serviceName, Constants.DEFAULT_GROUP, clusters, healthy, subscribe);
    }
    
    @Override
    public List<Instance> selectInstances(String serviceName, String groupName, List<String> clusters, boolean healthy,
            boolean subscribe) throws NacosException {
        ServiceInfo serviceInfo = getServiceInfo(serviceName, groupName, clusters, subscribe);
        return selectInstances(serviceInfo, healthy);
    }
    
    private List<Instance> selectInstances(ServiceInfo serviceInfo, boolean healthy) {
        List<Instance> list;
        if (serviceInfo == null || CollectionUtils.isEmpty(list = serviceInfo.getHosts())) {
            return new ArrayList<>();
        }
        
        Iterator<Instance> iterator = list.iterator();
        while (iterator.hasNext()) {
            Instance instance = iterator.next();
            if (healthy != instance.isHealthy() || !instance.isEnabled() || instance.getWeight() <= 0) {
                iterator.remove();
            }
        }
        
        return list;
    }
    
    private ServiceInfo getServiceInfo(String serviceName, String groupName, List<String> clusters, boolean subscribe)
            throws NacosException {
        ServiceInfo serviceInfo;
        NamingSelector clusterSelector = NamingSelectorFactory.newClusterSelector(clusters);
        if (serviceInfoHolder.isFailoverSwitch()) {
            serviceInfo = getServiceInfoByFailover(serviceName, groupName, clusterSelector);
            if (serviceInfo != null && !serviceInfo.getHosts().isEmpty()) {
                NAMING_LOGGER.debug("getServiceInfo from failover,serviceName: {}  data:{}", serviceName,
                        JacksonUtils.toJson(serviceInfo.getHosts()));
                return serviceInfo;
            }
        }
        serviceInfo = getServiceInfoBySubscribe(serviceName, groupName, clusters, clusterSelector, subscribe);
        return serviceInfo;
    }
    
    private ServiceInfo getServiceInfoByFailover(String serviceName, String groupName, NamingSelector clusterSelector) {
        ServiceInfo result = serviceInfoHolder.getFailoverServiceInfo(serviceName, groupName);
        return doSelectInstance(result, clusterSelector);
    }
    
    private ServiceInfo getServiceInfoBySubscribe(String serviceName, String groupName, List<String> clusters,
            NamingSelector selector, boolean subscribe) throws NacosException {
        ServiceInfo serviceInfo;
        if (subscribe) {
            serviceInfo = serviceInfoHolder.getServiceInfo(serviceName, groupName);
            serviceInfo = tryToSubscribe(serviceName, groupName, serviceInfo);
            serviceInfo = doSelectInstance(serviceInfo, selector);
        } else {
            String clusterString = NamingSelectorFactory.getUniqueClusterString(clusters);
            serviceInfo = clientProxy.queryInstancesOfService(serviceName, groupName, clusterString, false);
        }
        return serviceInfo;
    }
    
    private ServiceInfo tryToSubscribe(String serviceName, String groupName, ServiceInfo cachedServiceInfo)
            throws NacosException {
        // not found in cache, service never subscribed.
        if (null == cachedServiceInfo) {
            return clientProxy.subscribe(serviceName, groupName, StringUtils.EMPTY);
        }
        // found in cache, and subscribed.
        if (clientProxy.isSubscribed(serviceName, groupName, StringUtils.EMPTY)) {
            return cachedServiceInfo;
        }
        // found in cached, but not subscribed, such as cached from local file when starting.
        ServiceInfo result = cachedServiceInfo;
        try {
            result = clientProxy.subscribe(serviceName, groupName, StringUtils.EMPTY);
        } catch (NacosException e) {
            NAMING_LOGGER.warn("Subscribe from Server failed, will use local cache. fail message: ", e);
        }
        return result;
    }
    
    private ServiceInfo doSelectInstance(ServiceInfo serviceInfo, NamingSelector clusterSelector) {
        if (null == serviceInfo) {
            return null;
        }
        NamingContext context = new ServiceInfoContext(serviceInfo);
        NamingResult result = clusterSelector.select(context);
        serviceInfo.setHosts(result.getResult());
        return serviceInfo;
    }
    
    @Override
    public Instance selectOneHealthyInstance(String serviceName) throws NacosException {
        return selectOneHealthyInstance(serviceName, new ArrayList<>());
    }
    
    @Override
    public Instance selectOneHealthyInstance(String serviceName, String groupName) throws NacosException {
        return selectOneHealthyInstance(serviceName, groupName, true);
    }
    
    @Override
    public Instance selectOneHealthyInstance(String serviceName, boolean subscribe) throws NacosException {
        return selectOneHealthyInstance(serviceName, new ArrayList<>(), subscribe);
    }
    
    @Override
    public Instance selectOneHealthyInstance(String serviceName, String groupName, boolean subscribe)
            throws NacosException {
        return selectOneHealthyInstance(serviceName, groupName, new ArrayList<>(), subscribe);
    }
    
    @Override
    public Instance selectOneHealthyInstance(String serviceName, List<String> clusters) throws NacosException {
        return selectOneHealthyInstance(serviceName, clusters, true);
    }
    
    @Override
    public Instance selectOneHealthyInstance(String serviceName, String groupName, List<String> clusters)
            throws NacosException {
        return selectOneHealthyInstance(serviceName, groupName, clusters, true);
    }
    
    @Override
    public Instance selectOneHealthyInstance(String serviceName, List<String> clusters, boolean subscribe)
            throws NacosException {
        return selectOneHealthyInstance(serviceName, Constants.DEFAULT_GROUP, clusters, subscribe);
    }
    
    @Override
    public Instance selectOneHealthyInstance(String serviceName, String groupName, List<String> clusters,
            boolean subscribe) throws NacosException {
        ServiceInfo serviceInfo = getServiceInfo(serviceName, groupName, clusters, subscribe);
        return Balancer.RandomByWeight.selectHost(serviceInfo);
    }
    
    @Override
    public void subscribe(String serviceName, EventListener listener) throws NacosException {
        subscribe(serviceName, new ArrayList<>(), listener);
    }
    
    @Override
    public void subscribe(String serviceName, String groupName, EventListener listener) throws NacosException {
        subscribe(serviceName, groupName, new ArrayList<>(), listener);
    }
    
    @Override
    public void subscribe(String serviceName, List<String> clusters, EventListener listener) throws NacosException {
        subscribe(serviceName, Constants.DEFAULT_GROUP, clusters, listener);
    }
    
    @Override
    public void subscribe(String serviceName, String groupName, List<String> clusters, EventListener listener)
            throws NacosException {
        NamingSelector clusterSelector = NamingSelectorFactory.newClusterSelector(clusters);
        doSubscribe(serviceName, groupName, getUniqueClusterString(clusters), clusterSelector, listener);
    }
    
    @Override
    public void subscribe(String serviceName, NamingSelector selector, EventListener listener) throws NacosException {
        subscribe(serviceName, Constants.DEFAULT_GROUP, selector, listener);
    }
    
    @Override
    public void subscribe(String serviceName, String groupName, NamingSelector selector, EventListener listener)
            throws NacosException {
        doSubscribe(serviceName, groupName, Constants.NULL, selector, listener);
    }
    
    private void doSubscribe(String serviceName, String groupName, String clusters, NamingSelector selector,
            EventListener listener) throws NacosException {
        if (selector == null || listener == null) {
            return;
        }
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(serviceName, groupName, clusters, selector, listener);
        changeNotifier.registerListener(groupName, serviceName, wrapper);
        notifyIfSubscribed(serviceName, groupName, wrapper);
        clientProxy.subscribe(serviceName, groupName, Constants.NULL);
    }
    
    @Override
    public void unsubscribe(String serviceName, EventListener listener) throws NacosException {
        unsubscribe(serviceName, new ArrayList<>(), listener);
    }
    
    @Override
    public void unsubscribe(String serviceName, String groupName, EventListener listener) throws NacosException {
        unsubscribe(serviceName, groupName, new ArrayList<>(), listener);
    }
    
    @Override
    public void unsubscribe(String serviceName, List<String> clusters, EventListener listener) throws NacosException {
        unsubscribe(serviceName, Constants.DEFAULT_GROUP, clusters, listener);
    }
    
    @Override
    public void unsubscribe(String serviceName, String groupName, List<String> clusters, EventListener listener)
            throws NacosException {
        NamingSelector clusterSelector = NamingSelectorFactory.newClusterSelector(clusters);
        unsubscribe(serviceName, groupName, clusterSelector, listener);
    }
    
    @Override
    public void unsubscribe(String serviceName, NamingSelector selector, EventListener listener) throws NacosException {
        unsubscribe(serviceName, Constants.DEFAULT_GROUP, selector, listener);
    }
    
    @Override
    public void unsubscribe(String serviceName, String groupName, NamingSelector selector, EventListener listener)
            throws NacosException {
        doUnsubscribe(serviceName, groupName, selector, listener);
    }
    
    private void doUnsubscribe(String serviceName, String groupName, NamingSelector selector, EventListener listener)
            throws NacosException {
        if (selector == null || listener == null) {
            return;
        }
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(selector, listener);
        changeNotifier.deregisterListener(groupName, serviceName, wrapper);
        if (!changeNotifier.isSubscribed(groupName, serviceName)) {
            clientProxy.unsubscribe(serviceName, groupName, Constants.NULL);
        }
    }
    
    @Override
    public void fuzzyWatch(String fixedGroupName, FuzzyWatchEventWatcher listener) throws NacosException {
        doFuzzyWatch(ANY_PATTERN, fixedGroupName, listener);
    }
    
    @Override
    public void fuzzyWatch(String serviceNamePattern, String groupNamePattern, FuzzyWatchEventWatcher listener)
            throws NacosException {
        doFuzzyWatch(serviceNamePattern, groupNamePattern, listener);
    }
    
    @Override
    public Future<ListView<String>> fuzzyWatchWithServiceKeys(String fixedGroupName, FuzzyWatchEventWatcher listener)
            throws NacosException {
        return doFuzzyWatch(ANY_PATTERN, fixedGroupName, listener);
    }
    
    @Override
    public Future<ListView<String>> fuzzyWatchWithServiceKeys(String serviceNamePattern, String groupNamePattern,
            FuzzyWatchEventWatcher listener) throws NacosException {
        return doFuzzyWatch(serviceNamePattern, groupNamePattern, listener);
    }
    
    private Future<ListView<String>> doFuzzyWatch(String serviceNamePattern, String groupNamePattern,
            FuzzyWatchEventWatcher watcher) {
        if (null == watcher) {
            return null;
        }
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern(serviceNamePattern, groupNamePattern, namespace);
        NamingFuzzyWatchContext namingFuzzyWatchContext = namingFuzzyWatchServiceListHolder.registerFuzzyWatcher(
                groupKeyPattern, watcher);
        return namingFuzzyWatchContext.createNewFuture();
    }
    
    @Override
    public void cancelFuzzyWatch(String fixedGroupName, FuzzyWatchEventWatcher listener) throws NacosException {
        doCancelFuzzyWatch(ANY_PATTERN, fixedGroupName, listener);
    }
    
    @Override
    public void cancelFuzzyWatch(String serviceNamePattern, String fixedGroupName, FuzzyWatchEventWatcher listener)
            throws NacosException {
        doCancelFuzzyWatch(serviceNamePattern, fixedGroupName, listener);
    }
    
    private void doCancelFuzzyWatch(String serviceNamePattern, String groupNamePattern, FuzzyWatchEventWatcher watcher)
            throws NacosException {
        if (null == watcher) {
            return;
        }
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern(serviceNamePattern, groupNamePattern, namespace);
        
        NamingFuzzyWatchContext namingFuzzyWatchContext = namingFuzzyWatchServiceListHolder.getFuzzyWatchContext(
                groupKeyPattern);
        if (namingFuzzyWatchContext != null) {
            namingFuzzyWatchContext.removeWatcher(watcher);
        }
    }
    
    @Override
    public ListView<String> getServicesOfServer(int pageNo, int pageSize) throws NacosException {
        return getServicesOfServer(pageNo, pageSize, Constants.DEFAULT_GROUP);
    }
    
    @Override
    public ListView<String> getServicesOfServer(int pageNo, int pageSize, String groupName) throws NacosException {
        return getServicesOfServer(pageNo, pageSize, groupName, null);
    }
    
    @Override
    public ListView<String> getServicesOfServer(int pageNo, int pageSize, AbstractSelector selector)
            throws NacosException {
        return getServicesOfServer(pageNo, pageSize, Constants.DEFAULT_GROUP, selector);
    }
    
    @Override
    public ListView<String> getServicesOfServer(int pageNo, int pageSize, String groupName, AbstractSelector selector)
            throws NacosException {
        return clientProxy.getServiceList(pageNo, pageSize, groupName, selector);
    }
    
    @Override
    public List<ServiceInfo> getSubscribeServices() {
        return changeNotifier.getSubscribeServices();
    }
    
    @Override
    public String getServerStatus() {
        return clientProxy.serverHealthy() ? UP : DOWN;
    }
    
    @Override
    public void shutDown() throws NacosException {
        serviceInfoHolder.shutdown();
        clientProxy.shutdown();
        namingFuzzyWatchServiceListHolder.shutdown();
        NotifyCenter.deregisterSubscriber(changeNotifier);
    }
    
    private void batchCheckAndStripGroupNamePrefix(List<Instance> instances, String groupName) throws NacosException {
        for (Instance instance : instances) {
            checkAndStripGroupNamePrefix(instance, groupName);
        }
    }
    
    private void checkAndStripGroupNamePrefix(Instance instance, String groupName) throws NacosException {
        String serviceName = instance.getServiceName();
        if (NamingUtils.isServiceNameCompatibilityMode(serviceName)) {
            String groupNameOfInstance = NamingUtils.getGroupName(serviceName);
            if (!groupName.equals(groupNameOfInstance)) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, String.format(
                        "wrong group name prefix of instance service name! it should be: %s, Instance: %s", groupName,
                        instance));
            }
            instance.setServiceName(NamingUtils.getServiceName(serviceName));
        }
    }
    
    private void notifyIfSubscribed(String serviceName, String groupName, NamingSelectorWrapper wrapper)
            throws NacosException {
        if (clientProxy.isSubscribed(serviceName, groupName, StringUtils.EMPTY)) {
            NAMING_LOGGER.warn(
                    "Duplicate subscribe for groupName: {}, serviceName: {}; directly use current cached to notify.",
                    groupName, serviceName);
            ServiceInfo serviceInfo = serviceInfoHolder.getServiceInfo(serviceName, groupName);
            InstancesChangeEvent event = transferToEvent(serviceInfo);
            wrapper.notifyListener(event);
        }
    }
    
    private InstancesChangeEvent transferToEvent(ServiceInfo serviceInfo) {
        if (serviceInfo == null) {
            return null;
        }
        InstancesDiff diff = new InstancesDiff();
        diff.setAddedInstances(serviceInfo.getHosts());
        return new InstancesChangeEvent(notifierEventScope, serviceInfo.getName(), serviceInfo.getGroupName(),
                serviceInfo.getClusters(), serviceInfo.getHosts(), diff);
    }
}
