const _ = require("lodash")
const fetch = require("isomorphic-fetch")

//  Request Helper Module
//  Collection of static methods for up/downloading and content convertion.
export default class Request {

  // Build fetch-from method and inject given converter
  static fetchFactory(converter) {

    function responseHandler(response) {

      if (response.status >= 200 && response.status < 300)
        return response

      const error = new Error(response.statusText)
      error.response = response
      return Promise.reject(error)
    }


    function from(url, options) {

      if(!url.startsWith("http")){
        url = "http://localhost:9000" + url
      }

      return fetch(url, options)
        .then(responseHandler)
        .then(converter)
        .catch((e) => {
          console.error(e)
          return Promise.reject(e)
        })
    }

    function upload(url, options) {

      var body
      if(typeof(options.data) == "string")
        body = options.data
      else
        body = JSON.stringify(options.data)

      const headers = new Headers()
      headers.set("Content-Type", "application/json")

      const _options = _.defaultsDeep(options, {
        method : "POST",
        body : body,
        headers : headers,
      })

      return from(url, _options)
    }

    return {
      "from" : from,
      "upload" : upload
    }
  }


  // CONVERTERS
  static text() {
    return this.fetchFactory(response => response.text())
  }

  static json() {
    return this.fetchFactory(response => response.json())
  }
}
