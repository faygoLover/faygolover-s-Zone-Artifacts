#version 150

// Zone Artifacts: where the hand (and what it holds) is on screen, without a shader pack - what changed
// between the world alone (copied before the hand was drawn) and the whole frame.

uniform sampler2D WorldSampler;
uniform sampler2D FrameSampler;
uniform float Threshold;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 d = abs(texture(FrameSampler, texCoord).rgb - texture(WorldSampler, texCoord).rgb);
    float hand = max(d.r, max(d.g, d.b)) > Threshold ? 1.0 : 0.0;
    fragColor = vec4(hand, hand, hand, 1.0);
}
