### define
libs/request : request
libs/event_mixin : EventMixin
libs/json_socket : JsonSocket
model/game : Game
model/tracepoint : TracePointClass
###

# This takes care of the route. 
  
# Constants
BUFFER_SIZE = 262144 # 1024 * 1204 / 4
PUSH_THROTTLE_TIME = 30000 # 30s
INIT_TIMEOUT = 10000 # 10s

TYPE_USUAL  = 0
TYPE_BRANCH = 1

Route = 
  
  # Variables
  branchStack : []
  trees : []
  activeNode : null
  activeTree : null

  # Initializes this module and returns a position to start your work.
  initialize : _.once ->

    Game.initialize().pipe =>

      _.extend(this, new EventMixin())

      @socket = new JsonSocket(
        senders : [
          new JsonSocket.WebSocket("ws://#{document.location.host}/task/ws/#{Game.task.id}")
          new JsonSocket.Comet("/task/comet/#{Game.task.id}")
        ]
      )

      deferred = new $.Deferred()

      @socket.on "data", (data) =>
        
        Route.data = data
        console.log data
        @id        = data.dataSet.id
        #@branchStack = data.task.branchPoints.map (a) -> new Float32Array(a)
        @branchStack = (data.task.trees[branchPoint.treeId].nodes[branchPoint.id].position for branchPoint in data.task.branchPoints) # when data.task.trees[branchPoint.treeId]?.id? == branchPoint.treeId)
        @createBuffer()

        @idCount = 1
        @treeIdCount = 1
        @trees = []
        @activeNode = null
        @activeTree = null
        
        # Build sample tree
        #@putNewPoint([300, 300, 200], TYPE_USUAL)
        #branch = @putNewPoint([300, 320, 200], TYPE_BRANCH)
        #@putNewPoint([340, 340, 200], TYPE_USUAL)
        #@putNewPoint([360, 380, 200], TYPE_USUAL)
        #@activeNode = branch
        #branch = @putNewPoint([340, 280, 200], TYPE_BRANCH)
        #@putNewPoint([360, 270, 200], TYPE_USUAL)
        #@activeNode = branch
        #@putNewPoint([360, 290, 200], TYPE_USUAL)
        #console.log "--------- TREE ---------"
        #console.log @tree.toString()

        # Build sample data.task
        console.log "---------- Build data.task -----------"
        console.log data.task
        data.task = {
          activeNode : 6
          branchPoints : [{id : 1}, {id : 2}]
          editPosition : [400, 350, 200]
          id : "5029141a44aebdd7a089a062"
          trees : {
            1 : {
              color : [1, 0, 0, 0]
              edges : [{source : 1, target : 3},
                       {source : 3, target : 4},
                       {source : 1, target : 2},
                       {source : 2, target : 5},
                       {source : 2, target : 6},
                       {source : 2, target : 7}]
              id : 1
              nodes : {
                1 : { id : 1, position : [300, 300, 200], radius : 1}
                2 : { id : 2, position : [350, 350, 200], radius : 1}
                3 : { id : 3, position : [300, 350, 200], radius : 1}
                4 : { id : 4, position : [300, 400, 200], radius : 1}
                5 : { id : 5, position : [400, 300, 200], radius : 1}
                6 : { id : 6, position : [400, 350, 200], radius : 1}
                7 : { id : 7, position : [400, 400, 200], radius : 1}
              }
            }
            5 : {
              color : [1, 0, 0, 0]
              edges : [{source : 8, target : 9},
                       {source : 9, target : 10}]
              id : 2
              nodes : {
                8 : { id : 8, position : [350, 400, 200], radius : 1}
                9 : { id : 9, position : [400, 450, 200], radius : 1}
                10 : { id : 10, position : [350, 450, 200], radius : 1}
              }
            }
          }
        }
        console.log data.task

        #@recursionTest(0)

        ############ Load Tree from data.task ##############
        # get tree to build
        for t of data.task.trees
          tree = data.task.trees[t]
          # Initialize nodes
          nodes = []
          i = 0
          for nodeInd of tree.nodes
            node = tree.nodes[nodeInd]
            if node
              nodes.push(new TracePoint(null, TYPE_USUAL, node.id, node.position, node.radius, 1))
          # Set branchpoints
          for branchpoint in data.task.branchPoints
            node = @findNodeInList(nodes, branchpoint.id)
            if node
              node.type = TYPE_BRANCH
          # Initialize edges
          for edge in tree.edges
            sourceNode = @findNodeInList(nodes, tree.nodes[edge.source].id)
            targetNode  = @findNodeInList(nodes, tree.nodes[edge.target].id)
            sourceNode.appendNext(targetNode)
            targetNode.parent = sourceNode
          # Find root (only node without parent)
          for node in nodes
            unless node.parent
              node.treeId = tree.id
              @trees.push(node)
              break
          # Set active Node
          activeNodeT = @findNodeInList(nodes, data.task.activeNode)
          if activeNodeT
            @activeNode = activeNodeT
            # Active Tree is the one last added
            @activeTree = @trees[@trees.length - 1]
          # Set idCount
          for node in nodes
            @idCount = Math.max(node.id + 1, @idCount);
        
        for tree in @trees
          @treeIdCount = Math.max(tree.treeId + 1, @treeIdCount)
        unless @activeTree
          @createNewTree

        for tree in @trees
          console.log "Tree " + tree.treeId + ":"
          console.log tree.toString()

        console.log "NML-Object:"
        console.log @exportToNML()

        $(window).on(
          "unload"
          => 
            @putBranch(@lastPosition) if @lastPosition
            @pushImpl()
        )

        deferred.resolve(data.task.editPosition)

      setTimeout(
        -> deferred.reject()
        INIT_TIMEOUT
      )

      deferred

  # Returns an object that is structured the same way as data.task is
  exportToNML : ->
    result = {
      activeNode : @activeNode.id
      branchPoints : []
      editPosition : @activeNode.pos
      trees : {}
    }
    for tree in @trees
      nodes = @getNodeList(tree)
      # Get Branchpoints
      for node in nodes
        if node.type == TYPE_BRANCH
          result.branchPoints.push({id : node.id})
      result.trees[tree.treeId.toString()] = {}
      tree = result.trees[tree.treeId.toString()]
      tree.color = [1, 0, 0, 0]
      tree.edges = []
      # Get Edges
      for node in nodes
        for child in node.getChildren()
          tree.edges.push({source : node.id, target : child.id})
      tree.id = 1
      tree.nodes = {}
      # Get Nodes
      for node in nodes
        tree.nodes[node.id.toString()] = {
          id : node.id
          position : node.pos
          radius : node.size
        }

    return result


  # Pushes the buffered route to the server. Pushing happens at most 
  # every 30 seconds.
  push : ->
    console.log "push()"
    @push = _.throttle(_.mutexDeferred(@pushImpl, -1), PUSH_THROTTLE_TIME)
    @push()

  pushImpl : ->

    console.log "pushing..."

    @initialize().pipe =>
      
      transportBuffer = new Float32Array(@buffer.subarray(0, @bufferIndex))
      @createBuffer()

      request(
        url    : "/route/#{@id}"
        method : 'POST'
        data   : transportBuffer.buffer
      ).fail =>
        
        oldBuffer = @buffer
        oldIndex  = @bufferIndex
        @createBuffer()
        @buffer.set(oldBuffer.subarray(0, oldIndex))
        @buffer.set(transportBuffer, oldIndex)
        @bufferIndex = oldIndex + transportBuffer.length

        @push()

  createBuffer : ->
    @bufferIndex = 0
    @buffer = new Float32Array(BUFFER_SIZE)

  addToBuffer : (typeNumber, value) ->

    @buffer[@bufferIndex++] = typeNumber

    if value and typeNumber != 2
      @buffer.set(value, @bufferIndex)
      @bufferIndex += 3

    #console.log @buffer.subarray(0, 50)

    @push()

  putBranch : (position) ->

    @initialize().done =>
      
      @addToBuffer(1, position)
      # push TransformationMatrix for compatibility reasons
      @branchStack.push(position)

      @putNewPoint(position, TYPE_BRANCH)

    return

  popBranch : ->

    @initialize().pipe =>

      savedActiveNode = @activeNode
      if @activeNode
        while (true)
          @activeNode = @activeNode.parent
          unless @activeNode
            break
          if (@activeNode.type == TYPE_BRANCH)
            break
      deferred = new $.Deferred()
      unless @activeNode
        @activeNode = savedActiveNode
        deferred.reject()
      else
        deferred.resolve(@activeNode.pos)
      
      
      # Georg doesn't get the following lines
      #{ branchStack } = @

      #if branchStack.length > 0
      #  @addToBuffer(2)
        #deferred.resolve(branchStack.pop())
      #  deferred.resolve(@activeNode.pos)
      #else
      #  deferred.reject()

  # Add a point to the buffer. Just keep adding them.
  put : (position) ->

    @initialize().done =>

      position = V3.round(position, position)
      lastPosition = @lastPosition

      if not lastPosition or 
      lastPosition[0] != position[0] or 
      lastPosition[1] != position[1] or 
      lastPosition[2] != position[2]
        @lastPosition = position
        @addToBuffer(0, position)

      @putNewPoint(position, TYPE_USUAL)

    return

  putNewPoint : (position, type) ->
      point = new TracePoint(@activeNode, type, @idCount++, position, 10, 1)
      if @activeNode
        @activeNode.appendNext(point)
      else
        # Tree has to be empty, so replace sentinel with point
        point.treeId = @activeTree.treeId
        @trees[@activeTree.treeIndex] = point
        @activeTree = point
      @activeNode = point
      #console.log @activeTree.toString()
      #console.log @getNodeList()
      #for item in @getNodeList()
      #  console.log item.id

  getActiveNodeId : ->
    @activeNode.id

  getActiveNodePos : ->
    @activeNode.pos

  getActiveNodeType : ->
    @activeNode.type

  getActiveNodeRadius : ->
    @activeNode.size

  setActiveNodeRadius : (radius) ->
    if @activeNode
      @activeNode.size = radius

  setActiveNode : (id) ->
    for tree in @trees
      findResult = @findNodeInTree(id)
      if findResult
        @activeNode = findResult
    return @activeNode.pos

  setActiveTree : (id) ->
    for tree in @trees
      if tree.treeId == id
        @activeTree = tree

  createNewTree : (id) ->
    # Because a tree is represented by the root element and we
    # don't have any root element, we need a sentinel to save the
    # treeId and it's index within trees.
    sentinel = new TracePoint(null, null, null, null, null, null)
    sentinel.treeId = @treeIdCount++
    # save Index, so we can access it once we have the root element
    sentinel.treeIndex = @trees.length
    @trees.push(sentinel)
    @activeTree = sentinel
    return sentinel.treeId

  findNodeInTree : (id, tree) ->
    unless tree
      tree = @activeTree
    if @activeTree.id == id then @activeTree else @activeTree.findNodeById(id)

  deleteActiveNode : ->
    id = @activeNode.id
    @activeNode = @activeNode.parent
    @activeNode.remove(id)
    console.log @activeTree.toString()

  getTree : (id) ->
    unless id
      return @activeTree
    for tree in @trees
      if tree.treeId == id
        return tree
    return null

  getTrees : ->
    @trees

  getNodeList : (tree) ->
    unless tree
      tree = @activeTree
    result = [tree]
    for c in tree.getChildren()
      if c
        result = result.concat(@getNodeList(c))
    return result

  # Helper method used in initialization
  findNodeInList : (list, id) ->
    for node in list
      if node.id == id
        return node
    return null

  # Just to check how far we can go. I (Georg) had a call
  # stack of about 20,000
  recursionTest : (counter) ->
    console.log(counter++)
    return @recursionTest(counter)