/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.listener.startup;

import org.slf4j.Logger;

/**
 * Nacos Server Web API start up phase.
 *
 * @author xiweng.yy
 */
public class NacosWebStartUp extends AbstractNacosStartUp {
    
    public NacosWebStartUp() {
        super(NacosStartUp.WEB_START_UP_PHASE);
    }
    
    @Override
    protected String getPhaseNameInStartingInfo() {
        return "Nacos Server API";
    }
    
    @Override
    public void logStarted(Logger logger) {
        long endTimestamp = System.currentTimeMillis();
        long startupCost = endTimestamp - getStartTimestamp();
        logger.info("Nacos Server API started successfully in {} ms", startupCost);
    }
}
