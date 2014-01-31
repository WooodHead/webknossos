### define
../../libs/datgui/dat.gui : DatGui
../../libs/request : Request
../../libs/event_mixin : EventMixin
../../libs/toast : Toast
../model/dimensions : Dimensions
../constants : constants
###

class Gui 

  model : null
  
  constructor : (container, @model, @restrictions, @tracingSettings) ->
    
    _.extend(this, new EventMixin())

    @updateGlobalPosition( @model.flycam.getPosition() )

    @user = @model.user
    @qualityArray = ["high", "medium", "low"]

    @datasetPostfix = _.last(@model.binary["color"].dataSetName.split("_"))
    @datasetPosition = @initDatasetPosition(@user.briConNames)

    somaClickingAllowed = @tracingSettings.somaClickingAllowed
    
    @settings = 

      boundingBox : "0, 0, 0, 0, 0, 0"
      fourBit : @user.fourBit
      brightness : @user.brightness[@datasetPosition]
      contrast : @user.contrast[@datasetPosition]
      resetBrightnessAndContrast : => @resetBrightnessAndContrast()
      quality : @qualityArray[@user.quality]

      activeTreeID : @model.cellTracing.getActiveTreeId()
      activeNodeID : @model.cellTracing.getActiveNodeId() or -1
      activeCellID : @model.volumeTracing.getActiveCellId()
      newNodeNewTree : if somaClickingAllowed then @user.newNodeNewTree else false
      radius : @model.cellTracing.getActiveNodeRadius()
      deleteActiveNode : => @trigger "deleteActiveNode"
      createNewCell : => @trigger "createNewCell"

    if @datasetPosition == 0
      # add new dataset to settings
      @user.briConNames.push(@datasetPostfix)
      @user.brightness.push(@settings.brightness)
      @user.contrast.push(@settings.contrast)
      @datasetPosition = @user.briConNames.length - 1


    @gui = new dat.GUI(autoPlace: false, width : 280, hideable : false, closed : true)

    container.append @gui.domElement

    @folders = []
    
    @folders.push( fControls = @gui.addFolder("Controls") )
    @addCheckbox(fControls, @user, "inverseX", "Inverse X")
    @addCheckbox(fControls, @user, "inverseY", "Inverse Y")
    @addSlider(fControls, @user, "keyboardDelay",
      0, 500, 10, "Keyboard delay (ms)" )

    @folders.push( @fViewportcontrols = @gui.addFolder("Viewportoptions") )
    @moveValueController = @addSlider(@fViewportcontrols, @user, "moveValue",
      constants.MIN_MOVE_VALUE, constants.MAX_MOVE_VALUE, 10, "Move Value (nm/s)")
    @zoomController = @addSlider(@fViewportcontrols, @user, "zoom",
      0.01, @model.flycam.getMaxZoomStep(), 0.001, "Zoom")
    @scaleController = @addSlider(@fViewportcontrols, @user, "scale", constants.MIN_SCALE,
      constants.MAX_SCALE, 0.1, "Viewport Scale")
    @addCheckbox(@fViewportcontrols, @user, "dynamicSpaceDirection", "d/f-Switching")

    @folders.push( @fFlightcontrols = @gui.addFolder("Flightoptions") )
    @addSlider(@fFlightcontrols, @user, "mouseRotateValue",
      0.001, 0.02, 0.001, "Mouse Rotation")
    @addSlider(@fFlightcontrols, @user, "rotateValue",
      0.001, 0.08, 0.001, "Keyboard Rotation Value")
    @moveValue3dController = @addSlider(@fFlightcontrols, @user, "moveValue3d",
      constants.MIN_MOVE_VALUE, constants.MAX_MOVE_VALUE, 10, "Move Value (nm/s)")
    @addSlider(@fFlightcontrols, @user, "crosshairSize",
      0.05, 0.5, 0.01, "Crosshair size")

    @folders.push( @fView = @gui.addFolder("View") )
    bbController = @fView.add(@settings, "boundingBox").name("Bounding Box").onChange(@setBoundingBox)
    @addTooltip(bbController, "Format: minX, minY, minZ, maxX, maxY, maxZ")
    @addCheckbox(@fView, @settings, "fourBit", "4 Bit")
    @addCheckbox(@fView, @user, "interpolation", "Interpolation")
    @brightnessController =
      @addSlider(@fView, @settings, "brightness",
        -256, 256, 5, "Brightness", @setBrightnessAndContrast)
    @contrastController =
      @addSlider(@fView, @settings, "contrast",
        0.5, 5, 0.1, "Contrast", @setBrightnessAndContrast)
    @addFunction(@fView, @settings, "resetBrightnessAndContrast",
      "Reset B/C")
    @clippingController = @addSlider(@fView, @user, "clippingDistance",
      1, 1000 * @model.scaleInfo.baseVoxel, 1, "Clipping Distance")
    @clippingControllerArbitrary = @addSlider(@fView, @user, "clippingDistanceArbitrary",
      1, 127, 1, "Clipping Distance")
    @addCheckbox(@fView, @user, "displayCrosshair", "Show Crosshairs")
    (@fView.add @settings, "quality", @qualityArray)
                          .name("Quality")
                          .onChange((v) => @setQuality(v))

    @folders.push(@fTDView = @gui.addFolder("3D View"))
    @addCheckbox(@fTDView, @user, "displayTDViewXY", "Display XY-Plane")
    @addCheckbox(@fTDView, @user, "displayTDViewYZ", "Display YZ-Plane")
    @addCheckbox(@fTDView, @user, "displayTDViewXZ", "Display XZ-Plane")

    @folders.push( @fTrees = @gui.addFolder("Trees") )
    @activeTreeIdController = @addNumber(@fTrees, @settings, "activeTreeID",
      1, 1, "Active Tree ID", (value) => @trigger( "setActiveTree", value))
    if somaClickingAllowed
      @addCheckbox(@fTrees, @settings, "newNodeNewTree", "Soma clicking mode")
    else
      @set("newNodeNewTree", false, Boolean)

    @folders.push( @fNodes = @gui.addFolder("Nodes") )
    @activeNodeIdController = @addNumber(@fNodes, @settings, "activeNodeID",
      1, 1, "Active Node ID", (value) => @trigger( "setActiveNode", value))
    @radiusController = @addSlider(@fNodes, @settings, "radius",
      @model.cellTracing.MIN_RADIUS, @model.cellTracing.MAX_RADIUS, 1, "Radius", (radius) =>
        @model.cellTracing.setActiveNodeRadius( radius ))
    @particleSizeController = @addSlider(@fNodes, @user, "particleSize",
      constants.MIN_PARTICLE_SIZE, constants.MAX_PARTICLE_SIZE, 1, "Min. node size")
    @addFunction(@fNodes, @settings, "deleteActiveNode", "Delete Active Node")

    @folders.push( @fCells = @gui.addFolder("Cells") )
    @activeCellIdController = @addNumber(@fCells, @settings, "activeCellID",
      0, 1, "Active Cell ID", (value) => @trigger( "setActiveCell", value))
    @addFunction(@fCells, @settings, "createNewCell", "Create new Cell")

    @fTrees.open()
    @fNodes.open()
    @fCells.open()

    $("#dataset-name").text(@model.binary["color"].dataSetName)

    $("#trace-position-input").on "change", (event) => 

      @setPosFromString(event.target.value)
      $("#trace-position-input").blur()

    $("#trace-finish-button").click (event) =>

      event.preventDefault()
      @saveNow().done =>
        if confirm("Are you sure you want to permanently finish this tracing?")
          window.location.href = event.srcElement.href

    $("#trace-download-button").click (event) =>

      event.preventDefault()
      @saveNow().done =>
          window.location.href = event.srcElement.href

    $("#trace-save-button").click (event) =>

      event.preventDefault()
      @saveNow()


    @model.flycam.on
      positionChanged : (position) => 
        @updateGlobalPosition(position)

    @model.user.on
      zoomChanged : (zoom) =>
        nm = zoom * constants.PLANE_WIDTH * @model.scaleInfo.baseVoxel
        if(nm<1000)
          $("#zoomFactor").html("<p>Viewport width: " + nm.toFixed(0) + " nm</p>")
        else if (nm<1000000)
          $("#zoomFactor").html("<p>Viewport width: " + (nm / 1000).toFixed(1) + " μm</p>")
        else
          $("#zoomFactor").html("<p>Viewport width: " + (nm / 1000000).toFixed(1) + " mm</p>")

    @model.cellTracing.on
      newActiveNode       : => @update()
      newActiveTree       : => @update()
      newActiveNodeRadius : => @update()
      deleteActiveTree    : => @update()
      deleteActiveNode    : => @update()
      deleteLastNode      : => @update()
      newNode             : => @update()
      newTree             : => @update()

    @model.volumeTracing.on
      newActiveCell    : =>
        console.log "newActiveCell!"
        @update()

    @model.user.on
      scaleChanged : => @updateScale()
      zoomChanged : => @updateZoom()
      moveValueChanged : => @updateMoveValue()
      moveValue3dChanged : => @updateMoveValue3d()
      particleSizeChanged : => @updateParticleSize()

    @model.binary["segmentation"]?.cube.on
      bucketLoaded : => @updateSegmentID()

    @createTooltips()


  addCheckbox : (folder, object, propertyName, displayName) =>

    return (folder.add object, propertyName)
                          .name(displayName)
                          .onChange((v) => @set(propertyName, v,  Boolean))


  addSlider : (folder, object, propertyName, start, end, step, displayName, onChange) =>

    unless onChange?
      onChange = (v) => @set(propertyName, v, Number)
    return (folder.add object, propertyName, start, end)
                          .step(step)
                          .name(displayName)
                          .onChange(onChange)


  addFunction : (folder, object, propertyName, displayName) =>

    return (folder.add object, propertyName)
                          .name(displayName)


  addNumber : (folder, object, propertyName, min, step, displayName, onChange) =>

    unless onChange?
      onChange = (v) => @set(propertyName, v, Number)
    return (folder.add object, propertyName)
                          .min(min)
                          .step(step)
                          .name(displayName)
                          .onChange(onChange)


  saveNow : =>

    @user.pushImpl()
    if @restrictions.allowUpdate
      @model.cellTracing.pushNow()
        .then( 
          -> Toast.success("Saved!")
          -> Toast.error("Couldn't save. Please try again.")
        )
    else
      new $.Deferred().resolve()


  setBoundingBox : (value) =>

    bbArray = @stringToNumberArray( value )
    if bbArray?.length == 6
      @trigger("newBoundingBox", bbArray)


  setPosFromString : (posString) =>
 
    posArray = @stringToNumberArray( posString )
    if posArray?.length == 3
      @model.flycam.setPosition(posArray)
      return
    else
      @updateGlobalPosition(@model.flycam.getPosition())


  stringToNumberArray : (s) ->

    # remove leading/trailing whitespaces
    s = s.trim()
    # replace remaining whitespaces with commata
    s = s.replace /,?\s+,?/g, ","
    stringArray = s.split(",")

    result = []
    for e in stringArray
      if not isNaN(newEl = parseInt(e))
        result.push(newEl)
      else
        return null

    return result


  setPosFromString : (posString) =>

    # remove leading/trailing whitespaces
    strippedString = posString.trim()
    # replace remaining whitespaces with commata
    unifiedString = strippedString.replace /,?\s+,?/g, ","
    stringArray = unifiedString.split(",")
    if stringArray.length == 3
      pos = [parseInt(stringArray[0]), parseInt(stringArray[1]), parseInt(stringArray[2])]
      if !isNaN(pos[0]) and !isNaN(pos[1]) and !isNaN(pos[2])
        @model.flycam.setPosition(pos)
        return
    @updateGlobalPosition(@model.flycam.getPosition())


  initDatasetPosition : (briConNames) ->

    for i in [0...briConNames.length]
      if briConNames[i] == @datasetPostfix
        datasetPosition = i
    unless datasetPosition
      # take default values
      datasetPosition = 0
    datasetPosition


  addTooltip : (element, title) ->

    $(element.domElement).parent().parent().tooltip({ title : title })


  createTooltips : ->

      $(".cr.number.has-slider").tooltip({"title" : "Move mouse up or down while clicking the number to easily adjust the value"})


  updateGlobalPosition : (globalPos) =>

    stringPos = Math.floor(globalPos[0]) + ", " + Math.floor(globalPos[1]) + ", " + Math.floor(globalPos[2])
    $("#trace-position-input").val(stringPos)
    @updateSegmentID()

  updateSegmentID : ->

    if @model.binary["segmentation"]?
      segmentID = @model.binary["segmentation"].cube.getDataValue( @model.flycam.getPosition() )
      if segmentID?
        $("#segment-id").html("<p>Segment ID: " + segmentID + "</p>")
      else
        $("#segment-id").html("<p>Segment ID: -</p>")


  set : (name, value, type) =>

    @user.setValue( name, (type) value)


  setBrightnessAndContrast : =>
    @model.binary["color"].updateContrastCurve(@settings.brightness, @settings.contrast)
    
    @user.brightness[@datasetPosition] = (Number) @settings.brightness
    @user.contrast[@datasetPosition] = (Number) @settings.contrast
    @user.push()


  resetBrightnessAndContrast : =>

    Request.send(
      url : "/user/configuration/default"
      dataType : "json"
    ).done (defaultData) =>
      defaultDatasetPosition = @initDatasetPosition(defaultData.briConNames)

      @settings.brightness = defaultData.brightness[defaultDatasetPosition]
      @settings.contrast = defaultData.contrast[defaultDatasetPosition]
      @setBrightnessAndContrast()
      @brightnessController.updateDisplay()
      @contrastController.updateDisplay()


  setQuality : (value) =>

    for i in [0...@qualityArray.length]
      if @qualityArray[i] == value
        value = i
    @set("quality", value, Number)


  updateParticleSize : =>

    @particleSizeController.updateDisplay()


  updateMoveValue : =>

    @moveValueController.updateDisplay()


  updateMoveValue3d : =>

    @moveValue3dController.updateDisplay()


  updateScale : =>

    @scaleController.updateDisplay()


  updateZoom : =>

    @zoomController.updateDisplay()


  update : ->

    # Helper method to combine common update methods
    # called when value user switch to different active node
    @settings.activeNodeID = @model.cellTracing.getActiveNodeId() or -1
    @settings.activeTreeID = @model.cellTracing.getActiveTreeId()
    @settings.activeCellID = @model.volumeTracing.getActiveCellId()
    @settings.radius = @model.cellTracing.getActiveNodeRadius()
    @activeNodeIdController.updateDisplay()
    @activeTreeIdController.updateDisplay()
    @activeCellIdController.updateDisplay()
    @radiusController.updateDisplay()


  setFolderVisibility : (folder, visible) ->

    $element = $(folder.domElement)
    if visible then $element.show() else $element.hide()


  setFolderElementVisibility : (element, visible) ->

    $element = $(element.domElement).parents(".cr")
    if visible then $element.show() else $element.hide()


  hideFolders : (folders) ->

    for folder in folders
      @setFolderVisibility( folder, false)


  setMode : (mode) ->

    for folder in @folders
      @setFolderVisibility(folder, true)
    @setFolderElementVisibility( @clippingControllerArbitrary, false )
    @setFolderElementVisibility( @clippingController, true )

    if      mode == constants.MODE_PLANE_TRACING
      @hideFolders( [ @fFlightcontrols, @fCells ] )
      @user.triggerAll()
    else if mode == constants.MODE_ARBITRARY or mode == constants.MODE_ARBITRARY_PLANE
      @hideFolders( [ @fViewportcontrols, @fTDView, @fCells ] )
      @setFolderElementVisibility( @clippingControllerArbitrary, true )
      @setFolderElementVisibility( @clippingController, false )
      @user.triggerAll()
    else if mode == constants.MODE_VOLUME
      @hideFolders( [ @fTrees, @fNodes, @fFlightcontrols ] )

