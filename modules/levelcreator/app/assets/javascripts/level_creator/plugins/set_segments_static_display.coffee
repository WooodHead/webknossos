### define ###

class SetSegmentsStaticDisplay

  PUBLIC : true
  COMMAND : "setSegmentsStaticDisplay()"
  FRIENDLY_NAME : "Set Segments StaticDisplay"
  DESCRIPTION : "sets a value so this segments are displayrd throw all slides"  
  PARAMETER : 
    input: 
      segments: "[]"
      mission: "{}" # as static component
  EXAMPLES : []


  constructor : () ->


  execute : (options) ->

    { input: { segments, mission } } = options


    activeSegments = _.filter(segments, (segment) => segment.display is true)
    
    mission.staticDisplay = []    
    for segment in activeSegments
      mission.staticDisplay.push segment.value