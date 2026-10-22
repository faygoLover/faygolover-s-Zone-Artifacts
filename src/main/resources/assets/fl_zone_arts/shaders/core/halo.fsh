#version 150

#moj_import <fog.glsl>
#moj_import <fl_zone_arts:za_scene.glsl>

// Zone Artifacts: the glow of the air round a fireball - a soft, wide halo falling off smoothly (a
// gaussian, worked out per pixel), additive. UV runs -1..1 over the quad; the colour's alpha is its strength.

uniform vec4 ColorModulator;
uniform float ZaFogStart;
uniform float ZaFogEnd;

in float vertexDistance;
in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float r2 = dot(texCoord, texCoord);
    if (r2 >= 1.0) {
        discard;
    }
    float glow = (exp(-r2 * 4.0) - exp(-4.0)) / (1.0 - exp(-4.0));
    float a = vertexColor.a * glow * linear_fog_fade(vertexDistance, ZaFogStart, ZaFogEnd) * za_soft(vertexDistance);
    fragColor = vec4(za_dither(vertexColor.rgb * ColorModulator.rgb), a);
}
