_          = require("lodash")
Marionette = require("backbone.marionette")
Subviews   = require("backbone-subviews")

class CategoryView extends Marionette.ItemView


  template : _.template("""
    <div class="panel panel-default">
      <div class="panel-heading" data-toggle="collapse" data-target="#user-settings-<%- tabId %>">
        <h4 class="panel-title">
          <a>
            <i class="caret-down"></i>
            <i class="caret-right"></i>
            <%- caption %>
          </a>
        </h4>
      </div>
      <div id="user-settings-<%- tabId %>" class="panel-collapse collapse in">
        <div class="panel-body">

          <% _.forEach(subviewCreators, function (subview, key) { %>
            <div data-subview="<%- key %>"></div>
          <% }) %>

        </div>
      </div>
    </div>
  """)


  initialize : ->

    Subviews.add(this)


  serializeData : ->

    return { @subviewCreators, @caption, tabId : _.uniqueId() }


  hide : ->

    @$el.hide()


  show : ->

    @$el.show()

module.exports = CategoryView
