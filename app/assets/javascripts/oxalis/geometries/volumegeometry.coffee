### define
three : THREE
###

class VolumeGeometry

  COLORS : [0xff0000, 0x00ff00, 0x0000ff, 0xffff00, 0x00ffff, 0xff00ff]

  constructor : (triangles, @id) ->

    geo = new THREE.Geometry()
    color = @COLORS[ (@id - 1) % 6 ]

    i = 0
    for triangle in triangles
      for vertex in triangle
        geo.vertices.push( new THREE.Vector3(vertex...) )
      normal = @getTriangleNormal( triangle )
      geo.faces.push( new THREE.Face3(i++, i++, i++, normal) )

    @mesh = new THREE.Mesh( geo,
      new THREE.MeshPhongMaterial({
        color : color
      }))
    @mesh.oberdraw = true

  getTriangleNormal : (triangle) ->

    v1 = new THREE.Vector3( triangle[1][0] - triangle[0][0],
                            triangle[1][1] - triangle[0][1],
                            triangle[1][2] - triangle[0][2] )

    v2 = new THREE.Vector3( triangle[2][0] - triangle[0][0],
                            triangle[2][1] - triangle[0][1],
                            triangle[2][2] - triangle[0][2] )

    v1.cross(v2)
    v1.normalize()
    return v1

  getMeshes : ->

    return [@mesh]
