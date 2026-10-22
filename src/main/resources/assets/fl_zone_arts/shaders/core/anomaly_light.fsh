#version 150

// Zone Artifacts: the light the anomalies cast on the world around them - for the picture only (the
// world's light levels don't change). Per pixel, from the depth: where the surface is (relative to the
// camera) and which way it faces; up to 8 lights, each a position, a reach, a colour and a strength.
// Out: the lit pixel (the frame's colour, brightened in the light's colour - mostly by its own colour, a
// little on its own so dark surfaces at night still catch it); untouched pixels are discarded.

uniform sampler2D DepthSampler;
uniform sampler2D SceneSampler;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform float LightData[64];
uniform int LightCount;
uniform float HandDepth;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    if (depth >= 0.99999 || depth < HandDepth) {
        discard;
    }
    vec4 view = InvProjMat * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 p = (InvViewMat * vec4(view.xyz / view.w, 1.0)).xyz;
    vec3 n = cross(dFdx(p), dFdy(p));
    float nl = length(n);
    n = nl > 1.0e-6 ? n / nl : vec3(0.0, 1.0, 0.0);
    if (dot(n, -p) < 0.0) n = -n;
    vec3 light = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= LightCount) break;
        int k = i * 8;
        vec3 lp = vec3(LightData[k], LightData[k + 1], LightData[k + 2]);
        float reach = LightData[k + 3];
        vec3 d = lp - p;
        float dist = length(d);
        if (dist >= reach) continue;
        float fall = 1.0 - dist / reach;
        fall *= fall;
        float facing = 0.3 + 0.7 * max(dot(n, d / max(dist, 1.0e-4)), 0.0);
        light += vec3(LightData[k + 4], LightData[k + 5], LightData[k + 6]) * LightData[k + 7] * fall * facing;
    }
    if (light.r + light.g + light.b < 0.002) {
        discard;
    }
    vec3 scene = texture(SceneSampler, texCoord).rgb;
    fragColor = vec4(scene + light * (scene * 0.9 + 0.12), 1.0);
}
