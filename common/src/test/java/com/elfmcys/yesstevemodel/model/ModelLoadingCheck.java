package com.elfmcys.yesstevemodel.model;

import com.elfmcys.yesstevemodel.config.ModelLoadingMode;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel.*;
import com.elfmcys.yesstevemodel.network.message.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.net.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModelLoadingCheck {
    public static void main(String[] args) throws Exception {
        URL[] urls=Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).map(s->{try{return Path.of(s).toUri().toURL();}catch(Exception e){throw new RuntimeException(e);}}).toArray(URL[]::new);
        try(URLClassLoader isolated=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.startsWith("net.minecraft.client.") || name.startsWith("com.mojang.blaze3d.")
                        || name.startsWith("com.elfmcys.yesstevemodel.client.") || name.startsWith("net.everla.reimaginedysm.gui."))
                    throw new ClassNotFoundException("Client class reached by headless thumbnail: "+name);
                return super.loadClass(name,resolve);
            }
        }) {
            isolated.setDefaultAssertionStatus(true);
            Thread.currentThread().setContextClassLoader(isolated);
            isolated.loadClass(Headless.class.getName()).getMethod("run").invoke(null);
        }
        System.out.println("PASS: headless midpoint thumbnails, GUI layers and inventory lighting, depth/alpha, bounded packets, page cancellation, cache reuse and request coalescing");
    }

    public static final class Headless {
        public static void run() throws Exception {
            Path roamingRoot=Files.createTempDirectory("ysm-local-roaming-check");
            UUID roamingPlayer=UUID.randomUUID();String roamingHash="a".repeat(64);
            int roamingName=com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool.computeIfAbsent("outfit");
            LocalRoamingStore roamingStore=new LocalRoamingStore(roamingRoot);
            var roamingValues=roamingStore.load(roamingPlayer,roamingHash);roamingValues.put(roamingName,3.5f);
            roamingStore.changed(roamingPlayer,roamingHash,roamingValues);roamingStore.flush();
            assert roamingStore.load(roamingPlayer,roamingHash)==roamingValues : "Player replacement lost local values";
            assert roamingStore.load(UUID.randomUUID(),roamingHash).isEmpty() : "Different players share roaming";
            assert roamingStore.load(roamingPlayer,"b".repeat(64)).isEmpty() : "Different models share roaming";
            roamingStore.close();
            LocalRoamingStore restoredRoaming=new LocalRoamingStore(roamingRoot);
            assert restoredRoaming.load(roamingPlayer,roamingHash).get(roamingName)==3.5f : "Reconnect/restart lost roaming";
            restoredRoaming.close();
            try(var files=Files.walk(roamingRoot)) { for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.delete(path); }
            assert ModelLoadingMode.POTATO.downloadsOnPage()==false;
            assert ModelLoadingMode.PAGE.downloadsOnPage();
            assert ModelLoadingMode.FULL.downloadsOnPage();
            for (ModelLoadingMode mode : ModelLoadingMode.values()) {
                assert mode.allows(ModelLoadingMode.Request.USE);
                assert mode.allows(ModelLoadingMode.Request.PRELOAD) == (mode == ModelLoadingMode.FULL);
                assert mode.allows(ModelLoadingMode.Request.PAGE) == (mode != ModelLoadingMode.POTATO);
            }
            try (var reader = new com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer(Path.of(Headless.class.getResource("/assets/yes_steve_model/builtin/default").toURI()))) {
                byte[] builtin = ModelThumbnailRenderer.render(reader.deserialize());
                assert ModelThumbnailRenderer.validHeader(builtin);
                Files.write(Path.of("builtin-preview-thumbnail.png"),builtin);
                BufferedImage preview=ImageIO.read(new ByteArrayInputStream(builtin));
                int pixels=0;
                for(int y=0;y<preview.getHeight();y++) for(int x=0;x<preview.getWidth();x++) if((preview.getRGB(x,y)>>>24)>0) pixels++;
                assert pixels>100 : "Bundled player thumbnail is empty";
            }
            Path builtinRoot=Path.of(Headless.class.getResource("/assets/yes_steve_model/builtin").toURI());
            try(var paths=Files.walk(builtinRoot)) {
                int checked=0;
                for(Path config:paths.filter(p->p.getFileName().toString().equals("ysm.json")).toList()) {
                    try(var reader=new com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer(config.getParent())) {
                        assert ModelThumbnailRenderer.validHeader(ModelThumbnailRenderer.render(reader.deserialize()));
                        checked++;
                    } catch(Exception ex) { throw new AssertionError("Builtin thumbnail failed: "+config,ex); }
                }
                System.out.println("Rendered builtin thumbnails: "+checked);
            }
            String external=System.getProperty("ysm.thumbnailModels");
            if(external!=null && Files.isRegularFile(Path.of(external))) {
                byte[] decrypted=net.everla.reimaginedysm.security.YsmCrypt.decryptYsmFile(Files.readAllBytes(Path.of(external)));
                try(var reader=new com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer(decrypted)) {
                    RawYsmModel binary=reader.deserialize();
                    long faces=binary.mainEntity.mainModel.bones.stream().flatMap(b->b.cubes.stream()).mapToLong(c->c.faces.size()).sum();
                    System.out.println("Binary thumbnail geometry: bones="+binary.mainEntity.mainModel.bones.size()+", faces="+faces);
                    byte[] png=ModelThumbnailRenderer.render(binary);
                    Files.write(Path.of("external-preview-thumbnail.png"),png);
                    BufferedImage preview=ImageIO.read(new ByteArrayInputStream(png));
                    int pixels=0;
                    for(int y=0;y<preview.getHeight();y++) for(int x=0;x<preview.getWidth();x++) if((preview.getRGB(x,y)>>>24)>0) pixels++;
                    assert pixels>100 : "Binary thumbnail is empty";
                    System.out.println("Rendered binary thumbnail: "+pixels+" visible pixels");
                }
            }
            if(external!=null && Files.isDirectory(Path.of(external))) try(var paths=Files.walk(Path.of(external))) {
                int checked=0;
                for(Path config:paths.filter(p->p.getFileName().toString().equals("ysm.json")).toList()) {
                    try(var reader=new com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer(config.getParent())) {
                        ModelThumbnailRenderer.render(reader.deserialize()); checked++;
                    } catch(Exception ex) { throw new AssertionError("External thumbnail failed: "+config,ex); }
                }
                System.out.println("Rendered external thumbnails: "+checked);
            }
            RawYsmModel raw=fixture();
            byte[] original=ModelThumbnailRenderer.render(raw);
            BufferedImage image=ImageIO.read(new ByteArrayInputStream(original));
            assert image.getWidth()==52 && image.getHeight()==70;
            assert (image.getRGB(20,55)>>>24)==255 : "Opaque face missing";
            assert (image.getRGB(45,55)>>>24)==0 : "Transparent viewport lost";
            RawImage background=new RawImage();background.name="gui_background";background.width=1;background.height=2;background.format=-1;
            background.data=new byte[]{0,0,(byte)255,(byte)255,0,(byte)255,0,(byte)255};
            raw.properties.backgroundImages.add(background);
            BufferedImage backed=ImageIO.read(new ByteArrayInputStream(ModelThumbnailRenderer.render(raw)));
            assert backed.getRGB(45,5)==0xFF0000FF && backed.getRGB(45,65)==0xFF00FF00 : "GUI background missing or scaled incorrectly";
            assert backed.getRGB(20,55)==image.getRGB(20,55) : "Background covered opaque geometry";
            background.name="gui_foreground";
            BufferedImage foreground=ImageIO.read(new ByteArrayInputStream(ModelThumbnailRenderer.render(raw)));
            assert foreground.getRGB(20,55)==0xFF00FF00 : "GUI foreground did not cover geometry";
            raw.properties.backgroundImages.clear();
            RawFace litFace=raw.mainEntity.mainModel.bones.get(0).cubes.get(0).faces.get(0);
            litFace.normal=new float[]{0,1,0};
            BufferedImage lit=ImageIO.read(new ByteArrayInputStream(ModelThumbnailRenderer.render(raw)));
            assert lit.getRGB(20,55)==0xFFFF0000 : "Preview normal transform or inventory lighting ignored";
            litFace.normal=new float[]{0,0,1};
            assert ModelThumbnailRenderer.inventoryLight(new org.joml.Vector3f(0,1,0))==.4f;
            assert ModelThumbnailRenderer.inventoryLight(new org.joml.Vector3f(0,-1,0))==1f;
            assert ModelThumbnailRenderer.inventoryLight(new org.joml.Vector3f(0,0,-1))>.8f;
            // A nearer face must occlude the farther face independent of submission order.
            RawBone front=new RawBone();front.name="front";
            RawCube cube=new RawCube();RawFace face=face(-.25f);cube.faces.add(face);front.cubes.add(cube);raw.mainEntity.mainModel.bones.add(front);
            for(int i=0;i<4;i++) face.u[i]=.75f;
            byte[] transparent=ModelThumbnailRenderer.render(raw);
            BufferedImage layered=ImageIO.read(new ByteArrayInputStream(transparent));
            int center=layered.getRGB(20,55);
            assert (center>>>24)==255 && ((center>>8)&255)>0 && ((center>>16)&255)>0 : "Alpha layer not blended";
            Collections.reverse(raw.mainEntity.mainModel.bones);
            assert Arrays.equals(transparent,ModelThumbnailRenderer.render(raw)) : "Depth depends on bone submission order";
            raw.mainEntity.mainModel.bones.remove(front);
            RawAnimation animation=new RawAnimation();animation.name="preview";animation.length=1;animation.loopMode=1;
            RawBoneAnimation bone=new RawBoneAnimation();bone.boneName="body";
            RawKeyframe start=new RawKeyframe();start.timestamp=0;start.postData=new Object[]{0f,0f,0f};
            RawKeyframe last=new RawKeyframe();last.timestamp=1;last.postData=new Object[]{"math.sin(90) * 4 + q.anim_time",0f,0f};
            bone.position.add(start);bone.position.add(last);animation.boneAnimations.add(bone);
            RawAnimationFile file=new RawAnimationFile();file.animType=1;file.animations.put("preview",animation);raw.mainEntity.animationFiles.put("main",file);raw.properties.previewAnimation="preview";
            assert Math.abs(ModelThumbnailRenderer.finalPose(raw).get("body")[1][0]-2.25f)<.001 : "Animation midpoint or expression ignored";
            animation.length=Float.POSITIVE_INFINITY;
            assert Math.abs(ModelThumbnailRenderer.finalPose(raw).get("body")[1][0]-2.25f)<.001 : "Unspecified animation length midpoint ignored";
            animation.length=1;
            RawTimelineEvent early=new RawTimelineEvent();early.timestamp=.25f;early.events.add("v.midpoint=3;");
            RawTimelineEvent late=new RawTimelineEvent();late.timestamp=.75f;late.events.add("v.midpoint=99;");
            animation.timelineEvents.add(early);animation.timelineEvents.add(late);
            Object saved=last.postData[0];last.postData[0]="v.midpoint";
            assert ModelThumbnailRenderer.finalPose(raw).get("body")[1][0]==1.5f : "Timeline executed beyond midpoint";
            last.postData[0]=saved;animation.timelineEvents.clear();
            byte[] animated=ModelThumbnailRenderer.render(raw);
            assert !Arrays.equals(original,animated) : "Midpoint preview pose ignored";
            animation.blendWeight=0f;
            assert Arrays.equals(original,ModelThumbnailRenderer.render(raw)) : "Animation blend weight ignored";
            animation.blendWeight=null;
            RawAnimation parallel=new RawAnimation();parallel.name="parallel0";parallel.length=Float.POSITIVE_INFINITY;
            RawBoneAnimation hidden=new RawBoneAnimation();hidden.boneName="body";
            RawKeyframe zero=new RawKeyframe();zero.postData=new Object[]{"v.bv=ysm.has_helmet; return v.bv",0f,0f};
            hidden.scale.add(zero);parallel.boneAnimations.add(hidden);file.animations.put("parallel0",parallel);
            var layeredPose=ModelThumbnailRenderer.finalPose(raw).get("body");
            assert layeredPose[2][0]==0 && layeredPose[1][0]==2.25f : "Parallel hide or omitted-channel preservation lost";
            BufferedImage hiddenImage=ImageIO.read(new ByteArrayInputStream(ModelThumbnailRenderer.render(raw)));
            for(int y=0;y<hiddenImage.getHeight();y++) for(int x=0;x<hiddenImage.getWidth();x++)
                assert (hiddenImage.getRGB(x,y)>>>24)==0 : "Parallel-hidden group still rendered";
            // Dense models must not spend the triangle budget on zero-scale alternate geometry.
            byte[] hiddenBeforeDense=ModelThumbnailRenderer.render(raw);
            RawCube denseCube=raw.mainEntity.mainModel.bones.get(0).cubes.get(0);
            RawFace denseFace=denseCube.faces.get(0);
            for(int i=0;i<110000;i++) denseCube.faces.add(denseFace);
            assert Arrays.equals(ModelThumbnailRenderer.render(raw),hiddenBeforeDense) : "Hidden dense geometry changed thumbnail";
            denseCube.faces.subList(1,denseCube.faces.size()).clear();
            raw.properties.previewAnimation="missing";
            assert ModelThumbnailRenderer.finalPose(raw).get("body")[2][0]==0 : "Parallel skipped without preview animation";
            raw.properties.previewAnimation="preview";
            file.animations.remove("parallel0");
            RawAnimation pre=new RawAnimation();pre.name="pre_parallel0";
            RawBoneAnimation preBone=new RawBoneAnimation();preBone.boneName="body";
            RawKeyframe preRotation=new RawKeyframe();preRotation.postData=new Object[]{10f,0f,0f};preBone.rotation.add(preRotation);pre.boneAnimations.add(preBone);
            file.animations.put("pre_parallel0",pre);
            RawBoneAnimation parallelBone=new RawBoneAnimation();parallelBone.boneName="body";
            RawKeyframe rotation=new RawKeyframe();rotation.postData=new Object[]{5f,0f,0f};parallelBone.rotation.add(rotation);
            parallel.boneAnimations.clear();parallel.boneAnimations.add(parallelBone);file.animations.put("parallel0",parallel);
            assert ModelThumbnailRenderer.finalPose(raw).get("body")[0][0]==15 : "Parallel additive rotation/order lost";
            RawAnimationControllerFile controllers=new RawAnimationControllerFile();RawAnimationController controller=new RawAnimationController();
            controller.animationName="player.parallel_custom";controller.initialState="default";
            RawControllerState state=new RawControllerState();state.name="default";state.onEntry.add("v.hide=0;");
            state.animations.add(Map.entry("hide", "1"));controller.states.add(state);controllers.controllers.put(controller.animationName,controller);
            raw.mainEntity.animationControllerFiles.add(controllers);
            RawAnimation hide=new RawAnimation();hide.boneAnimations.add(hidden);file.animations.put("hide",hide);
            assert ModelThumbnailRenderer.finalPose(raw).get("body")[2][0]==0 : "Mapped parallel controller ignored";
            raw.mainEntity.animationControllerFiles.clear();file.animations.remove("hide");file.animations.remove("parallel0");file.animations.remove("pre_parallel0");

            // Parent transforms and rotated bones affect the final image.
            RawBone parent=new RawBone();parent.name="root";parent.rotation[2]=.4f;parent.pivot[1]=8;
            raw.mainEntity.mainModel.bones.get(0).parentName="root";raw.mainEntity.mainModel.bones.add(parent);
            assert !Arrays.equals(animated,ModelThumbnailRenderer.render(raw)) : "Parent rotation ignored";
            parent.parentName="body";
            boolean rejected=false;
            try{ModelThumbnailRenderer.render(raw);}catch(IOException expected){rejected=true;}
            assert rejected : "Cyclic hierarchy accepted";
            parent.parentName="";
            Files.write(Path.of("thumbnail-fixture.png"),ModelThumbnailRenderer.render(raw));
            Path cacheRoot=Files.createTempDirectory("ysm-thumbnail-cache");
            String cacheHash="a".repeat(64);
            Path cacheFile=ModelThumbnailService.cache(cacheRoot,cacheHash);
            Path legacyFile=cacheFile.resolveSibling(cacheHash+".png");
            byte[] cachePng=ModelThumbnailRenderer.render(raw);
            try {
                ModelThumbnailService.writeCache(cacheRoot,cacheHash,cachePng);
                assert cacheFile.toString().endsWith(".ysth");
                assert !ModelThumbnailRenderer.validHeader(Files.readAllBytes(cacheFile));
                assert Arrays.equals(cachePng,ModelThumbnailService.readCache(cacheRoot,cacheHash));
                Files.delete(cacheFile);
                Files.write(legacyFile,cachePng);
                assert Arrays.equals(cachePng,ModelThumbnailService.readCache(cacheRoot,cacheHash));
                assert Files.exists(cacheFile) && !Files.exists(legacyFile) : "Legacy PNG cache was not migrated";
            } finally {
                Files.deleteIfExists(cacheFile);Files.deleteIfExists(legacyFile);
                Files.deleteIfExists(cacheFile.getParent());Files.deleteIfExists(cacheRoot);
            }
            rejected=false;
            try{ModelThumbnailService.cache(Path.of("."),"../escape");}catch(IllegalArgumentException expected){rejected=true;}
            assert rejected;
            FriendlyByteBuf packet=new FriendlyByteBuf(Unpooled.buffer());
            packet.writeLong(1);packet.writeVarInt(11);rejected=false;
            try{C2SModelThumbnailsPacket.decode(packet);}catch(IllegalArgumentException expected){rejected=true;}finally{packet.release();}
            assert rejected : "Oversized thumbnail page accepted";
            packet=new FriendlyByteBuf(Unpooled.buffer());
            packet.writeLong(1);packet.writeUtf("body");packet.writeUtf("a".repeat(64));packet.writeByteArray(new byte[ModelThumbnailRenderer.MAX_BYTES+1]);
            rejected=false;
            try { S2CModelThumbnailPacket.decode(packet); } catch(io.netty.handler.codec.DecoderException expected) { rejected=true; } finally { packet.release(); }
            assert rejected : "Oversized thumbnail payload accepted";
            assert !ModelThumbnailRenderer.validHeader(new byte[24]);
            packet=new FriendlyByteBuf(Unpooled.buffer());
            C2SVersionCheckPacket.encode(new C2SVersionCheckPacket(true),packet);
            assert C2SVersionCheckPacket.decode(packet).restartsSession() : "Resume request lost restart flag";packet.release();
            packet=new FriendlyByteBuf(Unpooled.buffer());
            C2SVersionCheckPacket.encode(new C2SVersionCheckPacket(),packet);
            assert !C2SVersionCheckPacket.decode(packet).restartsSession() : "Ordinary handshake restarts session";packet.release();
            packet=new FriendlyByteBuf(Unpooled.buffer());packet.writeUtf(com.elfmcys.yesstevemodel.network.NetworkHandler.VERSION);packet.writeUtf("reimagined_ysm:model_sync_fragments_v1");
            assert !C2SVersionCheckPacket.decode(packet).restartsSession() : "Legacy handshake requires restart field";packet.release();

            List<String> adminValues=List.of("POTATO","true","default","default","hidden,other","0","5","0","false","1");
            assert com.elfmcys.yesstevemodel.config.ServerConfigPanel.validate(adminValues).size()==10;
            for(int field:new int[]{0,1,2,5,6,7,8,9}) {
                List<String> invalid=new ArrayList<>(adminValues);invalid.set(field,field==2?"":"invalid");
                boolean invalidRejected=false;
                try { com.elfmcys.yesstevemodel.config.ServerConfigPanel.validate(invalid); } catch(IllegalArgumentException expected) { invalidRejected=true; }
                assert invalidRejected : "Invalid administrator field accepted: "+field;
            }
            packet=new FriendlyByteBuf(Unpooled.buffer());
            var adminRequest=new C2SServerConfigPacket(adminValues);C2SServerConfigPacket.encode(adminRequest,packet);
            assert C2SServerConfigPacket.decode(packet).equals(adminRequest);packet.release();
            packet=new FriendlyByteBuf(Unpooled.buffer());packet.writeVarInt(11);rejected=false;
            try { C2SServerConfigPacket.decode(packet); } catch(IllegalArgumentException expected) { rejected=true; } finally { packet.release(); }
            assert rejected : "Unbounded admin config packet";
            packet=new FriendlyByteBuf(Unpooled.buffer());
            var adminResponse=new S2CServerConfigPacket(true,adminValues,"saved");S2CServerConfigPacket.encode(adminResponse,packet);
            assert S2CServerConfigPacket.decode(packet).equals(adminResponse);packet.release();

            packet=new FriendlyByteBuf(Unpooled.buffer());
            var commonPolicy=new S2CCommonPolicyPacket(false,true,List.of("hidden"));
            S2CCommonPolicyPacket.encode(commonPolicy,packet);
            assert S2CCommonPolicyPacket.decode(packet).equals(commonPolicy) : "Common server policy roundtrip lost";
            packet.release();
            packet=new FriendlyByteBuf(Unpooled.buffer());packet.writeBoolean(true);packet.writeBoolean(false);packet.writeVarInt(16385);
            rejected=false;
            try { S2CCommonPolicyPacket.decode(packet); } catch(IllegalArgumentException expected) { rejected=true; } finally { packet.release(); }
            assert rejected : "Unbounded common policy list";

            packet=new FriendlyByteBuf(Unpooled.buffer());
            S2CModelRequestCancelledPacket.encode(new S2CModelRequestCancelledPacket(3,4,5,true),packet);
            var cancelled=S2CModelRequestCancelledPacket.decode(packet);packet.release();
            assert cancelled.h1()==3 && cancelled.h2()==4 && cancelled.token()==5 && cancelled.retryDemand();

            coalescing(raw);
        }
        private static void coalescing(RawYsmModel raw) throws Exception {
            String hash=UUID.randomUUID().toString().replace("-","");
            UUID player=UUID.randomUUID();ModelThumbnailService.page(player,1);
            AtomicInteger reads=new AtomicInteger(),cancelledReads=new AtomicInteger();
            CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(2);
            ModelThumbnailService.Source source=()->{reads.incrementAndGet();started.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Timed out");return raw;};
            ModelThumbnailService.request(hash,source,()->ModelThumbnailService.visible(player,1),png->{assert png.length>0;done.countDown();});
            assert started.await(5,TimeUnit.SECONDS);
            ModelThumbnailService.request(hash,source,()->ModelThumbnailService.visible(player,1),png->{assert png.length>0;done.countDown();});
            UUID other=UUID.randomUUID();ModelThumbnailService.page(other,1);
            ModelThumbnailService.request("f".repeat(64),()->{cancelledReads.incrementAndGet();return raw;},()->ModelThumbnailService.visible(other,1),png->{});
            ModelThumbnailService.page(other,2);release.countDown();
            assert done.await(5,TimeUnit.SECONDS) : "Coalesced response missing";
            assert reads.get()==1 : "Duplicate generation";
            CountDownLatch cached=new CountDownLatch(1);
            ModelThumbnailService.request(hash,()->{throw new IOException("Cache was not reused");},()->true,png->{assert png.length>0;cached.countDown();});
            assert cached.await(5,TimeUnit.SECONDS);
            assert cancelledReads.get()==0 : "Old page generated after cancellation";
            ModelThumbnailService.forget(player);ModelThumbnailService.forget(other);
        }
    }
    private static RawYsmModel fixture() {
        RawYsmModel raw=new RawYsmModel();raw.properties.disablePreviewRotation=true;raw.properties.guiNoLighting=true;
        raw.properties.widthScale=1;raw.properties.heightScale=1;
        raw.mainEntity.mainModel=new RawGeometry();RawBone bone=new RawBone();bone.name="body";
        RawCube cube=new RawCube();cube.faces.add(face(0));bone.cubes.add(cube);raw.mainEntity.mainModel.bones.add(bone);
        RawTexture tex=new RawTexture();tex.name="default";tex.width=2;tex.height=1;tex.imageFormat=-1;
        tex.data=new byte[]{(byte)255,0,0,(byte)255,0,(byte)255,0,(byte)128};raw.mainEntity.textures.put("default",tex);
        return raw;
    }
    private static RawFace face(float z) {
        RawFace face=new RawFace();face.positions=new float[][]{{-.5f,0,z},{.5f,0,z},{.5f,1,z},{-.5f,1,z}};
        face.normal=new float[]{0,0,1};
        face.u=new float[]{0,0,0,0};face.v=new float[]{0,0,0,0};return face;
    }
}

