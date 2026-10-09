package com.example.sonic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/** Sesler assets/sonicmod/sounds/*.ogg (sounds.json ile tanimli). Sadece istemci tarafi. */
public final class SonicSounds {
    private SonicSounds() {}

    public static final SoundEvent BOOST_START = ev("boost_start");
    public static final SoundEvent BOOST_LOOP = ev("boost_loop");
    public static final SoundEvent HOMING_LAUNCH = ev("homing_launch");
    public static final SoundEvent HOMING_HIT = ev("homing_hit");

    private static BoostLoop loop;

    private static SoundEvent ev(String name) {
        return SoundEvent.of(Identifier.of("sonicmod", name));
    }

    public static void play(SoundEvent e, float volume, float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(e, pitch, volume));
    }

    public static void startBoostLoop() {
        if (loop != null && !loop.isDone()) return;
        loop = new BoostLoop();
        MinecraftClient.getInstance().getSoundManager().play(loop);
    }

    /** Boost sirasinda yukselen ruzgar/enerji sesi; boost bitince sonerek kapanir. */
    private static final class BoostLoop extends MovingSoundInstance {
        BoostLoop() {
            super(BOOST_LOOP, SoundCategory.PLAYERS, Random.create());
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 0.45f;
            this.pitch = 0.9f;
            this.relative = true;
            this.attenuationType = SoundInstance.AttenuationType.NONE;
        }

        @Override
        public void tick() {
            if (MinecraftClient.getInstance().player == null) {
                setDone();
                return;
            }
            if (SonicClient.isBoosting()) {
                float f = (float) (SonicClient.boostSpeed() / SonicClient.BOOST_MAX_SPEED);
                this.volume = Math.min(0.75f, this.volume + 0.08f);
                this.pitch = 0.85f + 0.4f * f;
            } else {
                this.volume -= 0.12f;
                if (this.volume <= 0.02f) setDone();
            }
        }
    }
}
