#version 150

// Zone Artifacts: the hand drawn back over the psi zone's swimming picture, or its fading after-image
// (the last frame's hand, shifted by Offset) — only where the mask says the hand was.

uniform sampler2D DiffuseSampler;
uniform sampler2D MaskSampler;
uniform vec2 Offset;
uniform float Alpha;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord - Offset;
    float mask = texture(MaskSampler, uv).r;
    if (mask < 0.5) {
        discard;
    }
    fragColor = vec4(texture(DiffuseSampler, uv).rgb, Alpha);
}
