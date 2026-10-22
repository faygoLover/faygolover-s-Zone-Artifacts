#version 150

// Zone Artifacts: the psi sight's after-image of the hand - the fading trail (premultiplied), shifted by Offset.

uniform sampler2D DiffuseSampler;
uniform vec2 Offset;
uniform float Alpha;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = texture(DiffuseSampler, texCoord - Offset) * Alpha;
}
