package net.everla.ysmPhysLib;

import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** The only Minecraft/rendering adapter. No simulation runs for UI previews or nonplayers. */
public final class PlayerPhysics {
    private PlayerPhysics() {}

    public static void beforeAnimation(AnimatedGeoModel model) {
        if(model!=null && model.ysmPhysics!=null) model.ysmPhysics.restore(model.getMatrixData());
    }

    public static void render(AnimatedGeoModel model, AnimatableEntity<?> animatable, float partialTick, Matrix4f renderPose) {
        var mc=Minecraft.getInstance();
        boolean eligible=WorldPhysicsContext.allows(animatable.entity instanceof Player,
                animatable instanceof IPreviewAnimatable || ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer())
                && mc.level!=null && animatable.entity.level()==mc.level;
        if(!eligible) {
            beforeAnimation(model);
            return;
        }
        var definition=model.getGeoModel().ysmPhysics;
        if(definition==null || definition.joints.isEmpty()) return;
        if(model.ysmPhysics==null) model.ysmPhysics=new PhysSimulation(definition);
        Player player=(Player)animatable.entity;
        double x=Mth.lerp(partialTick,player.xo,player.getX());
        double y=Mth.lerp(partialTick,player.yo,player.getY());
        double z=Mth.lerp(partialTick,player.zo,player.getZ());
        // World rendering pose includes camera rotation and camera-relative translation.
        // Undo both, then rebase near the player to retain float precision far from spawn.
        var camera=mc.gameRenderer.getMainCamera();
        var cameraPos=camera.getPosition();
        Matrix4f root=new Matrix4f().translation((float)(cameraPos.x-x),(float)(cameraPos.y-y),(float)(cameraPos.z-z))
                .mul(WorldPhysicsContext.modelToWorld(renderPose));
        model.ysmPhysics.update(model.getMatrixData(),root,(player.tickCount+partialTick)/20.0,mc.isPaused(),
                player.level(),x,y,z);
    }

    public static float[] hideEndpoints(com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel model, float[] parameters) {
        if(model.ysmPhysics==null || model.ysmPhysics.ends.length==0) return parameters;
        float[] result=parameters.clone();
        for(int end:model.ysmPhysics.ends) {
            result[end*12+9]=1;
            result[end*12+10]=1;
            result[end*12+6]=result[end*12+7]=result[end*12+8]=0;
        }
        return result;
    }
}
