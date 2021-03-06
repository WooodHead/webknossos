// This module should be used to access the window object, so it can be mocked in the unit tests
// mockRequire("libs/window", myFakeWindow);
export const alert = typeof window === "undefined" ? console.log.bind(console) : window.alert;
export const document = typeof window === "undefined" ? {} : window.document;
export const location =
  typeof window === "undefined" ? { reload: () => {} /* noop */ } : window.location;

export default (typeof window === "undefined" ? null : window);
