import { throttle, call, select, put, take } from "redux-saga/effects";
import Request from "libs/request";
import { initializeSettingsAction } from "oxalis/model/actions/settings_actions";

export function* initializeSettingsAsync() {
  yield take("SET_DATASET");
  const datasetName = yield select(state => state.dataset.name);
  const [initialUserSettings, initialDatasetSettings] = yield [
    call(Request.receiveJSON.bind(Request), "/api/user/userConfiguration"),
    call(Request.receiveJSON.bind(Request), `/api/dataSetConfigurations/${datasetName}`),
  ];
  const action = initializeSettingsAction(initialUserSettings, initialDatasetSettings);
  yield put(action);
}

function* pushUserSettingsAsync() {
  const payload = yield select(state => state.userConfiguration);
  yield call(Request.sendJSONReceiveJSON.bind(Request), "/api/user/userConfiguration", { data: payload });
}

function* pushDatasetSettingsAsync() {
  const datasetName = yield select(state => state.dataset.name);
  const payload = yield select(state => state.datasetConfiguration);
  yield call(Request.sendJSONReceiveJSON.bind(Request), `/api/dataSetConfigurations/${datasetName}`, { data: payload });
}

export function* watchPushSettingsAsync() {
  yield take("INITIALIZE_SETTINGS");
  yield [
    throttle(500, "UPDATE_USER_SETTING", pushUserSettingsAsync),
    throttle(500, "UPDATE_DATASET_SETTING", pushDatasetSettingsAsync),
    throttle(500, "UPDATE_LAYER_SETTING", pushDatasetSettingsAsync),
  ];
}
