class ArbitraryPlaneInfo

  WIDTH : 110
  HEIGHT : 40
  ALPHA : 0.8
  LINE_WIDTH : 3

  distance : 0
  context : null
  canvas : null

  isRecording : false

  constructor : ->

    { WIDTH, HEIGHT } = @

    canvas = document.createElement("canvas")
    canvas.width = WIDTH
    canvas.height = HEIGHT

    @context = canvas.getContext("2d")

    @updateInfo(false)

    $(canvas).attr("id": "arbitrary-info-canvas")
    $(canvas).css(position: "absolute", left: 10, top: 10)
    $("#render").append(canvas)


  updateInfo : (@isRecording) ->

    { context, WIDTH, HEIGHT, ALPHA, LINE_WIDTH } = @

    if @isRecording
      text = "TRACING"
      backColor = "rgba(95, 183, 105, #{ALPHA})"
    else
      text = "WATCHING"
      backColor = "rgba(100, 100, 100, #{ALPHA})"

    context.textAlign = "center"
    context.font = "13px Calibri"
    context.fillStyle = "rgba(0, 0, 0, 0)"
    context.clearRect(0, 0, WIDTH, HEIGHT)
    context.fillStyle = backColor

    stroke = true
    radius = 20
    x = LINE_WIDTH
    y = LINE_WIDTH
    fill = true
    rWidth = WIDTH - LINE_WIDTH * 2
    rHeight = HEIGHT - LINE_WIDTH * 2

    context.lineWidth = LINE_WIDTH
    context.strokeStyle = "rgba(255, 255, 255, #{ALPHA})"

    context.beginPath()
    context.moveTo x + radius, y
    context.lineTo x + rWidth - radius, y
    context.quadraticCurveTo x + rWidth, y, x + rWidth, y + radius
    context.lineTo x + rWidth, y + rHeight - radius
    context.quadraticCurveTo x + rWidth, y + rHeight, x + rWidth - radius, y + rHeight
    context.lineTo x + radius, y + rHeight
    context.quadraticCurveTo x, y + rHeight, x, y + rHeight - radius
    context.lineTo x, y + radius
    context.quadraticCurveTo x, y, x + radius, y
    context.closePath()
    context.stroke()  if stroke
    context.fill()  if fill
    context.fillStyle = "rgba(255, 255, 255, #{ALPHA})"
    context.fillText(text, WIDTH * 0.5, HEIGHT * 0.5 + 3)


module.exports = ArbitraryPlaneInfo
