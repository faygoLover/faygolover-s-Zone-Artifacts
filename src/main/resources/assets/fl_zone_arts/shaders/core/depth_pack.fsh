#version 150

// Zone Artifacts: the frame's depth packed into a colour texture (24 bits over r, g, b), so our shaders
// can read it while drawing into the frame (soft edges, smoke stopping at walls).

uniform sampler2D DepthSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float d = min(texture(DepthSampler, texCoord).r, 0.99999);
    vec3 enc = fract(d * vec3(1.0, 255.0, 65025.0));
    enc -= enc.yzz * vec3(1.0 / 255.0, 1.0 / 255.0, 0.0);
    fragColor = vec4(enc, 1.0);
}
