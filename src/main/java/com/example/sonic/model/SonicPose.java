package com.example.sonic.model;

/**
 * Prosedurel pozlar: yuruyus/bekleme, boost kosusu (kollar arkada), top hali (homing attack).
 * Python onizlemesiyle birebir ayni formuller (test: ParityTest).
 */
public final class SonicPose {
    private static final float LEAN = 42f;
    /** Top donusunun merkezi (model uzayi) */
    public static final float[] BALL_CENTER = {0f, 0.45f, 0.12f};

    private final SonicModel m;
    private final int n;
    private final float[] a, b, c;       // walk, boost, ball quaternionlari (4 * n)
    public final float[] quats;          // sonuc
    public final float[] root = new float[12];
    public boolean hasRoot;

    // kemik indeksleri
    private final int thighL, thighR, calfL, calfR, footL, footR, upL, upR, foreL, foreR;
    private final int spine, spine1, neck, head, hips;
    private final int[][] fingers = new int[2][];   // [side][..]  index1..3,middle1..3,ring1..3,pinky1..3,thumb1
    private final int[] needles, needles1;

    public SonicPose(SonicModel model) {
        this.m = model;
        this.n = model.boneCount;
        a = new float[n * 4]; b = new float[n * 4]; c = new float[n * 4]; quats = new float[n * 4];
        thighL = m.bone("Thigh_L"); thighR = m.bone("Thigh_R");
        calfL = m.bone("Calf_L"); calfR = m.bone("Calf_R");
        footL = m.bone("Foot_L"); footR = m.bone("Foot_R");
        upL = m.bone("UpperArm_L"); upR = m.bone("UpperArm_R");
        foreL = m.bone("ForeArm_L"); foreR = m.bone("ForeArm_R");
        spine = m.bone("Spine"); spine1 = m.bone("Spine1"); neck = m.bone("Neck"); head = m.bone("Head");
        hips = m.bone("Hips");
        for (int s = 0; s < 2; s++) {
            String sd = s == 0 ? "L" : "R";
            String[] fn = {"Index", "Middle", "Ring", "Pinky"};
            int[] f = new int[13];
            int k = 0;
            for (String name : fn) for (int j = 1; j <= 3; j++) f[k++] = m.bone(name + j + "_" + sd);
            f[12] = m.bone("Thumb1_" + sd);
            fingers[s] = f;
        }
        String[] nn = {"Needle_U_L", "Needle_U_C", "Needle_U_R", "Needle_B_L", "Needle_B_C", "Needle_B_R"};
        String[] n1 = {"Needle1_U_L", "Needle1_U_C", "Needle1_U_R", "Needle1_B_L", "Needle1_B_C", "Needle1_B_R"};
        needles = new int[6]; needles1 = new int[6];
        for (int i = 0; i < 6; i++) { needles[i] = m.bone(nn[i]); needles1[i] = m.bone(n1[i]); }
    }

    // ---- quaternion yardimcilari ----
    private static void ident(float[] q, int i) { q[i * 4] = 0; q[i * 4 + 1] = 0; q[i * 4 + 2] = 0; q[i * 4 + 3] = 1; }

    private static void axis(float[] out, char ax, float deg) {
        float h = (float) Math.toRadians(deg) / 2f, s = (float) Math.sin(h), cc = (float) Math.cos(h);
        out[0] = ax == 'x' ? s : 0; out[1] = ax == 'y' ? s : 0; out[2] = ax == 'z' ? s : 0; out[3] = cc;
    }

    private static void mul(float[] a, float[] b, float[] out) {
        float ax = a[0], ay = a[1], az = a[2], aw = a[3], bx = b[0], by = b[1], bz = b[2], bw = b[3];
        out[0] = aw * bx + ax * bw + ay * bz - az * by;
        out[1] = aw * by - ax * bz + ay * bw + az * bx;
        out[2] = aw * bz + ax * by - ay * bx + az * bw;
        out[3] = aw * bw - ax * bx - ay * by - az * bz;
    }

    private final float[] t1 = new float[4], t2 = new float[4], t3 = new float[4];

    /** q[i] = Q(op1, op2, ...)  -- ilk op once uygulanir (python Q() ile ayni) */
    private void set(float[] q, int i, char ax1, float d1) {
        axis(t1, ax1, d1);
        q[i * 4] = t1[0]; q[i * 4 + 1] = t1[1]; q[i * 4 + 2] = t1[2]; q[i * 4 + 3] = t1[3];
    }

    private void set(float[] q, int i, char ax1, float d1, char ax2, float d2) {
        axis(t1, ax1, d1);
        axis(t2, ax2, d2);
        mul(t2, t1, t3);
        q[i * 4] = t3[0]; q[i * 4 + 1] = t3[1]; q[i * 4 + 2] = t3[2]; q[i * 4 + 3] = t3[3];
    }

    private void fist(float[] q, int side, float curl) {
        float s = side == 0 ? 1f : -1f;
        float[][] ang = {{70, 80, 50}, {75, 85, 55}, {75, 85, 55}, {70, 80, 55}};
        int k = 0;
        for (int f = 0; f < 4; f++)
            for (int j = 0; j < 3; j++) {
                int bi = fingers[side][k++];
                if (bi >= 0) set(q, bi, 'z', -s * ang[f][j] * curl);
            }
        int th = fingers[side][12];
        if (th >= 0) set(q, th, 'z', -s * 20f * curl, 'y', s * 25f * curl);
    }

    private void walk(float phase, float amp) {
        for (int i = 0; i < n; i++) ident(a, i);
        float A = 38f * amp;
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? 1f : -1f;
            float ph = phase + (side == 0 ? 0f : (float) Math.PI);
            float sw = (float) Math.sin(ph);
            float lift = Math.max(0f, (float) Math.sin(ph + Math.PI / 2));
            set(a, side == 0 ? thighL : thighR, 'x', -A * sw);
            set(a, side == 0 ? calfL : calfR, 'x', lift * 55f * amp);
            set(a, side == 0 ? footL : footR, 'x', -lift * 20f * amp);
            set(a, side == 0 ? upL : upR, 'z', -s * 78f, 'x', A * 0.8f * sw - 4f * (1f - amp));
            set(a, side == 0 ? foreL : foreR, 'y', -s * (12f + 23f * amp));
            fist(a, side, 0.7f);
        }
        set(a, spine, 'x', 6f * amp);
        set(a, head, 'x', -6f * amp);
    }

    private void boost(float phase) {
        for (int i = 0; i < n; i++) ident(b, i);
        set(b, spine, 'x', LEAN * 0.55f);
        set(b, spine1, 'x', LEAN * 0.45f);
        set(b, neck, 'x', -LEAN * 0.45f);
        set(b, head, 'x', -LEAN * 0.4f);
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? 1f : -1f;
            float ph = phase + (side == 0 ? 0f : (float) Math.PI);
            float sw = (float) Math.sin(ph);
            float lift = Math.max(0f, (float) Math.sin(ph + Math.PI / 2));
            set(b, side == 0 ? thighL : thighR, 'x', -50f * sw);
            set(b, side == 0 ? calfL : calfR, 'x', 20f + (sw < 0.3f ? 95f * lift : 30f * lift));
            set(b, side == 0 ? footL : footR, 'x', -15f * lift);
            set(b, side == 0 ? upL : upR, 'z', -s * (74f + 2f * sw), 'x', LEAN * 0.9f + 8f + 3f * sw);
            set(b, side == 0 ? foreL : foreR, 'y', -s * 2f);
            fist(b, side, 0.9f);
        }
    }

    private void ball() {
        for (int i = 0; i < n; i++) ident(c, i);
        set(c, hips, 'x', 20f); set(c, spine, 'x', 50f); set(c, spine1, 'x', 45f);
        set(c, neck, 'x', 30f); set(c, head, 'x', 15f);
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? 1f : -1f;
            set(c, side == 0 ? thighL : thighR, 'x', -135f, 'z', -s * 8f);
            set(c, side == 0 ? calfL : calfR, 'x', 150f);
            set(c, side == 0 ? footL : footR, 'x', -30f);
            set(c, side == 0 ? upL : upR, 'z', -s * 60f, 'x', -60f);
            set(c, side == 0 ? foreL : foreR, 'y', -s * 95f);
            fist(c, side, 1f);
        }
        for (int i = 0; i < 6; i++) {
            if (needles[i] >= 0) set(c, needles[i], 'x', -35f);
            if (needles1[i] >= 0) set(c, needles1[i], 'x', -30f);
        }
    }

    private static void nlerp(float[] x, float[] y, int i, float t, float[] out) {
        int o = i * 4;
        float dot = x[o] * y[o] + x[o + 1] * y[o + 1] + x[o + 2] * y[o + 2] + x[o + 3] * y[o + 3];
        float sg = dot < 0 ? -1f : 1f;
        float qx = x[o] * (1 - t) + sg * y[o] * t, qy = x[o + 1] * (1 - t) + sg * y[o + 1] * t;
        float qz = x[o + 2] * (1 - t) + sg * y[o + 2] * t, qw = x[o + 3] * (1 - t) + sg * y[o + 3] * t;
        float l = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        out[o] = qx / l; out[o + 1] = qy / l; out[o + 2] = qz / l; out[o + 3] = qw / l;
    }

    /**
     * @param relYaw  bas - govde yaw farki (derece, MC isareti: + = saga)
     * @param pitch   MC pitch (+ = asagi)
     */
    public void compose(float walkPhase, float walkAmp, float boostW, float boostPhase, float ballW,
                        float relYaw, float pitch, float spinDeg) {
        walk(walkPhase, walkAmp);
        boost(boostPhase);
        ball();
        for (int i = 0; i < n; i++) {
            if (boostW > 0) nlerp(a, b, i, boostW, quats); else System.arraycopy(a, i * 4, quats, i * 4, 4);
            if (ballW > 0) nlerp(quats, c, i, ballW, quats);
        }
        float k = 1f - 0.8f * ballW;
        lookAt(head, relYaw * 0.6f * k, pitch * 0.6f * k);
        lookAt(neck, relYaw * 0.4f * k, pitch * 0.4f * k);

        hasRoot = ballW > 0;
        if (hasRoot) {
            float ang = (float) Math.toRadians(spinDeg * ballW);
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            // Rx(ang)
            float r00 = 1, r01 = 0, r02 = 0, r10 = 0, r11 = ca, r12 = -sa;
            float r20 = 0, r21 = sa, r22 = ca;
            float cx = BALL_CENTER[0], cy = BALL_CENTER[1], cz = BALL_CENTER[2];
            root[0] = r00; root[1] = r01; root[2] = r02;
            root[3] = r10; root[4] = r11; root[5] = r12;
            root[6] = r20; root[7] = r21; root[8] = r22;
            root[9] = cx - (r00 * cx + r01 * cy + r02 * cz);
            root[10] = cy - (r10 * cx + r11 * cy + r12 * cz);
            root[11] = cz - (r20 * cx + r21 * cy + r22 * cz);
        }
    }

    private void lookAt(int bone, float relYaw, float pitch) {
        if (bone < 0) return;
        axis(t1, 'y', -relYaw);
        axis(t2, 'x', pitch);
        mul(t2, t1, t3);                 // look = Rx * Ry  (Q(('y',..),('x',..)))
        float[] cur = {quats[bone * 4], quats[bone * 4 + 1], quats[bone * 4 + 2], quats[bone * 4 + 3]};
        float[] res = new float[4];
        mul(t3, cur, res);
        System.arraycopy(res, 0, quats, bone * 4, 4);
    }
}
