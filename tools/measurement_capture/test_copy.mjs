import test from 'node:test';
import assert from 'node:assert/strict';
import {COPY} from './copy.mjs';
import {POINTS,FIELDS} from './engine.mjs';
test('French covers capture, detector failures, anatomical labels and all English keys',()=>{
 assert.deepEqual(Object.keys(COPY.fr).sort(),Object.keys(COPY.en).sort());
 for(const locale of ['en','fr']){
  for(const key of [...POINTS,...FIELDS,'modelUnavailable','personCount','poseUncertain','maskUncertain','armOcclusion','wrongView']) assert.equal(typeof COPY[locale][key],'string');
  assert.equal(COPY[locale].steps.length,4);
 }
});
