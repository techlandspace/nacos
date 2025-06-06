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
import PropTypes from 'prop-types';
import { Button, ConfigProvider, Dialog, Loading, Table, Form } from '@alifd/next';
import RegionGroup from '../../components/RegionGroup';
import NewNameSpace from '../../components/NewNameSpace';
import EditorNameSpace from '../../components/EditorNameSpace';
import { getParams, setParams, request } from '../../globalLib';

import './index.scss';
import PageTitle from '../../components/PageTitle';

@ConfigProvider.config
class NameSpace extends React.Component {
  static displayName = 'NameSpace';

  static propTypes = {
    locale: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.editgroup = React.createRef();
    this.newnamespace = React.createRef();
    this.state = {
      loading: false,
      defaultNamespace: '',
      defaultNamespaceName: 'public',
      dataSource: [],
    };
  }

  componentDidMount() {
    this.getNameSpaces(0);
  }

  getNameSpaces() {
    const { locale = {} } = this.props;
    const { prompt } = locale;
    const self = this;
    self.openLoading();
    request({
      type: 'get',
      beforeSend() {},
      url: 'v3/console/core/namespace/list',
      success: res => {
        if (res.code === 0) {
          const data = res.data || [];
          window.namespaceList = data;

          for (let i = 0; i < data.length; i++) {
            if (data[i].type === 1) {
              this.setState({
                defaultNamespace: data[i].namespace,
              });
            }
          }

          this.setState({
            dataSource: data,
          });
        } else {
          Dialog.alert({
            title: prompt,
            content: res.message,
          });
        }
      },
      complete() {
        self.closeLoading();
      },
      error: res => {
        window.namespaceList = [
          {
            namespace: '',
            namespaceShowName: '公共空间',
            type: 0,
          },
        ];
      },
    });
  }

  openLoading() {
    this.setState({
      loading: true,
    });
  }

  closeLoading() {
    this.setState({
      loading: false,
    });
  }

  detailNamespace(record) {
    const { locale = {} } = this.props;
    const { namespaceDetails, namespaceName, namespaceID, configuration, description } = locale;
    const { namespace } = record; // 获取ak,sk
    request({
      url: `v3/console/core/namespace?namespaceId=${namespace}`,
      beforeSend: () => {
        this.openLoading();
      },
      success: res => {
        res = res.data;
        if (res !== null) {
          Dialog.alert({
            style: { width: '500px' },
            needWrapper: false,
            title: namespaceDetails,
            content: (
              <div>
                <div style={{ marginTop: '10px' }}>
                  <p>
                    <span style={{ color: '#999', marginRight: 5 }}>{`${namespaceName}`}</span>
                    <span style={{ color: '#c7254e' }}>{res.namespaceShowName}</span>
                  </p>
                  <p>
                    <span style={{ color: '#999', marginRight: 5 }}>{`${namespaceID}`}</span>
                    <span style={{ color: '#c7254e' }}>{res.namespace}</span>
                  </p>
                  <p>
                    <span style={{ color: '#999', marginRight: 5 }}>{`${configuration}`}</span>
                    <span style={{ color: '#c7254e' }}>
                      {res.configCount} / {res.quota}
                    </span>
                  </p>
                  <p>
                    <span style={{ color: '#999', marginRight: 5 }}>{`${description}`}</span>
                    <span style={{ color: '#c7254e' }}>{res.namespaceDesc}</span>
                  </p>
                </div>
              </div>
            ),
          });
        }
      },
      complete: () => {
        this.closeLoading();
      },
    });
  }

  removeNamespace(record) {
    const { locale = {} } = this.props;
    const {
      removeNamespace,
      confirmDelete,
      namespaceName,
      namespaceID,
      configurationManagement,
      deletedFailure,
    } = locale;
    Dialog.confirm({
      title: removeNamespace,
      content: (
        <div style={{ marginTop: '-20px' }}>
          <h3>{confirmDelete}</h3>
          <p>
            <span style={{ color: '#999', marginRight: 5 }}>{`${namespaceName}`}</span>
            <span style={{ color: '#c7254e' }}>{record.namespaceShowName}</span>
          </p>
          <p>
            <span style={{ color: '#999', marginRight: 5 }}>{`${namespaceID}`}</span>
            <span style={{ color: '#c7254e' }}>{record.namespace}</span>
          </p>
        </div>
      ),
      onOk: () => {
        const url = `v3/console/core/namespace?namespaceId=${record.namespace}`;
        request({
          url,
          type: 'delete',
          success: res => {
            res = res.data;
            const _payload = {};
            _payload.title = configurationManagement;
            if (res === true) {
              const urlnamespace = getParams('namespace');
              if (record.namespace === urlnamespace) {
                setParams('namespace', this.state.defaultNamespace);
                setParams('namespaceShowName', this.state.defaultNamespaceName);
                window.nownamespace = this.state.defaultNamespace;
                window.namespaceShowName = this.state.defaultNamespaceName;
              }
            } else {
              Dialog.alert({ content: res.message, title: deletedFailure });
            }
            this.getNameSpaces();
          },
        });
      },
    });
  }

  refreshNameSpace() {
    request({
      type: 'get',
      url: 'v3/console/core/namespace/list',
      success: res => {
        if (res.code === 0) {
          window.namespaceList = res.data;
        }
      },
      error: res => {
        window.namespaceList = [
          {
            namespace: '',
            namespaceShowName: '公共空间',
            type: 0,
          },
        ];
      },
    });
  }

  openToEdit(record) {
    this.editgroup.current.getInstance().openDialog(record);
  }

  renderOption(value, index, record) {
    const { locale = {} } = this.props;
    const { namespaceDelete, details, edit } = locale;
    let _delinfo = (
      <a onClick={this.removeNamespace.bind(this, record)} style={{ marginRight: 10 }}>
        {namespaceDelete}
      </a>
    );
    if (record.type === 0) {
      _delinfo = (
        <span style={{ marginRight: 10, cursor: 'not-allowed', color: '#999' }} disabled>
          {namespaceDelete}
        </span>
      );
    }
    const _detailinfo = (
      <a onClick={this.detailNamespace.bind(this, record)} style={{ marginRight: 10 }}>
        {details}
      </a>
    );

    let _editinfo = <a onClick={this.openToEdit.bind(this, record)}>{edit}</a>;
    if (record.type === 0) {
      _editinfo = (
        <span style={{ marginRight: 10, cursor: 'not-allowed', color: '#999' }} disabled>
          {edit}
        </span>
      );
    }
    return (
      <div>
        {_detailinfo}
        {_delinfo}
        {_editinfo}
      </div>
    );
  }

  addNameSpace() {
    this.newnamespace.current.getInstance().openDialog(this.state.dataSource);
  }

  renderName(value, index, record) {
    const { locale = {} } = this.props;
    const { namespacePublic } = locale;
    let name = record.namespaceShowName;
    if (record.type === 0) {
      name = namespacePublic;
    }
    return <div>{name}</div>;
  }

  render() {
    const { locale = {} } = this.props;
    const {
      pubNoData,
      namespace,
      namespaceAdd,
      namespaceNames,
      description,
      namespaceNumber,
      configuration,
      namespaceOperation,
    } = locale;
    return (
      <>
        <PageTitle title={namespace} />
        <div className="fusion-demo">
          <Loading
            shape="flower"
            tip="Loading..."
            color="#333"
            style={{ width: '100%' }}
            visible={this.state.loading}
          >
            <div
              style={{
                position: 'relative',
                marginTop: 10,
                height: 'auto',
                overflow: 'visible',
              }}
            >
              <Form inline>
                <Form.Item>
                  <Button type="primary" onClick={this.addNameSpace.bind(this)}>
                    {namespaceAdd}
                  </Button>
                </Form.Item>
                <Form.Item>
                  <Button type="secondary" onClick={() => this.getNameSpaces()}>
                    {locale.refresh}
                  </Button>
                </Form.Item>
              </Form>
            </div>

            <div>
              <div>
                <Table dataSource={this.state.dataSource} locale={{ empty: pubNoData }}>
                  <Table.Column
                    title={namespaceNames}
                    dataIndex="namespaceShowName"
                    cell={this.renderName.bind(this)}
                  />
                  <Table.Column title={namespaceNumber} dataIndex="namespace" />
                  <Table.Column title={description} dataIndex="namespaceDesc" />
                  <Table.Column title={configuration} dataIndex="configCount" />
                  <Table.Column
                    title={namespaceOperation}
                    dataIndex="time"
                    cell={this.renderOption.bind(this)}
                  />
                </Table>
              </div>
            </div>
            <NewNameSpace ref={this.newnamespace} getNameSpaces={this.getNameSpaces.bind(this)} />
            <EditorNameSpace ref={this.editgroup} getNameSpaces={this.getNameSpaces.bind(this)} />
          </Loading>
        </div>
      </>
    );
  }
}

export default NameSpace;
