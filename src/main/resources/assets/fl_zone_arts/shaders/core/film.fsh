#version 150

#moj_import <fog.glsl>
#moj_import <fl_zone_arts:za_scene.glsl>

// Zone Artifacts: a soap bubble's film worked out per pixel — nearly clear where one looks straight
// through it, dense towards the outline (the Fresnel effect); thin-film rainbow swirls drifting over it
// (the film's thickness from noise); a sharp highlight. Vertex colour: rgb = how lit it is where it
// floats, a = how bright (charging bubbles swell and brighten). Normal: the sphere's, in the world.

uniform vec4 ColorModulator;
uniform float ZaFogStart;
uniform float ZaFogEnd;
uniform vec4 ZaFogColor;
uniform float ZaTime;

in float vertexDistance;
in vec4 vertexColor;
in vec3 normal;
in vec3 toEye;

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

void main() {
    vec3 n = normalize(normal);
    vec3 v = normalize(toEye);
    float facing = abs(dot(n, v));
    float rim = 1.0 - facing;
    float fresnel = pow(rim, 2.2);
    // The film's thickness, swirling slowly, a little thicker at the bottom (it drains down).
    float thick = 0.45 + 0.35 * noise3(n * 2.6 + vec3(ZaTime * 0.12, -ZaTime * 0.07, ZaTime * 0.05)) - 0.12 * n.y;
    float phase = thick * 2.4 + rim * 0.6;
    vec3 rainbow = 0.5 + 0.5 * cos(6.2832 * (phase * vec3(1.0, 1.12, 1.27)));
    float alpha = (0.04 + 0.66 * fresnel) * vertexColor.a;
    vec3 color = mix(vec3(1.0), rainbow, 0.35 + 0.5 * rim) * vertexColor.rgb;
    // The highlight: a sun up to one side.
    vec3 sun = normalize(vec3(-0.45, 0.75, 0.5));
    float spec = pow(max(dot(reflect(-sun, n), v), 0.0), 70.0);
    color += vec3(spec * 1.2) * vertexColor.rgb;
    alpha = min(alpha + spec * 0.8, 0.9);
    alpha *= za_soft(vertexDistance);
    vec4 c = linear_fog(vec4(color, alpha) * ColorModulator, vertexDistance, ZaFogStart, ZaFogEnd, ZaFogColor);
    fragColor = vec4(za_dither(c.rgb), c.a);
}
