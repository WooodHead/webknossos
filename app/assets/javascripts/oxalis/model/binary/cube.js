import Backbone from "backbone";
import _ from "lodash";
import ErrorHandling from "../../../libs/error_handling";
import { Bucket, NullBucket } from "./bucket";
import ArbitraryCubeAdapter from "./arbitrary_cube_adapter";
import TemporalBucketManager from "./temporal_bucket_manager";
import BoundingBox from "./bounding_box";

class Cube {
  static initClass() {
  
    // Constants
    this.prototype.BUCKET_SIZE_P  = 5;
    this.prototype.CUBE_SIZE_P    = 7;
    this.prototype.LOCAL_ID_SIZE_P  = 16;
    this.prototype.BUCKET_LENGTH  = 0;
    this.prototype.ZOOM_STEP_COUNT  = 0;
    this.prototype.LOOKUP_DEPTH_UP  = 0;
    this.prototype.LOOKUP_DEPTH_DOWN  = 1;
    this.prototype.MAXIMUM_BUCKET_COUNT  = 5000;
    this.prototype.ARBITRARY_MAX_ZOOMSTEP  = 2;
  
    this.prototype.EMPTY_MAPPING  = null;
  
    this.prototype.arbitraryCube  = null;
    this.prototype.dataCubes  = null;
    this.prototype.upperBoundary  = null;
  
    this.prototype.buckets  = null;
    this.prototype.bucketIterator  = 0;
    this.prototype.bucketCount  = 0;
  }


  // The cube stores the buckets in a seperate array for each zoomStep. For each
  // zoomStep the cube-array contains the boundaries and an array holding the buckets.
  // The bucket-arrays are initialized large enough to hold the whole cube. Thus no
  // expanding is necessary. bucketCount keeps track of how many buckets are currently
  // in the cube.
  //
  // Each bucket consists of an access-value, the zoomStep and the actual data.
  // The access-values are used for garbage collection. When a bucket is accessed, its
  // access-flag is set to true.
  // When buckets have to be collected, an iterator will loop through the the buckets at the beginning of the queue will be removed
  // from the queue and the access-value will be decreased. If the access-value of a
  // bucket becomes 0, itsis no longer in the access-queue and is least resently used.
  // It is then removed from the cube.


  constructor(globalBoundingBox, upperBoundary, ZOOM_STEP_COUNT, BIT_DEPTH) {

    this.upperBoundary = upperBoundary;
    this.ZOOM_STEP_COUNT = ZOOM_STEP_COUNT;
    this.BIT_DEPTH = BIT_DEPTH;
    _.extend(this, Backbone.Events);

    this.NULL_BUCKET_OUT_OF_BB = new NullBucket(NullBucket.prototype.TYPE_OUT_OF_BOUNDING_BOX);
    this.NULL_BUCKET = new NullBucket(NullBucket.prototype.TYPE_OTHER);

    this.LOOKUP_DEPTH_UP = this.ZOOM_STEP_COUNT - 1;
    this.MAX_ZOOM_STEP   = this.ZOOM_STEP_COUNT - 1;
    this.BUCKET_LENGTH   = (1 << (this.BUCKET_SIZE_P * 3)) * (this.BIT_DEPTH >> 3);
    this.BYTE_OFFSET     = (this.BIT_DEPTH >> 3);

    this.cubes = [];
    this.buckets = new Array(this.MAXIMUM_BUCKET_COUNT);

    this.mapping = this.EMPTY_MAPPING;
    this.currentMapping = this.mapping;

    // Initializing the cube-arrays with boundaries
    let cubeBoundary = [
      Math.ceil(this.upperBoundary[0] / (1 << this.BUCKET_SIZE_P)),
      Math.ceil(this.upperBoundary[1] / (1 << this.BUCKET_SIZE_P)),
      Math.ceil(this.upperBoundary[2] / (1 << this.BUCKET_SIZE_P))
    ];

    this.arbitraryCube = new ArbitraryCubeAdapter(this, cubeBoundary.slice());

    for (let i of __range__(0, this.ZOOM_STEP_COUNT, false)) {

      this.cubes[i] = {};
      this.cubes[i].data = new Array(cubeBoundary[0] * cubeBoundary[1] * cubeBoundary[2]);
      this.cubes[i].boundary = cubeBoundary.slice();

      cubeBoundary = [
        (cubeBoundary[0] + 1) >> 1,
        (cubeBoundary[1] + 1) >> 1,
        (cubeBoundary[2] + 1) >> 1
      ];
    }

    this.boundingBox = new BoundingBox(globalBoundingBox, this);
  }


  initializeWithQueues(pullQueue, pushQueue) {
    // Due to cyclic references, this method has to be called before the cube is
    // used with the instantiated queues

    this.pullQueue = pullQueue;
    this.pushQueue = pushQueue;
    return this.temporalBucketManager = new TemporalBucketManager(this.pullQueue, this.pushQueue);
  }


  getNullBucket(bucket) {

    if (this.boundingBox.containsBucket(bucket)) {
      return this.NULL_BUCKET;
    } else {
      return this.NULL_BUCKET_OUT_OF_BB;
    }
  }


  setMapping(mapping) {

    this.mapping = mapping;
    return this.trigger("mappingChanged");
  }


  setMappingEnabled(isEnabled) {

    this.currentMapping = isEnabled ? this.mapping : this.EMPTY_MAPPING;
    return this.trigger("newMapping");
  }


  hasMapping() {

    return (this.mapping != null);
  }


  setMapping(newMapping) {

    // Generate fake mapping
    //if not newMapping.length
    //  newMapping = new newMapping.constructor(1 << @BIT_DEPTH)
    //  for i in [0...(1 << @BIT_DEPTH)]
    //    newMapping[i] = if i == 0 then 0 else i + 1

    if (this.currentMapping === this.mapping) {
      this.currentMapping = newMapping;
    }
    this.mapping = newMapping;

    return this.trigger("newMapping");
  }


  mapId(idToMap) {

    let mappedId;
    return (this.currentMapping != null) && ((mappedId = this.currentMapping[idToMap]) != null) ? mappedId : idToMap;
  }


  getArbitraryCube() {

    return this.arbitraryCube;
  }


  getVoxelIndexByVoxelOffset([x, y, z]) {

    return this.BYTE_OFFSET *
      (x + (y * (1 << this.BUCKET_SIZE_P)) + (z * (1 << (this.BUCKET_SIZE_P * 2))));
  }


  isWithinBounds([x, y, z, zoomStep]) {

    if (zoomStep >= this.ZOOM_STEP_COUNT) {
      return false;
    }

    ErrorHandling.assertExists(
      this.cubes[zoomStep],
      "Cube for given zoomStep does not exist", {
        cubeCount: this.cubes.length,
        zoomStep,
        zoomStepCount: this.ZOOM_STEP_COUNT
      }
    );

    return this.boundingBox.containsBucket([x, y, z, zoomStep]);
  }


  getBucketIndex([x, y, z, zoomStep]) {

    ErrorHandling.assert(this.isWithinBounds([x, y, z, zoomStep]));

    const { boundary } = this.cubes[zoomStep];
    return (x * boundary[2] * boundary[1]) + (y * boundary[2]) + z;
  }


  // Either returns the existing bucket or creates a new one. Only returns
  // NULL_BUCKET if the bucket cannot possibly exist, e.g. because it is
  // outside the dataset's bounding box.
  getOrCreateBucket(address) {

    if (!this.isWithinBounds(address)) {
      return this.getNullBucket(address);
    }

    let bucket = this.getBucket(address);
    if (bucket.isNullBucket) {
      bucket = this.createBucket(address);
    }

    return bucket;
  }


  // Returns the Bucket object if it exists, or NULL_BUCKET otherwise.
  getBucket(address) {

    if (!this.isWithinBounds(address)) {
      return this.getNullBucket(address);
    }

    const bucketIndex = this.getBucketIndex(address);
    const cube = this.cubes[address[3]].data;

    if (cube[bucketIndex] != null) {
      return cube[bucketIndex];
    }

    return this.getNullBucket(address);
  }


  createBucket(address) {

    const bucket = new Bucket(this.BIT_DEPTH, address, this.temporalBucketManager);
    bucket.on({
      bucketLoaded : () => {
        return this.trigger("bucketLoaded", address);
      }
    });
    this.addBucketToGarbageCollection(bucket);

    const bucketIndex = this.getBucketIndex(address);
    this.cubes[address[3]].data[bucketIndex] = bucket;
    return bucket;
  }


  addBucketToGarbageCollection(bucket) {

    if (this.bucketCount >= this.MAXIMUM_BUCKET_COUNT) {

      for (let i of __range__(0, (2 * this.bucketCount), false)) {

        this.bucketIterator = ++this.bucketIterator % this.MAXIMUM_BUCKET_COUNT;
        if (this.buckets[this.bucketIterator].shouldCollect()) { break; }
      }

      if (!this.buckets[this.bucketIterator].shouldCollect()) {
        throw new Error("All buckets have shouldCollect == false permanently");
      }

      this.collectBucket(this.buckets[this.bucketIterator]);
      this.bucketCount--;
    }

    this.bucketCount++;
    this.buckets[this.bucketIterator] = bucket;
    return this.bucketIterator = ++this.bucketIterator % this.MAXIMUM_BUCKET_COUNT;
  }


  collectBucket(bucket) {

    const address = bucket.zoomedAddress;
    const bucketIndex = this.getBucketIndex(address);
    const cube = this.cubes[address[3]].data;
    return cube[bucketIndex] = null;
  }


  labelTestShape() {
    // draw a sqhere, centered at (100, 100, 100) with radius 50

    for (let x = 80; x <= 120; x++) {
      for (let y = 80; y <= 120; y++) {
        for (let z = 80; z <= 120; z++) {

          if (Math.sqrt(((x-100) * (x-100)) + ((y-100) * (y-100)) + ((z-100) * (z-100))) <= 20) {
            this.labelVoxel([x, y, z], 5);
          }
        }
      }
    }

    return this.trigger("volumeLabeled");
  }


  labelVoxels(iterator, label) {

    while (iterator.hasNext) {
      const voxel = iterator.getNext();
      this.labelVoxel(voxel, label);
    }

    this.pushQueue.push();
    return this.trigger("volumeLabeled");
  }


  labelVoxel(voxel, label) {

    let voxelInCube = true;
    for (let i = 0; i <= 2; i++) {
      voxelInCube &= 0 <= voxel[i] && voxel[i] < this.upperBoundary[i];
    }

    if (voxelInCube) {

      const address = this.positionToZoomedAddress(voxel);
      const bucket = this.getOrCreateBucket(address);
      const voxelIndex = this.getVoxelIndex(voxel);

      const labelFunc = data => {
        // Write label in little endian order
        return __range__(0, this.BYTE_OFFSET, false).map((i) =>
          data[voxelIndex + i] = (label >> (i * 8) ) & 0xff);
      };

      bucket.label(labelFunc);

      // Push bucket if it's loaded, otherwise, TemporalBucketManager will push
      // it once it is.
      if (bucket.isLoaded()) {
        return this.pushQueue.insert(address);
      }
    }
  }


  getDataValue( voxel, mapping ) {

    if (mapping == null) { mapping = this.EMPTY_MAPPING; }
    const bucket = this.getBucket(this.positionToZoomedAddress(voxel));
    const voxelIndex = this.getVoxelIndex(voxel);

    if (bucket.hasData()) {

      const data = bucket.getData();
      let result = 0;
      // Assuming little endian byte order
      for (let i of __range__(0, this.BYTE_OFFSET, false)) {
        result += (1 << (8 * i)) * data[ voxelIndex + i];
      }

      if (__guard__(mapping, x => x[result]) != null) {
        return mapping[result];
      }

      return result;
    }

    return 0;
  }


  getMappedDataValue(voxel) {

    return this.getDataValue(voxel, this.currentMapping);
  }


  getVoxelIndex(voxel) {

    const voxelOffset = voxel.map(v => v & 0b11111);
    return this.getVoxelIndexByVoxelOffset(voxelOffset);
  }


  positionToZoomedAddress([x, y, z], zoomStep) {
    // return the bucket a given voxel lies in

    if (zoomStep == null) { zoomStep = 0; }
    return [
      x >> (this.BUCKET_SIZE_P + zoomStep),
      y >> (this.BUCKET_SIZE_P + zoomStep),
      z >> (this.BUCKET_SIZE_P + zoomStep),
      zoomStep
    ];
  }
}
Cube.initClass();

export default Cube;

function __range__(left, right, inclusive) {
  let range = [];
  let ascending = left < right;
  let end = !inclusive ? right : ascending ? right + 1 : right - 1;
  for (let i = left; ascending ? i < end : i > end; ascending ? i++ : i--) {
    range.push(i);
  }
  return range;
}
function __guard__(value, transform) {
  return (typeof value !== 'undefined' && value !== null) ? transform(value) : undefined;
}