### define
../model/dimensions : Dimensions
../constants : constants
###

class VolumeTracingController

  constructor : ( { @model, @view, @sceneController, @cameraController, @move, @calculateGlobalPos } ) ->

    @inDeleteMode = false

    @mouseControls =
      
      leftDownMove : (delta, pos, ctrlPressed) =>
        console.log("leftDownMove")
        if ctrlPressed
          @move [
            delta.x * @model.user.getMouseInversionX() / @view.scaleFactor
            delta.y * @model.user.getMouseInversionY() / @view.scaleFactor
            0
          ]
        else
          @model.volumeTracing.addToLayer( @calculateGlobalPos(pos))
      
      leftMouseDown : (pos, shiftPressed, altPressed) =>
        console.log("leftMouseDown")
        @enterDeleteMode( shiftPressed )
        @model.volumeTracing.startEditing()
      
      leftMouseUp : =>
        console.log("leftMouseUp")
        @model.volumeTracing.finishLayer()
        @restoreAfterDeleteMode()
      
      rightDownMove : (delta, pos, ctrlPressed) =>
        console.log("rightDownMove")
        @model.volumeTracing.addToLayer( @calculateGlobalPos(pos))
      
      rightMouseDown : (pos, shiftPressed, altPressed) =>
        console.log("rightMouseDown")
        @enterDeleteMode()
        @model.volumeTracing.startEditing()
      
      rightMouseUp : =>
        console.log("rightMouseUp")
        @model.volumeTracing.finishLayer()
        @restoreAfterDeleteMode()

      leftClick : (pos, shiftPressed, altPressed) =>
        console.log("leftClick")

        cell = @model.binary.cube.getLabel(
                  @calculateGlobalPos( pos ))

        if cell > 0
          @model.volumeTracing.setActiveCell( cell )
          

    @keyboardControls =

      "c" : =>
        @model.volumeTracing.createCell()


  enterDeleteMode : (enter = true) ->

    @inDeleteMode = enter

    if @inDeleteMode
      @prevActiveCell = @model.volumeTracing.getActiveCellId()
      @model.volumeTracing.setActiveCell(0)


  restoreAfterDeleteMode : ->

    if @inDeleteMode
      @model.volumeTracing.setActiveCell( @prevActiveCell )
    @inDeleteMode = false


  drawVolume : (pos) ->

    @model.volumeTracing.addToLayer(pos)
