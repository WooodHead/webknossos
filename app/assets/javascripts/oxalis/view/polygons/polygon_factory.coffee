### define
./tlt : tlt
underscore : _
###

# This class is capable of turning voxel data into triangles
# Based on the marching cubes algorithm
class PolygonFactory

  MAX_SAMPLES : 80

  constructor : (@modelCube, min, max, @id) ->

    @voxelsToSkip = Math.ceil((max[0] - min[0]) / @MAX_SAMPLES) || 1
    @chunkSize  = 10000

    round = (number) =>
      Math.floor(number / @voxelsToSkip) * @voxelsToSkip

    [ @startX, @endX ] = [ round(min[0]), round(max[0]) + @voxelsToSkip ]
    [ @startY, @endY ] = [ round(min[1]), round(max[1]) + @voxelsToSkip ]
    [ @startZ, @endZ ] = [ round(min[2]), round(max[2]) + @voxelsToSkip ]


  getTriangles : () ->

    result    = {}
    @deferred = new $.Deferred()

    _.defer( @calculateTrianglesAsync, result )
    return @deferred


  calculateTrianglesAsync : (result, lastPosition) =>

    i = 0
    position = @getNextPosition(lastPosition)

    while @isPositionInBoundingBox(position)
      @updateTriangles(result, position)

      # If chunk size is reached, pause execution
      if i == @chunkSize
        _.defer(@calculateTrianglesAsync, result, position)
        return
      i++

      position = @getNextPosition(position)

    @deferred.resolve( result )


  isPositionInBoundingBox : (position) ->

    if position?
      [x, y, z] = position
      return (x >= @startX and y >= @startY and z >= @startZ) and
        (x <= @endX and y <= @endY and z <= @endZ)
    return false


  getNextPosition : (lastPosition) ->

    unless lastPosition?
      return [@startX, @startY, @startZ]

    else
      [oldX, oldY, oldZ] = lastPosition

      if oldX + @voxelsToSkip < @endX
        return [oldX + @voxelsToSkip, oldY, oldZ]
      if oldY + @voxelsToSkip < @endY
        return [@startX, oldY + @voxelsToSkip, oldZ]
      else
        return [@startX, @startY, oldZ + @voxelsToSkip]


  updateTriangles : (result, position) ->

    cubeIndices = @getCubeIndices(position)

    for cellId, cubeIndex of cubeIndices
      unless result[cellId]?
        result[cellId] = []
      unless cubeIndex == 0 or cubeIndex == 256
        @addNewTriangles(result[cellId], cubeIndex, position)


  getCubeIndices : ([x, y, z]) ->

    labels = [
      @modelCube.getDataValue( [x, y, z]                                                 ),
      @modelCube.getDataValue( [x + @voxelsToSkip, y, z]                                 ),
      @modelCube.getDataValue( [x + @voxelsToSkip, y, z + @voxelsToSkip]                 ),
      @modelCube.getDataValue( [x, y, z + @voxelsToSkip]                                 ),
      @modelCube.getDataValue( [x, y + @voxelsToSkip, z]                                 ),
      @modelCube.getDataValue( [x + @voxelsToSkip, y + @voxelsToSkip, z]                 ),
      @modelCube.getDataValue( [x + @voxelsToSkip, y + @voxelsToSkip, z + @voxelsToSkip] ),
      @modelCube.getDataValue( [x, y + @voxelsToSkip, z + @voxelsToSkip]                 ) ]

    cellIds = []
    for label in labels
      unless label in cellIds or label == 0 or (@id? and @id != label)
        cellIds.push( label )

    result = {}
    for cellId in cellIds
      cubeIndex = 0

      for i in [0..7]
        bit = if cellId == labels[i] then 1 else 0
        cubeIndex |= bit << i

      result[cellId] = cubeIndex

    return result


  addNewTriangles : (triangleList, cubeIndex, [x, y, z]) ->

      for triangle in tlt[ cubeIndex ]
        vertices = []

        for vertex in triangle
          vertices.push( [ vertex[0] * @voxelsToSkip + x,
                          vertex[1] * @voxelsToSkip + y,
                          vertex[2] * @voxelsToSkip + z ] )

        triangleList.push(vertices)
