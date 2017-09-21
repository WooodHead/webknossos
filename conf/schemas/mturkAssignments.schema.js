db.runCommand({
  collMod: "mturkAssignments",
  validator: {
    $and: [
      {
        _task: { $type: "objectId", $exists: true },
      },
      {
        team: { $type: "string", $exists: true },
      },
      {
        _project: { $type: "string", $exists: true },
      },
      {
        hitId: { $type: "string", $exists: true },
      },
      {
        key: { $type: "string", $exists: true },
      },
      {
        numberOfOpenAssignments: { $type: "int", $exists: true },
      },
      {
        numberOfInProgressAssignments: { $type: "number", $exists: true },
      },
      {
        created: { $type: "long", $exists: true },
      },
      {
        annotations: { $exists: true },
      },
      {
        $or: [
          {
            $and: [
              {
                "annotations._annotation": { $type: "objectId", $exists: true },
              },
              {
                "annotations._user": { $type: "objectId", $exists: true },
              },
              {
                "annotations.assignmentId": { $type: "string", $exists: true },
              },
            ],
          },
          { annotations: { $size: 0 } },
        ],
      },

      {
        _id: { $type: "objectId", $exists: true },
      },
    ],
  },
});
