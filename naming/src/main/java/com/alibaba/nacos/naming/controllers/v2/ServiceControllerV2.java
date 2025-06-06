/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.naming.DeregisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.UpdateServiceTraceEvent;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.controller.compatibility.Compatibility;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.naming.pojo.ServiceNameView;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.naming.utils.ServiceUtil;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service operation controller.
 *
 * @author nkorange
 */
@Deprecated
@NacosApi
@RestController
@RequestMapping(UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_SERVICE_CONTEXT)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
public class ServiceControllerV2 {
    
    private final ServiceOperatorV2Impl serviceOperatorV2;
    
    private final SelectorManager selectorManager;
    
    public ServiceControllerV2(ServiceOperatorV2Impl serviceOperatorV2, SelectorManager selectorManager) {
        this.serviceOperatorV2 = serviceOperatorV2;
        this.selectorManager = selectorManager;
    }
    
    /**
     * Create a new service. This API will create a persistence service.
     */
    @PostMapping()
    @TpsControl(pointName = "NamingServiceRegister", name = "HttpNamingServiceRegister")
    @Secured(action = ActionTypes.WRITE)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "POST ${contextPath:nacos}/v3/admin/ns/service")
    public Result<String> create(ServiceForm serviceForm) throws Exception {
        serviceForm.validate();
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(serviceForm.getProtectThreshold());
        serviceMetadata.setSelector(parseSelector(serviceForm.getSelector()));
        serviceMetadata.setExtendData(UtilsAndCommons.parseMetadata(serviceForm.getMetadata()));
        serviceMetadata.setEphemeral(serviceForm.getEphemeral());
        serviceOperatorV2.create(Service.newService(serviceForm.getNamespaceId(), serviceForm.getGroupName(),
                serviceForm.getServiceName(), serviceForm.getEphemeral()), serviceMetadata);
        NotifyCenter.publishEvent(
                new RegisterServiceTraceEvent(System.currentTimeMillis(), serviceForm.getNamespaceId(),
                        serviceForm.getGroupName(), serviceForm.getServiceName()));
        return Result.success("ok");
    }
    
    /**
     * Remove service.
     */
    @DeleteMapping()
    @TpsControl(pointName = "NamingServiceDeregister", name = "HttpNamingServiceDeregister")
    @Secured(action = ActionTypes.WRITE)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "DELETE ${contextPath:nacos}/v3/admin/ns/service")
    public Result<String> remove(
            @RequestParam(value = "namespaceId", defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "groupName", defaultValue = Constants.DEFAULT_GROUP) String groupName)
            throws Exception {
        serviceOperatorV2.delete(Service.newService(namespaceId, groupName, serviceName));
        NotifyCenter.publishEvent(
                new DeregisterServiceTraceEvent(System.currentTimeMillis(), namespaceId, groupName, serviceName));
        return Result.success("ok");
    }
    
    /**
     * Get detail of service.
     */
    @GetMapping()
    @TpsControl(pointName = "NamingServiceQuery", name = "HttpNamingServiceQuery")
    @Secured(action = ActionTypes.READ)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "GET ${contextPath:nacos}/v3/admin/ns/service")
    public Result<com.alibaba.nacos.naming.pojo.ServiceDetailInfo> detail(
            @RequestParam(value = "namespaceId", defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "groupName", defaultValue = Constants.DEFAULT_GROUP) String groupName)
            throws Exception {
        ServiceDetailInfo serviceDetailInfo = serviceOperatorV2.queryService(
                Service.newService(namespaceId, groupName, serviceName));
        com.alibaba.nacos.naming.pojo.ServiceDetailInfo result = com.alibaba.nacos.naming.pojo.ServiceDetailInfo.from(
                serviceDetailInfo);
        return Result.success(result);
    }
    
    /**
     * List all service names.
     */
    @GetMapping("/list")
    @TpsControl(pointName = "NamingServiceListQuery", name = "HttpNamingServiceListQuery")
    @Secured(action = ActionTypes.READ)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "GET ${contextPath:nacos}/v3/admin/ns/service/list")
    public Result<ServiceNameView> list(
            @RequestParam(value = "namespaceId", required = false, defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam(value = "groupName", required = false, defaultValue = Constants.DEFAULT_GROUP) String groupName,
            @RequestParam(value = "selector", required = false, defaultValue = StringUtils.EMPTY) String selector,
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize)
            throws Exception {
        pageSize = Math.min(500, pageSize);
        ServiceNameView result = new ServiceNameView();
        Collection<String> serviceNameList = serviceOperatorV2.listService(namespaceId, groupName, selector);
        result.setCount(serviceNameList.size());
        result.setServices(ServiceUtil.pageServiceName(pageNo, pageSize, serviceNameList));
        return Result.success(result);
    }
    
    /**
     * Update service.
     */
    @PutMapping()
    @TpsControl(pointName = "NamingServiceUpdate", name = "HttpNamingServiceUpdate")
    @Secured(action = ActionTypes.WRITE)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "PUT ${contextPath:nacos}/v3/admin/ns/service")
    public Result<String> update(ServiceForm serviceForm) throws Exception {
        serviceForm.validate();
        Map<String, String> metadata = UtilsAndCommons.parseMetadata(serviceForm.getMetadata());
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(serviceForm.getProtectThreshold());
        serviceMetadata.setExtendData(metadata);
        serviceMetadata.setSelector(parseSelector(serviceForm.getSelector()));
        Service service = Service.newService(serviceForm.getNamespaceId(), serviceForm.getGroupName(),
                serviceForm.getServiceName());
        serviceOperatorV2.update(service, serviceMetadata);
        NotifyCenter.publishEvent(new UpdateServiceTraceEvent(System.currentTimeMillis(), serviceForm.getNamespaceId(),
                serviceForm.getGroupName(), serviceForm.getServiceName(), metadata));
        return Result.success("ok");
    }
    
    private Selector parseSelector(String selectorJsonString) throws Exception {
        if (StringUtils.isBlank(selectorJsonString)) {
            return new NoneSelector();
        }
        
        JsonNode selectorJson = JacksonUtils.toObj(URLDecoder.decode(selectorJsonString, "UTF-8"));
        String type = Optional.ofNullable(selectorJson.get("type")).orElseThrow(
                () -> new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.SELECTOR_ERROR,
                        "not match any type of selector!")).asText();
        String expression = Optional.ofNullable(selectorJson.get("expression")).map(JsonNode::asText).orElse(null);
        Selector selector = selectorManager.parseSelector(type, expression);
        if (Objects.isNull(selector)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.SELECTOR_ERROR,
                    "not match any type of selector!");
        }
        return selector;
    }
}
