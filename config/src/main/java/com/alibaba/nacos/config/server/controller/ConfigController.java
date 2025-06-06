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

package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.DateFormatUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.constant.ParametersField;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.enums.ApiVersionEnum;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigListenState;
import com.alibaba.nacos.config.server.model.ConfigMetadata;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.paramcheck.ConfigBlurSearchHttpParamExtractor;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.paramcheck.ConfigListenerHttpParamExtractor;
import com.alibaba.nacos.config.server.result.code.ResultCodeEnum;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.ConfigSubService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.YamlParserUtil;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.controller.compatibility.Compatibility;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.nacos.config.server.utils.RequestUtil.getRemoteIp;

/**
 * Special controller for soft load client to publish data.
 *
 * @author leiwen
 */
@Deprecated
@RestController
@RequestMapping(Constants.CONFIG_CONTROLLER_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class ConfigController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigController.class);
    
    private static final String EXPORT_CONFIG_FILE_NAME = "nacos_config_export_";
    
    private static final String EXPORT_CONFIG_FILE_NAME_EXT = ".zip";
    
    private static final String EXPORT_CONFIG_FILE_NAME_DATE_FORMAT = "yyyyMMddHHmmss";
    
    private final ConfigServletInner inner;
    
    private ConfigInfoPersistService configInfoPersistService;
    
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    private NamespacePersistService namespacePersistService;
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigSubService configSubService;
    
    public ConfigController(ConfigServletInner inner, ConfigOperationService configOperationService,
            ConfigSubService configSubService, ConfigInfoPersistService configInfoPersistService,
            NamespacePersistService namespacePersistService,
            ConfigInfoGrayPersistService configInfoGrayPersistService) {
        this.inner = inner;
        this.configOperationService = configOperationService;
        this.configSubService = configSubService;
        this.configInfoPersistService = configInfoPersistService;
        this.namespacePersistService = namespacePersistService;
        this.configInfoGrayPersistService = configInfoGrayPersistService;
    }
    
    /**
     * Adds or updates non-aggregated data.
     * <p>
     * request and response will be used in aspect, see
     * {@link com.alibaba.nacos.config.server.aspect.CapacityManagementAspect} and
     * {@link com.alibaba.nacos.config.server.aspect.RequestLogAspect}.
     * </p>
     *
     * @throws NacosException NacosException.
     */
    @PostMapping
    @TpsControl(pointName = "ConfigPublish")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.OPEN_API, alternatives = "POST ${contextPath:nacos}/v3/admin/cs/config")
    public Boolean publishConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam(value = "dataId") String dataId, @RequestParam(value = "group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "content") String content, @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam(value = "desc", required = false) String desc,
            @RequestParam(value = "use", required = false) String use,
            @RequestParam(value = "effect", required = false) String effect,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam(required = false) String encryptedDataKey) throws NacosException {
        
        String encryptedDataKeyFinal = null;
        if (StringUtils.isNotBlank(encryptedDataKey)) {
            encryptedDataKeyFinal = encryptedDataKey;
        } else {
            Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
            content = pair.getSecond();
            encryptedDataKeyFinal = pair.getFirst();
        }
        
        // check tenant
        final boolean namespaceTransferred = NamespaceUtil.isNeedTransferNamespace(tenant);
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        ParamUtils.checkTenant(tenant);
        ParamUtils.checkParam(dataId, group, "datumId", content);
        ParamUtils.checkParam(tag);
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(dataId);
        configForm.setGroup(group);
        configForm.setNamespaceId(tenant);
        configForm.setContent(content);
        configForm.setTag(tag);
        configForm.setAppName(appName);
        configForm.setSrcUser(srcUser);
        configForm.setConfigTags(configTags);
        configForm.setDesc(desc);
        configForm.setUse(use);
        configForm.setEffect(effect);
        configForm.setType(type);
        configForm.setSchema(schema);
        if (StringUtils.isBlank(srcUser)) {
            configForm.setSrcUser(RequestUtil.getSrcUserName(request));
        }
        if (!ConfigType.isValidType(type)) {
            configForm.setType(ConfigType.getDefaultType().getType());
        }
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp(RequestUtil.getRemoteIp(request));
        configRequestInfo.setSrcType(Constants.HTTP);
        configRequestInfo.setRequestIpApp(RequestUtil.getAppName(request));
        configRequestInfo.setBetaIps(request.getHeader("betaIps"));
        configRequestInfo.setCasMd5(request.getHeader("casMd5"));
        configRequestInfo.setNamespaceTransferred(namespaceTransferred);
        
        boolean publishResult = false;
        try {
            publishResult = configOperationService.publishConfig(configForm, configRequestInfo, encryptedDataKeyFinal);
        } catch (NacosException e) {
            response.setStatus(e.getErrCode());
        }
        return publishResult;
    }
    
    /**
     * Get configure board information fail.
     *
     * @throws ServletException ServletException.
     * @throws IOException      IOException.
     * @throws NacosException   NacosException.
     */
    @GetMapping
    @TpsControl(pointName = "ConfigQuery")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.OPEN_API, alternatives = "GET ${contextPath:nacos}/v3/client/cs/config")
    public void getConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "tag", required = false) String tag)
            throws IOException, ServletException, NacosException {
        // check tenant
        ParamUtils.checkTenant(tenant);
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        // check params
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        ParamUtils.checkParam(tag);
        
        final String clientIp = RequestUtil.getRemoteIp(request);
        String isNotify = request.getHeader("notify");
        inner.doGetConfig(request, response, dataId, group, tenant, tag, isNotify, clientIp, ApiVersionEnum.V1);
    }
    
    /**
     * Get the specific configuration information that the console USES.
     *
     * @throws NacosException NacosException.
     */
    @GetMapping(params = "show=all")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/cs/config")
    public ConfigAllInfo detailConfigInfo(@RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant)
            throws NacosException {
        // check tenant
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        ParamUtils.checkTenant(tenant);
        // check params
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        ConfigAllInfo configAllInfo = configInfoPersistService.findConfigAllInfo(dataId, group, tenant);
        
        // decrypted
        if (Objects.nonNull(configAllInfo)) {
            String encryptedDataKey = configAllInfo.getEncryptedDataKey();
            Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                    configAllInfo.getContent());
            configAllInfo.setContent(pair.getSecond());
        }
        return configAllInfo;
    }
    
    /**
     * Synchronously delete all pre-aggregation data under a dataId.
     *
     * <p>
     * request and response will be used in aspect, see
     * {@link com.alibaba.nacos.config.server.aspect.CapacityManagementAspect} and
     * {@link com.alibaba.nacos.config.server.aspect.RequestLogAspect}.
     * </p>
     *
     * @throws NacosException NacosException.
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.OPEN_API, alternatives = "DELETE ${contextPath:nacos}/v3/admin/cs/config")
    public Boolean deleteConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "tag", required = false) String tag) throws NacosException {
        // check tenant
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        ParamUtils.checkTenant(tenant);
        ParamUtils.checkParam(dataId, group, "datumId", "rm");
        ParamUtils.checkParam(tag);
        
        String clientIp = RequestUtil.getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        
        return configOperationService.deleteConfig(dataId, group, tenant, tag, clientIp, srcUser, Constants.HTTP);
    }
    
    /**
     * delete configs based on the IDs list.
     */
    @DeleteMapping(params = "delType=ids")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/cs/config/batchDelete")
    public RestResult<Boolean> deleteConfigs(HttpServletRequest request, @RequestParam(value = "ids") List<Long> ids) {
        String clientIp = RequestUtil.getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        try {
            for (Long id : ids) {
                ConfigInfo configInfo = configInfoPersistService.findConfigInfo(id);
                if (configInfo == null) {
                    LOGGER.warn("[deleteConfigs] configInfo is null, id: {}", id);
                    continue;
                }
                configOperationService.deleteConfig(configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant(), null, clientIp, srcUser, Constants.HTTP);
            }
            return RestResultUtils.success(true);
        } catch (Exception e) {
            LOGGER.error("delete configs based on the IDs list error, IDs: {}", ids, e);
            return RestResultUtils.failed(e.getMessage());
        }
    }
    
    @GetMapping("/catalog")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "GET ${contextPath:nacos}/v3/admin/cs/config")
    public RestResult<ConfigAdvanceInfo> getConfigAdvanceInfo(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant) {
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        ConfigAdvanceInfo configInfo = configInfoPersistService.findConfigAdvanceInfo(dataId, group, tenant);
        return RestResultUtils.success(configInfo);
    }
    
    /**
     * The client listens for configuration changes.
     */
    @PostMapping("/listener")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @ExtractorManager.Extractor(httpExtractor = ConfigListenerHttpParamExtractor.class)
    @Compatibility(apiType = ApiType.OPEN_API)
    public void listener(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        request.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true);
        String probeModify = request.getParameter("Listening-Configs");
        if (StringUtils.isBlank(probeModify)) {
            LOGGER.warn("invalid probeModify is blank");
            throw new IllegalArgumentException("invalid probeModify");
        }
        
        probeModify = URLDecoder.decode(probeModify, Constants.ENCODE);
        
        Map<String, ConfigListenState> clientMd5Map;
        try {
            clientMd5Map = MD5Util.getClientMd5Map(probeModify);
        } catch (Throwable e) {
            throw new IllegalArgumentException("invalid probeModify");
        }
        
        // do long-polling
        inner.doPollingConfig(request, response, clientMd5Map, probeModify.length());
    }
    
    /**
     * Subscribe to configured client information.
     */
    @GetMapping("/listener")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/cs/config/listener")
    public GroupkeyListenserStatus getListeners(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group, @RequestParam(value = "tenant", required = false) String tenant,
            @RequestParam(value = "sampleTime", required = false, defaultValue = "1") int sampleTime) throws Exception {
        group = StringUtils.isBlank(group) ? Constants.DEFAULT_GROUP : group;
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        SampleResult collectSampleResult = configSubService.getCollectSampleResult(dataId, group, tenant, sampleTime);
        GroupkeyListenserStatus gls = new GroupkeyListenserStatus();
        gls.setCollectStatus(200);
        if (collectSampleResult.getLisentersGroupkeyStatus() != null) {
            gls.setLisentersGroupkeyStatus(collectSampleResult.getLisentersGroupkeyStatus());
        }
        return gls;
    }
    
    /**
     * Query the configuration information and return it in JSON format.
     */
    @GetMapping(params = "search=accurate")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @ExtractorManager.Extractor(httpExtractor = ConfigBlurSearchHttpParamExtractor.class)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/cs/config/list")
    public Page<ConfigInfo> searchConfig(@RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam("pageNo") int pageNo, @RequestParam("pageSize") int pageSize) {
        Map<String, Object> configAdvanceInfo = new HashMap<>(100);
        if (StringUtils.isNotBlank(appName)) {
            configAdvanceInfo.put("appName", appName);
        }
        if (StringUtils.isNotBlank(configTags)) {
            configAdvanceInfo.put("config_tags", configTags);
        }
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        try {
            return configInfoPersistService.findConfigInfo4Page(pageNo, pageSize, dataId, group, tenant,
                    configAdvanceInfo);
        } catch (Exception e) {
            String errorMsg = "serialize page error, dataId=" + dataId + ", group=" + group;
            LOGGER.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    /**
     * Fuzzy query configuration information. Fuzzy queries based only on content are not allowed, that is, both dataId
     * and group are NULL, but content is not NULL. In this case, all configurations are returned.
     */
    @GetMapping(params = "search=blur")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @ExtractorManager.Extractor(httpExtractor = ConfigBlurSearchHttpParamExtractor.class)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/cs/config/list")
    public Page<ConfigInfo> fuzzySearchConfig(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group, @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam(value = "types", required = false) String types, @RequestParam("pageNo") int pageNo,
            @RequestParam("pageSize") int pageSize) {
        MetricsMonitor.getFuzzySearchMonitor().incrementAndGet();
        Map<String, Object> configAdvanceInfo = new HashMap<>(50);
        if (StringUtils.isNotBlank(appName)) {
            configAdvanceInfo.put("appName", appName);
        }
        if (StringUtils.isNotBlank(configTags)) {
            configAdvanceInfo.put("config_tags", configTags);
        }
        if (StringUtils.isNotBlank(types)) {
            configAdvanceInfo.put(ParametersField.TYPES, types);
        }
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        try {
            return configInfoPersistService.findConfigInfoLike4Page(pageNo, pageSize, dataId, group, tenant,
                    configAdvanceInfo);
        } catch (Exception e) {
            String errorMsg = "serialize page error, dataId=" + dataId + ", group=" + group;
            LOGGER.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    /**
     * Execute to remove beta operation.
     *
     * @param dataId dataId string value.
     * @param group  group string value.
     * @param tenant tenant string value.
     * @return Execute to operate result.
     */
    @DeleteMapping(params = "beta=true")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "DELETE ${contextPath:nacos}/v3/console/cs/config/beta")
    public RestResult<Boolean> stopBeta(HttpServletRequest httpServletRequest,
            @RequestParam(value = "dataId") String dataId, @RequestParam(value = "group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "srcUser", required = false, defaultValue = StringUtils.EMPTY) String srcUser) {
        String remoteIp = getRemoteIp(httpServletRequest);
        if (StringUtils.isBlank(srcUser)) {
            srcUser = RequestUtil.getSrcUserName(httpServletRequest);
        }
        String requestIpApp = RequestUtil.getAppName(httpServletRequest);
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        try {
            
            configOperationService.deleteConfig(dataId, group, tenant, BetaGrayRule.TYPE_BETA, remoteIp, srcUser, Constants.HTTP);
        } catch (Throwable e) {
            LOGGER.error("remove beta data error", e);
            return RestResultUtils.failed(500, false, "remove beta data error");
        }
        return RestResultUtils.success("stop beta ok", true);
    }
    
    /**
     * Execute to query beta operation.
     *
     * @param dataId dataId string value.
     * @param group  group string value.
     * @param tenant tenant string value.
     * @return RestResult for ConfigInfo4Beta.
     */
    @GetMapping(params = "beta=true")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/cs/config/beta")
    public RestResult<ConfigInfo4Beta> queryBeta(@RequestParam(value = "dataId") String dataId,
            @RequestParam(value = "group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant) {
        try {
            tenant = NamespaceUtil.processNamespaceParameter(tenant);
            ConfigInfo4Beta configInfo4Beta = null;
            ConfigInfoGrayWrapper beta4Gray = configInfoGrayPersistService.findConfigInfo4Gray(dataId, group, tenant,
                    "beta");
            if (Objects.nonNull(beta4Gray)) {
                String encryptedDataKey = beta4Gray.getEncryptedDataKey();
                Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                        beta4Gray.getContent());
                beta4Gray.setContent(pair.getSecond());
                configInfo4Beta = new ConfigInfo4Beta();
                BeanUtils.copyProperties(beta4Gray, configInfo4Beta);
                configInfo4Beta.setBetaIps(
                        GrayRuleManager.deserializeConfigGrayPersistInfo(beta4Gray.getGrayRule()).getExpr());
            }
            return RestResultUtils.success("query beta ok", configInfo4Beta);
        } catch (Throwable e) {
            LOGGER.error("query beta data error", e);
            return RestResultUtils.failed("query beta data error");
        }
    }
    
    /**
     * Execute export config operation.
     *
     * @param dataId  dataId string value.
     * @param group   group string value.
     * @param appName appName string value.
     * @param tenant  tenant string value.
     * @param ids     id list value.
     * @return ResponseEntity.
     */
    @GetMapping(params = "export=true")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/cs/config/export")
    public ResponseEntity<byte[]> exportConfig(@RequestParam(value = "dataId", required = false) String dataId,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "ids", required = false) List<Long> ids) {
        ids.removeAll(Collections.singleton(null));
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        List<ConfigAllInfo> dataList = configInfoPersistService.findAllConfigInfo4Export(dataId, group, tenant, appName,
                ids);
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>();
        StringBuilder metaData = null;
        for (ConfigInfo ci : dataList) {
            if (StringUtils.isNotBlank(ci.getAppName())) {
                // Handle appName
                if (metaData == null) {
                    metaData = new StringBuilder();
                }
                String metaDataId = ci.getDataId();
                if (metaDataId.contains(".")) {
                    metaDataId = metaDataId.substring(0, metaDataId.lastIndexOf(".")) + "~" + metaDataId.substring(
                            metaDataId.lastIndexOf(".") + 1);
                }
                metaData.append(ci.getGroup()).append('.').append(metaDataId).append(".app=")
                        // Fixed use of "\r\n" here
                        .append(ci.getAppName()).append("\r\n");
            }
            Pair<String, String> pair = EncryptionHandler.decryptHandler(ci.getDataId(), ci.getEncryptedDataKey(),
                    ci.getContent());
            String itemName = ci.getGroup() + Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR + ci.getDataId();
            zipItemList.add(new ZipUtils.ZipItem(itemName, pair.getSecond()));
        }
        if (metaData != null) {
            zipItemList.add(new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA, metaData.toString()));
        }
        
        HttpHeaders headers = new HttpHeaders();
        String fileName =
                EXPORT_CONFIG_FILE_NAME + DateFormatUtils.format(new Date(), EXPORT_CONFIG_FILE_NAME_DATE_FORMAT)
                        + EXPORT_CONFIG_FILE_NAME_EXT;
        headers.add("Content-Disposition", "attachment;filename=" + fileName);
        return new ResponseEntity<>(ZipUtils.zip(zipItemList), headers, HttpStatus.OK);
    }
    
    /**
     * new version export config add metadata.yml file record config metadata.
     *
     * @param dataId  dataId string value.
     * @param group   group string value.
     * @param appName appName string value.
     * @param tenant  tenant string value.
     * @param ids     id list value.
     * @return ResponseEntity.
     */
    @GetMapping(params = "exportV2=true")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/cs/config/export2")
    public ResponseEntity<byte[]> exportConfigV2(@RequestParam(value = "dataId", required = false) String dataId,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "ids", required = false) List<Long> ids) {
        ids.removeAll(Collections.singleton(null));
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        List<ConfigAllInfo> dataList = configInfoPersistService.findAllConfigInfo4Export(dataId, group, tenant, appName,
                ids);
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>();
        List<ConfigMetadata.ConfigExportItem> configMetadataItems = new ArrayList<>();
        for (ConfigAllInfo ci : dataList) {
            ConfigMetadata.ConfigExportItem configMetadataItem = new ConfigMetadata.ConfigExportItem();
            configMetadataItem.setAppName(ci.getAppName());
            configMetadataItem.setDataId(ci.getDataId());
            configMetadataItem.setDesc(ci.getDesc());
            configMetadataItem.setGroup(ci.getGroup());
            configMetadataItem.setType(ci.getType());
            configMetadataItem.setConfigTags(ci.getConfigTags());
            configMetadataItems.add(configMetadataItem);
            Pair<String, String> pair = EncryptionHandler.decryptHandler(ci.getDataId(), ci.getEncryptedDataKey(),
                    ci.getContent());
            String itemName = ci.getGroup() + Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR + ci.getDataId();
            zipItemList.add(new ZipUtils.ZipItem(itemName, pair.getSecond()));
        }
        ConfigMetadata configMetadata = new ConfigMetadata();
        configMetadata.setMetadata(configMetadataItems);
        zipItemList.add(
                new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW, YamlParserUtil.dumpObject(configMetadata)));
        HttpHeaders headers = new HttpHeaders();
        String fileName =
                EXPORT_CONFIG_FILE_NAME + DateFormatUtils.format(new Date(), EXPORT_CONFIG_FILE_NAME_DATE_FORMAT)
                        + EXPORT_CONFIG_FILE_NAME_EXT;
        headers.add("Content-Disposition", "attachment;filename=" + fileName);
        return new ResponseEntity<>(ZipUtils.zip(zipItemList), headers, HttpStatus.OK);
    }
    
    /**
     * Execute import and publish config operation.
     *
     * @param request   http servlet request .
     * @param srcUser   src user string value.
     * @param namespace namespace string value.
     * @param policy    policy model.
     * @param file      MultipartFile.
     * @return RestResult Map.
     * @throws NacosException NacosException.
     */
    @PostMapping(params = "import=true")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/cs/config/import")
    public RestResult<Map<String, Object>> importAndPublishConfig(HttpServletRequest request,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "namespace", required = false) String namespace,
            @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy, MultipartFile file)
            throws NacosException {
        Map<String, Object> failedData = new HashMap<>(4);
        
        if (Objects.isNull(file)) {
            return RestResultUtils.buildResult(ResultCodeEnum.DATA_EMPTY, failedData);
        }
        
        final boolean namespaceTransferred = NamespaceUtil.isNeedTransferNamespace(namespace);
        namespace = NamespaceUtil.processNamespaceParameter(namespace);
        if (StringUtils.isNotBlank(namespace) && namespacePersistService.tenantInfoCountByTenantId(namespace) <= 0) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.NAMESPACE_NOT_EXIST, failedData);
        }
        if (StringUtils.isBlank(srcUser)) {
            srcUser = RequestUtil.getSrcUserName(request);
        }
        List<ConfigAllInfo> configInfoList = new ArrayList<>();
        List<Map<String, String>> unrecognizedList = new ArrayList<>();
        try {
            ZipUtils.UnZipResult unziped = ZipUtils.unzip(file.getBytes());
            ZipUtils.ZipItem metaDataZipItem = unziped.getMetaDataItem();
            RestResult<Map<String, Object>> errorResult;
            if (metaDataZipItem != null && Constants.CONFIG_EXPORT_METADATA_NEW.equals(metaDataZipItem.getItemName())) {
                // new export
                errorResult = parseImportDataV2(srcUser, unziped, configInfoList, unrecognizedList, namespace);
            } else {
                errorResult = parseImportData(srcUser, unziped, configInfoList, unrecognizedList, namespace);
            }
            if (errorResult != null) {
                return errorResult;
            }
        } catch (IOException e) {
            failedData.put("succCount", 0);
            LOGGER.error("parsing data failed", e);
            return RestResultUtils.buildResult(ResultCodeEnum.PARSING_DATA_FAILED, failedData);
        }
        
        if (CollectionUtils.isEmpty(configInfoList)) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.DATA_EMPTY, failedData);
        }
        
        Map<String, Object> saveResult = batchImportAndPublishConfigs(configInfoList, request, srcUser, namespace,
                namespaceTransferred, policy);
        
        // unrecognizedCount
        if (!unrecognizedList.isEmpty()) {
            saveResult.put("unrecognizedCount", unrecognizedList.size());
            saveResult.put("unrecognizedData", unrecognizedList);
        }
        return RestResultUtils.success("import success", saveResult);
    }
    
    /**
     * old import config.
     *
     * @param unziped          export file.
     * @param configInfoList   parse file result.
     * @param unrecognizedList unrecognized file.
     * @param namespace        import namespace.
     * @return error result.
     */
    private RestResult<Map<String, Object>> parseImportData(String srcUser, ZipUtils.UnZipResult unziped,
            List<ConfigAllInfo> configInfoList, List<Map<String, String>> unrecognizedList, String namespace) {
        ZipUtils.ZipItem metaDataZipItem = unziped.getMetaDataItem();
        
        Map<String, String> metaDataMap = new HashMap<>(16);
        if (metaDataZipItem != null) {
            // compatible all file separator
            String metaDataStr = metaDataZipItem.getItemData().replaceAll("[\r\n]+", "|");
            String[] metaDataArr = metaDataStr.split("\\|");
            Map<String, Object> failedData = new HashMap<>(4);
            for (String metaDataItem : metaDataArr) {
                String[] metaDataItemArr = metaDataItem.split("=");
                if (metaDataItemArr.length != 2) {
                    failedData.put("succCount", 0);
                    return RestResultUtils.buildResult(ResultCodeEnum.METADATA_ILLEGAL, failedData);
                }
                metaDataMap.put(metaDataItemArr[0], metaDataItemArr[1]);
            }
        }
        
        List<ZipUtils.ZipItem> itemList = unziped.getZipItemList();
        if (itemList != null && !itemList.isEmpty()) {
            for (ZipUtils.ZipItem item : itemList) {
                String[] groupAdnDataId = item.getItemName().split(Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR);
                if (groupAdnDataId.length != 2) {
                    Map<String, String> unrecognizedItem = new HashMap<>(2);
                    unrecognizedItem.put("itemName", item.getItemName());
                    unrecognizedList.add(unrecognizedItem);
                    continue;
                }
                String group = groupAdnDataId[0];
                String dataId = groupAdnDataId[1];
                String tempDataId = dataId;
                if (tempDataId.contains(".")) {
                    tempDataId = tempDataId.substring(0, tempDataId.lastIndexOf(".")) + "~" + tempDataId.substring(
                            tempDataId.lastIndexOf(".") + 1);
                }
                final String metaDataId = group + "." + tempDataId + ".app";
                
                //encrypted
                String content = item.getItemData();
                Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
                content = pair.getSecond();
                
                ConfigAllInfo ci = new ConfigAllInfo();
                ci.setGroup(group);
                ci.setDataId(dataId);
                ci.setContent(content);
                if (metaDataMap.get(metaDataId) != null) {
                    ci.setAppName(metaDataMap.get(metaDataId));
                }
                ci.setTenant(namespace);
                ci.setEncryptedDataKey(pair.getFirst());
                ci.setCreateUser(srcUser);
                configInfoList.add(ci);
            }
        }
        return null;
    }
    
    /**
     * new version import config add .metadata.yml file.
     *
     * @param unziped          export file.
     * @param configInfoList   parse file result.
     * @param unrecognizedList unrecognized file.
     * @param namespace        import namespace.
     * @return error result.
     */
    private RestResult<Map<String, Object>> parseImportDataV2(String srcUser, ZipUtils.UnZipResult unziped,
            List<ConfigAllInfo> configInfoList, List<Map<String, String>> unrecognizedList, String namespace) {
        ZipUtils.ZipItem metaDataItem = unziped.getMetaDataItem();
        String metaData = metaDataItem.getItemData();
        Map<String, Object> failedData = new HashMap<>(4);
        
        ConfigMetadata configMetadata = YamlParserUtil.loadObject(metaData, ConfigMetadata.class);
        if (configMetadata == null || CollectionUtils.isEmpty(configMetadata.getMetadata())) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.METADATA_ILLEGAL, failedData);
        }
        List<ConfigMetadata.ConfigExportItem> configExportItems = configMetadata.getMetadata();
        // check config metadata
        for (ConfigMetadata.ConfigExportItem configExportItem : configExportItems) {
            if (StringUtils.isBlank(configExportItem.getDataId()) || StringUtils.isBlank(configExportItem.getGroup())
                    || StringUtils.isBlank(configExportItem.getType())) {
                failedData.put("succCount", 0);
                return RestResultUtils.buildResult(ResultCodeEnum.METADATA_ILLEGAL, failedData);
            }
        }
        
        List<ZipUtils.ZipItem> zipItemList = unziped.getZipItemList();
        Set<String> metaDataKeys = configExportItems.stream()
                .map(metaItem -> GroupKey.getKey(metaItem.getDataId(), metaItem.getGroup()))
                .collect(Collectors.toSet());
        
        Map<String, String> configContentMap = new HashMap<>(zipItemList.size());
        int itemNameLength = 2;
        zipItemList.forEach(item -> {
            String itemName = item.getItemName();
            String[] groupAdnDataId = itemName.split(Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR);
            if (groupAdnDataId.length != itemNameLength) {
                Map<String, String> unrecognizedItem = new HashMap<>(2);
                unrecognizedItem.put("itemName", item.getItemName());
                unrecognizedList.add(unrecognizedItem);
                return;
            }
            
            String group = groupAdnDataId[0];
            String dataId = groupAdnDataId[1];
            String key = GroupKey.getKey(dataId, group);
            // metadata does not contain config file
            if (!metaDataKeys.contains(key)) {
                Map<String, String> unrecognizedItem = new HashMap<>(2);
                unrecognizedItem.put("itemName", "未在元数据中找到: " + item.getItemName());
                unrecognizedList.add(unrecognizedItem);
                return;
            }
            String itemData = item.getItemData();
            configContentMap.put(key, itemData);
        });
        
        for (ConfigMetadata.ConfigExportItem configExportItem : configExportItems) {
            String dataId = configExportItem.getDataId();
            String group = configExportItem.getGroup();
            String content = configContentMap.get(GroupKey.getKey(dataId, group));
            // config file not in metadata
            if (content == null) {
                Map<String, String> unrecognizedItem = new HashMap<>(2);
                unrecognizedItem.put("itemName", "未在文件中找到: " + group + "/" + dataId);
                unrecognizedList.add(unrecognizedItem);
                continue;
            }
            // encrypted
            Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
            content = pair.getSecond();
            
            ConfigAllInfo ci = new ConfigAllInfo();
            ci.setGroup(group);
            ci.setDataId(dataId);
            ci.setContent(content);
            ci.setType(configExportItem.getType());
            ci.setDesc(configExportItem.getDesc());
            ci.setAppName(configExportItem.getAppName());
            ci.setTenant(namespace);
            ci.setEncryptedDataKey(pair.getFirst());
            ci.setCreateUser(srcUser);
            ci.setConfigTags(configExportItem.getConfigTags());
            configInfoList.add(ci);
        }
        return null;
    }
    
    /**
     * Execute clone config operation.
     *
     * @param request         http servlet request .
     * @param srcUser         src user string value.
     * @param namespace       namespace string value.
     * @param configBeansList config beans list.
     * @param policy          config policy model.
     * @return RestResult for map.
     * @throws NacosException NacosException.
     */
    @PostMapping(params = "clone=true")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/cs/config/clone")
    public RestResult<Map<String, Object>> cloneConfig(HttpServletRequest request,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "tenant") String namespace,
            @RequestBody List<SameNamespaceCloneConfigBean> configBeansList,
            @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy) throws NacosException {
        Map<String, Object> failedData = new HashMap<>(4);
        if (CollectionUtils.isEmpty(configBeansList)) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.NO_SELECTED_CONFIG, failedData);
        }
        configBeansList.removeAll(Collections.singleton(null));
        
        final boolean namespaceTransferred = NamespaceUtil.isNeedTransferNamespace(namespace);
        namespace = NamespaceUtil.processNamespaceParameter(namespace);
        if (StringUtils.isNotBlank(namespace) && namespacePersistService.tenantInfoCountByTenantId(namespace) <= 0) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.NAMESPACE_NOT_EXIST, failedData);
        }
        
        List<Long> idList = new ArrayList<>(configBeansList.size());
        Map<Long, SameNamespaceCloneConfigBean> configBeansMap = configBeansList.stream()
                .collect(Collectors.toMap(SameNamespaceCloneConfigBean::getCfgId, cfg -> {
                    idList.add(cfg.getCfgId());
                    return cfg;
                }, (k1, k2) -> k1));
        
        List<ConfigAllInfo> queryedDataList = configInfoPersistService.findAllConfigInfo4Export(null, null, null, null,
                idList);
        
        if (queryedDataList == null || queryedDataList.isEmpty()) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.DATA_EMPTY, failedData);
        }
        
        List<ConfigAllInfo> configInfoList4Clone = new ArrayList<>(queryedDataList.size());
        
        for (ConfigAllInfo ci : queryedDataList) {
            SameNamespaceCloneConfigBean paramBean = configBeansMap.get(ci.getId());
            ConfigAllInfo ci4save = new ConfigAllInfo();
            ci4save.setType(ci.getType());
            ci4save.setGroup((paramBean != null && StringUtils.isNotBlank(paramBean.getGroup())) ? paramBean.getGroup()
                    : ci.getGroup());
            ci4save.setDataId(
                    (paramBean != null && StringUtils.isNotBlank(paramBean.getDataId())) ? paramBean.getDataId()
                            : ci.getDataId());
            ci4save.setContent(ci.getContent());
            if (StringUtils.isNotBlank(ci.getAppName())) {
                ci4save.setAppName(ci.getAppName());
            }
            ci4save.setDesc(ci.getDesc());
            ci4save.setEncryptedDataKey(
                    ci.getEncryptedDataKey() == null ? StringUtils.EMPTY : ci.getEncryptedDataKey());
            ParamUtils.checkParam(ci4save.getDataId(), ci4save.getGroup(), "datumId", ci4save.getContent());
            configInfoList4Clone.add(ci4save);
        }
        if (StringUtils.isBlank(srcUser)) {
            srcUser = RequestUtil.getSrcUserName(request);
        }
        Map<String, Object> saveResult = batchImportAndPublishConfigs(configInfoList4Clone, request, srcUser, namespace,
                namespaceTransferred, policy);

        return RestResultUtils.success("Clone Completed Successfully", saveResult);
    }
    
    private Map<String, Object> batchImportAndPublishConfigs(List<ConfigAllInfo> configAllInfoList, HttpServletRequest request,
            String srcUser, String targetNamespaceId, boolean namespaceTransferred, SameConfigPolicy sameConfigPolicy) throws NacosException {
        Map<String, Object> saveResult = new HashMap<>(16);
        int succCount = 0;
        int skipCount = 0;
        List<Map<String, String>> failData = new ArrayList<>();
        List<Map<String, String>> skipData = new ArrayList<>();
        for (int i = 0; i < configAllInfoList.size(); i++) {
            ConfigAllInfo configAllInfo = configAllInfoList.get(i);
            ConfigForm configForm = transferToConfigForm(configAllInfo, srcUser, targetNamespaceId);
            ConfigRequestInfo configRequestInfo = transferToConfigRequestInfo(request);
            configRequestInfo.setNamespaceTransferred(namespaceTransferred);
            if (sameConfigPolicy != SameConfigPolicy.OVERWRITE) {
                configRequestInfo.setUpdateForExist(false);
            }
            try {
                configOperationService.publishConfig(configForm, configRequestInfo, configAllInfo.getEncryptedDataKey());
                succCount++;
            } catch (ConfigAlreadyExistsException ex) {
                if (SameConfigPolicy.SKIP == sameConfigPolicy) {
                    skipCount++;
                    Map<String, String> skipItem = new HashMap<>(2);
                    skipItem.put("dataId", configAllInfo.getDataId());
                    skipItem.put("group", configAllInfo.getGroup());
                    skipData.add(skipItem);
                } else if (SameConfigPolicy.ABORT == sameConfigPolicy) {
                    Map<String, String> failedItem = new HashMap<>(2);
                    failedItem.put("dataId", configAllInfo.getDataId());
                    failedItem.put("group", configAllInfo.getGroup());
                    failData.add(failedItem);
                    // skip remaining configs
                    for (int j = i + 1; j < configAllInfoList.size(); j++) {
                        ConfigAllInfo skipConfigInfo = configAllInfoList.get(j);
                        Map<String, String> skipitem = new HashMap<>(2);
                        skipitem.put("dataId", skipConfigInfo.getDataId());
                        skipitem.put("group", skipConfigInfo.getGroup());
                        skipData.add(skipitem);
                        skipCount++;
                    }
                    break;
                }
            }
        }
        
        saveResult.put("succCount", succCount);
        saveResult.put("skipCount", skipCount);
        if (!failData.isEmpty()) {
            saveResult.put("failData", failData);
        }
        if (!skipData.isEmpty()) {
            saveResult.put("skipData", skipData);
        }
        
        return saveResult;
    }
    
    private ConfigForm transferToConfigForm(ConfigAllInfo configInfo, String srcUser, String targetNamespaceId) {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(configInfo.getDataId());
        configForm.setGroup(configInfo.getGroup());
        configForm.setNamespaceId(targetNamespaceId);
        configForm.setContent(configInfo.getContent());
        configForm.setAppName(configInfo.getAppName());
        configForm.setConfigTags(configInfo.getConfigTags());
        configForm.setDesc(configInfo.getDesc());
        configForm.setUse(configInfo.getUse());
        configForm.setEffect(configInfo.getEffect());
        configForm.setType(configInfo.getType());
        configForm.setSchema(configInfo.getSchema());
        configForm.setEncryptedDataKey(configInfo.getEncryptedDataKey());
        configForm.setSrcUser(srcUser);
        return configForm;
    }
    
    private ConfigRequestInfo transferToConfigRequestInfo(HttpServletRequest request) {
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp(RequestUtil.getRemoteIp(request));
        configRequestInfo.setSrcType(Constants.HTTP);
        configRequestInfo.setRequestIpApp(RequestUtil.getAppName(request));
        return configRequestInfo;
    }
}
