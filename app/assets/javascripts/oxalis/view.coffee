### define 
jquery : $
../libs/toast : Toast
./constants : constants
./view/modal : modal
###

class View

  constructor : (@model) ->

    unless @webGlSupported()
      Toast.error("Couldn't initialise WebGL, please make sure you are using Google Chrome and WebGL is enabled.<br>"+
        "<a href='http://get.webgl.org/'>http://get.webgl.org/</a>")

    @renderer = new THREE.WebGLRenderer( clearColor: 0x000000, clearAlpha: 1.0, antialias: false )
    @scene = new THREE.Scene()
   
    @setTheme(constants.THEME_BRIGHT)
    @createKeyboardCommandOverlay()

    @model.cellTracing.on({
      emptyBranchStack : =>

        Toast.error("No more branchpoints", false)


      noBranchPoints : =>

        Toast.error("Setting branchpoints isn't necessary in this tracing mode.", false)


      wrongDirection : =>

        Toast.error("You're tracing in the wrong direction")


      updateComments : => 

        @updateComments()


      newActiveNode : => 

        @updateActiveComment()
        @updateActiveTree()


      deleteTree : =>

        @updateActiveComment()
        @updateTrees()


      mergeTree : =>

        @updateTrees()
        @updateComments()


      reloadTrees : =>

        @updateTrees()
        @updateComments()


      newNode : => 

        @updateActiveComment()
        @updateTreesThrottled()


      newTreeName : => 

        @updateTrees()
        @updateComments()


      newTree : => 

        @updateTrees()
        @updateComments()


      newActiveTreeColor : =>

        @updateTrees()


      deleteActiveNode : =>

        @updateTrees() })


    @model.user.on
      sortTreesByNameChanged : =>
        @updateTreesSortButton()
        @updateTrees()
      sortCommentsAscChanged : =>
        @updateCommentsSortButton()
        @updateComments()

    @model.cellTracing.stateLogger.on
      pushFailed       : (critical) =>
        if not critical or @reloadDenied
          Toast.error("Auto-Save failed!")
        else
          modal.show("Several attempts to reach our server have failed. You should reload the page
            to make sure that your work won't be lost.",
            [ { id : "reload-button", label : "OK, reload", callback : ( ->
              $(window).on(
                "beforeunload"
                =>return null)
              window.location.reload() )},
            {id : "cancel-button", label : "Cancel", callback : ( => @reloadDenied = true ) } ] )

    $("a[href=#tab-comments]").on "shown", (event) =>
      @updateActiveComment()
    $("a[href=#tab-trees]").on "shown", (event) =>
      @updateActiveTree()

    @updateComments()
    @updateTrees()
    @updateTreesSortButton()
    @updateCommentsSortButton()

    # disable loader, show oxalis
    $("#loader").css("display" : "none")
    $("#container").css("display" : "inline")


  toggleTheme : ->

    if @currentTheme is constants.THEME_BRIGHT 
      @setTheme(constants.THEME_DARK)
    else
      @setTheme(constants.THEME_BRIGHT)


  setTheme : (theme) ->

    if theme is constants.THEME_BRIGHT
      $("body").attr('class', 'bright')
    else
      $("body").attr('class', 'dark')

    @currentTheme = theme


  setMode : (mode) ->

    @createKeyboardCommandOverlay(mode)


  createKeyboardCommandOverlay : (mode) ->

    generalKeys =
      '<tr><th colspan="4">General</th></tr>
      <tr><td>Q</td><td>Fullscreen</td><td>K,L</td><td>Scale up/down viewports</td></tr>
      <tr><td>P,N</td><td>Previous/Next comment</td><td>Del</td><td>Delete node/Split trees</td></tr>
      <tr><td>C</td><td>Create new tree</td><td>Shift + Alt + Leftclick</td><td>Merge two trees</td></tr>
      <tr><td>T</td><td>Toggle theme</td><td>M</td><td>Toggle mode</td></tr>
      <tr><td>1</td><td>Toggle skeleton visibility</td><td>2</td><td>Toggle inactive tree visibility</td></tr>
      <tr><td>Shift + Mousewheel</td><td>Change node size</td><td></td><td></td></tr>'

    TDViewKeys =
      '<tr><th colspan="4">3D-view</th></tr>
      <tr><td>Mousewheel</td><td>Zoom in and out</td><td>Rightclick drag</td><td>Rotate 3D View</td></tr>'
    viewportKeys =
      '<tr><th colspan="4">Viewports</th></tr>
      <tr><td>Leftclick or Arrow keys</td><td>Move</td><td>Shift + Leftclick</td><td>Select node</td></tr>
      <tr><td>Mousewheel or D and F</td><td>Move along 3rd axis</td><td>Rightclick</td><td>Set tracepoint</td></tr>
      <tr><td>I,O or Alt + Mousewheel</td><td>Zoom in/out</td><td>B,J</td><td>Set/Jump to branchpoint</td></tr>
      <tr><td>S</td><td>Center active node</td><td></td><td></td></tr>'
    arbitraryKeys =
      '<tr><th colspan="4">Flightmode</th></tr>
      <tr><td>Mouse or Arrow keys</td><td>Rotation</td><td>R</td><td>Reset rotation</td></tr>
      <tr><td>Shift + Mouse or Shift + Arrow</td><td>Rotation around Axis</td><td>W A S D</td><td>Move</td></tr>
      <tr><td>Space</td><td>Forward</td><td>B, J</td><td>Set/Jump to last branchpoint</td></tr>
      <tr><td>Y</td><td>Center active node</td><td>I, O</td><td>Zoom in and out</td></tr>
      <tr><td>Z, U</td><td>Start/Stop recording waypoints</td><td>Shift + Space</td><td>Delete active node, Recenter previous node</td></tr>'
    volumeKeys =
      '<tr><th colspan="4">Volume Tracing</th></tr>
      <tr><td>Left Mouse drag</td><td>Add to current Cell</td><td>Ctrl + Left Mouse drag / Arrow keys</td><td>Move</td></tr>
      <tr><td>Shift + Left Mouse drag / Right Mouse drag</td><td>Remove voxels from cell</td><td>Left mouse click</td><td>Set active cell</td></tr>
      <tr><td>C</td><td>Create new cell</td><td></td><td></td></tr>'

    html = '''<div class="modal-header"><button type="button" class="close" data-dismiss="modal">x</button>
            <h3>keyboard commands</h3></div>
            <div class="modal-body" id="help-modal-body"><p>
            <table class="table table-condensed table-nohead table-bordered"><tbody>'''
      
    html += generalKeys
    if mode == constants.MODE_PLANE_TRACING 
      html += viewportKeys + TDViewKeys
    else if mode == constants.MODE_ARBITRARY
      html += arbitraryKeys
    else if mode == constants.MODE_VOLUME
      html += volumeKeys

    html += '''</tbody>
            </table>
            <br>
            <p>All other options like moving speed, clipping distance and particle size can be adjusted in the options located to the left.
            <br>Select the different categories to open/close them.
            Please report any issues.</p>
            </p></div><div class="modal-footer">
            <a href="#" class="btn" data-dismiss="modal">Close</a></div>'''

    $("#help-modal").html(html)


  updateComments : ->
    
    comments = @model.cellTracing.getComments( @model.user.get("sortCommentsAsc") )
    commentList = $("#comment-list")
    commentList.empty()

    # DOM container to append all elements at once for increased performance
    newContent = document.createDocumentFragment()

    lastTreeId = -1
    for comment in comments
      treeId = comment.node.treeId
      if treeId != lastTreeId
        newContent.appendChild((
          $('<li>').append($('<i>', {"class": "icon-sitemap"}),
          $('<span>', {"data-treeid": treeId, "text": @model.cellTracing.getTree(treeId)?.name})))[0])
        lastTreeId = treeId
      newContent.appendChild((
        $('<li>').append($('<i>', {"class": "icon-angle-right"}), 
        $('<a>', {"href": "#", "data-nodeid": comment.node.id, "text": comment.node.id + " - " + comment.content})))[0])

    commentList.append(newContent)

    @updateActiveComment()


  updateActiveComment : ->

    comment = @model.cellTracing.getComment()
    if comment
      $("#comment-input").val(comment)
    else
      $("#comment-input").val("")

    oldIcon = $("#comment-container i.icon-arrow-right")
    if oldIcon.length
      oldIcon.toggleClass("icon-arrow-right", false)
      oldIcon.toggleClass("icon-angle-right", true)

    activeHref = $("#comment-container a[data-nodeid=#{@model.cellTracing.getActiveNodeId()}]")
    if activeHref.length

      newIcon = activeHref.parent("li").children("i")
      newIcon.toggleClass("icon-arrow-right", true)
      newIcon.toggleClass("icon-angle-right", false)

      # animate scrolling to the new comment
      $("#comment-container").animate({
        scrollTop: newIcon.offset().top - $("#comment-container").offset().top + $("#comment-container").scrollTop()}, 250)
    else
      activeTree = $("#comment-container span[data-treeid=#{@model.cellTracing.getActiveTreeId()}]")
      if activeTree.length
        $("#comment-container").animate({
          scrollTop: activeTree.offset().top - $("#comment-container").offset().top + $("#comment-container").scrollTop()}, 250)


  updateActiveTree : ->

    activeTree = @model.cellTracing.getTree()
    if activeTree
      $("#tree-name-input").val(activeTree.name)
      $("#tree-name").text(activeTree.name)
      $("#tree-active-color").css("color": "##{('000000'+activeTree.color.toString(16)).slice(-6)}")
      activeHref = $("#tree-list a[data-treeid=#{activeTree.treeId}]")

    oldIcon = $("#tree-list i.icon-arrow-right")
    if oldIcon.length
      oldIcon.toggleClass("icon-arrow-right", false)
      oldIcon.toggleClass("icon-bull", true)

    if activeHref?.length

      newIcon = activeHref.parent("li").children("i")
      newIcon.toggleClass("icon-arrow-right", true)
      newIcon.toggleClass("icon-bull", false)

      # animate scrolling to the new tree
      $("#tree-list").animate({
        scrollTop: newIcon.offset().top - $("#tree-list").offset().top + $("#tree-list").scrollTop()}, 250)


  updateTreesThrottled : ->
    # avoid lags caused by frequent DOM modification

    @updateTreesThrottled = _.throttle(
      => @updateTrees()
      1000
    )
    @updateTreesThrottled()


  updateTrees : ->

    trees = @model.cellTracing.getTreesSorted()

    treeList = $("#tree-list")
    treeList.empty()

    newContent = document.createDocumentFragment()

    for tree in trees
      newContent.appendChild((
        $('<li>').append($('<i>', {"class": "icon-bull"}),
          $('<a>', {"href": "#", "data-treeid": tree.treeId})
          .append($('<span>', {"title": "nodes", "text": tree.nodes.length}).css("display": "inline-block", "width": "50px"),
          $('<i>', {"class": "icon-sign-blank"}).css(
            "color": "##{('000000'+tree.color.toString(16)).slice(-6)}"),
          $('<span>', {"title": "name", "text": tree.name}) )) )[0])

    treeList.append(newContent)

    @updateActiveTree()


  updateTreesSortButton : ->

    @toggleIconVisibility(
      @model.user.get("sortTreesByName"),
      $("#sort-name-icon"),
      $("#sort-id-icon"))


  updateCommentsSortButton : ->

    @toggleIconVisibility(
      @model.user.get("sortCommentsAsc"),
      $("#sort-asc-icon"),
      $("#sort-desc-icon"))


  toggleIconVisibility : (isFirst, firstIcon, secondIcon) ->

    if isFirst
      firstIcon.show()
      secondIcon.hide()
    else
      firstIcon.hide()
      secondIcon.show()


  webGlSupported : ->

    return window.WebGLRenderingContext and document.createElement('canvas').getContext('experimental-webgl')