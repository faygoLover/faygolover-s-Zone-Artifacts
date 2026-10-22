#version 150

#moj_import <fog.glsl>

// Zone Artifacts: glows and arcs (additive) — like vanilla lightning, fading out in the world's fog.

uniform vec4 ColorModulator;
uniform float ZaFogStart;
uniform float ZaFogEnd;

in float vertexDistance;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    fragColor = vertexColor * ColorModulator * linear_fog_fade(vertexDistance, ZaFogStart, ZaFogEnd);
}
