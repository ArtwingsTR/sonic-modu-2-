package com.example.sonic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SonicClient implements ClientModInitializer {

    // ---- Ayarlar (ince ayar burada yapilir) ----
    static final double BOOST_MAX_SPEED = 1.8;   // blok/tick (1.8 = 36 blok/sn)
    static final double BOOST_START_KICK = 0.8;  // boost'a basinca aninda gelen ilk hiz (Generations'taki "patlama")
    static final double BOOST_ACCEL = 0.10;      // her tick hiz artisi
    static final float GAUGE_MAX = 100f;
    static final float GAUGE_DRAIN = 1.1f;       // boost sirasinda tick basina
    static final float GAUGE_REGEN = 0.35f;      // boost yokken tick basina
    static final double HOMING_RANGE = 16.0;
    static final double HOMING_SPEED = 2.2;
    static final int HOMING_MAX_TICKS = 25;
    static final float HOMING_GAUGE_BONUS = 15f;
    static final double FOV_MAX_BONUS = 38.0;

    // ---- Durum ----
    public static volatile double fovBonus = 0;
    static float gauge = GAUGE_MAX;
    static double boostSpeed = 0;
    static boolean prevJump = false;
    static LivingEntity homingTarget = null;
    static int homingTicks = 0;

    public static final Logger LOGGER = LoggerFactory.getLogger("sonicmod");

    // gorsel durum (renderer okur)
    static boolean boosting = false;
    static boolean wasBoosting = false;
    static boolean exhausted = false;   // gauge bitince, 25'e dolana kadar boost kilitli (titreme olmasin)
    static boolean homingActive = false;
    static float visBoost, visBoostPrev, visBall, visBallPrev;
    static float spin, spinPrev, runPhase, runPhasePrev;

    public static boolean isBoosting() { return boosting; }
    public static double boostSpeed() { return boostSpeed; }
    public static float visBoost(float t) { return MathHelper.lerp(t, visBoostPrev, visBoost); }
    public static float visBall(float t) { return MathHelper.lerp(t, visBallPrev, visBall); }
    public static float spin(float t) { return MathHelper.lerp(t, spinPrev, spin); }
    public static float runPhase(float t) { return MathHelper.lerp(t, runPhasePrev, runPhase); }

    static KeyBinding boostKey;
    static KeyBinding homingKey;

    @Override
    public void onInitializeClient() {
        boostKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sonicmod.boost", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.sonicmod"));
        homingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sonicmod.homing", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.sonicmod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick(client);
            updateVisuals(client);
        });
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> renderHud(ctx));
    }

    private static void tick(MinecraftClient client) {
        var player = client.player;
        boosting = false;
        homingActive = false;
        if (player == null || client.world == null || client.currentScreen != null) {
            boostSpeed = 0;
            homingTarget = null;
            fovBonus *= 0.8;
            wasBoosting = false;
            return;
        }

        boolean jumpNow = client.options.jumpKey.isPressed();
        boolean jumpEdge = jumpNow && !prevJump;
        prevJump = jumpNow;
        boolean airborne = !player.isOnGround() && !player.isTouchingWater() && !player.isClimbing();

        // ---------- Homing Attack ----------
        boolean homingRequested = jumpEdge || homingKey.isPressed();
        if (homingTarget == null && homingRequested && airborne) {
            homingTarget = findTarget(client);
            homingTicks = 0;
            if (homingTarget != null) SonicFx.onHomingStart(player);
        }

        if (homingTarget != null) {
            homingTicks++;
            if (!homingTarget.isAlive() || homingTicks > HOMING_MAX_TICKS) {
                homingTarget = null;
            } else {
                Vec3d to = homingTarget.getBoundingBox().getCenter().subtract(player.getPos().add(0, 0.9, 0));
                double dist = to.length();
                if (dist < 2.2) {
                    if (client.interactionManager != null) {
                        client.interactionManager.attackEntity(player, homingTarget);
                        player.swingHand(Hand.MAIN_HAND);
                    }
                    SonicFx.onHomingHit(player, homingTarget);
                    // Sonic gibi sekme
                    player.setVelocity(0, 0.65, 0);
                    player.fallDistance = 0;
                    gauge = Math.min(GAUGE_MAX, gauge + HOMING_GAUGE_BONUS);
                    homingTarget = null;
                } else {
                    Vec3d v = to.normalize().multiply(HOMING_SPEED);
                    player.setVelocity(v);
                    player.fallDistance = 0;
                    homingActive = true;
                    SonicFx.homingTick(player);
                }
            }
            fovBonus += (FOV_MAX_BONUS * 0.8 - fovBonus) * 0.25;
            wasBoosting = false;
            return;
        }

        // ---------- Boost ----------
        if (gauge <= 0) exhausted = true;
        else if (exhausted && gauge >= 25f) exhausted = false;
        boolean wantBoost = boostKey.isPressed() && !exhausted && gauge > 0;
        if (wantBoost) {
            if (!wasBoosting) {
                boostSpeed = Math.max(boostSpeed, BOOST_START_KICK);
                SonicFx.onBoostStart(player);
            }
            boostSpeed = Math.min(BOOST_MAX_SPEED, boostSpeed + BOOST_ACCEL);
            if (player.horizontalCollision) {
                boostSpeed = 0.3; // duvara carpinca yavasla
            }
            Vec3d dir = Vec3d.fromPolar(0, player.getYaw());
            Vec3d vel = player.getVelocity();
            player.setVelocity(dir.x * boostSpeed, vel.y, dir.z * boostSpeed);
            player.fallDistance = 0;
            gauge = Math.max(0, gauge - GAUGE_DRAIN);
            boosting = true;
            SonicFx.boostTick(player, boostSpeed / BOOST_MAX_SPEED);
        } else {
            boostSpeed = 0;
            gauge = Math.min(GAUGE_MAX, gauge + GAUGE_REGEN);
        }

        wasBoosting = boosting;

        double targetFov = FOV_MAX_BONUS * (boostSpeed / BOOST_MAX_SPEED);
        fovBonus += (targetFov - fovBonus) * 0.2;
    }

    /** Model animasyonu icin yumusatilmis degerler (her tick). */
    private static void updateVisuals(MinecraftClient client) {
        visBoostPrev = visBoost;
        visBallPrev = visBall;
        spinPrev = spin;
        runPhasePrev = runPhase;

        visBoost += ((boosting ? 1f : 0f) - visBoost) * 0.4f;
        visBall += ((homingActive ? 1f : 0f) - visBall) * 0.6f;
        if (visBoost < 0.01f) visBoost = 0f;
        if (visBall < 0.01f) visBall = 0f;

        if (visBall > 0.01f) spin += 58f;
        if (visBoost > 0.01f) {
            float f = (float) (boostSpeed / BOOST_MAX_SPEED);
            runPhase += 0.55f + 0.75f * f;
        }
    }

    private static LivingEntity findTarget(MinecraftClient client) {
        var player = client.player;
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d eye = player.getEyePos();
        List<LivingEntity> list = client.world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(HOMING_RANGE),
                e -> e != player && e.isAlive() && !e.isSpectator());

        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity e : list) {
            Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > HOMING_RANGE || d < 0.5) continue;
            double dot = look.dotProduct(to.normalize());
            if (dot < 0.5) continue; // onde olmali
            double score = dot * 2.0 - d / HOMING_RANGE;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static void renderHud(net.minecraft.client.gui.DrawContext ctx) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        drawSpeedEffects(ctx, w, h);
        int barW = 100, barH = 8;
        int x = w - barW - 12;
        int y = h - 28;

        ctx.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, 0xCC000000);
        int filled = (int) (barW * (gauge / GAUGE_MAX));
        int color = boostSpeed > 0 ? 0xFF00C8FF : 0xFF1E78FF;
        ctx.fill(x, y, x + filled, y + barH, color);
        ctx.drawText(client.textRenderer, "BOOST", x, y - 12, 0xFFFFFFFF, true);
    }

    /** Boost'ta ekran kenarlarinda mavi parlama ve merkezden acilan hiz cizgileri. */
    private static void drawSpeedEffects(net.minecraft.client.gui.DrawContext ctx, int w, int h) {
        float v = visBoost;
        if (v < 0.03f) return;
        int band = h / 5;
        int a = (int) (v * 90);
        int top = (a << 24) | 0x1E8CFF;
        ctx.fillGradient(0, 0, w, band, top, 0x001E8CFF);
        ctx.fillGradient(0, h - band, w, h, 0x001E8CFF, top);

        var m = ctx.getMatrices();
        java.util.Random rnd = new java.util.Random(System.nanoTime() / 40_000_000L);
        int lines = (int) (22 * v);
        float cx = w / 2f, cy = h / 2f, maxR = (float) Math.hypot(cx, cy);
        for (int i = 0; i < lines; i++) {
            float ang = rnd.nextFloat() * 360f;
            float r0 = maxR * (0.35f + rnd.nextFloat() * 0.45f);
            float len = maxR * (0.12f + rnd.nextFloat() * 0.25f);
            int la = (int) (v * (60 + rnd.nextInt(100)));
            m.push();
            m.translate(cx, cy, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ang));
            ctx.fill((int) r0, 0, (int) (r0 + len), 1, (la << 24) | 0xCFEFFF);
            m.pop();
        }
    }
}
