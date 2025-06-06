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
import {
  Button,
  Field,
  Form,
  Grid,
  Input,
  Loading,
  Pagination,
  Table,
  Dialog,
  Message,
  ConfigProvider,
  Switch,
} from '@alifd/next';
import { getParams, setParams, request } from '../../../globalLib';
import { generateUrl } from '../../../utils/nacosutil';
import RegionGroup from '../../../components/RegionGroup';
import EditServiceDialog from '../ServiceDetail/EditServiceDialog';
import ShowServiceCodeing from 'components/ShowCodeing/ShowServiceCodeing';
import PageTitle from '../../../components/PageTitle';
import TotalRender from '../../../components/Page/TotalRender';

import './ServiceList.scss';
import { GLOBAL_PAGE_SIZE_LIST } from '../../../constants';

const FormItem = Form.Item;
const { Row, Col } = Grid;
const { Column } = Table;

@ConfigProvider.config
class ServiceList extends React.Component {
  static displayName = 'ServiceList';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.editServiceDialog = React.createRef();
    this.showcode = React.createRef();
    this.state = {
      loading: false,
      total: 0,
      pageSize: 10,
      currentPage: 1,
      dataSource: [],
      search: {
        serviceName: getParams('serviceNameParam') || '',
        groupName: getParams('groupNameParam') || '',
      },
      ignoreEmptyService: !(localStorage.getItem('ignoreEmptyService') === 'false'),
    };
    this.field = new Field(this);
  }

  openLoading() {
    this.setState({ loading: true });
  }

  closeLoading() {
    this.setState({ loading: false });
  }

  openEditServiceDialog() {
    try {
      this.editServiceDialog.current.getInstance().show(this.state.service);
    } catch (error) {}
  }

  queryServiceList() {
    const { currentPage, pageSize, search, withInstances = false, ignoreEmptyService } = this.state;
    const parameter = [
      `ignoreEmptyService=${ignoreEmptyService}`,
      `withInstances=${withInstances}`,
      `pageNo=${currentPage}`,
      `pageSize=${pageSize}`,
      `serviceNameParam=${search.serviceName}`,
      `groupNameParam=${search.groupName}`,
    ];
    setParams({
      serviceNameParam: search.serviceName,
      groupNameParam: search.groupName,
    });
    this.openLoading();
    request({
      url: `v3/console/ns/service/list?${parameter.join('&')}`,
      success: ({ data: { totalCount = 0, pageItems = [] } = {} }) => {
        this.setState({
          dataSource: pageItems,
          total: totalCount,
          loading: false,
        });
      },
      error: () =>
        this.setState({
          dataSource: [],
          total: 0,
          currentPage: 0,
          loading: false,
        }),
    });
  }

  getQueryLater = () => {
    setTimeout(() => this.queryServiceList());
  };

  showcode = () => {
    setTimeout(() => this.queryServiceList());
  };

  /**
   *
   * Added method to open sample code window
   * @author yongchao9  #2019年05月18日 下午5:46:28
   *
   */
  showSampleCode(record) {
    this.showcode.current.getInstance().openDialog(record);
  }

  querySubscriber(record) {
    const { name, groupName } = record;
    const namespace = this.state.nowNamespaceId;
    this.props.history.push(generateUrl('/subscriberList', { namespace, name, groupName }));
  }

  handlePageSizeChange(pageSize) {
    this.setState({ pageSize }, () => this.queryServiceList());
  }

  deleteService(service) {
    const { locale = {} } = this.props;
    const { prompt, promptDelete } = locale;
    Dialog.confirm({
      title: prompt,
      content: promptDelete,
      onOk: () => {
        // # issue-13267 编码名称使其符合RFC规范
        const encodedServiceName = encodeURIComponent(service.name);
        request({
          method: 'DELETE',
          url: `v3/console/ns/service?serviceName=${encodedServiceName}&groupName=${service.groupName}`,
          dataType: 'json',
          beforeSend: () => this.openLoading(),
          success: res => {
            if (res.code !== 0) {
              Message.error(res.message || '删除服务失败');
            } else {
              Message.success('服务删除成功');
              this.queryServiceList();
            }
          },
          error: res => {
            Message.error(res.data?.responseText || res.statusText || '请求失败');
          },
          complete: () => this.closeLoading(),
        });
      },
    });
  }

  setNowNameSpace = (nowNamespaceName, nowNamespaceId, nowNamespaceDesc) =>
    this.setState({
      nowNamespaceName,
      nowNamespaceId,
      nowNamespaceDesc,
    });

  rowColor = row => ({ className: !row.healthyInstanceCount ? 'row-bg-red' : '' });

  render() {
    const { locale = {} } = this.props;
    const {
      pubNoData,
      serviceList,
      serviceName,
      serviceNamePlaceholder,
      groupName,
      groupNamePlaceholder,
      hiddenEmptyService,
      query,
      create,
      operation,
      detail,
      sampleCode,
      deleteAction,
      subscriber,
    } = locale;
    const {
      search,
      nowNamespaceName,
      nowNamespaceId,
      nowNamespaceDesc,
      ignoreEmptyService,
    } = this.state;
    const { init, getValue } = this.field;
    this.init = init;
    this.getValue = getValue;

    return (
      <div className="main-container service-management">
        <PageTitle
          title={serviceList}
          desc={nowNamespaceDesc}
          namespaceId={nowNamespaceId}
          namespaceName={nowNamespaceName}
          nameSpace
        />
        <RegionGroup
          setNowNameSpace={this.setNowNameSpace}
          namespaceCallBack={this.getQueryLater}
        />
        <Row
          className="demo-row"
          style={{
            marginBottom: 10,
            padding: 0,
          }}
        >
          <Col span="24">
            <Form inline field={this.field}>
              <FormItem label="">
                <Button type="primary" onClick={() => this.openEditServiceDialog()}>
                  {create}
                </Button>
              </FormItem>
              <FormItem label={serviceName}>
                <Input
                  placeholder={serviceNamePlaceholder}
                  style={{ width: 200 }}
                  value={search.serviceName}
                  onChange={serviceName => this.setState({ search: { ...search, serviceName } })}
                  onPressEnter={() =>
                    this.setState({ currentPage: 1 }, () => this.queryServiceList())
                  }
                />
              </FormItem>
              <FormItem label={groupName}>
                <Input
                  placeholder={groupNamePlaceholder}
                  style={{ width: 200 }}
                  value={search.groupName}
                  onChange={groupName => this.setState({ search: { ...search, groupName } })}
                  onPressEnter={() =>
                    this.setState({ currentPage: 1 }, () => this.queryServiceList())
                  }
                />
              </FormItem>
              <Form.Item label={`${hiddenEmptyService}`}>
                <Switch
                  checked={ignoreEmptyService}
                  onChange={ignoreEmptyService =>
                    this.setState({ ignoreEmptyService, currentPage: 1 }, () => {
                      localStorage.setItem('ignoreEmptyService', ignoreEmptyService);
                      this.queryServiceList();
                    })
                  }
                />
              </Form.Item>
              <FormItem label="">
                <Button
                  type="primary"
                  onClick={() => this.setState({ currentPage: 1 }, () => this.queryServiceList())}
                  style={{ marginRight: 10 }}
                >
                  {query}
                </Button>
              </FormItem>
            </Form>
          </Col>
        </Row>
        <Row style={{ padding: 0 }}>
          <Col span="24" style={{ padding: 0 }}>
            <Table
              dataSource={this.state.dataSource}
              locale={{ empty: pubNoData }}
              rowProps={row => this.rowColor(row)}
              loading={this.state.loading}
            >
              <Column title={locale.columnServiceName} dataIndex="name" />
              <Column title={locale.groupName} dataIndex="groupName" />
              <Column title={locale.columnClusterCount} dataIndex="clusterCount" />
              <Column title={locale.columnIpCount} dataIndex="ipCount" />
              <Column title={locale.columnHealthyInstanceCount} dataIndex="healthyInstanceCount" />
              <Column title={locale.columnTriggerFlag} dataIndex="triggerFlag" />
              <Column
                title={operation}
                align="center"
                cell={(value, index, record) => (
                  // @author yongchao9  #2019年05月18日 下午5:46:28
                  /* Add a link to view "sample code"
                     replace the original button with a label,
                     which is consistent with the operation style in configuration management.
                     */
                  <div>
                    <a
                      onClick={() => {
                        const { name, groupName } = record;
                        this.props.history.push(generateUrl('/serviceDetail', { name, groupName }));
                      }}
                      style={{ marginRight: 5 }}
                    >
                      {detail}
                    </a>
                    <span style={{ marginRight: 5 }}>|</span>
                    <a style={{ marginRight: 5 }} onClick={() => this.showSampleCode(record)}>
                      {sampleCode}
                    </a>
                    <span style={{ marginRight: 5 }}>|</span>
                    <a style={{ marginRight: 5 }} onClick={() => this.querySubscriber(record)}>
                      {subscriber}
                    </a>
                    <span style={{ marginRight: 5 }}>|</span>
                    <a onClick={() => this.deleteService(record)} style={{ marginRight: 5 }}>
                      {deleteAction}
                    </a>
                  </div>
                )}
              />
            </Table>
          </Col>
        </Row>
        <div
          style={{
            marginTop: 10,
            textAlign: 'right',
          }}
        >
          <Pagination
            current={this.state.currentPage}
            pageSizeList={GLOBAL_PAGE_SIZE_LIST}
            pageSizePosition="start"
            pageSizeSelector="dropdown"
            popupProps={{ align: 'bl tl' }}
            total={this.state.total}
            pageSize={this.state.pageSize}
            totalRender={total => <TotalRender locale={locale} total={total} />}
            onPageSizeChange={pageSize => this.handlePageSizeChange(pageSize)}
            onChange={currentPage => this.setState({ currentPage }, () => this.queryServiceList())}
          />
        </div>

        <ShowServiceCodeing ref={this.showcode} />
        <EditServiceDialog
          ref={this.editServiceDialog}
          openLoading={() => this.openLoading()}
          closeLoading={() => this.closeLoading()}
          queryServiceList={() => this.setState({ currentPage: 1 }, () => this.queryServiceList())}
        />
      </div>
    );
  }
}

export default ServiceList;
