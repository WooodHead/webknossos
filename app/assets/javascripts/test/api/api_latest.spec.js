/* eslint import/no-extraneous-dependencies: ["error", {"peerDependencies": true}] */
import test from "ava";
import mockRequire from "mock-require";
import sinon from "sinon";
import _ from "lodash";
import Backbone from "backbone";
import "backbone.marionette";
import constants from "oxalis/constants";
import TRACING_OBJECT from "../fixtures/tracing_object";

function makeModelMock() {
  class ModelMock {}
  ModelMock.prototype.fetch = sinon.stub();
  ModelMock.prototype.fetch.returns(Promise.resolve());
  ModelMock.prototype.get = function (key) { return this[key]; };
  ModelMock.prototype.set = function (key, val) { this[key] = val; };
  return ModelMock;
}

const User = makeModelMock();
const DatasetConfiguration = makeModelMock();
const Request = {
  receiveJSON: sinon.stub(),
  sendJSONReceiveJSON: sinon.stub(),
  sendArraybufferReceiveArraybuffer: sinon.stub(),
  always: () => Promise.resolve(),
};
const ErrorHandling = {
  assertExtendContext: _.noop,
  assertExists: _.noop,
  assert: _.noop,
};
const window = {
  location: {
    pathname: "annotationUrl",
  },
  alert: console.log.bind(console),
};
const currentUser = {
  firstName: "SCM",
  lastName: "Boy",
};
const app = {
  vent: Backbone.Radio.channel("global"),
  currentUser,
};
const KeyboardJS = {
  bind: _.noop,
  unbind: _.noop,
};

mockRequire("libs/toast", { error: _.noop });
mockRequire("libs/window", window);
mockRequire("libs/request", Request);
mockRequire("libs/error_handling", ErrorHandling);
mockRequire("app", app);
mockRequire("oxalis/model/volumetracing/volumetracing", _.noop);
mockRequire("oxalis/model/user", User);
mockRequire("oxalis/model/dataset_configuration", DatasetConfiguration);
mockRequire("keyboardjs", KeyboardJS);

// Avoid node caching and make sure all mockRequires are applied
const Model = mockRequire.reRequire("oxalis/model").default;
const OxalisApi = mockRequire.reRequire("oxalis/api/api_loader").default;

test.beforeEach((t) => {
  const model = t.context.model = new Model();
  model.set("state", { position: [1, 2, 3] });
  model.set("tracingType", "tracingTypeValue");
  model.set("tracingId", "tracingIdValue");
  model.set("controlMode", constants.CONTROL_MODE_TRACE);

  const webknossos = t.context.webknossos = new OxalisApi(model);

  Request.receiveJSON.returns(Promise.resolve(_.cloneDeep(TRACING_OBJECT)));
  User.prototype.fetch.returns(Promise.resolve());

  return model.fetch()
    .then(() => {
      // Trigger the event ourselves, as the OxalisController is not instantiated
      app.vent.trigger("webknossos:ready");
      webknossos.apiReady(2).then((apiObject) => {
        t.context.api = apiObject;
      });
    })
    .catch((error) => {
      console.error("model.fetch() failed", error);
      fail(error.message);
    });
});

test("getActiveNodeId should get the active node id", (t) => {
  const api = t.context.api;
  t.is(api.tracing.getActiveNodeId(), 3);
});

test("setActiveNode should set the active node id", (t) => {
  const api = t.context.api;
  api.tracing.setActiveNode(1);
  t.is(api.tracing.getActiveNodeId(), 1);
});

test("getActiveTree should get the active tree id", (t) => {
  const api = t.context.api;
  api.tracing.setActiveNode(3);
  t.is(api.tracing.getActiveTreeId(), 2);
});

test("getAllNodes should get a list of all nodes", (t) => {
  const api = t.context.api;
  const nodes = api.tracing.getAllNodes();
  t.is(nodes.length, 3);
});

test("getCommentForNode should get the comment of a node", (t) => {
  const api = t.context.api;
  const comment = api.tracing.getCommentForNode(3);
  t.is(comment, "Test");
});

test("getCommentForNode should throw an error if the supplied treeId doesn't exist", (t) => {
  const api = t.context.api;
  t.throws(() => api.tracing.getCommentForNode(3, 3));
});

test("setCommentForNode should set the comment of a node", (t) => {
  const api = t.context.api;
  const COMMENT = "a comment";
  api.tracing.setCommentForNode(COMMENT, 2);
  const comment = api.tracing.getCommentForNode(2);
  t.is(comment, COMMENT);
});

test("setCommentForNode should throw an error if the supplied nodeId doesn't exist", (t) => {
  const api = t.context.api;
  t.throws(() => api.tracing.setCommentForNode("another comment", 4));
});


test("Data Api getLayerNames should get an array of all layer names", (t) => {
  const api = t.context.api;
  t.is(api.data.getLayerNames().length, 2);
  t.regex(api.data.getLayerNames(), /segmentation/);
  t.regex(api.data.getLayerNames(), /color/);
});

test("setMapping should throw an error if the layer name is not valid", (t) => {
  const api = t.context.api;
  t.throws(() => api.data.setMapping("nonExistingLayer", [1, 3]));
});

test("setMapping should set a mapping of a layer", (t) => {
  const { api, model } = t.context;
  const cube = model.getBinaryByName("segmentation").cube;
  t.is(cube.hasMapping(), false);
  api.data.setMapping("segmentation", [1, 3]);
  t.is(cube.hasMapping(), true);
  t.is(cube.mapId(1), 3);
});

test("getBoundingBox should throw an error if the layer name is not valid", (t) => {
  const api = t.context.api;
  t.throws(() => api.data.getBoundingBox("nonExistingLayer"));
});

test("getBoundingBox should get the bounding box of a layer", (t) => {
  const api = t.context.api;
  const correctBoundingBox = [[3840, 4220, 2304], [3968, 4351, 2688]];
  const boundingBox = api.data.getBoundingBox("color");
  t.deepEqual(boundingBox, correctBoundingBox);
});

test("getDataValue should throw an error if the layer name is not valid", (t) => {
  const api = t.context.api;
  t.throws(() => api.data.getDataValue("nonExistingLayer", [1, 2, 3]));
});

test("getDataValue should get the data value for a layer, position and zoomstep", (t) => {
  // Currently, this test only makes sure pullQueue.pull is being called.
  // There is another spec for pullqueue.js
  const { api, model } = t.context;
  const cube = model.getBinaryByName("segmentation").cube;

  sinon.stub(cube.pullQueue, "pull").returns([Promise.resolve(true)]);
  sinon.stub(cube, "getDataValue").returns(1337);

  return api.data.getDataValue("segmentation", [3840, 4220, 2304], 0).then((dataValue) => {
    t.is(dataValue, 1337);
  });
});

test("User Api: setConfiguration should set and get a user configuration value", (t) => {
  const api = t.context.api;
  const MOVE_VALUE = 10;
  api.user.setConfiguration("moveValue", MOVE_VALUE);
  t.is(api.user.getConfiguration("moveValue"), MOVE_VALUE);
});

test.serial.cb("Utils Api: sleep should sleep", (t) => {
  const api = t.context.api;
  let bool = false;
  api.utils.sleep(200).then(() => { bool = true; });
  t.false(bool);
  setTimeout(() => {
    t.true(bool, true);
    t.end();
  }, 400);
});

test("registerKeyHandler should register a key handler and return a handler to unregister it again", (t) => {
  const api = t.context.api;
  // Unfortunately this is not properly testable as KeyboardJS doesn't work without a DOM
  sinon.spy(KeyboardJS, "bind");
  sinon.spy(KeyboardJS, "unbind");
  const binding = api.utils.registerKeyHandler("g", () => {});
  t.true(KeyboardJS.bind.calledOnce);
  binding.unregister();
  t.true(KeyboardJS.unbind.calledOnce);
});

test("registerOverwrite should overwrite an existing function", (t) => {
  const api = t.context.api;
  let bool = false;
  api.utils.registerOverwrite("SET_ACTIVE_NODE", (store, call, action) => {
    bool = true;
    call(action);
  });

  api.tracing.setActiveNode(2);
  // The added instructions should have been executed
  t.true(bool);
  // And the original method should have been called
  t.is(api.tracing.getActiveNodeId(), 2);
});