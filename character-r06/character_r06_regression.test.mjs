// R0.6 geometry/rig regression tests. Authored 2026-10-12; EXECUTION: NOT_RUN.
// This is test-source escrow, not a claim that a new character model exists.
// Run with Node 22+, three@0.180.0 and acorn@8 installed:
// CHARACTER_HTML=/absolute/path/character_r06_hair_body_candidate.html node --test character_r06_regression.test.mjs
// Tests extract the actual function declarations from the supplied HTML.
// They do not substitute a copied implementation or call the model host.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createHash} from 'node:crypto';
import {parse} from 'acorn';
import * as THREE from 'three';
const filename=process.env.CHARACTER_HTML;
assert.ok(filename&&fs.existsSync(filename),'CHARACTER_HTML must identify the actual source HTML');
assert.equal(THREE.REVISION,'180','Use the same Three.js version as the source');
const html=fs.readFileSync(filename,'utf8');
const match=html.match(/<script id="viewer-app" type="module">([\s\S]*?)<\/script>/);
assert.ok(match,'Source viewer module was not found');
const source=match[1];
const wanted=['r06Require','r06Meshes','r06Geometry','r06Clamp','r06Smooth','r06MeshCheck','r06Bones','r06SurfaceIndex','r06SurfaceWeights','r06FrontSampler','r06ClayMaterial','r06SkinFingerprint','r06Attach','r07BodyShape','r06HairLimit'];
const ast=parse(source,{ecmaVersion:'latest',sourceType:'module'});
const declarations=ast.body.filter(n=>n.type==='FunctionDeclaration'&&wanted.includes(n.id.name)).map(n=>source.slice(n.start,n.end));
const resultExpression=wanted.map(n=>n+':typeof '+n+"==='undefined'?undefined:"+n).join(',');
const api=new Function('THREE','"use strict";\n'+declarations.join('\n')+'\nreturn {'+resultExpression+'};')(THREE);
console.log('# actual source SHA256 '+createHash('sha256').update(html).digest('hex'));
function call(name,...args){assert.equal(typeof api[name],'function','Missing actual source function: '+name);return api[name](...args);}
function near(a,b,t=1e-6){assert.ok(Math.abs(a-b)<=t,'Expected '+a+' to be within '+t+' of '+b);}
function geometry(points,index=[0,1,2]){const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(points,3));if(index!==null)g.setIndex(index);g.computeVertexNormals();return g;}
function tilted(){const g=geometry([0,0,0, .01,0,.01, .01,.01,.02, 0,.01,.01],[0,1,2,0,2,3]);const m=new THREE.Mesh(g,new THREE.MeshStandardMaterial());m.name='Open_Hooded_Jacket';m.updateMatrixWorld(true);return m;}
function skinned(count=3){const g=geometry([0,0,0, .01,0,0, 0,.01,0]);g.setAttribute('skinIndex',new THREE.Uint16BufferAttribute([0,0,0,0,1,0,0,0,2,0,0,0],4));g.setAttribute('skinWeight',new THREE.Float32BufferAttribute([1,0,0,0,1,0,0,0,1,0,0,0],4));const parent=new THREE.Group();parent.name='FixtureScene';const bones=Array.from({length:count},(_,i)=>{const b=new THREE.Bone();b.name=i===0?'head':'joint_'+i;return b;});for(let i=1;i<bones.length;i++)bones[0].add(bones[i]);parent.add(bones[0]);const mesh=new THREE.SkinnedMesh(g,new THREE.MeshStandardMaterial());mesh.name='FixtureBody';parent.add(mesh);parent.updateMatrixWorld(true);mesh.bind(new THREE.Skeleton(bones));parent.updateMatrixWorld(true);return {mesh,parent,bones};}


test('front depth uses a triangle interior rather than nearest sampled vertex',()=>{const m=tilted();near(call('r06FrontSampler',m)(.0025,.0025),.005);});
test('front sampling rejects a point outside the cloth instead of inventing depth',()=>{assert.throws(()=>call('r06FrontSampler',tilted())(.1,.1));});
test('front sampling respects mesh rotation and translation',()=>{const m=tilted();m.position.set(.2,.1,.3);m.rotation.z=.31;m.updateMatrixWorld(true);const p=new THREE.Vector3(.003,.004,.007).applyMatrix4(m.matrixWorld);near(call('r06FrontSampler',m)(p.x,p.y),p.z);});
test('front sampling accepts either triangle winding',()=>{const m=tilted();m.geometry.setIndex([2,1,0,3,2,0]);near(call('r06FrontSampler',m)(.0025,.0025),.005);});
test('closest surface reports a projected point and barycentric coordinates',()=>{const m=new THREE.Mesh(geometry([0,0,0,.01,0,0,0,.01,0]));const hit=call('r06SurfaceIndex',m).nearest(new THREE.Vector3(.0025,.0025,.001),.01);near(hit.distance,.001);near(hit.point.z,0);near(hit.barycentric.x,.5);near(hit.barycentric.y,.25);near(hit.barycentric.z,.25);});
test('non-indexed triangle surfaces are supported',()=>{const m=new THREE.Mesh(geometry([0,0,0,.01,0,0,0,.01,0],null));const hit=call('r06SurfaceIndex',m).nearest(new THREE.Vector3(.002,.002,.001),.01);near(hit.point.x,.002);near(hit.point.y,.002);near(hit.point.z,0);});
test('a surface consisting only of degenerate faces is rejected',()=>{const m=new THREE.Mesh(geometry([0,0,0,0,0,0,0,0,0]));assert.throws(()=>call('r06SurfaceIndex',m));});
test('surface acceleration data invalidates after geometry version changes',()=>{const m=tilted(),first=call('r06FrontSampler',m)(.0025,.0025),a=m.geometry.getAttribute('position');for(let i=0;i<a.count;i++)a.setZ(i,a.getZ(i)+.02);a.needsUpdate=true;near(call('r06FrontSampler',m)(.0025,.0025),first+.02);});
test('weights interpolate across all three vertices of the closest face',()=>{const {mesh}=skinned();const w=call('r06SurfaceWeights',mesh,new THREE.Vector3(.0025,.0025,.001),{maxDistance:.01});near(w.weights[0],.5);near(w.weights[1],.25);near(w.weights[2],.25);assert.deepEqual(w.joints.slice(0,3),[0,1,2]);near(w.droppedWeight,0);});
test('four-influence reduction reports lost mass and renormalizes retained weights',()=>{const {mesh}=skinned(8);mesh.geometry.setAttribute('skinIndex',new THREE.Uint16BufferAttribute([0,1,2,3,2,3,4,5,4,5,6,7],4));mesh.geometry.setAttribute('skinWeight',new THREE.Float32BufferAttribute(Array(12).fill(.25),4));const length=mesh.geometry.getAttribute('position').getX(1);const w=call('r06SurfaceWeights',mesh,new THREE.Vector3(length/3,length/3,.001),{maxDistance:.01,maxDroppedWeight:.5});assert.deepEqual([...w.joints].sort((a,b)=>a-b),[2,3,4,5]);for(const x of w.weights)near(x,.25);near(w.weights.reduce((a,b)=>a+b,0),1);near(w.droppedWeight,1/3);});
test('excessive influence loss fails rather than silently changing deformation',()=>{const {mesh}=skinned(8);mesh.geometry.setAttribute('skinIndex',new THREE.Uint16BufferAttribute([0,1,2,3,2,3,4,5,4,5,6,7],4));mesh.geometry.setAttribute('skinWeight',new THREE.Float32BufferAttribute(Array(12).fill(.25),4));const length=mesh.geometry.getAttribute('position').getX(1);assert.throws(()=>call('r06SurfaceWeights',mesh,new THREE.Vector3(length/3,length/3,.001),{maxDistance:.01,maxDroppedWeight:.1}));});
test('distant attachment refuses unrelated surface weights',()=>{const {mesh}=skinned();assert.throws(()=>call('r06SurfaceWeights',mesh,new THREE.Vector3(1,1,1),{maxDistance:.01}));});
test('fractional skin joint indices fail mesh validation',()=>{const {mesh}=skinned();mesh.geometry.setAttribute('skinIndex',new THREE.Float32BufferAttribute([.5,0,0,0,1,0,0,0,2,0,0,0],4));assert.throws(()=>call('r06MeshCheck',mesh));});
test('skin attributes must contain one row per position',()=>{const {mesh}=skinned();mesh.geometry.setAttribute('skinWeight',new THREE.Float32BufferAttribute([1,0,0,0,1,0,0,0],4));assert.throws(()=>call('r06MeshCheck',mesh));});
test('bone fingerprint detects reparenting with unchanged local transforms',()=>{const {parent,bones}=skinned();const before=call('r06Bones',parent);bones[1].add(bones[2]);assert.notEqual(call('r06Bones',parent),before);});
test('binding fingerprint detects inverse-bind mutations',()=>{const {mesh}=skinned();const before=call('r06SkinFingerprint',mesh);mesh.skeleton.boneInverses[0].elements[12]+=.001;assert.notEqual(call('r06SkinFingerprint',mesh),before);});
test('gray hair material keeps coverage controls while dropping texture RGB',()=>{const map=new THREE.Texture(),alphaMap=new THREE.Texture();const src=new THREE.MeshStandardMaterial({map,alphaMap,alphaTest:.27,side:THREE.DoubleSide});const gray=call('r06ClayMaterial',src);assert.equal(gray.map,map);assert.equal(gray.alphaMap,alphaMap);near(gray.alphaTest,.27);assert.equal(gray.side,src.side);const shader={fragmentShader:'#include <map_fragment>'};gray.onBeforeCompile(shader);assert.ok(shader.fragmentShader.includes('diffuseColor.rgb = diffuse;'));assert.equal(src.onBeforeCompile===gray.onBeforeCompile,false);assert.equal(src.map,map);});
test('opaque skin gray mode does not retain the reference-photo map',()=>{const src=new THREE.MeshStandardMaterial({map:new THREE.Texture()});const gray=call('r06ClayMaterial',src);assert.equal(gray.map,null);assert.equal(gray.alphaMap,null);});
test('attached geometry preserves world location and shares the intended bind state',()=>{const {mesh,parent}=skinned();mesh.position.set(.05,.06,.07);parent.updateMatrixWorld(true);mesh.bind(mesh.skeleton,mesh.matrixWorld.clone());parent.updateMatrixWorld(true);const g=geometry([.002,.002,.001,.004,.002,.001,.002,.004,.001]);g.applyMatrix4(mesh.matrixWorld);const expected=[];for(let i=0;i<3;i++)expected.push(new THREE.Vector3().fromBufferAttribute(g.getAttribute('position'),i));const added=call('r06Attach',g,mesh,new THREE.MeshStandardMaterial(),'R06_Test_Pocket');parent.updateMatrixWorld(true);for(let i=0;i<3;i++){const actual=new THREE.Vector3().fromBufferAttribute(added.geometry.getAttribute('position'),i).applyMatrix4(added.matrixWorld);near(actual.distanceTo(expected[i]),0);}assert.equal(call('r06SkinFingerprint',added),call('r06SkinFingerprint',mesh));});
test('rigid hair attachment refuses a missing named head bone',()=>{const {mesh}=skinned();assert.throws(()=>call('r06Attach',geometry([0,0,0,.01,0,0,0,.01,0]),mesh,new THREE.MeshStandardMaterial(),'R06_Test_Hair','missing_head'));});


test('body shape narrows central hip and leg regions without moving protected upper-body or distant arm vertices',()=>{
 const g=geometry([
   .14,.90,.00, .25,.90,.00, .10,.60,.00,
   -.14,.90,.00, -.25,.90,.00, -.10,.60,.00,
   .08,1.25,.00, -.08,1.25,.00, 0,1.30,.01
 ],[0,1,2,3,4,5,6,7,8]);
 const parent=new THREE.Group(),mesh=new THREE.Mesh(g,new THREE.MeshStandardMaterial());mesh.name='Body';parent.add(mesh);parent.updateMatrixWorld(true);
 const before=Array.from(g.getAttribute('position').array);const out=call('r07BodyShape',[mesh]);const p=mesh.geometry.getAttribute('position');
 assert.ok(Math.abs(p.getX(0))<Math.abs(before[0]),'hip must narrow');
 assert.equal(p.getX(1),before[3],'distant arm/hand x must stay fixed');
 assert.ok(Math.abs(p.getX(2)-.09)<Math.abs(before[6]-.09),'leg section must narrow around leg center');
 assert.equal(p.getX(6),before[18],'upper body must stay fixed');
 assert.ok(out.verticesChanged>=4);
});

// Scope: these are numerical and material-configuration regression tests.
// They do NOT certify shader compilation, visual likeness, global intersection,
// garment draping, full-body animation, or successful export of the actual R0.5.
// Required next: run this suite against the unmodified baseline (record real RED),
// then the patched source (real GREEN), followed by real-browser GLB round-trip
// and fixed-camera before/after screenshots. Do not mark tests passed from source review.

test('R0.9 scalp hairline covers front and back without trimming to crown',()=>{
 const front=call('r06HairLimit',0),back=call('r06HairLimit',Math.PI),side=call('r06HairLimit',Math.PI/2);
 assert.ok(front>=1.37&&front<=1.47,'front hairline must terminate above eyebrows');
 assert.ok(back>=1.86&&back<=1.95,'back hairline must cover rear scalp');
 assert.ok(side>=1.62&&side<=1.69,'side hairline must clear upper ear');
});
