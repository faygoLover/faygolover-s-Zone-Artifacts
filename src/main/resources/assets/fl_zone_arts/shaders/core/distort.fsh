#version 150

// Zone Artifacts: refraction worked out per pixel. The mesh gives where each spot samples the frame
// (smoothly between its points); on top of that the air boils (noise moving with time) and the
// colours part along the boil. Vertex colour: r = boiling, g = colours apart, b = blur, a = how much shows.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float ZaTime;
uniform float ZaAspect;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 uv = texCoord;
    vec2 q = vec2(uv.x * ZaAspect, uv.y) * 9.0;
    vec2 boil = vec2(fbm(q + vec2(ZaTime * 0.6, 0.0)), fbm(q + vec2(3.1, -ZaTime * 0.5))) - 0.5;
    uv += boil * vertexColor.r * 0.06;
    float len = length(boil);
    vec2 dir = len > 1.0e-4 ? boil / len : vec2(1.0, 0.0);
    vec2 apart = dir * vertexColor.g * 0.03;
    float blur = vertexColor.b * 0.02;
    vec3 c;
    if (blur > 1.0e-4) {
        vec2 bx = vec2(blur, 0.0);
        vec2 by = vec2(0.0, blur);
        c.r = (texture(Sampler0, uv + apart).r * 2.0 + texture(Sampler0, uv + apart + bx).r + texture(Sampler0, uv + apart - bx).r
                + texture(Sampler0, uv + apart + by).r + texture(Sampler0, uv + apart - by).r) / 6.0;
        c.g = (texture(Sampler0, uv).g * 2.0 + texture(Sampler0, uv + bx).g + texture(Sampler0, uv - bx).g
                + texture(Sampler0, uv + by).g + texture(Sampler0, uv - by).g) / 6.0;
        c.b = (texture(Sampler0, uv - apart).b * 2.0 + texture(Sampler0, uv - apart + bx).b + texture(Sampler0, uv - apart - bx).b
                + texture(Sampler0, uv - apart + by).b + texture(Sampler0, uv - apart - by).b) / 6.0;
    } else {
        c.r = texture(Sampler0, uv + apart).r;
        c.g = texture(Sampler0, uv).g;
        c.b = texture(Sampler0, uv - apart).b;
    }
    fragColor = vec4(c, vertexColor.a) * ColorModulator;
}
