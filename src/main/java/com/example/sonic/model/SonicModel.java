package com.example.sonic.model;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Iskeletli Sonic modeli (sonic.bin). Minecraft'tan bagimsiz saf Java:
 * kemik rotasyonlarindan (quaternion) CPU skinning yapar.
 *
 * Rotasyon duzeni: her kemigin rotasyonu T-pose (bind) eksenlerinde, kemigin pivotu etrafinda
 * uygulanir ve ust kemiklerin hareketiyle birlestirilir:  C_b = C_parent * T(p) * R * T(-p)
 */
public final class SonicModel {
    public final int boneCount;
    public final int[] parent;
    public final float[] pivot;          // 3 * boneCount
    public final String[] names;
    public final Map<String, Integer> boneIndex = new HashMap<>();

    public final int vertCount;
    public final float[] pos, nrm, uv;   // bind pose
    public final short[] joints;         // 4 * vertCount
    public final float[] weights;        // 4 * vertCount

    public final int[] groupTex;         // texture slot (0..3) per group
    public final int[][] groupTris;      // 3 indices per triangle

    public final float[] outPos, outNrm; // skin() sonucu
    private final float[] mats;          // 12 floats / bone: R(9) + t(3)
    private final int[] order;

    private SonicModel(DataInputStream in) throws IOException {
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'S' || magic[1] != 'N' || magic[2] != 'C' || magic[3] != '1')
            throw new IOException("Bad sonic.bin magic");
        boneCount = in.readInt();
        parent = new int[boneCount];
        pivot = new float[boneCount * 3];
        names = new String[boneCount];
        for (int i = 0; i < boneCount; i++) {
            parent[i] = in.readInt();
            pivot[i * 3] = in.readFloat();
            pivot[i * 3 + 1] = in.readFloat();
            pivot[i * 3 + 2] = in.readFloat();
            int len = in.readUnsignedShort();
            byte[] nb = new byte[len];
            in.readFully(nb);
            names[i] = new String(nb, java.nio.charset.StandardCharsets.UTF_8);
            boneIndex.put(names[i], i);
        }
        vertCount = in.readInt();
        pos = new float[vertCount * 3];
        nrm = new float[vertCount * 3];
        uv = new float[vertCount * 2];
        joints = new short[vertCount * 4];
        weights = new float[vertCount * 4];
        for (int i = 0; i < vertCount; i++) {
            for (int k = 0; k < 3; k++) pos[i * 3 + k] = in.readFloat();
            for (int k = 0; k < 3; k++) nrm[i * 3 + k] = in.readFloat();
            uv[i * 2] = in.readFloat();
            uv[i * 2 + 1] = in.readFloat();
            for (int k = 0; k < 4; k++) joints[i * 4 + k] = (short) in.readUnsignedShort();
            for (int k = 0; k < 4; k++) weights[i * 4 + k] = in.readFloat();
        }
        int ng = in.readInt();
        groupTex = new int[ng];
        groupTris = new int[ng][];
        for (int g = 0; g < ng; g++) {
            groupTex[g] = in.readInt();
            int nt = in.readInt();
            int[] t = new int[nt * 3];
            for (int i = 0; i < t.length; i++) t[i] = in.readInt();
            groupTris[g] = t;
        }
        outPos = new float[vertCount * 3];
        outNrm = new float[vertCount * 3];
        mats = new float[boneCount * 12];

        // ebeveyn -> cocuk sirasi
        order = new int[boneCount];
        boolean[] done = new boolean[boneCount];
        int n = 0;
        for (int i = 0; i < boneCount; i++) n = visit(i, done, order, n);
    }

    private int visit(int i, boolean[] done, int[] ord, int n) {
        if (done[i]) return n;
        if (parent[i] >= 0) n = visit(parent[i], done, ord, n);
        done[i] = true;
        ord[n] = i;
        return n + 1;
    }

    public static SonicModel load(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw, 1 << 16))) {
            return new SonicModel(in);
        }
    }

    public int bone(String name) {
        Integer i = boneIndex.get(name);
        return i == null ? -1 : i;
    }

    /**
     * @param quats 4 * boneCount (x,y,z,w); kimlik = (0,0,0,1)
     * @param root  null veya 12 float (R 3x3 satir-major + t) : tum vucuda uygulanir (or. top donusu)
     */
    public void skin(float[] quats, float[] root) {
        for (int bi = 0; bi < boneCount; bi++) {
            int i = order[bi];
            float x = quats[i * 4], y = quats[i * 4 + 1], z = quats[i * 4 + 2], w = quats[i * 4 + 3];
            float r00 = 1 - 2 * (y * y + z * z), r01 = 2 * (x * y - z * w), r02 = 2 * (x * z + y * w);
            float r10 = 2 * (x * y + z * w), r11 = 1 - 2 * (x * x + z * z), r12 = 2 * (y * z - x * w);
            float r20 = 2 * (x * z - y * w), r21 = 2 * (y * z + x * w), r22 = 1 - 2 * (x * x + y * y);
            float px = pivot[i * 3], py = pivot[i * 3 + 1], pz = pivot[i * 3 + 2];
            float tx = px - (r00 * px + r01 * py + r02 * pz);
            float ty = py - (r10 * px + r11 * py + r12 * pz);
            float tz = pz - (r20 * px + r21 * py + r22 * pz);
            int m = i * 12;
            int p = parent[i];
            if (p < 0) {
                if (root == null) {
                    set(m, r00, r01, r02, r10, r11, r12, r20, r21, r22, tx, ty, tz);
                } else {
                    mul(root, 0, r00, r01, r02, r10, r11, r12, r20, r21, r22, tx, ty, tz, m);
                }
            } else {
                mul(mats, p * 12, r00, r01, r02, r10, r11, r12, r20, r21, r22, tx, ty, tz, m);
            }
        }
        for (int v = 0; v < vertCount; v++) {
            float x = pos[v * 3], y = pos[v * 3 + 1], z = pos[v * 3 + 2];
            float nx = nrm[v * 3], ny = nrm[v * 3 + 1], nz = nrm[v * 3 + 2];
            float ox = 0, oy = 0, oz = 0, onx = 0, ony = 0, onz = 0;
            for (int k = 0; k < 4; k++) {
                float wt = weights[v * 4 + k];
                if (wt == 0f) continue;
                int m = joints[v * 4 + k] * 12;
                ox += wt * (mats[m] * x + mats[m + 1] * y + mats[m + 2] * z + mats[m + 9]);
                oy += wt * (mats[m + 3] * x + mats[m + 4] * y + mats[m + 5] * z + mats[m + 10]);
                oz += wt * (mats[m + 6] * x + mats[m + 7] * y + mats[m + 8] * z + mats[m + 11]);
                onx += wt * (mats[m] * nx + mats[m + 1] * ny + mats[m + 2] * nz);
                ony += wt * (mats[m + 3] * nx + mats[m + 4] * ny + mats[m + 5] * nz);
                onz += wt * (mats[m + 6] * nx + mats[m + 7] * ny + mats[m + 8] * nz);
            }
            float l = (float) Math.sqrt(onx * onx + ony * ony + onz * onz);
            if (l > 1e-6f) { onx /= l; ony /= l; onz /= l; } else { onx = 0; ony = 1; onz = 0; }
            outPos[v * 3] = ox; outPos[v * 3 + 1] = oy; outPos[v * 3 + 2] = oz;
            outNrm[v * 3] = onx; outNrm[v * 3 + 1] = ony; outNrm[v * 3 + 2] = onz;
        }
    }

    private void set(int m, float a, float b, float c, float d, float e, float f, float g, float h, float i,
                     float tx, float ty, float tz) {
        mats[m] = a; mats[m + 1] = b; mats[m + 2] = c;
        mats[m + 3] = d; mats[m + 4] = e; mats[m + 5] = f;
        mats[m + 6] = g; mats[m + 7] = h; mats[m + 8] = i;
        mats[m + 9] = tx; mats[m + 10] = ty; mats[m + 11] = tz;
    }

    /** out = P * (R,t)   (P = src[off..off+11], 12 float: R satir-major, t son 3) */
    private void mul(float[] src, int off, float r00, float r01, float r02, float r10, float r11, float r12,
                     float r20, float r21, float r22, float tx, float ty, float tz, int outM) {
        float a00 = src[off], a01 = src[off + 1], a02 = src[off + 2];
        float a10 = src[off + 3], a11 = src[off + 4], a12 = src[off + 5];
        float a20 = src[off + 6], a21 = src[off + 7], a22 = src[off + 8];
        float at0 = src[off + 9], at1 = src[off + 10], at2 = src[off + 11];
        mats[outM] = a00 * r00 + a01 * r10 + a02 * r20;
        mats[outM + 1] = a00 * r01 + a01 * r11 + a02 * r21;
        mats[outM + 2] = a00 * r02 + a01 * r12 + a02 * r22;
        mats[outM + 3] = a10 * r00 + a11 * r10 + a12 * r20;
        mats[outM + 4] = a10 * r01 + a11 * r11 + a12 * r21;
        mats[outM + 5] = a10 * r02 + a11 * r12 + a12 * r22;
        mats[outM + 6] = a20 * r00 + a21 * r10 + a22 * r20;
        mats[outM + 7] = a20 * r01 + a21 * r11 + a22 * r21;
        mats[outM + 8] = a20 * r02 + a21 * r12 + a22 * r22;
        mats[outM + 9] = a00 * tx + a01 * ty + a02 * tz + at0;
        mats[outM + 10] = a10 * tx + a11 * ty + a12 * tz + at1;
        mats[outM + 11] = a20 * tx + a21 * ty + a22 * tz + at2;
    }
}
