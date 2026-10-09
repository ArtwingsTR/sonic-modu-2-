package com.example.sonic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Parcaciklar (mavi enerji, kivilcim, ruzgar cizgileri). */
public final class SonicFx {
    private SonicFx() {}

    private static final ParticleEffect BLUE = new DustParticleEffect(new Vector3f(0.15f, 0.55f, 1.0f), 1.6f);
    private static final ParticleEffect BLUE_BIG = new DustParticleEffect(new Vector3f(0.25f, 0.7f, 1.0f), 2.4f);
    private static final ParticleEffect CYAN = new DustParticleEffect(new Vector3f(0.6f, 0.95f, 1.0f), 1.2f);

    private static double rnd(double spread) {
        return (Math.random() - 0.5) * 2.0 * spread;
    }

    public static void onBoostStart(ClientPlayerEntity p) {
        SonicSounds.play(SonicSounds.BOOST_START, 1.0f, 1.0f);
        SonicSounds.startBoostLoop();
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        Vec3d c = p.getPos().add(0, 0.9, 0);
        w.addParticle(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 0, 0, 0);
        for (int i = 0; i < 28; i++) {
            double a = i / 28.0 * Math.PI * 2;
            w.addParticle(BLUE_BIG, c.x, c.y, c.z, Math.cos(a) * 0.45, rnd(0.05), Math.sin(a) * 0.45);
        }
    }

    /** Her tick, boost sirasinda. */
    public static void boostTick(ClientPlayerEntity p, double speedFrac) {
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        Vec3d dir = Vec3d.fromPolar(0, p.getYaw());
        Vec3d pos = p.getPos();
        // arkada mavi enerji izi
        for (int i = 0; i < 3; i++) {
            double back = 0.3 + Math.random() * 1.2;
            w.addParticle(i == 0 ? CYAN : BLUE,
                    pos.x - dir.x * back + rnd(0.3), pos.y + 0.3 + Math.random() * 1.1, pos.z - dir.z * back + rnd(0.3),
                    -dir.x * 0.05, 0.0, -dir.z * 0.05);
        }
        if (p.age % 2 == 0) {
            w.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.x - dir.x * 0.5 + rnd(0.4), pos.y + 0.2 + Math.random() * 1.3, pos.z - dir.z * 0.5 + rnd(0.4),
                    -dir.x * 0.2, rnd(0.05), -dir.z * 0.2);
        }
        // onden gelip gecen ruzgar cizgileri (hiz hissi)
        int streaks = 2 + (int) Math.round(speedFrac * 2);
        Vec3d side = new Vec3d(-dir.z, 0, dir.x);
        for (int i = 0; i < streaks; i++) {
            double ahead = 3.5 + Math.random() * 5.0;
            double lat = rnd(2.4);
            w.addParticle(ParticleTypes.END_ROD,
                    pos.x + dir.x * ahead + side.x * lat, pos.y + 0.2 + Math.random() * 2.2, pos.z + dir.z * ahead + side.z * lat,
                    -dir.x * (0.9 + speedFrac), 0.0, -dir.z * (0.9 + speedFrac));
        }
    }

    public static void onHomingStart(ClientPlayerEntity p) {
        SonicSounds.play(SonicSounds.HOMING_LAUNCH, 1.0f, 1.0f);
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        Vec3d c = p.getPos().add(0, 0.9, 0);
        for (int i = 0; i < 14; i++) {
            double a = i / 14.0 * Math.PI * 2;
            w.addParticle(BLUE, c.x, c.y, c.z, Math.cos(a) * 0.3, rnd(0.1), Math.sin(a) * 0.3);
        }
    }

    public static void homingTick(ClientPlayerEntity p) {
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        Vec3d c = p.getPos().add(0, 0.9, 0);
        for (int i = 0; i < 4; i++) {
            w.addParticle(i % 2 == 0 ? BLUE_BIG : CYAN, c.x + rnd(0.25), c.y + rnd(0.25), c.z + rnd(0.25), 0, 0, 0);
        }
        w.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, rnd(0.2), rnd(0.2), rnd(0.2));
    }

    public static void onHomingHit(ClientPlayerEntity p, Entity target) {
        SonicSounds.play(SonicSounds.HOMING_HIT, 1.0f, 1.0f);
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        Vec3d c = target.getBoundingBox().getCenter();
        w.addParticle(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 0, 0, 0);
        for (int i = 0; i < 30; i++) {
            double a = Math.random() * Math.PI * 2, e = (Math.random() - 0.5) * 1.6;
            double s = 0.25 + Math.random() * 0.4;
            w.addParticle(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : BLUE_BIG,
                    c.x, c.y, c.z, Math.cos(a) * s, e * s, Math.sin(a) * s);
        }
    }
}
