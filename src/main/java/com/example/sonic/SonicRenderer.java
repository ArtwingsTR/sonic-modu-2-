package com.example.sonic;

import com.example.sonic.model.SonicModel;
import com.example.sonic.model.SonicPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.io.InputStream;

/** Oyuncu modelinin yerine Sonic'i cizer (sadece yerel oyuncu). */
public final class SonicRenderer {
    private SonicRenderer() {}

    /** Modelin blok cinsinden boyu: 1.0 model birimi = SCALE blok. */
    public static final float SCALE = 1.45f;

    private static final Identifier[] TEX = {
            Identifier.of("sonicmod", "textures/entity/sonic_eyes.png"),
            Identifier.of("sonicmod", "textures/entity/sonic_body.png"),
            Identifier.of("sonicmod", "textures/entity/sonic_shoes.png"),
            Identifier.of("sonicmod", "textures/entity/sonic_cloth.png")
    };
    private static final Identifier AURA = Identifier.of("sonicmod", "textures/entity/aura.png");
    private static final Identifier GLOW = Identifier.of("sonicmod", "textures/entity/glow.png");

    private static SonicModel model;
    private static SonicPose pose;
    private static boolean failed;

    private static void ensureLoaded() {
        if (model != null || failed) return;
        try (InputStream in = MinecraftClient.getInstance().getResourceManager()
                .open(Identifier.of("sonicmod", "model/sonic.bin"))) {
            model = SonicModel.load(in);
            pose = new SonicPose(model);
        } catch (Exception ex) {
            failed = true;
            SonicClient.LOGGER.error("Sonic modeli yuklenemedi, normal oyuncu modeli kullanilacak", ex);
        }
    }

    public static boolean isReady() {
        ensureLoaded();
        return model != null;
    }

    /** @return true ise vanilla render iptal edilir */
    public static boolean render(AbstractClientPlayerEntity p, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider vcp, int light) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (p != mc.player) return false;
        if (!isReady()) return false;
        if (p.isInvisible() || p.isSpectator()) return true;

        float boostW = SonicClient.visBoost(tickDelta);
        float ballW = SonicClient.visBall(tickDelta);
        float spin = SonicClient.spin(tickDelta);
        float runPhase = SonicClient.runPhase(tickDelta);

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, p.prevBodyYaw, p.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, p.prevHeadYaw, p.headYaw);
        float pitch = MathHelper.lerp(tickDelta, p.prevPitch, p.getPitch());

        float yaw = bodyYaw;
        if (boostW > 0.05f) yaw = MathHelper.lerpAngleDegrees(tickDelta, p.prevYaw, p.getYaw());
        if (ballW > 0.05f) {
            Vec3d v = p.getVelocity();
            if (v.x * v.x + v.z * v.z > 0.01) yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
        }
        float relYaw = MathHelper.clamp(MathHelper.wrapDegrees(headYaw - yaw), -75f, 75f);

        float limbPos = p.limbAnimator.getPos(tickDelta);
        float limbSpeed = Math.min(1f, p.limbAnimator.getSpeed(tickDelta));
        float walkAmp = Math.min(1f, limbSpeed * 1.5f);

        pose.compose(limbPos * 0.6662f, walkAmp, boostW, runPhase, ballW, relYaw, pitch, spin);
        model.skin(pose.quats, pose.hasRoot ? pose.root : null);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        matrices.scale(SCALE, SCALE, SCALE);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pm = entry.getPositionMatrix();
        int overlay = LivingEntityRenderer.getOverlay(p, 0f);
        float[] op = model.outPos, on = model.outNrm, uv = model.uv;

        for (int g = 0; g < model.groupTex.length; g++) {
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX[model.groupTex[g]]));
            int[] t = model.groupTris[g];
            for (int i = 0; i < t.length; i += 3) {
                int a = t[i], b = t[i + 1], c = t[i + 2];
                vert(vc, pm, entry, op, on, uv, a, overlay, light);
                vert(vc, pm, entry, op, on, uv, b, overlay, light);
                vert(vc, pm, entry, op, on, uv, c, overlay, light);
                vert(vc, pm, entry, op, on, uv, c, overlay, light); // ucgen = dejenere dortgen
            }
        }

        float time = (p.age + tickDelta) / 20f;
        if (boostW > 0.03f) drawBoostAura(vcp, matrices, boostW, time);
        if (ballW > 0.03f) drawBallGlow(vcp, matrices, ballW, spin);

        matrices.pop();
        return true;
    }

    private static void vert(VertexConsumer vc, Matrix4f pm, MatrixStack.Entry e, float[] op, float[] on, float[] uv,
                             int i, int overlay, int light) {
        vc.vertex(pm, op[i * 3], op[i * 3 + 1], op[i * 3 + 2])
                .color(255, 255, 255, 255)
                .texture(uv[i * 2], uv[i * 2 + 1])
                .overlay(overlay)
                .light(light)
                .normal(e, on[i * 3], on[i * 3 + 1], on[i * 3 + 2]);
    }

    // ------------------------------------------------------------------ efektler

    private static void fx(VertexConsumer vc, Matrix4f pm, MatrixStack.Entry e, float x, float y, float z,
                           float u, float v, int r, int g, int b, int a) {
        vc.vertex(pm, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(e, 0f, 1f, 0f);
    }

    private static float teardrop(float s) {
        float cap = 0.16f;
        if (s < cap) {
            float x = (cap - s) / cap;
            return (float) Math.sqrt(Math.max(0f, 1f - x * x));
        }
        float k = (s - cap) / (1f - cap);
        return (float) Math.pow(1f - k, 1.3);
    }

    /** Boost: Sonic'i saran, arkaya uzanan mavi enerji damlasi + ruzgar cizgileri. */
    private static void drawBoostAura(VertexConsumerProvider vcp, MatrixStack matrices, float vis, float time) {
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(AURA));
        for (int shell = 0; shell < 2; shell++) {
            matrices.push();
            matrices.translate(0f, 0.50f, 0.12f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(time * (shell == 0 ? 420f : -300f)));
            MatrixStack.Entry e = matrices.peek();
            Matrix4f pm = e.getPositionMatrix();
            float radius = (shell == 0 ? 0.50f : 0.36f) * (0.75f + 0.25f * vis);
            float len = (shell == 0 ? 2.5f : 1.7f) * vis;
            float zNose = 0.42f;
            int alpha = (int) (vis * (shell == 0 ? 170 : 215));
            int ring = 16, seg = 12;
            for (int j = 0; j < seg; j++) {
                float s0 = j / (float) seg, s1 = (j + 1) / (float) seg;
                float r0 = radius * teardrop(s0), r1 = radius * teardrop(s1);
                float z0 = zNose - s0 * len, z1 = zNose - s1 * len;
                for (int i = 0; i < ring; i++) {
                    float a0 = (float) (i * 2 * Math.PI / ring), a1 = (float) ((i + 1) * 2 * Math.PI / ring);
                    float u0 = i / (float) ring, u1 = (i + 1) / (float) ring;
                    float wob = 1f + 0.05f * (float) Math.sin(time * 40f + i * 2f + j);
                    fx(vc, pm, e, (float) Math.cos(a0) * r0 * wob, (float) Math.sin(a0) * r0 * wob, z0, u0, s0, 255, 255, 255, alpha);
                    fx(vc, pm, e, (float) Math.cos(a1) * r0 * wob, (float) Math.sin(a1) * r0 * wob, z0, u1, s0, 255, 255, 255, alpha);
                    fx(vc, pm, e, (float) Math.cos(a1) * r1 * wob, (float) Math.sin(a1) * r1 * wob, z1, u1, s1, 255, 255, 255, alpha);
                    fx(vc, pm, e, (float) Math.cos(a0) * r1 * wob, (float) Math.sin(a0) * r1 * wob, z1, u0, s1, 255, 255, 255, alpha);
                }
            }
            matrices.pop();
        }

        // arkaya akan ince hiz cizgileri (capraz iki dortgen)
        matrices.push();
        matrices.translate(0f, 0.50f, 0f);
        MatrixStack.Entry e = matrices.peek();
        Matrix4f pm = e.getPositionMatrix();
        for (int i = 0; i < 10; i++) {
            float ph = (time * 2.4f + i * 0.137f) % 1f;
            float ang = i * 2.399f;
            float rad = 0.28f + 0.22f * ((i * 7) % 5) / 4f;
            float x = (float) Math.cos(ang) * rad, y = (float) Math.sin(ang) * rad;
            float zHead = 0.3f - ph * 2.6f, zTail = zHead - 0.9f;
            int a = (int) (vis * 200 * (1f - ph));
            float w = 0.022f;
            fx(vc, pm, e, x - w, y, zHead, 0f, 0.0f, 255, 255, 255, a);
            fx(vc, pm, e, x + w, y, zHead, 1f, 0.0f, 255, 255, 255, a);
            fx(vc, pm, e, x + w, y, zTail, 1f, 0.9f, 255, 255, 255, 0);
            fx(vc, pm, e, x - w, y, zTail, 0f, 0.9f, 255, 255, 255, 0);
            fx(vc, pm, e, x, y - w, zHead, 0f, 0.0f, 255, 255, 255, a);
            fx(vc, pm, e, x, y + w, zHead, 1f, 0.0f, 255, 255, 255, a);
            fx(vc, pm, e, x, y + w, zTail, 1f, 0.9f, 255, 255, 255, 0);
            fx(vc, pm, e, x, y - w, zTail, 0f, 0.9f, 255, 255, 255, 0);
        }
        matrices.pop();
    }

    /** Homing attack: top halinin etrafinda donen mavi enerji kuresi. */
    private static void drawBallGlow(VertexConsumerProvider vcp, MatrixStack matrices, float vis, float spin) {
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(GLOW));
        matrices.push();
        float[] c = SonicPose.BALL_CENTER;
        matrices.translate(c[0], c[1], c[2]);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-spin * 0.6f));
        MatrixStack.Entry e = matrices.peek();
        Matrix4f pm = e.getPositionMatrix();
        float radius = 0.50f * (0.6f + 0.4f * vis);
        int alpha = (int) (vis * 190);
        int lon = 20, lat = 10;
        for (int j = 0; j < lat; j++) {
            float p0 = (float) (Math.PI * j / lat), p1 = (float) (Math.PI * (j + 1) / lat);
            for (int i = 0; i < lon; i++) {
                float t0 = (float) (2 * Math.PI * i / lon), t1 = (float) (2 * Math.PI * (i + 1) / lon);
                // kutuplar X ekseninde (donus ekseni)
                sph(vc, pm, e, radius, p0, t0, i / (float) lon, j / (float) lat, alpha);
                sph(vc, pm, e, radius, p0, t1, (i + 1) / (float) lon, j / (float) lat, alpha);
                sph(vc, pm, e, radius, p1, t1, (i + 1) / (float) lon, (j + 1) / (float) lat, alpha);
                sph(vc, pm, e, radius, p1, t0, i / (float) lon, (j + 1) / (float) lat, alpha);
            }
        }
        matrices.pop();
    }

    private static void sph(VertexConsumer vc, Matrix4f pm, MatrixStack.Entry e, float r, float phi, float theta,
                            float u, float v, int a) {
        float x = (float) Math.cos(phi) * r;
        float s = (float) Math.sin(phi) * r;
        fx(vc, pm, e, x, (float) Math.cos(theta) * s, (float) Math.sin(theta) * s, u, v, 255, 255, 255, a);
    }
}
