#version 150

#moj_import <fl_zone_arts:za_scene.glsl>

// Zone Artifacts: smoke with depth (the Dusk, the Haze). The zone's box is drawn (its back faces); each
// pixel follows its ray from the camera through the box, stopping at whatever is behind (the frame's
// depth), and gathers density: dense in the middle, torn and curling at the edges (3D noise drifting
// with time). ZaSmoke: rgb colour, a = density per block. Positions are camera-relative, in the world's
// orientation; ZaOrigin anchors the noise in the world.

uniform vec3 ZaBoxMin;
uniform vec3 ZaBoxMax;
uniform vec3 ZaOrigin;
uniform float ZaTime;
uniform vec4 ZaSmoke;
uniform float ZaEdge;
uniform int ZaSteps;

in vec3 rayEnd;

out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), f.x), mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), f.x), mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm3(vec3 p) {
    return 0.55 * noise3(p) + 0.3 * noise3(p * 2.07 + 3.1) + 0.15 * noise3(p * 4.13 + 7.7);
}

float density(vec3 p) {
    vec3 w = p + ZaOrigin;
    vec3 drift = vec3(ZaTime * 0.11, ZaTime * 0.05, -ZaTime * 0.08);
    // Edges torn by noise: how deep inside the box (in blocks), pushed in and out.
    vec3 c = (ZaBoxMin + ZaBoxMax) * 0.5;
    vec3 h = (ZaBoxMax - ZaBoxMin) * 0.5;
    vec3 q = abs(p - c) - h;
    float inside = -max(max(q.x, q.y), q.z);
    inside += (fbm3(w * 0.45 + drift) - 0.5) * 2.2 * ZaEdge * 4.0;
    float shape = clamp(inside / max(ZaEdge * 4.0, 0.01), 0.0, 1.0);
    float churn = 0.55 + 0.7 * fbm3(w * 0.8 - drift * 1.3);
    return shape * churn;
}

void main() {
    vec3 dir = normalize(rayEnd);
    vec3 inv = 1.0 / dir;
    vec3 t0 = (ZaBoxMin - vec3(0.0)) * inv;
    vec3 t1 = (ZaBoxMax - vec3(0.0)) * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    float near = max(max(max(tmin.x, tmin.y), tmin.z), 0.0);
    float far = min(min(tmax.x, tmax.y), tmax.z);
    far = min(far, za_scene_distance());
    if (far <= near) {
        discard;
    }
    int steps = max(ZaSteps, 4);
    float step = (far - near) / float(steps);
    float jitter = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    float transmit = 1.0;
    for (int i = 0; i < 64; i++) {
        if (i >= steps) break;
        float t = near + (float(i) + jitter) * step;
        float d = density(dir * t);
        transmit *= exp(-d * ZaSmoke.a * step);
        if (transmit < 0.01) break;
    }
    float a = 1.0 - transmit;
    if (a < 0.003) {
        discard;
    }
    fragColor = vec4(za_dither(ZaSmoke.rgb), a);
}
