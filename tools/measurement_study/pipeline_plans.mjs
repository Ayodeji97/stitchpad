// Build-time helper only. The portable kit contains the derived JSON, not a Node requirement.
import {estimateCapture} from '../measurement_capture/engine.mjs';
import {OUTLINE_VERSION} from '../measurement_capture/outline.mjs';
import {readFile} from 'node:fs/promises';
const manifest=JSON.parse(await readFile(new URL('../measurement_capture/assets.lock.json',import.meta.url)));
const model=manifest.files.find(x=>x.path==='pose_landmarker_full.task');
function view(mode){
 return {width:800,height:1600,qualityConfirmed:true,synthetic:true,points:{
  top:{x:.5,y:.05},floor:{x:.5,y:.95},chestLeft:{x:.3,y:.3},chestRight:{x:.7,y:.3},
  waistLeft:{x:.35,y:.42},waistRight:{x:.65,y:.42},hipsLeft:{x:.3,y:.54},hipsRight:{x:.7,y:.54},
 },provenance:mode==='M'?undefined:{method:OUTLINE_VERSION,sdk:manifest.sdk,modelSha256:model.sha256,edited:mode==='E'}};
}
const plans={};
for(const front of ['A','E','M'])for(const side of ['A','E','M']){
 const capture={consent:true,journey:'existing_customer',heightMm:1800,front:view(front),side:view(side)};
 plans[front+side]={front_mode:front,side_mode:side,engine_version:estimateCapture(capture).pipelineVersion};
}
process.stdout.write(JSON.stringify({note:'Planned source versions only. No measurements or accuracy approval.',plans},null,2)+'\n');
