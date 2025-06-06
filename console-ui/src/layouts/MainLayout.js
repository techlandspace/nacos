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

import React from 'react';
import { withRouter } from 'react-router-dom';
import { connect } from 'react-redux';
import PropTypes from 'prop-types';
import { ConfigProvider, Icon, Menu, Message, Dialog, Badge } from '@alifd/next';
import Header from './Header';
import { getState, getNotice, getGuide } from '../reducers/base';
import getMenuData, { McpServerManagementRoute, McpServerManagementRouteName } from './menu';
import './index.scss';

const { SubMenu, Item } = Menu;

@withRouter
@connect(state => ({ ...state.locale, ...state.base }), { getState, getNotice, getGuide })
@ConfigProvider.config
class MainLayout extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      visible: true,
    };
  }

  static displayName = 'MainLayout';

  static propTypes = {
    locale: PropTypes.object,
    location: PropTypes.object,
    history: PropTypes.object,
    version: PropTypes.any,
    startupMode: PropTypes.any,
    getState: PropTypes.func,
    functionMode: PropTypes.string,
    authEnabled: PropTypes.string,
    children: PropTypes.array,
    getNotice: PropTypes.func,
    notice: PropTypes.string,
    consoleUiEnable: PropTypes.string,
    getGuide: PropTypes.func,
    guideMsg: PropTypes.string,
  };

  componentDidMount() {
    this.props.getState();
    this.props.getNotice();
    this.props.getGuide();
  }

  goBack() {
    this.props.history.goBack();
  }

  navTo(url) {
    const { search } = this.props.location;
    let urlSearchParams = new URLSearchParams(search);
    urlSearchParams.set('namespace', window.nownamespace);
    urlSearchParams.set('namespaceShowName', window.namespaceShowName);
    this.props.history.push([url, '?', urlSearchParams.toString()].join(''));
  }

  isCurrentPath(url) {
    const { location } = this.props;
    return url === location.pathname ? 'current-path next-selected' : undefined;
  }

  defaultOpenKeys() {
    const MenuData = getMenuData(this.props.functionMode);
    for (let i = 0, len = MenuData.length; i < len; i++) {
      const { children } = MenuData[i];
      if (children && children.filter(({ url }) => url === this.props.location.pathname).length) {
        return String(i);
      }
    }
  }

  isShowGoBack() {
    const urls = [];
    const MenuData = getMenuData(this.props.functionMode);
    MenuData.forEach(item => {
      if (item.url) urls.push(item.url);
      if (item.children) item.children.forEach(({ url }) => urls.push(url));
    });
    return !urls.includes(this.props.location.pathname);
  }

  render() {
    const {
      locale = {},
      version,
      functionMode,
      authEnabled,
      consoleUiEnable,
      startupMode,
    } = this.props;
    const { visible } = this.state;
    const MenuData = getMenuData(functionMode);
    return (
      <section
        className="next-shell next-shell-desktop next-shell-brand"
        style={{ minHeight: '100vh' }}
      >
        <Header />
        <section className="next-shell-sub-main">
          <div className="main-container next-shell-main">
            <div className="left-panel next-aside-navigation">
              <div
                className="next-shell-navigation next-shell-mini next-shell-aside"
                style={{ padding: 0 }}
              >
                {this.isShowGoBack() ? (
                  <div className="go-back" onClick={() => this.goBack()}>
                    <Icon type="arrow-left" />
                  </div>
                ) : (
                  <>
                    <h1 className="nav-title">
                      {locale.nacosName}
                      <span>{version}</span>
                    </h1>
                    <h1 className="nav-mode">
                      {locale.nacosMode}
                      <span>{startupMode}</span>
                    </h1>
                    <Menu
                      defaultOpenKeys={this.defaultOpenKeys()}
                      className="next-nav next-normal next-active next-right next-no-arrow next-nav-embeddable"
                      openMode="single"
                    >
                      {consoleUiEnable &&
                        consoleUiEnable === 'true' &&
                        MenuData.map((subMenu, idx) => {
                          if (subMenu.children) {
                            const sublabel = subMenu.badge ? (
                              <span>
                                <Badge
                                  content={subMenu.badge}
                                  style={{
                                    backgroundColor: '#FC0E3D',
                                    color: '#FFFFFF',
                                    right: '-45px',
                                    top: '-10px',
                                  }}
                                >
                                  {locale[subMenu.key]}
                                </Badge>
                              </span>
                            ) : (
                              `${locale[subMenu.key]}`
                            );
                            return (
                              <SubMenu key={String(idx)} label={sublabel}>
                                {subMenu.children.map((item, i) => (
                                  <Item
                                    key={[idx, i].join('-')}
                                    onClick={() => this.navTo(item.url)}
                                    className={this.isCurrentPath(item.url)}
                                  >
                                    {locale[item.key]}
                                  </Item>
                                ))}
                              </SubMenu>
                            );
                          }
                          return (
                            <Item
                              key={String(idx)}
                              className={['first-menu', this.isCurrentPath(subMenu.url)]
                                .filter(c => c)
                                .join(' ')}
                              onClick={() => this.navTo(subMenu.url)}
                            >
                              {locale[subMenu.key]}
                            </Item>
                          );
                        })}
                    </Menu>
                  </>
                )}
              </div>
            </div>
            <div className="right-panel next-shell-sub-main">
              {authEnabled === 'false' && consoleUiEnable === 'true' ? (
                <Message type="notice">
                  <div dangerouslySetInnerHTML={{ __html: this.props.notice }} />
                </Message>
              ) : null}
              {consoleUiEnable === 'false' && (
                <Dialog
                  visible={visible}
                  title={locale.consoleClosed}
                  style={{ width: 600 }}
                  hasMask={false}
                  footer={false}
                  className="enable-dialog"
                >
                  <Message type="notice">
                    <div
                      style={{ lineHeight: '24px' }}
                      dangerouslySetInnerHTML={{ __html: this.props.guideMsg }}
                    />
                  </Message>
                </Dialog>
              )}
              {this.props.children}
            </div>
          </div>
        </section>
      </section>
    );
  }
}

export default MainLayout;
