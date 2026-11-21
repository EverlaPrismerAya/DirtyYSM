package net.everla.ysmPhysLib;

import org.joml.Matrix4f;
import java.util.ArrayDeque;
import java.util.Deque;

/** Render-thread context: only LevelRenderer passes may simulate world entities. */
public final class WorldPhysicsContext {
    private static final Deque<Matrix4f> views=new ArrayDeque<>();
    private WorldPhysicsContext() {}
    public static void begin(Matrix4f view) {views.push(new Matrix4f(view).invert());}
    public static void end() {if(!views.isEmpty()) views.pop();}
    public static boolean allows(boolean preview) {return !views.isEmpty() && !preview;}
    public static Matrix4f modelToWorld(Matrix4f pose) {return new Matrix4f(views.element()).mul(pose);}
}
