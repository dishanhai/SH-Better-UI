// Author: dishanhai
package com.dishanhai.sh_better_ui.client.spatial;

import com.dishanhai.sh_better_ui.spatial.internal.SpatialMesh.Vertex;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** Loader-specific vertex submission signatures. */
final class PlatformSpatialVertices {
    private PlatformSpatialVertices() {}

    static void emit(VertexConsumer out, Matrix4f pose, Vertex v, int color, boolean sdf) {
        var vertex = out.addVertex(pose, (float) v.x(), (float) v.y(), 0);
        if (sdf) vertex.setUv((float) v.u(), (float) v.v());
        vertex.setColor(color);
    }
}
