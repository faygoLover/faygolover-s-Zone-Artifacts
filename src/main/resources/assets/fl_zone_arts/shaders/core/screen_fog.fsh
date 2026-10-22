#version 150

// Zone Artifacts: the Haze's / the Dusk's fog over a shader pack's finished frame (the packs ignore
// vanilla's fog distances) - vanilla's linear fog, from the depth. The hand (nearer than HandDepth) stays clear.

uniform sampler2D DepthSampler;
uniform mat4 InvProjMat;
uniform float ZaFogStart;
uniform float ZaFogEnd;
uniform vec4 ZaFogColor;
uniform float HandDepth;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    if (depth < HandDepth) {
        discard;
    }
    vec4 view = InvProjMat * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    float dist = length(view.xyz / view.w);
    float fog = dist <= ZaFogStart ? 0.0 : (dist < ZaFogEnd ? smoothstep(ZaFogStart, ZaFogEnd, dist) : 1.0);
    fragColor = vec4(ZaFogColor.rgb, fog * ZaFogColor.a);
}
