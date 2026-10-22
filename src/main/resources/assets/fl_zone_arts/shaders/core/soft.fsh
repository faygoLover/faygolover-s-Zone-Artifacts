#version 150

#moj_import <fog.glsl>
#moj_import <fl_zone_arts:za_scene.glsl>

// Zone Artifacts: soft alpha-blended shapes (jelly, gas, dark bands) - position_color plus the world's fog,
// so they sink into it (the Haze, the far plane) like everything else.

uniform vec4 ColorModulator;
uniform float ZaFogStart;
uniform float ZaFogEnd;
uniform vec4 ZaFogColor;

in float vertexDistance;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a == 0.0) {
        discard;
    }
    color.a *= za_soft(vertexDistance);
    color = linear_fog(color, vertexDistance, ZaFogStart, ZaFogEnd, ZaFogColor);
    fragColor = vec4(za_dither(color.rgb), color.a);
}
