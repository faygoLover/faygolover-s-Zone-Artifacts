#version 150

#moj_import <fog.glsl>
#moj_import <fl_zone_arts:za_scene.glsl>

// Zone Artifacts: glows and arcs (additive) — like vanilla lightning, fading out in the world's fog.

uniform vec4 ColorModulator;
uniform float ZaFogStart;
uniform float ZaFogEnd;

in float vertexDistance;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator * linear_fog_fade(vertexDistance, ZaFogStart, ZaFogEnd) * za_soft(vertexDistance);
    fragColor = vec4(za_dither(color.rgb), color.a);
}
