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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import com.alibaba.nacos.console.paramcheck.ConsoleDefaultHttpParamExtractor;
import com.alibaba.nacos.core.controller.compatibility.Compatibility;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.service.NacosServerStateService;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Map;

import static com.alibaba.nacos.common.utils.StringUtils.FOLDER_SEPARATOR;
import static com.alibaba.nacos.common.utils.StringUtils.TOP_PATH;
import static com.alibaba.nacos.common.utils.StringUtils.WINDOWS_FOLDER_SEPARATOR;

/**
 * Server state controller.
 *
 * @author xingxuechao on:2019/2/27 11:17 AM
 */
@RestController
@RequestMapping("/v1/console/server")
@ExtractorManager.Extractor(httpExtractor = ConsoleDefaultHttpParamExtractor.class)
@EnabledInnerHandler
public class ServerStateController {
    
    private static final String ANNOUNCEMENT_FILE = "announcement.conf";
    
    private static final String GUIDE_FILE = "console-guide.conf";
    
    private final NacosServerStateService stateService;
    
    public ServerStateController(NacosServerStateService stateService) {
        this.stateService = stateService;
    }
    
    /**
     * Get server state of current server.
     *
     * @return state json.
     */
    @GetMapping("/state")
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/server/state")
    public ResponseEntity<Map<String, String>> serverState() {
        return ResponseEntity.ok(stateService.getServerState());
    }
    
    @GetMapping("/announcement")
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/server/announcement")
    public RestResult<String> getAnnouncement(
            @RequestParam(required = false, name = "language", defaultValue = "zh-CN") String language) {
        String file = ANNOUNCEMENT_FILE.substring(0, ANNOUNCEMENT_FILE.length() - 5) + "_" + language + ".conf";
        if (file.contains(TOP_PATH) || file.contains(FOLDER_SEPARATOR) || file.contains(WINDOWS_FOLDER_SEPARATOR)) {
            throw new IllegalArgumentException("Invalid filename");
        }
        File announcementFile = new File(EnvUtil.getConfPath(), file);
        String announcement = null;
        if (announcementFile.exists() && announcementFile.isFile()) {
            announcement = DiskUtils.readFile(announcementFile);
        }
        return RestResultUtils.success(announcement);
    }
    
    @GetMapping("/guide")
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/console/server/guide")
    public RestResult<String> getConsoleUiGuide() {
        File guideFile = new File(EnvUtil.getConfPath(), GUIDE_FILE);
        String guideInformation = null;
        if (guideFile.exists() && guideFile.isFile()) {
            guideInformation = DiskUtils.readFile(guideFile);
        }
        return RestResultUtils.success(guideInformation);
    }
}
