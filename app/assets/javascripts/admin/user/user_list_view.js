// @flow
/* eslint-disable jsx-a11y/href-no-hash */

import _ from "lodash";
import * as React from "react";
import { Link, withRouter } from "react-router-dom";
import { Table, Tag, Icon, Spin, Button, Input } from "antd";
import TeamRoleModalView from "admin/user/team_role_modal_view";
import ExperienceModalView from "admin/user/experience_modal_view";
import TemplateHelpers from "libs/template_helpers";
import Utils from "libs/utils";
import { getEditableUsers, updateUser } from "admin/admin_rest_api";
import Persistence from "libs/persistence";
import { PropTypes } from "@scalableminds/prop-types";
import type { APIUserType, APITeamRoleType, ExperienceMapType } from "admin/api_flow_types";
import type { RouterHistory } from "react-router-dom";

const { Column } = Table;
const { Search } = Input;

type Props = {
  history: RouterHistory,
};

type State = {
  isLoading: boolean,
  users: Array<APIUserType>,
  selectedUserIds: Array<string>,
  isExperienceModalVisible: boolean,
  isTeamRoleModalVisible: boolean,
  activationFilter: Array<"true" | "false">,
  searchQuery: string,
};

const persistence: Persistence<State> = new Persistence(
  {
    searchQuery: PropTypes.string,
    activationFilter: PropTypes.arrayOf(PropTypes.string),
  },
  "userList",
);

class UserListView extends React.PureComponent<Props, State> {
  state = {
    isLoading: true,
    users: [],
    selectedUserIds: [],
    isExperienceModalVisible: false,
    isTeamRoleModalVisible: false,
    activationFilter: ["true"],
    searchQuery: "",
  };

  componentWillMount() {
    this.setState(persistence.load(this.props.history));
  }

  componentDidMount() {
    this.fetchData();
  }

  componentWillUpdate(nextProps, nextState) {
    persistence.persist(this.props.history, nextState);
  }

  async fetchData(): Promise<void> {
    const users = await getEditableUsers();

    this.setState({
      isLoading: false,
      users,
    });
  }

  activateUser = (selectedUser: APIUserType, isActive: boolean = true): void => {
    const newUsers = this.state.users.map(user => {
      if (selectedUser.id === user.id) {
        const newUser = Object.assign({}, user, { isActive });

        updateUser(newUser);
        return newUser;
      }

      return user;
    });

    this.setState({
      users: newUsers,
      selectedUserIds: [selectedUser.id],
      isTeamRoleModalVisible: isActive,
    });
  };

  deactivateUser = (user: APIUserType): void => {
    this.activateUser(user, false);
  };

  handleUsersChange = (updatedUsers: Array<APIUserType>): void => {
    this.setState({
      users: updatedUsers,
      isExperienceModalVisible: false,
      isTeamRoleModalVisible: false,
    });
  };

  handleSearch = (event: SyntheticInputEvent<>): void => {
    this.setState({ searchQuery: event.target.value });
  };

  handleDismissActivationFilter = () => {
    this.setState({
      activationFilter: [],
    });
  };

  render() {
    const hasRowsSelected = this.state.selectedUserIds.length > 0;
    const rowSelection = {
      onChange: selectedUserIds => {
        this.setState({ selectedUserIds });
      },
      getCheckboxProps: user => ({
        disabled: !user.isActive,
      }),
    };

    const activationFilterWarning = this.state.activationFilter.includes("true") ? (
      <Tag closable onClose={this.handleDismissActivationFilter} color="blue">
        Show Active User Only
      </Tag>
    ) : null;

    const marginRight = { marginRight: 20 };

    return (
      <div className="container test-UserListView">
        <h3>Users</h3>

        {hasRowsSelected ? (
          <span style={marginRight}>{this.state.selectedUserIds.length} selected user(s)</span>
        ) : null}
        <Button
          onClick={() => this.setState({ isTeamRoleModalVisible: true })}
          icon="team"
          disabled={!hasRowsSelected}
          style={marginRight}
        >
          Edit Teams
        </Button>
        <Button
          onClick={() => this.setState({ isExperienceModalVisible: true })}
          icon="trophy"
          disabled={!hasRowsSelected}
          style={marginRight}
        >
          Change Experience
        </Button>
        {activationFilterWarning}
        <Search
          style={{ width: 200, float: "right" }}
          onPressEnter={this.handleSearch}
          onChange={this.handleSearch}
          value={this.state.searchQuery}
        />

        <Spin size="large" spinning={this.state.isLoading}>
          <Table
            dataSource={Utils.filterWithSearchQueryOR(
              this.state.users,
              ["firstName", "lastName", "email", "teams", "experiences"],
              this.state.searchQuery,
            )}
            rowKey="id"
            rowSelection={rowSelection}
            pagination={{
              defaultPageSize: 50,
            }}
            style={{ marginTop: 30, marginBotton: 30 }}
            onChange={(pagination, filters) =>
              this.setState({
                activationFilter: filters.isActive,
              })
            }
          >
            <Column
              title="Last Name"
              dataIndex="lastName"
              key="lastName"
              width={130}
              sorter={Utils.localeCompareBy("lastName")}
            />
            <Column
              title="First Name"
              dataIndex="firstName"
              key="firstName"
              width={130}
              sorter={Utils.localeCompareBy("firstName")}
            />
            <Column
              title="Email"
              dataIndex="email"
              key="email"
              sorter={Utils.localeCompareBy("email")}
            />
            <Column
              title="Experiences"
              dataIndex="experiences"
              key="experiences"
              width={300}
              render={(experiences: ExperienceMapType, user: APIUserType) =>
                _.map(experiences, (value, domain) => (
                  <Tag key={`experience_${user.id}_${domain}`}>
                    {domain} : {value}
                  </Tag>
                ))
              }
            />
            <Column
              title="Teams - Role"
              dataIndex="teams"
              key="teams_"
              width={300}
              render={(teams: Array<APITeamRoleType>, user: APIUserType) =>
                teams.map(team => (
                  <Tag
                    key={`team_role_${user.id}_${team.team}`}
                    color={TemplateHelpers.stringToColor(team.role.name)}
                  >
                    {team.team}: {team.role.name}
                  </Tag>
                ))
              }
            />
            <Column
              title="Status"
              dataIndex="isActive"
              key="isActive"
              width={110}
              filters={[
                { text: "Activated", value: "true" },
                { text: "Deactivated", value: "false" },
              ]}
              filtered
              filteredValue={this.state.activationFilter}
              onFilter={(value: boolean, user: APIUserType) => user.isActive.toString() === value}
              render={isActive => {
                const icon = isActive ? "check-circle-o" : "close-circle-o";
                return <Icon type={icon} style={{ fontSize: 20 }} />;
              }}
            />
            <Column
              title="Actions"
              key="actions"
              width={160}
              render={(__, user: APIUserType) => (
                <span>
                  <Link to={`/users/${user.id}/details`}>
                    <Icon type="user" />Show Tracings
                  </Link>
                  <br />
                  {user.isActive ? (
                    <a href="#" onClick={() => this.deactivateUser(user)}>
                      <Icon type="user-delete" />Deactivate User
                    </a>
                  ) : (
                    <a href="#" onClick={() => this.activateUser(user)}>
                      <Icon type="user-add" />Activate User
                    </a>
                  )}
                </span>
              )}
            />
          </Table>
        </Spin>
        <ExperienceModalView
          visible={this.state.isExperienceModalVisible}
          selectedUserIds={this.state.selectedUserIds}
          users={this.state.users}
          onChange={this.handleUsersChange}
          onCancel={() => this.setState({ isExperienceModalVisible: false })}
        />
        <TeamRoleModalView
          visible={this.state.isTeamRoleModalVisible}
          selectedUserIds={this.state.selectedUserIds}
          users={this.state.users}
          onChange={this.handleUsersChange}
          onCancel={() => this.setState({ isTeamRoleModalVisible: false })}
        />
      </div>
    );
  }
}

export default withRouter(UserListView);
