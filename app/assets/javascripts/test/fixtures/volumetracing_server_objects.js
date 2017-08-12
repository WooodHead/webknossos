export const tracing = {
  activeCell: 10000,
  customLayers: [
    {
      name: "64007765-cef9-4e31-b206-dba795b5be17",
      category: "segmentation",
      boundingBox: {
        topLeft: [3840, 4220, 2304],
        width: 128,
        height: 131,
        depth: 384,
      },
      resolutions: [1],
      fallback: {
        dataSourceName: "2012-09-28_ex145_07x2",
        layerName: "segmentation",
      },
      elementClass: "uint16",
      mappings: [
        {
          name: "mapping_1",
          path: "mapping_path",
        },
      ],
    },
  ],
  nextCell: 21890,
  zoomLevel: 0,
  editPosition: [3904, 4282, 2496],
  editRotation: [0, 0, 0],
  boundingBox: null,
  contentType: "volume",
};

export const annotation = {
  created: "2017-08-09 20:19",
  state: { isAssigned: true, isFinished: false, isInProgress: true },
  id: "598b52293c00009906f043e7",
  name: "",
  typ: "Explorational",
  task: null,
  stats: null,
  restrictions: { allowAccess: true, allowUpdate: true, allowFinish: true, allowDownload: true },
  formattedHash: "f043e7",
  content: { id: "47e37793-d0be-4240-a371-87ce68561a13", typ: "volume" },
  dataSetName: "ROI2017_wkw",
  dataStore: { name: "localhost", url: "http://localhost:9000", typ: "webknossos-store" },
  settings: {
    allowedModes: ["volume"],
    branchPointsAllowed: true,
    somaClickingAllowed: true,
    advancedOptionsAllowed: true,
  },
  tracingTime: 0,
};