package com.elfmcys.yesstevemodel.client.texture;

import net.everla.dirtyYsm.compat.oculus.ShadersTextureType;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.Map;

public interface ITextureMap {
    Map<ShadersTextureType, ? extends AbstractTexture> getSuffixTextures();
}