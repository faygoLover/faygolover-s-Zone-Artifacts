#version 150

// Zone Artifacts: where the hand (and what it holds) is on screen - from the depth: it's nearer than Threshold.

uniform sampler2D DepthSampler;
uniform float Threshold;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    float hand = depth < Threshold ? 1.0 : 0.0;
    fragColor = vec4(hand, hand, hand, 1.0);
}
