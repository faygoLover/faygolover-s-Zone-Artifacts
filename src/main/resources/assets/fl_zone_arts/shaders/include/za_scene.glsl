#version 150

// Zone Artifacts: what our shaders know of the frame — the packed depth (see depth_pack.fsh), turned
// back into the distance of whatever is behind this pixel; and a little noise against banding.

uniform sampler2D ZaDepth;
uniform mat4 ZaInvProj;
uniform vec2 ZaScreen;
uniform float ZaSoft;
uniform float ZaDither;

float za_scene_distance() {
    vec2 uv = gl_FragCoord.xy / ZaScreen;
    float d = dot(texture(ZaDepth, uv).rgb, vec3(1.0, 1.0 / 255.0, 1.0 / 65025.0));
    vec4 v = ZaInvProj * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    return length(v.xyz / v.w);
}

/** 0..1: how much shows of something this far away — fading out where it runs into the world behind. */
float za_soft(float distance) {
    if (ZaSoft <= 0.0) return 1.0;
    return clamp((za_scene_distance() - distance) / ZaSoft, 0.0, 1.0);
}

vec3 za_dither(vec3 color) {
    if (ZaDither <= 0.0) return color;
    float n = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    return color + (n - 0.5) * ZaDither / 255.0;
}
