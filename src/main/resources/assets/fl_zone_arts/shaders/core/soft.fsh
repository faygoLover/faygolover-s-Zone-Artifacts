#version 150

#moj_import <fog.glsl>

// Zone Artifacts: soft alpha-blended shapes (jelly, gas, dark bands) — position_color plus the world's fog,
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
    fragColor = linear_fog(color, vertexDistance, ZaFogStart, ZaFogEnd, ZaFogColor);
}
